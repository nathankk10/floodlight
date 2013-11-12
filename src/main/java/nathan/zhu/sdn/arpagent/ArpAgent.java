package nathan.zhu.sdn.arpagent;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;

import net.floodlightcontroller.core.FloodlightContext;
import net.floodlightcontroller.core.IFloodlightProviderService;
import net.floodlightcontroller.core.IOFMessageListener;
import net.floodlightcontroller.core.IOFSwitch;
import net.floodlightcontroller.core.module.FloodlightModuleContext;
import net.floodlightcontroller.core.module.FloodlightModuleException;
import net.floodlightcontroller.core.module.IFloodlightModule;
import net.floodlightcontroller.core.module.IFloodlightService;
import net.floodlightcontroller.packet.ARP;
import net.floodlightcontroller.packet.Ethernet;

import org.openflow.protocol.OFMessage;
import org.openflow.protocol.OFPacketIn;
import org.openflow.protocol.OFPacketOut;
import org.openflow.protocol.OFPort;
import org.openflow.protocol.OFType;
import org.openflow.protocol.action.OFAction;
import org.openflow.protocol.action.OFActionOutput;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ArpAgent implements IFloodlightModule {

	protected static Logger logger;
	
	private ArpAgent self;
	
	private IFloodlightProviderService floodlightProvider;
	private IOFMessageListener ofMessageListener;
	
	/**
	 * 不提供service
	 */
	@Override
	public Collection<Class<? extends IFloodlightService>> getModuleServices() {
		return null;
	}

	/**
	 * 不提供service
	 */
	@Override
	public Map<Class<? extends IFloodlightService>, IFloodlightService> getServiceImpls() {
		return null;
	}

	/**
	 * 需要IFloodlightService接受包
	 */
	@Override
	public Collection<Class<? extends IFloodlightService>> getModuleDependencies() {
		Collection<Class<? extends IFloodlightService>> services = new ArrayList<Class<? extends IFloodlightService>>();
		services.add(IFloodlightProviderService.class);
		return services;
	}
	
	
	
	@Override
	public void init(FloodlightModuleContext context)
			throws FloodlightModuleException {
		self = this;
		logger = LoggerFactory.getLogger(this.getClass());
		floodlightProvider = (IFloodlightProviderService) context.getServiceImpl(IFloodlightProviderService.class);
		initIOFMessageListener();
	}

	@Override
	public void startUp(FloodlightModuleContext context)
			throws FloodlightModuleException {
		logger.debug("Starting ARP Agent");
		
		// IF ARP flow is never added, all ARP request packages will be sent here
		floodlightProvider.addOFMessageListener(OFType.PACKET_IN, ofMessageListener);
		
	}
	
	
	
	private void initIOFMessageListener(){
		ofMessageListener = new IOFMessageListener() {
			
			@Override
			public boolean isCallbackOrderingPrereq(OFType type, String name) {
				// This may need to change!!!
				return false;
			}
			
			@Override
			public boolean isCallbackOrderingPostreq(OFType type, String name) {
				return false;
			}
			
			@Override
			public String getName() {
				return self.getClass().getCanonicalName();
			}
			
			/**
			 * Here we make sure the required ARP responses are made through PACKET_OUT message
			 */
			@Override
			public Command receive(IOFSwitch sw, OFMessage msg, FloodlightContext cntx) {
				if (msg.getType()!=OFType.PACKET_IN) 
					return Command.CONTINUE;
				
				Ethernet eth = IFloodlightProviderService.bcStore.get(cntx, 
                        IFloodlightProviderService.CONTEXT_PI_PAYLOAD);
				// if not ARP, return with continue
				if (eth.getEtherType()!=Ethernet.TYPE_ARP){
					return Command.CONTINUE;
				} 
				
				// the package is ARP, make a package out to response
		        OFPacketOut po = 
		            (OFPacketOut) floodlightProvider.getOFMessageFactory().getMessage(OFType.PACKET_OUT);
		        List<OFAction> actions = new ArrayList<OFAction>();
		        // which port to send? flood?
		        if (sw.hasAttribute(IOFSwitch.PROP_SUPPORTS_OFPP_FLOOD)) {
		            actions.add(new OFActionOutput(OFPort.OFPP_FLOOD.getValue(), 
		                                           (short)0xFFFF));
		        } else {
		            actions.add(new OFActionOutput(OFPort.OFPP_ALL.getValue(), 
		                                           (short)0xFFFF));
		        }
		        po.setActions(actions);
		        po.setActionsLength((short) OFActionOutput.MINIMUM_LENGTH);

		        // set buffer-id, in-port and packet-data based on packet-in
		        OFPacketIn pi = (OFPacketIn)msg;
		        short poLength = (short)(po.getActionsLength() + OFPacketOut.MINIMUM_LENGTH);
		        po.setBufferId(OFPacketOut.BUFFER_ID_NONE);
		        
		        // what is the PACKET_OUT in port? the controller?
		        po.setInPort(OFPort.OFPP_CONTROLLER);
		        
		        // ARP frame set up according to payload as template
		        ARP arp = (ARP)eth.getPayload();
		        // ......
		        eth.setPayload(arp);
		        byte[] packetData = eth.serialize();
		        
		        poLength += packetData.length;
		        po.setPacketData(packetData);
		        po.setLength(poLength);
		        
		        try {
					sw.write(po, cntx);
				} catch (IOException e) {
					e.printStackTrace();
				}
		        return Command.STOP;
			}
		};
	}



}
