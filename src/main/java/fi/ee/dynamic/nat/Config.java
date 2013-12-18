package fi.ee.dynamic.nat;

import net.floodlightcontroller.util.MACAddress;

public class Config {
	public static String NAT_SWITCH_MAC = "00:00:00:00:00:01";
	public static short NAT_SWITCH_OUTPORT = 2;
	public static String ROUTER_MAC = "A0:BC:AA:AA:AA:BB";
	public static String PUBLIC_IP = "166.111.8.1";
	
	public static short FLOWMOD_DEFAULT_IDLE_TIMEOUT = 5;
	public static short FLOWMOD_DEFAULT_HARD_TIMEOUT = 0;
	
}
