package graph;

public class Edge {
	private int vertexOrigin;
	private int vertexDestiny;
	private double weight;
	private static int isDirected;
	private String description;
	
	public Edge(int vertexOrigin, int vertexDestiny){
		this.vertexDestiny	= vertexDestiny;
		this.vertexOrigin	= vertexOrigin;
	}
	
	public Edge(int vertexOrigin, int vertexDestiny, double weight, int isDirected, String description) {
		super();
		this.vertexOrigin = vertexOrigin;
		this.vertexDestiny = vertexDestiny;
		this.weight = weight;
		this.description = description;
		this.isDirected = isDirected;
	}

	public int getVertexOrigin() {
		return vertexOrigin;
	}
	public int getVertexDestiny() {
		return vertexDestiny;
	}
	public double getWeight() {
		return weight;
	}
	public static int getIsDirected() {
		return isDirected;
	}
	public String getDescription() {
		return description;
	}
	public void setWeight(float weight) {
		this.weight = weight;
	}
	public static void setIsDirected(int isDirected) {
		Edge.isDirected = isDirected;
	}
	public void setDescription(String description) {
		this.description = description;
	}
	
	public void print(){
		System.out.printf("\n-------------------------\n"
				+ " - Vértice Origem (Key): %d\n"
				+ " - Vértice Destino (Key): %d\n"
				+ " - Descricao: %s\n"
				+ " - É direcionado: %d\n "
				+ " - Peso: %f\n"
				+ "-------------------------\n",this.getVertexOrigin(), this.getVertexDestiny(),this.getDescription(),this.getIsDirected(),this.getWeight());
	}
	
}
