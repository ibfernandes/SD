package copycat_command;

import io.atomix.copycat.Command;

public class DeleteVertexCommand implements Command<Object>{
	private thrift.Vertex v;

    public DeleteVertexCommand(thrift.Vertex v){
        this.v = v;
    }

	public thrift.Vertex getV() {
		return v;
	}

	public void setV(thrift.Vertex v) {
		this.v = v;
	}
}