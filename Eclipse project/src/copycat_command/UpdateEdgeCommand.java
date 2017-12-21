package copycat_command;

import io.atomix.copycat.Command;

public class UpdateEdgeCommand implements Command<Object>{
	private thrift.Edge e;

    public UpdateEdgeCommand(thrift.Edge e){
        this.e = e;
    }

	public thrift.Edge getE() {
		return e;
	}

	public void setE(thrift.Edge e) {
		this.e = e;
	}
}