package graph;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Comparator;
import java.util.EmptyStackException;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.Queue;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentHashMap.KeySetView;

import org.apache.thrift.TException;
import org.apache.thrift.transport.TTransportException;
import copycat_command.AddEdgeCommand;
import copycat_command.AddVertexCommand;
import copycat_command.DeleteEdgeCommand;
import copycat_command.DeleteVertexCommand;
import copycat_command.UpdateEdgeCommand;
import copycat_command.UpdateVertexCommand;
import io.atomix.catalyst.transport.Address;
import io.atomix.catalyst.transport.netty.NettyTransport;
import io.atomix.copycat.client.CopycatClient;
import io.atomix.copycat.server.Commit;
import io.atomix.copycat.server.CopycatServer;
import io.atomix.copycat.server.StateMachine;
import io.atomix.copycat.server.storage.Storage;
import io.atomix.copycat.server.storage.StorageLevel;
import sun.misc.Lock;
import server.Client;

import server.Server;
import thrift.NodeData;
import thrift.Vertex;
import thrift.service_graph.Iface;


public class Service extends StateMachine implements Iface{
	/*
	/*
	 *  Cases which you should take care of:
    	Thread1 - write | Thread2 - write | Safeness - No
    	Thread1 - write | Thread2 - read | Safeness - No
    	Thread1 - read | Thread2 - write | Safeness - No
  		Thread1 - read | Thread2 - read | Safeness - Yes
  		source: https://stackoverflow.com/questions/40214250/how-to-detect-a-critical-region-in-java
	 */
	/*
	 * From java doc (ConcurrentHashMap):
	 Retrieval operations (including get) generally do not block, so may overlap with update operations (including put and remove).
	 Retrievals reflect the results of the most recently completed update operations holding upon their onset.
	 For aggregate operations such as putAll and clear, concurrent retrievals may reflect insertion or removal of only some entries.
	 */
	private ConcurrentHashMap<Integer, Vertex> 	vertices		= new ConcurrentHashMap<>();
	private ConcurrentHashMap<String, Edge>		edges			= new ConcurrentHashMap<>();
	private ConcurrentHashMap<String, Integer>  permits		 	= new ConcurrentHashMap<>();
	private boolean debug = true;
	public static final int BEGIN = 0;
	public static final int END = 1;
	

	
	public synchronized boolean acquireResource(String key){
		if(permits.putIfAbsent(key,1)!=null){
			System.out.printf("\n\n[Token "+Thread.currentThread().getId()+"]\tThis token has this resource. Waiting...!\n");
			return false;
		}
		return true;
	}
	
	public synchronized void releaseResource(String key){
		permits.remove(key);
	}
	
	public int hashKey(int key) {
		return (int) (key%Math.pow(2, m));
	}
	
	public boolean shouldQueryForward(int key) {
		if(predecessor.id>nodeData.id)//Intervalo ciclico, se chegou no primeiro nó, verifica se deve alocar nele
			if(key<=nodeData.id)
				return false;
		
		if(key>predecessor.id && key<=nodeData.id)
			return false;
		else
			return true;
	}
	
	
	public void addVertex(Vertex v) {
		String op = "addVertex ( id "+v.getName()+" )";
		debug(op, BEGIN);

		
		while(!acquireResource(Integer.toString(v.getName()))){
			// If it fails to acquire resource -> keeps waiting
		}
		
		if(vertices.putIfAbsent(v.getName(), v)!=null)
			System.out.printf("\n[Token "+Thread.currentThread().getId()+"]\tThere's already a vertex with that name! ( id %d )\n", v.getName());
		
		System.out.println("\nVertice de key "+v.getName()+" hasheado para o nodo "+hashKey(v.getName())+" inserido no nodo "+nodeData.id);
		debug(op, END);
		releaseResource(Integer.toString(v.getName()));
	}
	
	public void addEdge(Edge e){
		
		int origin  = e.getVertexOrigin();
		int destiny = e.getVertexDestiny();
		String key 	= origin+"-"+destiny;
		
		String op = "addEdge ( id "+key+" )";
		debug(op, BEGIN);
		
		while(!acquireResource(key)){
			// If it fails to acquire resource -> keeps waiting
		}

		/* Critical [!!]
		 * Os valores dos vertices não podem mudar antes de verificar se eles existem para inserção da aresta.
		 *  */

		if(vertices.containsKey(origin) || vertices.containsKey(destiny)){// only inserts if its vertices already exists
			if(edges.putIfAbsent(key, e)!=null) {
				System.out.printf("\n[Token "+Thread.currentThread().getId()+"]\tThere's already an edge with that name! ( id %s )\n", key);
			}
		}else{
			System.out.printf("\n[Token "+Thread.currentThread().getId()+"]\tOne of these vertices doesn't exist! ( id %s )\n", key);
		}
		
		System.out.println("\nEdge de key "+e.getVertexOrigin()+"-"+e.getVertexDestiny()+" hasheado para os nodos "+hashKey(e.getVertexOrigin())+" e "+hashKey(e.getVertexDestiny())+" inserido no nodo "+nodeData.id);
		
		releaseResource(key);
		debug(op, END);
	}
	
	public void deleteVertex(Vertex v){
		String op = "deleteVertex ( id "+v.getName()+" )";
		debug(op,BEGIN);
		
		while(!acquireResource(Integer.toString(v.getName()))){
			// If it fails to acquire resource -> keeps waiting
		}
		
		ArrayList<String> removeKeys = new ArrayList<>();
		
		/* Critical [!!]
		 * Enquanto procura as arestas para serem removidas elas não podem ter sido deletadas por outra thread, senão causará nullPointer.
		 *  */
		//TODO: Faltando o deletar as arestas de outros nodos

		for(String key : edges.keySet()){
			if(key.matches(".-"+v.getName()) || key.matches(v.getName()+"-.")){
				removeKeys.add(key);
				while(!acquireResource(key)){}
			}
		}
		
		//Remove all edges that touchs on it
		for(String key : removeKeys){
			edges.remove(key);
			releaseResource(key);
		}
		
		//Remove vertex
		vertices.remove(v.getName());
		
		debug(op, END);
		releaseResource(Integer.toString(v.getName()));
	}
	
