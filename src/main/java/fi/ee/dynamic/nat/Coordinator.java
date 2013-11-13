package fi.ee.dynamic.nat;

import java.util.ArrayList;
import java.util.HashMap;

public class Coordinator {

	private HashMap<Integer, ArrayList<Integer>> port_used_table;
	private ArrayList<Integer[]> nat_table;
	
	public Coordinator (){
		port_used_table = new HashMap<Integer, ArrayList<Integer>>();
		nat_table = new ArrayList<Integer[]>();
	}
	
	public void register_used_port(Integer ip, ArrayList<Integer> ports){
		ArrayList<Integer> port_used_list = port_used_table.get(ip);
		port_used_list.addAll(ports);
	}
	
	
}
