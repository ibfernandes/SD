package server;

import java.io.File;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

import org.apache.thrift.TException;
import org.apache.thrift.transport.TTransport;
import org.apache.thrift.transport.TTransportException;

import agriculturamachinebook.AgriculturaMachineBook;
import copycat_command.AddEdgeCommand;
import graph.Periodic;
import graph.Service;
import graph.Settings;
import graph.Vertex;
import io.atomix.catalyst.transport.Address;
import io.atomix.catalyst.transport.netty.NettyTransport;
import io.atomix.copycat.client.CopycatClient;
import io.atomix.copycat.server.CopycatServer;
import io.atomix.copycat.server.storage.Storage;
import io.atomix.copycat.server.storage.StorageLevel;
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
		
		int fatorReplicacao = 3;
		int clusterOffset = 100;
		
		int quantidadeServidores = 2 * fatorReplicacao;
		int quantidadeDeClusters = quantidadeServidores / fatorReplicacao;
		int m = 4;
		int maxInt = (int) Math.pow(2,m)-1;
		int portaBase = 8000;

		NodeData	nodes[] = new NodeData[quantidadeServidores];
		NodeData	nodesPorCluster[][] = new NodeData[quantidadeDeClusters][fatorReplicacao];
		Random r = new Random();
		ArrayList<Integer> ids = new ArrayList<Integer>();   
		
		Server servers[] = new Server[quantidadeServidores];
		Thread thread_servers[] = new Thread[quantidadeServidores];
		
		/** 
		 * Gera as portas e id's aleatorios (sem repetição) dos servidores
		 */
		/*while (ids.size() < quantidadeServidores) {

		    int random = r .nextInt(maxInt+1);
		    if (!ids.contains(random)) {
		        ids.add(random);
		    }
		}*/
		while (ids.size() < quantidadeDeClusters) {

		    int random = r .nextInt(maxInt+1);
		    if (!ids.contains(random)) {
		        ids.add(random);
		    }
		}
		
		//PRESET DE TESTES
		/*ids.add(5);
		ids.add(7);
		ids.add(11);
		ids.add(14);
		ids.add(17);
		ids.add(19);*/
		
		Collections.sort(ids);
		for(int i=0; i < quantidadeDeClusters; i++) {
			for(int p=i*fatorReplicacao;p<(i*fatorReplicacao)+fatorReplicacao;p++) { 
				nodes[p] = new NodeData(ids.get(i),"localhost",portaBase+p,i);
				nodesPorCluster[i][p%fatorReplicacao] = nodes[p];
			}
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
				
				fingerTable[fingerIndex] = sucessor;
			}
			
			List<NodeData> fingerTableList = new ArrayList<NodeData>();
			
			for(int l=0;l<m;l++)
				fingerTableList.add(fingerTable[l]);
			
			HashMap<Integer, NodeData[]> fingerCluster;
			fingerCluster = new HashMap<Integer,NodeData[]>();
			
			for(int l=0;l<m;l++) {
				int clusterWhichBelongs = fingerTable[l].clusterId;
				NodeData nodesOfThatCluster[] = new NodeData[fatorReplicacao];
				
				for(int lk =0; lk<fatorReplicacao; lk++) {
					nodesOfThatCluster[lk] = nodesPorCluster[clusterWhichBelongs][lk];
				}
				
				fingerCluster.put(l, nodesOfThatCluster);
			}
			
			
			
			try {
				servers[s] = new Server();
				servers[s].init(nodes[s].port);
				servers[s].handler.init_node(m, nodes[s]);
				servers[s].handler.initFingerTable(fingerTableList);
				servers[s].handler.initFingerCluster(fingerCluster);
				servers[s].handler.initOwnCluster(nodesPorCluster[nodes[s].clusterId]);
				
			} catch (Exception e) {
				e.printStackTrace();
			}
			
			//Finger table para um único nodo
			/*System.out.println("+-----------------------+");
			System.out.println("| ["+s+"]Nodo ID "+nodes[s].id+"\t\t|\n");
				for(int h =0;h<m;h++) {
					int esquerda 	= (int) ((nodes[s].id + Math.pow(2, h))%Math.pow(2, m));
					int direita 	= (int) ((nodes[s].id + Math.pow(2, h+1))%Math.pow(2, m));
					
					System.out.println("| "+h+" ["+esquerda+","+direita+")  \t->  "+fingerTable[h].id+"\t|");
				}
			System.out.println("+-----------------------+");*/
			
			//Finger table com cluster
			System.out.println("+-------------------------------------------------------------------------------+");
			System.out.println("| ["+s+"]Nodo ID "+nodes[s].id+" - Cluster "+nodes[s].clusterId+" - "+nodes[s].ip+":"+nodes[s].port+"\t\t\t\t\t|\n");
				for(int h =0;h<m;h++) {
					int esquerda 	= (int) ((nodes[s].id + Math.pow(2, h))%Math.pow(2, m));
					int direita 	= (int) ((nodes[s].id + Math.pow(2, h+1))%Math.pow(2, m));
					
					NodeData nodesOfThatCluster[] = fingerCluster.get(h);
					
					System.out.printf("| "+h+" ["+esquerda+","+direita+")  \t-> Node [ID "+fingerTable[h].id+"] - Cluster ["+nodesOfThatCluster[0].clusterId+"] - Portas [");
					
					for(int jj = 0; jj< fatorReplicacao; jj++)
						System.out.printf(nodesOfThatCluster[jj].port+", ");
					
					
					System.out.printf("]\t|\n");
				}
				System.out.println("+-------------------------------------------------------------------------------+");
			
			}
		
		
		//Inicializa os predecessores 
		for(int s=0;s<quantidadeServidores;s++) {
			
			NodeData predecessor;
			if(s==0)
				predecessor = nodes[quantidadeServidores-1];
			else
				predecessor = nodes[s-1];
			
			servers[s].handler.initPredecessor(predecessor);
		}
		
		//Inicia Raft server configurations
		for(int s=0;s<quantidadeServidores;s++) 
			servers[s].handler.init_raft(fatorReplicacao, clusterOffset);
		
		
		//Inicializa os clusters de cada nodo principal
		for(int s=0;s<quantidadeDeClusters;s++) {
			ArrayList<Address> cluster = new ArrayList<Address>();
			
			//Armazena na var cluster todos os endereços que pertencem a este cluster
			System.out.println("Configurando o cluster "+s);
			for(int k=s*fatorReplicacao;k<(s*fatorReplicacao)+fatorReplicacao;k++) {
				System.out.println("Adicionando ("+nodes[k].ip+", "+nodes[k].port+") ao cluster.");
				cluster.add(new Address(nodes[k].ip, nodes[k].port+clusterOffset));
			}
			
			for(int k=s*fatorReplicacao;k<(s*fatorReplicacao)+fatorReplicacao;k++) {
				int aux = k;
				Thread thread = new Thread(new Runnable() {
				    public void run(){
				    	Server s = servers[aux];
				    	
				    	if((aux%fatorReplicacao)==0)
				    		s.handler.raft_server.bootstrap().join();
				    	else {
				    		s.handler.raft_server.join(cluster).join();
				    	}
				    }
				  });
				thread.start();
			}
		}
		
		for(int s=0;s<quantidadeServidores;s++) {
			thread_servers[s] = new Thread(servers[s]);
			thread_servers[s].start();
		}
		
		
		//Inicializa o cliente raft para testar os clusters
		try {
			Thread.sleep(9000);
			System.out.println("Iniciando cliente...");
		} catch (InterruptedException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		
			
		//Incializa o cliente thrift para testar os servidores
		Client c = new Client();
		c.init(nodes[0].ip, nodes[0].port, nodes[0].id);
		c.CURRENT_STATE = c.USE_REDE;
		
		
		Thread cc = new Thread(c);
		cc.start();
		
		try {
			Thread.sleep(5000);
		} catch (InterruptedException e1) {
			// TODO Auto-generated catch block
			e1.printStackTrace();
		}
		
		//Confere se os dados foram replicados no segundo nodo do primeiro cluster
		/*Client c2 = new Client();
		c2.init(nodes[1].ip, nodes[1].port, nodes[1].id);
		try {
		c2.open();
		c2.printVertices(c2.getService().getAllRingVertices());
		c2.close();
		}catch(Exception e) {
			e.printStackTrace();
		}*/
		
		try {
			AgriculturaMachineBook a = new AgriculturaMachineBook(c);
			a.RodaRede();
		} catch (Exception e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		
	  }
}
