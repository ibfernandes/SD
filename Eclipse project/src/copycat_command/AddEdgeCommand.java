package copycat_command;

import io.atomix.copycat.Command;

public class AddEdgeCommand implements Command<Boolean>{
	private thrift.Edge e;

    public AddEdgeCommand(thrift.Edge e){
        this.e = e;
    }

	public thrift.Edge getE() {
		return e;
	}

	public void setE(thrift.Edge e) {
		this.e = e;
	}
}