	public void deleteEdge(Edge e){
		int origin  = e.getVertexOrigin();
		int destiny = e.getVertexDestiny();
		String key = origin+"-"+destiny;
		String op = "deleteEdge ( id "+key+" )";
		debug(op, BEGIN);

		
		
		while(!acquireResource(key)){
			// If it fails to acquire resource -> keeps waiting
		}
		
		if(edges.containsKey(key))
			edges.remove(key);
		else
			System.out.printf("\n[Token "+Thread.currentThread().getId()+"]\tThere's no edge with these vertices ( id %s )\n",key);
		
		debug(op, END);
		releaseResource(key);
	}
	
	public void updateVertex(Vertex v){
		String op = "updateVertex ( id "+v.getName()+" )";
		debug(op, BEGIN);
		
		while(!acquireResource(Integer.toString(v.getName()))){
			// If it fails to acquire resource -> keeps waiting
		}
		
		/* Critical [!!]
		 * Se a thread perder a CPU antes do put, outra thread pode ter removido o valor e então ele será re-inserido atualizado.
		 *  */

		if(vertices.containsKey(v.getName()))
			vertices.put(v.getName(), v);
		else
			System.out.printf("\n[Token "+Thread.currentThread().getId()+"]\tThere's no vertice with that name ( id %d )\n", v.getName());

	
		debug(op, END);
		releaseResource(Integer.toString(v.getName()));
	}
	
	public void updateEdge(Edge e){ //Uma aresta não pode ter seus vértices mudados(??)
		int origin  = e.getVertexOrigin();
		int destiny = e.getVertexDestiny();
		String key 	= origin+"-"+destiny;
		String op = "updateEdge ( id "+key+" )";
		debug(op, BEGIN);
	
		
		while(!acquireResource(key)){
			// If it fails to acquire resource -> keeps waiting
		}
		
		/* Critical [!!]
		 * Mesma situação do updateVertex()
		 *  */

		if(edges.get(key)!=null) //Verificar quais atributos diferem
			edges.put(key, e);
		else
			System.out.printf("\n[Token "+Thread.currentThread().getId()+"]\tThere's no edge with these vertices! (id %s )", key);

	
		debug(op, END);
		releaseResource(key);
	}
	
	public void debug(String t, int var){
		if(nodeData==null) {
			nodeData = new NodeData();
			nodeData.id = -1;
		}
		if(debug){
			if(var==BEGIN)
				//System.out.printf("\n-------------\n[Token "+Thread.currentThread().getId()+", Node ID "+nodeData.id+"]\tBEGIN \t"+t+" \n");
				System.out.printf("\n-------------\n[Node ID "+nodeData.id+" - "+nodeData.port+"]\tBEGIN \t"+t+" \n");
			if(var==END)
				//System.out.printf("\n[Token "+Thread.currentThread().getId()+", Node ID "+nodeData.id+"]\tEND \t"+t+" \n-------------\n");
				System.out.printf("\n[Node ID "+nodeData.id+" - "+nodeData.port+"]\tEND \t"+t+" \n-------------\n");
		}
	}
	
	public thrift.Vertex convertVertexToThrift(Vertex v){
		thrift.Vertex tv = new thrift.Vertex();
		
		tv.name = v.getName();
		tv.color = v.getColor();
		tv.description =v.getDescription();
		tv.weight = v.getWeight();
		
		return tv;
	}
	
	public thrift.Edge convertEdgeToThrift(Edge e){
		thrift.Edge te = new thrift.Edge();
		
		te.vertexOrigin = e.getVertexOrigin();
		te.vertexDestiny = e.getVertexDestiny();
		te.description = e.getDescription();
		te.weight = e.getWeight(); 
		te.isDirected = e.getIsDirected();

		return te;
	}
	
	public Edge convertThriftToEdge(thrift.Edge e){
		Edge te = new Edge(e.getVertexOrigin(),e.getVertexDestiny(), e.getWeight(), e.getIsDirected(), e.getDescription());
		return te;
	}
	
	public Vertex convertThriftToVertex(thrift.Vertex v){
		Vertex tv = new Vertex(v.getName(),v.getColor(),v.getDescription(),v.getWeight());
		return tv;
	}
	
