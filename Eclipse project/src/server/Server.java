package server;

import org.apache.thrift.protocol.TBinaryProtocol;
import org.apache.thrift.server.TServer;
import org.apache.thrift.transport.TServerSocket;
import org.apache.thrift.transport.TServerTransport;
import org.apache.thrift.transport.TTransportFactory;

import graph.Graph;
import graph.Settings;

import org.apache.thrift.server.TServer.Args;
import org.apache.thrift.server.TSimpleServer;
import org.apache.thrift.server.TThreadPoolServer;


import java.util.HashMap;

public class Server implements Runnable{
	Graph handler;
	TServerTransport serverTransport ;
	TServer server ;
	TThreadPoolServer.Args serverArgs;
	/*	Thrift Network Stack
	 	+-------------------------------------------+
		| cGRE                                      |
		| Server                                    |
		| (single-threaded, event-driven etc)       |
		+-------------------------------------------+
		| cBLU                                      |
		| Processor                                 |
		| (compiler generated)                      |
		+-------------------------------------------+
		| cGRE                                      |
		| Protocol                                  |
		| (JSON, compact etc)                       |
		+-------------------------------------------+
		| cGRE                                      |
		| Transport                                 |
		| (raw TCP, HTTP etc)                       |
		+-------------------------------------------+
		source: https://diwakergupta.github.io/thrift-missing-guide/
	*/
  public void init() {
    try {
    	handler = new Graph();
    	thrift.service_graph.Processor processor = new thrift.service_graph.Processor(handler);
    	
    	serverTransport = new TServerSocket(Settings.port);
    	
		serverArgs=new TThreadPoolServer.Args(serverTransport);
	    serverArgs.processor(processor);
	    serverArgs.transportFactory(new TTransportFactory());
	    serverArgs.protocolFactory(new TBinaryProtocol.Factory(true,true));
	    serverArgs.maxWorkerThreads(8); // Recommended to be equal machine's cores
	    serverArgs.minWorkerThreads(4);
	    
	    server = new TThreadPoolServer(serverArgs);

    	System.out.println("Starting server...");
    } catch (Exception x) {
    	x.printStackTrace();
    }
  }

@Override
	public void run() {
		server.serve();
	}
}
