package fi.ee.dynamic.nat;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;

import net.floodlightcontroller.devicemanager.IDevice;

import org.python.antlr.PythonParser.return_stmt_return;

import com.kenai.jffi.Array;

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
	
	
	//ARP包的处理
	public byte[] get_MAC_address (byte[] ipAddress){
		//test for 
		if (Arrays.equals(ipAddress, new byte[]{10,0,2,1})){
			return new byte[]{0,0,0,0,1,2};
		}
		if (Arrays.equals(ipAddress, new byte[]{10,0,1,1})){
			return new byte[]{0,0,0,0,1,1};
		}
		if (Arrays.equals(ipAddress, new byte[]{10,0,3,3})){
			return new byte[]{0,0,0,0,3,3};
		}
		return null;
	}
	
	
	public NATRecord performNat(NATRecord original){
		// 根据转换前的流信息，计算NAT转换，并完成
		return null;
	}
	
	public void deviceRemoved(IDevice device){
		// TODO
		return;
	}
	
	public void deviceIPV4AddrChanged(IDevice device){
		// TODO
		return;
	}
	
	public void deviceAdded(IDevice device){
		// TODO
		return;
	}
	
}