	@Override
	public void addVertex(int name, int color, String description, double weight) throws TException {
		Vertex v = new Vertex(name, color, description, weight);
		String op = "thrift.addVertex ( id "+v.getName()+" )";
		debug(op, BEGIN);
		
		int key = hashKey(v.getName());
		if(shouldQueryForward(key)) {
			NodeData node = new NodeData(key,nodeData.ip,nodeData.port, nodeData.clusterId);
			node = find_sucessor(node);
				
			if(node.id!=nodeData.id) {
				Client c = new Client();
				c.init(node.ip, node.port);
				
				try {
					c.open();
					c.getService().addVertex(v.getName(), v.getColor(), v.getDescription(), v.getWeight());
					c.close();
				} catch (TException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
				debug(op, END);
				return;
			}
		}
		
		addVertex(new Vertex(name, color, description, weight));
		
		//Propaga para os outros nodos do cluster
		try {
			CopycatClient client = buildClient();
			
			Collection<Address> cluster = Arrays.asList(
					  new Address(nodeData.ip, nodeData.port+clusterOffset)
					);
			
			CompletableFuture<CopycatClient> future = client.connect(cluster);
			future.join();
			
			 CompletableFuture[] futures = new CompletableFuture[1];
			 futures[0] = client.submit(new AddVertexCommand(convertVertexToThrift(v)));
			 CompletableFuture.allOf(futures).thenRun(() -> System.out.println("Commands completed!"));
		}catch(Exception e) {
			System.out.println("e. !!");
		}
		
		debug(op, END);
	}

	@Override
	public void addEdge(int vertexOrigin, int vertexDestiny, String description, double weight, int isDirected)
			throws TException {
		Edge e = new Edge(vertexOrigin, vertexDestiny, weight, isDirected, description);
		int origin  = e.getVertexOrigin();
		int destiny = e.getVertexDestiny();
		String key 	= origin+"-"+destiny;
		String op = "thrift.addEdge ( id "+key+" )";
		debug(op, BEGIN);
		
		if(origin==destiny){
			System.out.printf("\n[Token "+Thread.currentThread().getId()+"]\tLoops are not allowed! Aborting...( id %s )\n", key);
			return;
		}
		
		
		int key_v1 = hashKey(origin);
		int key_v2 = hashKey(destiny);

		
		if(shouldQueryForward(key_v1)) {
			NodeData node = new NodeData(key_v1,nodeData.ip,nodeData.port, nodeData.clusterId);
			node = find_sucessor(node);
			
			if(node.id!=nodeData.id) {
				Client c = new Client();
				c.init(node.ip, node.port);
				
				try {
					c.open();
					c.getService().addEdge(e.getVertexOrigin(), e.getVertexDestiny(), e.getDescription(), e.getWeight(), e.getIsDirected());
					c.close();
				} catch (TException err) {
					// TODO Auto-generated catch block
					err.printStackTrace();
				}
		
				return;
			}
		}
		
		
		//Adiona a este nodo
		addEdge(e);
		
		//Propaga para os outros nodos do cluster
		try {
		CopycatClient client = buildClient();
		
		Collection<Address> cluster = Arrays.asList(
			  new Address(nodeData.ip, nodeData.port+clusterOffset)
			);
		
		CompletableFuture<CopycatClient> future = client.connect(cluster);
		future.join();
		
		 CompletableFuture[] futures = new CompletableFuture[1];
		 futures[0] = client.submit(new AddEdgeCommand(convertEdgeToThrift(e)));
		 CompletableFuture.allOf(futures).thenRun(() -> System.out.println("Commands completed!"));
		}catch(Exception ea) {
			System.out.println("e. !!");
		}
		
		
		debug(op, END);
		
	}

	@Override
	public void deleteVertex(int name) throws TException {
		Vertex v = new Vertex(name, 0, "", 0);
		String op = "thrift.deleteVertex ( id "+v.getName()+" )";
		debug(op,BEGIN);
		
		int hashkey = hashKey(v.getName());
		if(shouldQueryForward(hashkey)) {
			NodeData node = new NodeData(hashkey,nodeData.ip,nodeData.port, nodeData.clusterId);
			node = find_sucessor(node);
				
			if(node.id!=nodeData.id) {
				Client c = new Client();
				c.init(node.ip, node.port);
				
				try {
					c.open();
					c.getService().deleteVertex(v.getName());
					c.close();
				} catch (TException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
				debug(op, END);
				return;
			}
		}
		
		deleteVertex(new Vertex(name,0,"",0));
		
		//Propaga para os outros nodos do cluster
		try {
		CopycatClient client = buildClient();
		
		Collection<Address> cluster = Arrays.asList(
				  new Address(nodeData.ip, nodeData.port+clusterOffset)
				);
		
		CompletableFuture<CopycatClient> future = client.connect(cluster);
		future.join();
		
		 CompletableFuture[] futures = new CompletableFuture[1];
		 futures[0] = client.submit(new DeleteVertexCommand(convertVertexToThrift(v)));
		 CompletableFuture.allOf(futures).thenRun(() -> System.out.println("Commands completed!"));
		}catch(Exception e) {
			System.out.println("e. !!");
		}
		 
		 debug(op, END);
	}

	@Override
	public void deleteEdge(int vertexOrigin, int vertexDestiny) throws TException {
		Edge e = new Edge(vertexOrigin, vertexDestiny, 0, 0, "");
		int origin  = e.getVertexOrigin();
		int destiny = e.getVertexDestiny();
		String key = origin+"-"+destiny;
		String op = "thrift.deleteEdge ( id "+key+" )";
		debug(op, BEGIN);
		
		if(origin==destiny){
			System.out.printf("\n[Token "+Thread.currentThread().getId()+"]\tLoops are not allowed! Aborting...( id %s )\n", key);
			return;
		}
		
		
		int hashkey = hashKey(origin);
		if(shouldQueryForward(hashkey)) {
			NodeData node = new NodeData(hashkey,nodeData.ip,nodeData.port,nodeData.clusterId);
			node = find_sucessor(node);
				
			if(node.id!=nodeData.id) {
				Client c = new Client();
				c.init(node.ip, node.port);
				
				try {
					c.open();
					c.getService().deleteEdge(origin, destiny);
					c.close();
				} catch (TException err) {
					// TODO Auto-generated catch block
					err.printStackTrace();
				}
				return;
			}
		}
		
		deleteEdge(new Edge(vertexOrigin, vertexDestiny));
		
		//Propaga para os outros nodos do cluster
		try {
		CopycatClient client = buildClient();
		
		Collection<Address> cluster = Arrays.asList(
				  new Address(nodeData.ip, nodeData.port+clusterOffset)
				);
		
		CompletableFuture<CopycatClient> future = client.connect(cluster);
		future.join();
		
		 CompletableFuture[] futures = new CompletableFuture[1];
		 futures[0] = client.submit(new DeleteEdgeCommand(convertEdgeToThrift(e)));
		 CompletableFuture.allOf(futures).thenRun(() -> System.out.println("Commands completed!"));
		}catch(Exception ea) {
			System.out.println("e. !!");
		}
		debug(op, END);
	}

	@Override
	public void updateVertex(int name, int color, String description, double weight) throws TException {
		Vertex v = new Vertex(name, color, description, weight);
		String op = "thrift.updateVertex ( id "+v.getName()+" )";
		debug(op, BEGIN);
		
		int key = hashKey(v.getName());
		if(shouldQueryForward(key)) {
			NodeData node = new NodeData(key,nodeData.ip,nodeData.port,nodeData.clusterId);
			node = find_sucessor(node);
				
			if(node.id!=nodeData.id) {
				Client c = new Client();
				c.init(node.ip, node.port);
				
				try {
					c.open();
					c.getService().updateVertex(v.getName(), v.getColor(), v.getDescription(), v.getWeight());
					c.close();
				} catch (TException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
				debug(op, END);
				return;
			}
		}
		
		updateVertex(new Vertex(name, color, description, weight));
		
		//Propaga para os outros nodos do cluster
		try {
		CopycatClient client = buildClient();
		
		Collection<Address> cluster = Arrays.asList(
				  new Address(nodeData.ip, nodeData.port+clusterOffset)
				);
		
		CompletableFuture<CopycatClient> future = client.connect(cluster);
		future.join();
		
		 CompletableFuture[] futures = new CompletableFuture[1];
		 futures[0] = client.submit(new UpdateVertexCommand(convertVertexToThrift(v)));
		 CompletableFuture.allOf(futures).thenRun(() -> System.out.println("Commands completed!"));
		}catch(Exception ea) {
			System.out.println("e. !!");
		}
		debug(op, END);
	}

	@Override
	public void updateEdge(int vertexOrigin, int vertexDestiny, String description, double weight, int isDirected)
			throws TException {
		Edge e = new Edge(vertexOrigin, vertexDestiny, weight, isDirected, description);
		int origin  = e.getVertexOrigin();
		int destiny = e.getVertexDestiny();
		String key 	= origin+"-"+destiny;
		String op = "thrift.updateEdge ( id "+key+" )";
		debug(op, BEGIN);
		
		if(origin==destiny){
			System.out.printf("\n[Token "+Thread.currentThread().getId()+"]\tLoops are not allowed! Aborting...( id %s )\n", key);
			return;
		}
		

		int hashkey = hashKey(origin);
		if(shouldQueryForward(hashkey)) {
			NodeData node = new NodeData(hashkey,nodeData.ip,nodeData.port,nodeData.clusterId);
			node = find_sucessor(node);
				
			if(node.id!=nodeData.id) {
				Client c = new Client();
				c.init(node.ip, node.port);
				
				try {
					c.open();
					c.getService().updateEdge(e.getVertexOrigin(), e.getVertexDestiny(), e.getDescription(), e.getWeight(), e.getIsDirected());
					c.close();
				} catch (TException err) {
					// TODO Auto-generated catch block
					err.printStackTrace();
				}
				return;
			}
		}
		updateEdge(new Edge(vertexOrigin, vertexDestiny, weight, isDirected, description));
		
		//Propaga para os outros nodos do cluster
		try {
		CopycatClient client = buildClient();
		
		Collection<Address> cluster = Arrays.asList(
				  new Address(nodeData.ip, nodeData.port+clusterOffset)
				);
		
		CompletableFuture<CopycatClient> future = client.connect(cluster);
		future.join();
		
		 CompletableFuture[] futures = new CompletableFuture[1];
		 futures[0] = client.submit(new UpdateEdgeCommand(convertEdgeToThrift(e)));
		 CompletableFuture.allOf(futures).thenRun(() -> System.out.println("Commands completed!"));
		}catch(Exception ea) {
			System.out.println("e. !!");
		}
		debug(op, END);	
		
	}

	@Override
	public thrift.Vertex getVertex(int name) throws TException {
		String op = "getEdge ( "+name+" )";
		debug(op, BEGIN);
		
		int key = hashKey(name);
		if(shouldQueryForward(key)) {
			NodeData node = new NodeData(key,nodeData.ip,nodeData.port,nodeData.clusterId);
			node = find_sucessor(node);
			thrift.Vertex result = null;
				
			if(node.id!=nodeData.id) {
				Client c = new Client();
				c.init(node.ip, node.port);
				
				try {
					c.open();
					result = c.getService().getVertex(name);
					c.close();
				} catch (TException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
				debug(op, END);
				return result;
			}
		}
		
		Vertex v = vertices.get(name);
		if(v==null){
			System.out.printf("\n[Token "+Thread.currentThread().getId()+"]\tThere's no such vertex ( id %d )\n", name);
			return null;
		}
			
		
		debug(op, END);
		return convertVertexToThrift(v);
	}

	@Override
	public thrift.Edge getEdge(String name) throws TException {
		String op = "getEdge ( "+name+" )";
		debug(op, BEGIN);
		
		int key = hashKey(Integer.parseInt(name.split("-")[0]));
		if(shouldQueryForward(key)) {
			NodeData node = new NodeData(key,nodeData.ip,nodeData.port,nodeData.clusterId);
			node = find_sucessor(node);
			thrift.Edge result = null;
				
			if(node.id!=nodeData.id) {
				Client c = new Client();
				c.init(node.ip, node.port);
				
				try {
					c.open();
					result = c.getService().getEdge(name);
					c.close();
				} catch (TException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
				debug(op, END);
				return result;
			}
		}
		
		Edge e = edges.get(name);
		
		debug(op, END);
		return convertEdgeToThrift(e);
	}

	@Override
	public Map<Integer, thrift.Vertex> getVertices() throws TException {
		String op = "getVertices ()";
		debug(op, BEGIN);
		
		Map<Integer, thrift.Vertex> m  = new HashMap<>();
		KeySetView<Integer, Vertex> keys = vertices.keySet();
		for(int key : keys){
			m.put(key, convertVertexToThrift(vertices.get(key)));
		}
		debug(op, END);
		return m;
	}

	@Override
	public Map<String, thrift.Edge> getEdges() throws TException {
		String op = "getEdges ()";
		debug(op, BEGIN);
		
		Map<String, thrift.Edge> m  = new HashMap<>();
		KeySetView<String, Edge> keys = edges.keySet();
		for(String key : keys){
			m.put(key, convertEdgeToThrift(edges.get(key)));
		}
		
		debug(op, END);
		return m;
	}

	@Override
	public Map<String, thrift.Edge> getEdgesOfVertex(int name) throws TException {
		String op = "getEdgesOfVertex ( id "+name+" )";
		debug(op, BEGIN);
		Map<String, thrift.Edge> m  = new HashMap<>();
		
		int hashkey = hashKey(name);
		if(shouldQueryForward(hashkey)) {
			NodeData node = new NodeData(hashkey,nodeData.ip,nodeData.port,nodeData.clusterId);
			node = find_sucessor(node);
				
			if(node.id!=nodeData.id) {
				Client c = new Client();
				c.init(node.ip, node.port);
				
				try {
					c.open();
					m = c.getService().getEdgesOfVertex(name);
					c.close();
				} catch (TException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
				debug(op, END);
				return m;
			}
		}
		
		KeySetView<String, Edge> keys = edges.keySet();
		
		for(String key : keys){
			if(key.matches(name+"-.") || key.matches(".-"+name)){
				m.put(key, convertEdgeToThrift(edges.get(key)));
			}
		}
		
		debug(op, END);
		return m;
	}

	@Override
	public Map<Integer, Vertex> getAdjacentVertices(int name) throws TException {
		String op = "getAdjacentVertices ( id "+name+" )";
		debug(op, BEGIN);

		Map<Integer, thrift.Vertex> m  = new HashMap<>();
		
		/*int hashkey = hashKey(name);
		if(shouldQueryForward(hashkey)) {
			NodeData node = new NodeData(hashkey,nodeData.ip,nodeData.port,nodeData.clusterId);
			node = find_sucessor(node);
				
			if(node.id!=nodeData.id) {
				Client c = new Client();
				c.init(node.ip, node.port);
				
				try {
					c.open();
					m = c.getService().getAdjacentVertices(name);
					c.close();
				} catch (TException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
				debug(op, END);
				return m;
			}
		}*/
		
		Map<String, thrift.Edge> edgesAux = getAllRingEdges();
		Set<String> keys = edgesAux.keySet();
		Vertex buffer;
		
		Map<Integer, thrift.Vertex> verticesAux = getAllRingVertices();
		Vertex v = verticesAux.get(name);
		
		
		
		if(v!=null){
		
			for(String key : keys){
				if(key.matches(".-"+v.getName())){
					buffer = verticesAux.get(Integer.parseInt(key.split("-")[0]));
					
					
					if(buffer!=null)
						m.put(buffer.getName(), convertVertexToThrift(buffer));
				}
				if(key.matches(v.getName()+"-.")){
					buffer = verticesAux.get(Integer.parseInt(key.split("-")[1]));
					
					if(buffer!=null)
						m.put(buffer.getName(), convertVertexToThrift(buffer));
				}
			}
			
		}
		
		debug(op, END);
		return m;
	}
	
	@Override
	public Map<Integer, thrift.Vertex> getAllRingVertices() throws TException {
		String op = "getAllRingVertices ( )";
		debug(op, BEGIN);
		
		Map<Integer, thrift.Vertex> m  = new HashMap<>();
		m.putAll(vertices);
		
		
		NodeData node = getSucessor(nodeData);
		
		while(node.id!=nodeData.id) {
			
			Client c = new Client();
			c.init(node.ip, node.port);

			try {
				c.open();
				node = c.getService().getSucessor(node);
				m.putAll(c.getService().getVertices());
				//c.getService().getVertices();
				c.close();
			} catch (TException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		}


		debug(op, END);
		return m;
	}

	@Override
	public Map<String, thrift.Edge> getAllRingEdges() throws TException {
		String op = "getAllRingEdges ( )";
		debug(op, BEGIN);
		
		Map<String, thrift.Edge> m  = new HashMap<>();
		m.putAll(getEdges());
		
		
		NodeData node = getSucessor(nodeData);
		
		while(node.id!=nodeData.id) {
			
			Client c = new Client();
			c.init(node.ip, node.port);

			try {
				c.open();
				node = c.getService().getSucessor(node);
				m.putAll(c.getService().getEdges());
				//c.getService().getVertices();
				c.close();
			} catch (TException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		}


		debug(op, END);
		return m;
	}

	@Override
	public Map<String, thrift.Edge> shortestPathBetweenVertices(int n1, int n2) throws TException {
		String op = "shortestPathBetweenVertices ("+n1+" , "+n2+" )";
		debug(op, BEGIN);
		
		Map<String, thrift.Edge> edges_map  = new HashMap<>();
		edges_map = getAllRingEdges();
		
		Map<Integer, thrift.Vertex> vertices_map  = new HashMap<>();
		vertices_map = getAllRingVertices();
		
		thrift.Vertex vertices[] = vertices_map.values().toArray( new thrift.Vertex[0]);
		
		 boolean complete = false;
		 int size = 0;
		 ArrayList<thrift.Vertex> paths = new ArrayList<thrift.Vertex>();
		 ArrayList<thrift.Vertex> visited = new ArrayList<thrift.Vertex>();
		 Queue<thrift.Vertex> pathTable;
		 
		 thrift.Vertex a = getVertex(n1);
		 thrift.Vertex b = getVertex(n2);
		 
		 //adding nodes to the unvisited array
		 for(thrift.Vertex path: vertices){
		   paths.add(path);
		  }
		 

		  pathTable = new PriorityQueue<thrift.Vertex>(vertices.length, comp);
		  
		  //calling with Dijkstra with the source
		  paths.remove(a);
		  visited.add(a);
		  
		  thrift.Vertex n = a;
		  //Check the adjacent nodes and move forward
		  int count = 0;
		  while(count != vertices.length){
		   //find the adjacent nodes
			  Map<Integer, Vertex> adjancents = getAdjacentVertices(n.getName());
		   for(Object key: adjancents.keySet()){
		    thrift.Vertex currentNode = (thrift.Vertex) key;
		   
		    //if visited don't go through it
		    if(visited.contains(currentNode)){
		     continue;
		    }
		    
		    double newPathCost = n.getWeight() +   adjancents.get(currentNode.getName()).getWeight(); 
		    if (currentNode.getName() == 0.0){
		     currentNode.weight = newPathCost; 
		    } else if (currentNode.weight < newPathCost){
		     
		    } else {
		     //System.out.println("Ekhane " + currentNode.name);
		     currentNode.weight = newPathCost;
		    }
		   
		    if (pathTable.contains(currentNode)){
		     pathTable.remove(currentNode);
		     pathTable.add(currentNode);
		    } else {
		     pathTable.add(currentNode);
		    }
		   
		   }
		   //give n a new value go through it <the lowest path cost data
		   thrift.Vertex temp =(thrift.Vertex)pathTable.poll();
		   n = temp;
		   visited.add(temp);
		   count++;
		   
		  }
		  
		  //printing the final output
		  System.out.println("Shortest path distance from source: " + a.getName());
		  for(Object iter: paths.toArray()){
		   thrift.Vertex temp = (thrift.Vertex) iter;
		   String cost = Double.toString(temp.getWeight());
		     
		   if(!visited.contains(temp)){
		    cost = "Infinity";
		   }
		   System.out.println(temp.name + " " + cost);
		  }
		 
		debug(op, END);
		return edges_map;
	
	}
	
	 public static Comparator<thrift.Vertex> comp = new Comparator<thrift.Vertex>() {
		  public int compare (thrift.Vertex one, thrift.Vertex two){
		   if(one.getWeight() > two.getWeight()){
		    return 1;
		   } else {
		    return -1;
		   }
		   
		  }
	 };



	
	//+=================================================================================================================================
	//|	Chord functions
	//+=================================================================================================================================
	private NodeData finger[];
	
	
	private Server s;
	private Client c;
	
	public NodeData nodeData;
	
	private NodeData predecessor;
	
	int m;
	
	@Override
	public void init_node(int m, NodeData n) throws TException {
		String op = "init_node ( "+m+", "+n.id+" )";
		//debug(op, BEGIN);
		
		this.nodeData = n;
		this.m = m;
		finger = new NodeData[m];
		predecessor = null;
		
		
		//Inicia tabela com:
		
		//-------------------------
		// i = 0		|	N_id + 2^i < x <= próx sucessor existente maior igual 2^0 
		// i = 1		|	N_id + 2^i < x <= próx sucessor existente maior igual 2^1
		// i = 2		|	N_id + 2^i < x <= próx sucessor existente maior igual 2^2
		// ...			|	...
		
		
		//inicializa a finger table
		finger[0] = n;
		for(int i=1; i<m; i++) {
			finger[i] = null;
		}


		
		
		//debug(op, END);
	}

	
	@Override
	public void initFingerTable(List<NodeData> nodes) throws TException {
		for(int i=0;i<m;i++)
			finger[i] = nodes.get(i);
	}
	
	public void initPredecessor(NodeData predecessor) {
		this.predecessor = predecessor;
	}
	
	//n.find_sucessor(id) v wikipedia
	public NodeData find_sucessor(NodeData node) {
		String op;
		op = "find_sucessor ( "+node.id+" )";
		debug(op, BEGIN);
		
		if(node.id==nodeData.id) { //Se sucessor dele mesmo, retorna ele mesmo e exibir msg de erro
			debug(op, END);
			return nodeData;
		}
		
		if(checkCiclicOpenClosedInterval(node.id, nodeData.id, finger[0].id)) {
			debug(op, END);
			//return finger[0];
			return getNextServerAlive(0);
		}else {
		
			NodeData n_linha = closest_preceding_finger(node);//n' = closest_preceding_finger(id)
			NodeData sucessor = null;
			
			c = new Client();
			c.init(n_linha.ip, n_linha.port); 
			try {
				
				c.open();
				sucessor = c.getService().find_sucessor(node); //return n'.sucessor
				c.close();
				
			} catch (Exception e) {
				e.printStackTrace();
			}
			
			debug(op, END);
			return sucessor;
		}

	}
	
	public NodeData getSucessor(NodeData node) {
		String op = "getSucessor ( "+node.id+" )";
		debug(op, BEGIN);
		
		if(node.id == this.nodeData.id) {
			debug(op, END);
			//return finger[0];
			return getNextServerAlive(0);
		}
		
		NodeData sucessor = null;
		c = new Client();
		c.init(node.ip, node.port); 
		try {
			
			c.open();
			sucessor = c.getService().getSucessor(node);
			c.close();
			
		} catch (Exception e) {
			e.printStackTrace();
		}
		
		debug(op, END);
		return sucessor;
	}

	public NodeData getPredecessor() {
		String op = "getPredecessor ( self )";
		debug(op, BEGIN);
		debug(op, END);
		return predecessor;
	}
	
	
	public boolean checkCiclicOpenClosedInterval(int value, int esq, int dir) {
		
		if(esq>dir) { //se é um intervalo ciclico
			if(value>=dir)
				return true;
			if(value<esq)
				return true;
		}else { //se não ciclico:
			if(value>esq && value<=dir)
				return true;
			else
				return false;
		}
		
		return false;
	}
	
	public boolean checkCiclicOpenOpenInterval(int value, int esq, int dir) {
		
		if(esq>dir) { //se é um intervalo ciclico
			if(value>dir)
				return true;
			if(value<esq)
				return true;
		}else { //se não ciclico:
			if(value>esq && value<dir)
				return true;
			else
				return false;
		}
		
		return false;
	}
	
	//n.find_predecessor(id)
	public NodeData find_predecessor(NodeData node) {
		String op = "find_predecessor ( "+node.id+" )";
		debug(op, BEGIN);
		
		NodeData n_linha = this.nodeData; //n' = n;
		//while(node!=null && !(node.id>n_linha.id && node.id<=getSucessor(n_linha).id)) { //while(id !=e (n', n'.sucessor]))
		while(node!=null && !checkCiclicOpenClosedInterval(node.id, n_linha.id, getSucessor(n_linha).id)) { //while(id !=e (n', n'.sucessor]))
			if(n_linha.id != nodeData.id) {
				c = new Client();
				c.init(n_linha.ip, n_linha.port); 
				
				try {
					
					c.open();
					n_linha = c.getService().closest_preceding_finger(node); //n' = n'.closest_preceding_finger(id)
					c.close();
					
				} catch (Exception e) {
					e.printStackTrace();
				}
			}else {
				n_linha = closest_preceding_finger(node);
				
			}
		}
		
		debug(op, END);
		return n_linha;// return n'
	}
	
	//n.closest_preceding_finger(id)
	public NodeData closest_preceding_finger(NodeData node) {
		String op = "closest_preceding_finger ( "+node.id+" )";
		debug(op, BEGIN);
		
		for(int i=m-1; i>=0;i--) { // for(i=m downto 1)
			
			//System.out.println(finger[i].id+">"+nodeData.id+" && "+finger[i].id+"<"+node.id);
			//if(finger[i]!=null && (finger[i].id>nodeData.id && finger[i].id<node.id)) //if(finger[i].node e (d,id))
			if(finger[i]!=null && checkCiclicOpenOpenInterval(finger[i].id,nodeData.id,node.id)) //if(finger[i].node e (d,id))
				//return finger[i]; //return figer[i].node
				return getNextServerAlive(i); //return figer[i].node
		}
		
		debug(op, END);
		return nodeData;
	}
	
	public void join(NodeData known_node) {
		String op = "join ( "+known_node.id+" )";
		debug(op, BEGIN);
		
		predecessor = null;
		c = new Client();
		
		c.init(known_node.ip, known_node.port); 
		
		try {
			c.open();
			finger[0] = c.getService().find_sucessor(nodeData);
			c.close();
			
		} catch (Exception e) { 
			e.printStackTrace();
		}
		
		debug(op, END);
	}
	
	public void stabilize() {
		String op = "stabilize (  )";
		debug(op, BEGIN);
		
		NodeData x = null;
		
		c = new Client();
		c.init(finger[0].ip, finger[0].port); 
		
		//sucessor.predecessor
		if(finger[0].id!=nodeData.id) {
			try {
				
				c.open();
				x = c.getService().getPredecessor();
				c.close();
				
			} catch (Exception e) {
				e.printStackTrace();
			}
		}else {
			x = getPredecessor();
		}
		
		//if(x e (n,sucessor))
		if(x!=null) {
			if(checkCiclicOpenOpenInterval(x.id, nodeData.id, finger[0].id))
				finger[0] = x;
		}
		
		//sucessor.notify(n)
		if(finger[0].id!=nodeData.id) {
			c = new Client();
			c.init(finger[0].ip, finger[0].port); 
			try {
				
				c.open();
				c.getService().notifyNode(nodeData);
				c.close();
				
			} catch (Exception e) {
				e.printStackTrace();
			}
		}else {
			notifyNode(nodeData);
		}
		debug(op, END);
	}
	
	//n.notify(n')
	public void notifyNode(NodeData node) {
		String op = "notifyNode ( "+node.id+" )";
		debug(op, BEGIN);
		
		//if(predecessor is nill or n' e (predecessor,n))
		if(predecessor==null || checkCiclicOpenOpenInterval(node.id, predecessor.id, nodeData.id))
			predecessor = node; //predecessor = n'
		
		debug(op, END);
	}
	
	public void fixFingers() {
		String op = "fixFingers (  )";
		debug(op, BEGIN);
		
		Random r = new Random();
		int i = r.nextInt(m);
		while(finger[i]==null)
			i = r.nextInt(m);
		
		if(finger[i]!=null && (finger[i].id!=nodeData.id)) {
			c = new Client();
			c.init(finger[i].ip, finger[i].port); 
		
			try {
				
				c.open();
				finger[i] = c.getService().find_sucessor(finger[i]);
				c.close();
				
			} catch (Exception e) {
				e.printStackTrace();
			}
		}
			
		debug(op, END);
	}
	
	@Override
	public void printFingerTable() throws TException {
		System.out.println("Printing Finger Table:");
		for(int i= 0; i < finger.length; i ++)
			if(finger[i]!=null)
				System.out.println(finger[i].id);
	}
	
	public NodeData getNextServerAlive(int fingerIndex) {
		
		for(int i =0;i<fatorReplicacao;i++) {
			if(fingerCluster.get(fingerIndex)[i].port!=nodeData.port)
				if(isServerAlive(fingerCluster.get(fingerIndex)[i]))
					return fingerCluster.get(fingerIndex)[i];
		}
		
		return null;
	}
	
	public boolean isServerAlive(NodeData node) {
		Client c = new Client();
		c.init(node.ip, node.port, node.id);
		
		try {
			c.open();
			c.close();
		}catch(Exception e) {
			return false; //Não conseguiu conectar
		}
		
		return true; //Conseguiu conectar
	}
	
	
	//+=================================================================================================================================
	//|	Raft functions
	//+=================================================================================================================================
	
	public CopycatServer raft_server;
	public CopycatClient raft_client;
	private HashMap<Integer, NodeData[]> fingerCluster;
	private NodeData ownCluster[];
	public int fatorReplicacao = 3;
	public int clusterOffset;
	
	public void init_raft(int fatorReplicao, int clusterOffset) {
		this.clusterOffset = clusterOffset;
		String op = "init_raft (" + nodeData.ip+", " + nodeData.port +")";
		debug(op, BEGIN);
		
		//fingerCluster = new HashMap<Integer, NodeData[]>();
		
		this.fatorReplicacao = fatorReplicao;
		Address server_address = new Address(nodeData.ip, nodeData.port+clusterOffset);

        CopycatServer.Builder builder = CopycatServer.builder(server_address)
                                                     .withStateMachine(Service::new)
                                                     .withTransport( NettyTransport.builder()
                                                                     .withThreads(4)
                                                                     .build())
                                                     .withStorage( Storage.builder()
                                                                   .withDirectory(new java.io.File("logs_"+nodeData.id+clusterOffset)) //Must be unique
                                                                   .withStorageLevel(StorageLevel.DISK)
                                                                   .build())
                                                     .withName(nodeData.ip+nodeData.port)
                                                     ;

        raft_server = builder.build();
        
        debug(op, END);
	}
	
	public void initFingerCluster(HashMap<Integer, NodeData[]> map) {
		this.fingerCluster = map;
	}
	public void initOwnCluster(NodeData ownCluster[]) {
		this.ownCluster = ownCluster;
	}
	
	public void startCluster() {
		String op = "startCluster (" + nodeData.ip+", " + nodeData.port +")";
		debug(op, BEGIN);
		
		 Thread thread = new Thread(){
		    public void run(){
		    	CompletableFuture<CopycatServer> future = raft_server.bootstrap();
		    	future.join();
		    }
		  };
		  thread.start();
		  
		  debug(op, END);
	}
	
	public void startCluster(Collection<Address> cluster) {
		String op = "startCluster (" + nodeData.ip+", " + nodeData.port +")";
		debug(op, BEGIN);
		
		
		 Thread thread = new Thread(){
		    public void run(){
		    	CompletableFuture<CopycatServer> future = raft_server.bootstrap(cluster);
		    	future.join();
		    	
		    }
		  };
		  thread.start();
		  
		  debug(op, END);
	}
	
	public void joinCluster(Collection<Address> clusters) {
			String op = "joinCluster (" +")";
			debug(op, BEGIN);
			
			Thread thread = new Thread(){
			    public void run(){
			    	raft_server.join(clusters).join();
			    }
			  };
			  thread.start();
			
			debug(op, END);
	}
	
	public Boolean AddEdgeCommand(Commit<AddEdgeCommand> commit){
		if(nodeData!=null) {
			String op = "AddEdgeCommand ( commit:"+nodeData.port+")";
			debug(op, BEGIN);
		
			if(commit!=null && (commit.operation().getE()!=null)){
			    try {
			      addEdge(convertThriftToEdge(commit.operation().getE()));
			    } finally {
			      commit.close();
			    }
		    }
		    
		    debug(op, END);
		}else {
			System.out.println("NodeData is null... Loading from log.");
		}
		return true;
    }
	
	public Boolean AddVertexCommand(Commit<AddVertexCommand> commit){
		if(nodeData!=null) {
			String op = "AddVertexCommand ( commit:"+nodeData.port+")";
			debug(op, BEGIN);
		
			if(commit!=null && (commit.operation().getV()!=null)){
			    try {
			      addVertex(convertThriftToVertex(commit.operation().getV()));
			    } finally {
			      commit.close();
			    }
		    }else {
		    	System.out.println("Commit null");
		    }
		    
		    debug(op, END);
		}else {
			System.out.println("NodeData is null... Loading from log.");
		}
		return true;
	}
	
	public Boolean DeleteEdgeCommand(Commit<DeleteEdgeCommand> commit){
		if(nodeData!=null) {
			String op = "DeleteEdgeCommand ( commit:"+nodeData.port+")";
			debug(op, BEGIN);
		
			if(commit!=null && (commit.operation().getE()!=null)){
			    try {
			      deleteEdge(convertThriftToEdge(commit.operation().getE()));
			    } finally {
			      commit.close();
			    }
		    }
		    
		    debug(op, END);
		}else {
			System.out.println("NodeData is null... Loading from log.");
		}
		return true;
	}
	
	public Boolean DeleteVertexCommand(Commit<DeleteVertexCommand> commit){
		if(nodeData!=null) {
			String op = "DeleteVertexCommand ( commit:"+nodeData.port+")";
			debug(op, BEGIN);
		
			if(commit!=null && (commit.operation().getV()!=null)){
			    try {
			      deleteVertex(convertThriftToVertex(commit.operation().getV()));
			    } finally {
			      commit.close();
			    }
		    }
		    
		    debug(op, END);
		}else {
			System.out.println("NodeData is null... Loading from log.");
		}
		return true;
	}
	
	public Boolean UpdateEdgeCommand(Commit<UpdateEdgeCommand> commit){
		if(nodeData!=null) {
			String op = "UpdateEdgeCommand ( commit:"+nodeData.port+")";
			debug(op, BEGIN);
		
			if(commit!=null && (commit.operation().getE()!=null)){
			    try {
			      updateEdge(convertThriftToEdge(commit.operation().getE()));
			    } finally {
			      commit.close();
			    }
		    }
		    
		    debug(op, END);
		}else {
			System.out.println("NodeData is null... Loading from log.");
		}
		return true;
	}
	
	public Boolean UpdateVertexCommand(Commit<UpdateVertexCommand> commit){
		if(nodeData!=null) {
			String op = "UpdateVertxCommand ( commit:"+nodeData.port+")";
			debug(op, BEGIN);
		
			if(commit!=null && (commit.operation().getV()!=null)){
			    try {
			      updateVertex(convertThriftToVertex(commit.operation().getV()));
			    } finally {
			      commit.close();
			    }
		    }
		    
		    debug(op, END);
		}else {
			System.out.println("NodeData is null... Loading from log.");
		}
		return true;
	}

	
	public CopycatClient buildClient() {
		CopycatClient.Builder builder = CopycatClient.builder();
		builder.withTransport(NettyTransport.builder()
				  .withThreads(2)
				  .build());
		CopycatClient client = builder.build();
		client.serializer().register(AddEdgeCommand.class);
		client.serializer().register(AddVertexCommand.class);
		client.serializer().register(DeleteEdgeCommand.class);
		client.serializer().register(DeleteVertexCommand.class);
		client.serializer().register(UpdateEdgeCommand.class);
		client.serializer().register(UpdateVertexCommand.class);
		return client;
		
	}


	
	
}
