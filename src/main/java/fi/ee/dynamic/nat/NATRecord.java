package fi.ee.dynamic.nat;

public class NATRecord {

	public int src_IPAddress;
	public short src_IPPort;
	
	public int dst_IPAddress;
	public short dst_IPPort;
	
	
	public NATRecord(int _src_IPAddress, short _src_IPPort, int _dst_IPAddress, short _dst_IPPort){
		src_IPAddress = _src_IPAddress;
		src_IPPort = _src_IPPort;
		dst_IPAddress = _dst_IPAddress;
		dst_IPPort = _dst_IPPort;
	}
	
}
