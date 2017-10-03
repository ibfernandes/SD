package graph;

public class Vertex {
	private int name;
	private int color;
	private String description;
	private double weight;
	
	public Vertex(int name){
		this.name = name;
	}
	
	public Vertex(int name, int color, String description, double weight) {
		this.name = name;
		this.color = color;
		this.description = description;
		this.weight = weight;
	}
	
	public int getName() {
		return name;
	}
	public int getColor() {
		return color;
	}
	public String getDescription() {
		return description;
	}
	public double getWeight() {
		return weight;
	}

	public void setColor(int color) {
		this.color = color;
	}
	public void setDescription(String description) {
		this.description = description;
	}
	public void setWeight(float weight) {
		this.weight = weight;
	}
	
	public void print(){
		System.out.printf("\n-------------------------\n"
				+ " - Nome (Key): %d\n"
				+ " - Descricao: %s\n"
				+ " - Cor: %d\n"
				+ " - Peso: %f\n"
				+ "-------------------------\n",this.getName(),this.getDescription(),this.getColor(),this.getWeight());
	}
}
