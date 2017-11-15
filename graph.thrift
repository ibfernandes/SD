namespace java thrift

struct Vertex {
	1:i32 name,
	2:i32 color,
	3:string description,
	4:double weight
}

struct Edge {
	1:i32 vertexOrigin,
	2:i32 vertexDestiny,
	3:string description,
	4:double weight,
	5:i32 isDirected
}

struct NodeData {
	1:i32 id,
	2:string ip,
	3:i32 port
}

service service_graph{
    void addVertex(1:i32 name, 2:i32 color, 3:string description, 4:double weight),
    void addEdge(1:i32 vertexOrigin, 2:i32 vertexDestiny, 3:string description, 4:double weight, 5:i32 isDirected),
	void deleteVertex(1:i32 name),
	void deleteEdge(1:i32 vertexOrigin, 2:i32 vertexDestiny),
	void updateVertex(1:i32 name, 2:i32 color, 3:string description, 4:double weight),
	void updateEdge(1:i32 vertexOrigin, 2:i32 vertexDestiny, 3:string description, 4:double weight, 5:i32 isDirected),
	Vertex getVertex(1:i32 name),
	Edge getEdge(1:string name),
	map<i32,Vertex> getVertices(),
	map<string,Edge> getEdges(),
	map<string,Edge> getEdgesOfVertex(1:i32 name),
	map<i32,Vertex> getAdjacentVertices(1:i32 name),
	map<i32,Vertex> getAllRingVertices(),
	map<string,Edge> getAllRingEdges(),
	map<i32,Edge> shortestPathBetweenVertices(1:Vertex a, 2:Vertex b),
	
	void init_node(1:i32 m, 2:NodeData node),
	NodeData find_sucessor(1:NodeData node),
	NodeData getPredecessor(),
	NodeData getSucessor(1:NodeData node),
	NodeData find_predecessor(1:NodeData node),
	NodeData closest_preceding_finger(1:NodeData node),
	void join(1:NodeData node),
	void stabilize(),
	void notifyNode(1:NodeData node),
	void fixFingers(),
	void printFingerTable(),
	void initFingerTable(1:list<NodeData> nodes),
	void initPredecessor(1:NodeData predecessor)
	
}
 	
//You must rename files edge.java and vertex.java to Edge.java and Vertex.java after compiling