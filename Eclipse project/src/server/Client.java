package server;

import java.util.HashMap;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.ConcurrentHashMap.KeySetView;

import org.apache.thrift.TException;
import org.apache.thrift.transport.TTransport;
import org.apache.thrift.transport.TTransportException;

import graph.Edge;
import graph.Settings;
import graph.Vertex;

import org.apache.thrift.transport.TSocket;
import org.apache.thrift.protocol.TBinaryProtocol;
import org.apache.thrift.protocol.TProtocol;

public class Client implements Runnable {
	
	private thrift.service_graph.Client client;
	public int id = 0;
	private boolean running = true;
	private TTransport transport;
	private TProtocol protocol;
	public static final int SHOW_CONCURRENCY = 0;
	public static final int USE_PRESET = 1;
	public static final int USE_RANDOM = 2;
	public static final int CURRENT_STATE = SHOW_CONCURRENCY;
	
  public void init() {
      transport = new TSocket(Settings.ip, Settings.port);
      protocol = new  TBinaryProtocol(transport);
      client = new thrift.service_graph.Client(protocol);
  }
  
  public thrift.service_graph.Client getService(){
	  return client;
  }

  public void open() throws TTransportException{
	  transport.open();
  }
  public void close() throws TTransportException{
	  transport.close();
  }
  
  public void printThriftVertex(thrift.Vertex v){
	if(v!=null)
		System.out.printf("\n-------------------------\n"
				+ " - Nome (Key): %d\n"
				+ " - Descricao: %s\n"
				+ " - Cor: %d\n"
				+ " - Peso: %f\n"
				+ "-------------------------\n",v.getName(),v.getDescription(),v.getColor(),v.getWeight());
  }
  
  public void printThriftEdge(thrift.Edge e){
	if(e!=null)
		System.out.printf("\n-------------------------\n"
				+ " - Vértice Origem (Key): %d\n"
				+ " - Vértice Destino (Key): %d\n"
				+ " - Descricao: %s\n"
				+ " - É direcionado: %d\n "
				+ " - Peso: %f\n"
				+ "-------------------------\n",e.getVertexOrigin(), e.getVertexDestiny(),e.getDescription(),e.getIsDirected(),e.getWeight());
  }
  
  public void printVertices(Map<Integer,thrift.Vertex> m) {
		
	  if(m!=null){
			thrift.Vertex v;
			for(int key : m.keySet()){
				v = m.get(key);
				printThriftVertex(v);
			}
	  }
	}
	
	public void printEdges(Map<String, thrift.Edge> m) {
		
		if(m!=null){
			thrift.Edge e;
			for(String key : m.keySet()){
				e = m.get(key);
				printThriftEdge(e);
			}
		}
	}
  
  public String randomString(){
	  char[] chars = "abcdefghijklmnopqrstuvwxyz ".toCharArray();
	  StringBuilder sb = new StringBuilder();
	  Random random = new Random();
	  for (int i = 0; i < 20; i++) {
	      char c = chars[random.nextInt(chars.length)];
	      sb.append(c);
	  }
	  return sb.toString();
  }
  
  public void randomizeOperation(int id) throws TException{
	  Random r = new Random();
	  int MAX = 100;
	  if(CURRENT_STATE==SHOW_CONCURRENCY)
		  MAX = 2;
	  if(CURRENT_STATE==USE_RANDOM)
		  MAX = 20;
	  
	  int vertexOrigin = r.nextInt(MAX);
	  int vertexDestiny = r.nextInt(MAX);
	  String description = randomString();
	  double weight = r.nextDouble();
	  int isDirected = r.nextInt(2);
	  int name = r.nextInt(MAX);
	  int color = r.nextInt(MAX);
	  
	  switch(id){
	  	case 0:
	  		getService().addEdge(vertexOrigin, vertexDestiny, description, weight, isDirected);
	  		break;
	  	case 1:
	  		getService().addVertex(name, color, description, weight);
	  		break;
	  	case 2:
	  		getService().deleteEdge(vertexOrigin, vertexDestiny);
	  		break;
	  	case 3:
	  		getService().deleteVertex(name);
	  		break;
	  	case 4:
	  		getService().updateEdge(vertexOrigin, vertexDestiny, description, weight, isDirected);
	  		break;
	  	case 5:
	  		getService().updateVertex(name, color, description, weight);
	  		break;
	  	case 6:
	  		printVertices(getService().getAdjacentVertices(name));
	  		break;
	  	case 7:
	  		printEdges(getService().getEdges());
	  		break;
	  	case 8:
	  		printEdges(getService().getEdgesOfVertex(name));
	  		break;
	  	case 9:
	  		printVertices(getService().getVertices());
	  		break;
	  	
	  }
  }
  
  public void preSetOperation(){
	  try{
		  getService().addVertex(1, 1, "sou um vertice de 1 tentativa", 1.5);
		  getService().addVertex(1, 2, "sou um vertice de 2 tentativa", 2.5);
		  getService().addVertex(1, 3, "sou um vertice de 3 tentativa", 3.5);
		  getService().addVertex(2, 4, "sou um vertice de 4 tentativa", 4.5);
		  getService().addEdge(1, 2, "edge 1-2 tentativa 1", 1, 0);
		  getService().addEdge(2, 1, "edge 2-1 tentativa 1", 1, 0);
		  getService().addEdge(2, 1, "edge 2-1 tentativa 2", 2, 0);
		  printVertices(getService().getAdjacentVertices(1));
		  getService().deleteVertex(1);
		  getService().deleteVertex(1);
		  getService().deleteEdge(1, 2);
	  }catch(Exception e){
		  e.printStackTrace();
	  }
  }

  
	@SuppressWarnings("unused")
	@Override
	public void run() {
		int feed = 0;
		while(running){
			try {
				//Thread.sleep(new Random().nextInt(1000));
				Thread.sleep(20);
			} catch (InterruptedException e) {
				e.printStackTrace();
			}
			
			try {
				open();
					if(CURRENT_STATE==SHOW_CONCURRENCY){
						randomizeOperation(new Random().nextInt(2));
						randomizeOperation(new Random().nextInt(10));
					}
					if(CURRENT_STATE==USE_PRESET){
						preSetOperation();
						try {
							wait();
						} catch (InterruptedException e) {
							e.printStackTrace();
						}
					}
					if(CURRENT_STATE==USE_RANDOM){
						if(feed<100){
							randomizeOperation(new Random().nextInt(2));
							feed++;
						}else{
							randomizeOperation(new Random().nextInt(10));
						}
					}
				close();
			} catch (TException e) {
				//e.printStackTrace();
			}
		}
	}
}
