package copycat_command;

import io.atomix.copycat.Command;

public class AddVertexCommand implements Command<Boolean>{
	private thrift.Vertex v;

    public AddVertexCommand(thrift.Vertex v){
        this.v = v;
    }

	public thrift.Vertex getV() {
		return v;
	}

	public void setV(thrift.Vertex v) {
		this.v = v;
	}
}