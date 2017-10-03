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
	map<i32,Vertex> getAdjacentVertices(1:i32 name)
} 
 	
//You must rename files edge.java and vertex.java to Edge.java and Vertex.java after compiling