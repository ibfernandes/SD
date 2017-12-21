package copycat_command;

import io.atomix.copycat.Command;

public class UpdateVertexCommand implements Command<Object>{
	private thrift.Vertex v;

    public UpdateVertexCommand(thrift.Vertex v){
        this.v = v;
    }

	public thrift.Vertex getV() {
		return v;
	}

	public void setV(thrift.Vertex v) {
		this.v = v;
	}
}