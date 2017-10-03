package server;

import java.nio.file.Paths;

import org.apache.thrift.TException;
import org.apache.thrift.transport.TTransport;

import graph.Settings;

import org.apache.thrift.transport.TSocket;
import org.apache.thrift.protocol.TBinaryProtocol;
import org.apache.thrift.protocol.TProtocol;

public class Main {
	public static void main(String [] args) {
		Thread thread_server;
		Thread thread_client_1;
		Thread thread_client_2;
		Thread thread_client_3;
		Settings settings;
		settings = new Settings(Paths.get("").toAbsolutePath().toString()+"\\settings.ini");

		Server s = new Server();
		s.init();
		
		thread_server = new Thread(s);
		thread_server.start();
		
		
		Client c1 = new Client();
		c1.id = 1;
		c1.init();
		
		Client c2 = new Client();
		c2.id = 2;
		c2.init();
		
		Client c3 = new Client();
		c3.id = 3;
		c3.init();

		
		thread_client_1 = new Thread(c1);
		thread_client_1.start();
		
		thread_client_2 = new Thread(c2);
		thread_client_2.start();
		
		thread_client_3 = new Thread(c3);
		thread_client_3.start();

		
	  }
}
