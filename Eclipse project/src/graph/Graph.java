package graph;

import java.util.ArrayList;
import java.util.EmptyStackException;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentHashMap.KeySetView;

import org.apache.thrift.TException;

import sun.misc.Lock;
import thrift.service_graph.Iface;


public class Graph implements Iface{
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
	
	
	public void addVertex(Vertex v){
		String op = "addVertex ( id "+v.getName()+" )";
		debug(op, BEGIN);
		
		while(!acquireResource(Integer.toString(v.getName()))){
			// If it fails to acquire resource -> keeps waiting
		}
		
		if(vertices.putIfAbsent(v.getName(), v)!=null)
			System.out.printf("\n[Token "+Thread.currentThread().getId()+"]\tThere's already a vertex with that name! ( id %d )\n", v.getName());
		
		debug(op, END);
		releaseResource(Integer.toString(v.getName()));
	}
	
	public void addEdge(Edge e){
		int origin  = e.getVertexOrigin();
		int destiny = e.getVertexDestiny();
		String key 	= origin+"-"+destiny;
		String op = "addEdge ( id "+key+" )";
		debug(op, BEGIN);
		
		if(origin==destiny){
			System.out.printf("\n[Token "+Thread.currentThread().getId()+"]\tLoops are not allowed! Aborting...( id %s )\n", key);
			return;
		}
		
		while(!acquireResource(key)){
			// If it fails to acquire resource -> keeps waiting
		}

		/* Critical [!!]
		 * Os valores dos vertices não podem mudar antes de verificar se eles existem para inserção da aresta.
		 *  */

		if(vertices.containsKey(origin) && vertices.containsKey(destiny)){// only inserts if its vertices already exists
			if(edges.putIfAbsent(key, e)!=null)
				System.out.printf("\n[Token "+Thread.currentThread().getId()+"]\tThere's already an edge with that name! ( id %s )\n", key);
		}else{
			System.out.printf("\n[Token "+Thread.currentThread().getId()+"]\tOne of these vertices doesn't exist! ( id %s )\n", key);
		}
	
		debug(op, END);
		releaseResource(key);
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
		
		if(origin==destiny){
			System.out.printf("\n[Token "+Thread.currentThread().getId()+"]\tLoops are not allowed! Aborting...( id %s )\n", key);
			return;
		}
		
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
		
		if(origin==destiny){
			System.out.printf("\n[Token "+Thread.currentThread().getId()+"]\tLoops are not allowed! Aborting...( id %s )\n", key);
			return;
		}
		
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
		if(debug){
			if(var==BEGIN)
				System.out.printf("\n-------------\n[Token "+Thread.currentThread().getId()+"]\tBEGIN \t"+t+" \n");
			if(var==END)
				System.out.printf("\n[Token "+Thread.currentThread().getId()+"]\tEND \t"+t+" \n-------------\n");
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
	
	@Override
	public void addVertex(int name, int color, String description, double weight) throws TException {
		addVertex(new Vertex(name, color, description, weight));
	}

	@Override
	public void addEdge(int vertexOrigin, int vertexDestiny, String description, double weight, int isDirected)
			throws TException {
		addEdge(new Edge(vertexOrigin, vertexDestiny, weight, isDirected, description));
	}

	@Override
	public void deleteVertex(int name) throws TException {
		deleteVertex(new Vertex(name));
	}

	@Override
	public void deleteEdge(int vertexOrigin, int vertexDestiny) throws TException {
		deleteEdge(new Edge(vertexOrigin, vertexDestiny));
	}

	@Override
	public void updateVertex(int name, int color, String description, double weight) throws TException {
		updateVertex(new Vertex(name, color, description, weight));
	}

	@Override
	public void updateEdge(int vertexOrigin, int vertexDestiny, String description, double weight, int isDirected)
			throws TException {
		updateEdge(new Edge(vertexOrigin, vertexDestiny, weight, isDirected, description));
		
	}

	@Override
	public thrift.Vertex getVertex(int name) throws TException {
		String op = "getEdge ( "+name+" )";
		debug(op, BEGIN);
		
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
	public Map<Integer, thrift.Vertex> getAdjacentVertices(int name) throws TException {
		String op = "getAdjacentVertices ( id "+name+" )";
		debug(op, BEGIN);
		
		Map<Integer, thrift.Vertex> m  = new HashMap<>();
		KeySetView<String, Edge> keys = edges.keySet();
		Vertex buffer;
		Vertex v = vertices.get(name);
		if(v!=null){
		
			for(String key : keys){
				if(key.matches(".-"+v.getName())){
					buffer = vertices.get(key.split("-")[0]);
					if(buffer!=null)
						m.put(buffer.getName(), convertVertexToThrift(buffer));
				}
				if(key.matches(v.getName()+"-.")){
					buffer = vertices.get(key.split("-")[1]);
					if(buffer!=null)
						m.put(buffer.getName(), convertVertexToThrift(buffer));
				}
			}
		}
		
		debug(op, END);
		return m;
	}
	
}
