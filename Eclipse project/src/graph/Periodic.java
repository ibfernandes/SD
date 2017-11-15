package graph;

import server.Server;

public class Periodic implements Runnable{

	private Server s;
	private long segundo = 1000000000;
	private long period = 2 * segundo;
	
	public Periodic(Server s) {
		this.s = s;
	}
	@Override
	public void run() {

		
		long start = System.nanoTime();
		
		while(true) {

			long time = System.nanoTime();
			if(time-start>period) {
				start = System.nanoTime();
				
				s.handler.stabilize();
				s.handler.fixFingers();
			}
			
		}
	}

}
