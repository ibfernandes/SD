package server;

import java.util.HashMap;

import org.apache.thrift.transport.TTransportException;

import graph.Service;
import graph.Settings;
import thrift.NodeData;

public class Node {
	/*private NodeData finger[];
	
	private Server s;
	private Client c;
	
	private NodeData nodeData;
	
	private NodeData predecessor;
	private NodeData sucessor;
	int m;
	int next = 0; // 0 ou 1?
	
	public void init_node(int m, NodeData n){
		this.nodeData = n;
		this.m = m;
		finger = new NodeData[m];
		sucessor =  n;
		predecessor = null;
		
		
		//Inicia tabela com:
		
		//-------------------------
		// i = 0		|	N_id + 2^i < x <= próx sucessor existente maior igual 2^0 
		// i = 1		|	N_id + 2^i < x <= próx sucessor existente maior igual 2^1
		// i = 2		|	N_id + 2^i < x <= próx sucessor existente maior igual 2^2
		// ...			|	...
		
		
		//inicializa a finger table
		for(int i=0; i<m; i++) {
			finger[i] = null;
		}
	}
	
	
	public void net_join(NodeData node_id, int password){
		if(password==Settings.password) {
		
			predecessor = null;
			sucessor = net_findSucessor(node_id);
		
		}else {
			System.out.println("Senha incorreta");
		}
	}
	
	public void stabilize() {
		NodeData x = net_getPredecessor(sucessor);
		
		if(x.id>nodeData.id && x.id<sucessor.id)
			sucessor = x;
		net_notify(sucessor);
	}
	
	public void beNotified(NodeData node) {
		if(predecessor==null || (node.id>predecessor.id && node.id<nodeData.id))
			predecessor = node;
	}
	
	public void fixFingers() {
		next = next + 1;
		if(next>m) 
			next = 0; // 0 ou 1?
		finger[next] = findSucessor(finger[next]);
	}
	
	public void checkPredecessor() {
		c = new Client();
		c.init(predecessor.ip, predecessor.port);
		
		try {
			c.open();
			c.close();
		} catch (TTransportException e) {
			e.printStackTrace();
			predecessor = null;
		}
		
		
	}
	
	public void net_notify(NodeData node) {
		c = new Client();
		c.init(node.ip, node.port);
		
		try {
			c.open();
			c.getService().beNotified(node);
			c.close();
			
		} catch (Exception e) {
			e.printStackTrace();
		}
	}
	
	public NodeData getPredecessor() {
		return predecessor;
	}
	
	public NodeData net_getPredecessor(NodeData node) {
		NodeData result;
		c = new Client();
		c.init(node.ip, node.port);
		
		try {
			
			c.open();
			result = c.getPredecessor(node);
			c.close();
			
		} catch (TTransportException e) {
			e.printStackTrace();
		}
		
		return result;
	}
	
	public NodeData findSucessor(NodeData node) {
		if(node.id>nodeData.id && node.id<=sucessor.id)
				return sucessor;
		else {
			NodeData closest_node = closest_preceding_node(node);
			
			if(closest_node.id==nodeData.id)
				return closest_node;
			else
				return net_findSucessor(node); // repassa para o nodo mais proximo pergutando o sucessor
		}
	}
	
	public NodeData net_findSucessor(NodeData whos_is_sucessor_of_this_node) {
		NodeData closest_node;
		NodeData result;
		
		//same as closest_preceding_node
		for(int i = m; i>0; i-- ) {
			if(finger[i].id>nodeData.id && finger[i].id<whos_is_sucessor_of_this_node.id) {
				closest_node = finger[i];
			}
		}
		
		c = new Client();
		c.init(closest_node.ip, closest_node.port); // não garanti que o closest node recebeu algo
		
		try {
			
			c.open();
			result = c.findSucessor(whos_is_sucessor_of_this_node);
			c.close();
			
		} catch (TTransportException e) {
			e.printStackTrace();
		}
		
		return result;
	}
	
	public NodeData closest_preceding_node(NodeData node) { //Qual a node anterior mais próxima
		for(int i = m; i>0; i-- ) {
			if(finger[i].id>nodeData.id && finger[i].id<node.id)
				return finger[i];
		}
		return nodeData;
	}
	*/

	
	
	
}
