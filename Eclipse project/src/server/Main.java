package server;

import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

import org.apache.thrift.TException;
import org.apache.thrift.transport.TTransport;

import graph.Periodic;
import graph.Settings;
import graph.Vertex;
import thrift.NodeData;

import org.apache.thrift.transport.TSocket;
import org.apache.thrift.protocol.TBinaryProtocol;
import org.apache.thrift.protocol.TProtocol;

public class Main {

	public void checkInterval(int value, int a, int b) {
		
	}
	
	public static void main(String [] args) {
		Thread thread_server;
		Thread thread_client_1;
		Thread thread_client_2;
		Thread thread_client_3;
		Settings settings;
		
		settings = new Settings(Paths.get("").toAbsolutePath().toString()+"\\settings.ini");
		/*NodeData	nodes[] = new NodeData[5];
					nodes[0] = new NodeData(0,"localhost", 3030);
					nodes[1] = new NodeData(1,"localhost", 3031);
					nodes[2] = new NodeData(2,"localhost", 3032);
					nodes[3] = new NodeData(3,"localhost", 3033);
					nodes[4] = new NodeData(4,"localhost", 3034);*/
		
		int quantidadeServidores = 3;
		int m = 4;
		int maxInt = (int) Math.pow(2,m)-1;
		int portaBase = 3030;
		
		NodeData	nodes[] = new NodeData[quantidadeServidores];
		Random r = new Random();
		ArrayList<Integer> ids = new ArrayList<Integer>();   
		
		Server servers[] = new Server[quantidadeServidores];
		Thread thread_servers[] = new Thread[quantidadeServidores];
		
		//Gera as portas e id's aleatorios (sem repetição) dos servidores
		/*while (ids.size() < quantidadeServidores) {

		    int random = r .nextInt(maxInt+1);
		    if (!ids.contains(random)) {
		        ids.add(random);
		    }
		}*/
		ids.add(5);
		ids.add(7);
		ids.add(11);
		
		
		
		Collections.sort(ids);
	
		for(int i=0;i<quantidadeServidores;i++) {
			nodes[i] = new NodeData(ids.get(i),"localhost",portaBase+i);
		}

		
		//Inicializa os servidores e suas devidas fingerTable
		for(int s=0; s<quantidadeServidores; s++) {	
			
			NodeData fingerTable[] = new NodeData[m];
			
			for(int fingerIndex=0;fingerIndex<m;fingerIndex++) { //Inicializa a fingerTable desse servidor
				
				ArrayList<NodeData> potenciaisSucessores = new ArrayList<>();
				
				int esquerda 	= (int) ((nodes[s].id + Math.pow(2, fingerIndex))%Math.pow(2, m));
				int direita 	= (int) ((nodes[s].id + Math.pow(2, fingerIndex+1))%Math.pow(2, m));
				
				boolean intervaloCiclico = false;
				if(esquerda>direita) 
					intervaloCiclico = true;
				
				
				for(int servidorIndex=0;servidorIndex<quantidadeServidores;servidorIndex++) {
					
					if(!intervaloCiclico) {
						if( //Intervalor fechado
								(nodes[servidorIndex].id>=esquerda)
								&&
								(nodes[servidorIndex].id<direita)
						  ) {
								potenciaisSucessores.add(nodes[servidorIndex]);
						
						}
					}else { //Intervalo Ciclico

						if(nodes[servidorIndex].id>=esquerda)
							potenciaisSucessores.add(nodes[servidorIndex]);
						if(nodes[servidorIndex].id < (direita))
							potenciaisSucessores.add(nodes[servidorIndex]);
					}
					
				}
				
				NodeData sucessor = null;
				
				if(potenciaisSucessores.isEmpty()) { //Se não há ninguém nesse intervalo, pega o próximo sucessor
					//verifica se tem algum á direita
					for(int servidorIndex=0;servidorIndex<quantidadeServidores;servidorIndex++) {
						if(nodes[servidorIndex].id>=direita) {
							potenciaisSucessores.add(nodes[servidorIndex]);
							break;
						}
					}
					//se não há nenhum à direita, pega o primeiro da lista (ou primeiro a esquerda)
					if(potenciaisSucessores.isEmpty()) {
						potenciaisSucessores.add(nodes[0]);
					}
				}
				
				
				if(!intervaloCiclico) { //Se é um intervalo fechado, só pegar o primeiro da lista (menor)
					sucessor = potenciaisSucessores.get(0);
				}else {
					NodeData maior = potenciaisSucessores.get(0);
					for(int a=1;a<potenciaisSucessores.size();a++) { //Se não, pega o menor do intervalo da equerda até 0 e depois 0 até direita
						if(potenciaisSucessores.get(a).id>maior.id)
							maior = potenciaisSucessores.get(a);
					}
					
					if(maior.id>direita)
						sucessor = maior;
					else {
						NodeData menor = potenciaisSucessores.get(0);
						for(int a=1;a<potenciaisSucessores.size();a++) { //Se não, pega o menor do intervalo da equerda até 0 e depois 0 até direita
							if(menor.id>potenciaisSucessores.get(a).id)
								menor = potenciaisSucessores.get(a);
						}
						sucessor = menor;
					}
						
				}
					
				
				
				
				/*for(int p=1;p<potenciaisSucessores.size();p++) {
					if(potenciaisSucessores.get(p).id<menor.id)
						menor = potenciaisSucessores.get(p);
				}*/
				
				fingerTable[fingerIndex] = sucessor;
			}
			
			List<NodeData> fingerTableList = new ArrayList<NodeData>();
			
			for(int l=0;l<m;l++)
				fingerTableList.add(fingerTable[l]);
			
			
			
			
			try {
				servers[s] = new Server();
				servers[s].init(nodes[s].port);
				servers[s].handler.init_node(m, nodes[s]);
				servers[s].handler.initFingerTable(fingerTableList);
				
			} catch (Exception e) {
				e.printStackTrace();
			}
			
			System.out.println("+-----------------------+");
			System.out.println("| ["+s+"]Nodo "+nodes[s].id+"\t\t|\n");
				for(int h =0;h<m;h++) {
					int esquerda 	= (int) ((nodes[s].id + Math.pow(2, h))%Math.pow(2, m));
					int direita 	= (int) ((nodes[s].id + Math.pow(2, h+1))%Math.pow(2, m));
					
					System.out.println("| "+h+" ["+esquerda+","+direita+")  ->  "+fingerTable[h].id+"\t|");
				}
				System.out.println("+-----------------------+");
			
		}
		
		//Inicializa os predecessores de cada servidor
		for(int s=0;s<quantidadeServidores;s++) {
			NodeData predecessor;
			if(s==0)
				predecessor = nodes[quantidadeServidores-1];
			else
				predecessor = nodes[s-1];
			
			servers[s].handler.initPredecessor(predecessor);
			thread_servers[s] = new Thread(servers[s]);
			thread_servers[s].start();
		}
		

		
		Client c = new Client();
		c.init(nodes[0].ip, nodes[0].port, nodes[0].id);
		c.CURRENT_STATE = c.USE_PRESET_2;
		
		Thread cc = new Thread(c);
		cc.start();
		
		
		/*
		//---------------------------------------------
		//	Nodo0
		//---------------------------------------------
		Server nodo0 = new Server();
		nodo0.init(nodes[0].port);
		c.init(nodes[0].ip, nodes[0].port);
		
		thread_server = new Thread(nodo0);
		thread_server.start();
		
		try {
			c.open();
			c.getService().init_node(3, nodes[0]);
			c.close();
		} catch (TException e) {
			e.printStackTrace();
		}
		
		Periodic p0 = new Periodic(nodo0);
		Thread t0 = new Thread(p0);
		t0.start();
		
		//---------------------------------------------
		//	Nodo1
		//---------------------------------------------
		Server nodo1 = new Server();
		nodo1.init(nodes[1].port);
		c.init(nodes[1].ip, nodes[1].port);
		
		thread_server = new Thread(nodo1);
		thread_server.start();

		try {
			c.open();
			c.getService().init_node(3, nodes[1]);
			c.getService().join(nodes[0]);
			c.getService().printFingerTable();
			c.close();
		} catch (TException e) {
			e.printStackTrace();
		}
		System.out.println("aa");
		Periodic p1 = new Periodic(nodo1);
		Thread t1 = new Thread(p1);
		t1.start();
		
		//---------------------------------------------
		//	Nodo2
		//---------------------------------------------
		Server nodo2 = new Server();
		nodo2.init(nodes[2].port);
		c.init(nodes[2].ip, nodes[2].port);
		
		thread_server = new Thread(nodo2);
		thread_server.start();

		try {
			c.open();
			c.getService().init_node(3, nodes[2]);
			c.getService().join(nodes[0]);
			c.getService().printFingerTable();
			c.close();
		} catch (TException e) {
			e.printStackTrace();
		}
		
		Periodic p2 = new Periodic(nodo2);
		Thread t2 = new Thread(p2);
		t2.start();*/

		
		
		
		
		
		
		/*Client c1 = new Client();
		c1.id = 1;
		c1.init();
		
		Client c2 = new Client();
		c2.id = 2;
		c2.init();
		
		Client c3 = new Client();
		c3.id = 3;
		c3.init();

		
		thread_client_1 = new Thread(c1);
		thread_client_1.start();
		
		thread_client_2 = new Thread(c2);
		thread_client_2.start();
		
		thread_client_3 = new Thread(c3);
		thread_client_3.start();*/

		
	  }
}
