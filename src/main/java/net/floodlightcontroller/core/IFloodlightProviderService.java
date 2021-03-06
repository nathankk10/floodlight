/**
 *    Copyright 2011, Big Switch Networks, Inc. 
 *    Originally created by David Erickson, Stanford University
 * 
 *    Licensed under the Apache License, Version 2.0 (the "License"); you may
 *    not use this file except in compliance with the License. You may obtain
 *    a copy of the License at
 *
 *         http://www.apache.org/licenses/LICENSE-2.0
 *
 *    Unless required by applicable law or agreed to in writing, software
 *    distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 *    WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 *    License for the specific language governing permissions and limitations
 *    under the License.
 **/

package net.floodlightcontroller.core;

import java.util.List;
import java.util.Map;

import net.floodlightcontroller.core.module.IFloodlightService;
import net.floodlightcontroller.packet.Ethernet;

import org.openflow.protocol.OFMessage;
import org.openflow.protocol.OFType;
import org.openflow.protocol.factory.BasicFactory;

/**
 * The interface exposed by the core bundle that allows you to interact
 * with connected switches.<br>
 * IFloodlightProviderService提供的主要功能：<br>
 * 1. 监听器：IOFMessageListener（数据包）、IOFSwitchListener（Switch的增加减少）、IHAListener（在OF1.2中Controller的Role变化）<br>
 * 2. 存储的Packet-in的payload，为静态变量bcStore<br>
 * 3. getSwitches()获得所有连接的Switch<br>
 * 4. getOFMessageFactory()获得构建OpenFlow包的工厂类，得到的包，最后可以通过OFMessageDamper写入，或Switch.write方法写入<br>
 * 5. 其他……
 *
 * @author David Erickson (daviderickson@cs.stanford.edu)
 */
public interface IFloodlightProviderService extends
        IFloodlightService {

    /**
     * A value stored in the floodlight context containing a parsed packet
     * representation of the payload of a packet-in message. 
     */
    public static final String CONTEXT_PI_PAYLOAD = 
            "net.floodlightcontroller.core.IFloodlightProvider.piPayload";

    /**
     * The role of the controller as used by the OF 1.2 and OVS failover and
     * load-balancing mechanism.
     */
    public static enum Role { EQUAL, MASTER, SLAVE };
    
    /**
     * A FloodlightContextStore object that can be used to retrieve the 
     * packet-in payload
     */
    public static final FloodlightContextStore<Ethernet> bcStore = 
            new FloodlightContextStore<Ethernet>();

    /**
     * Adds an OpenFlow message listener
     * @param type The OFType the component wants to listen for
     * @param listener The component that wants to listen for the message
     */
    public void addOFMessageListener(OFType type, IOFMessageListener listener);

    /**
     * Removes an OpenFlow message listener
     * @param type The OFType the component no long wants to listen for
     * @param listener The component that no longer wants to receive the message
     */
    public void removeOFMessageListener(OFType type, IOFMessageListener listener);
    
    /**
     * Return a non-modifiable list of all current listeners
     * @return listeners
     */
    public Map<OFType, List<IOFMessageListener>> getListeners();

    /**
     * Returns an unmodifiable map of all actively connected OpenFlow switches. This doesn't
     * contain switches that are connected but the controller's in the slave role.
     * 返回的Map中元素遍历输出多组数据，每个元素为：Switch:OFSwitchBase [/192.168.221.130:49937 DPID[00:00:00:00:00:00:00:03]]
     * @return the set of actively connected switches<br>
     * Map的Key是Long IOFSwitch.getId()，Value是IOFSwitch
     */
    public Map<Long, IOFSwitch> getSwitches();
    
    /**
     * Get the current role of the controller
     */
    public Role getRole();
    
    /**
     * Get the current role of the controller
     */
    public RoleInfo getRoleInfo();

    /**
     * Get the current mapping of controller IDs to their IP addresses
     * Returns a copy of the current mapping. 
     * @see IHAListener
     */
    public Map<String,String> getControllerNodeIPs();
    
    
    /**
     * Set the role of the controller
     * @param role The new role for the controller node
     * @param changeDescription The reason or other information for this role change
     */
    public void setRole(Role role, String changeDescription);
    
    /**
     * Add a switch listener
     * @param listener The module that wants to listen for events
     */
    public void addOFSwitchListener(IOFSwitchListener listener);

    /**
     * Remove a switch listener
     * @param listener The The module that no longer wants to listen for events
     */
    public void removeOFSwitchListener(IOFSwitchListener listener);
    
    /**
     * Adds a listener for HA role events
     * @param listener The module that wants to listen for events
     */
    public void addHAListener(IHAListener listener);
    
    /**
     * Removes a listener for HA role events
     * @param listener The module that no longer wants to listen for events
     */
    public void removeHAListener(IHAListener listener);

    /**
     * Terminate the process
     */
    public void terminate();

    /**
     * Re-injects an OFMessage back into the packet processing chain
     * @param sw The switch to use for the message
     * @param msg the message to inject
     * @return True if successfully re-injected, false otherwise
     */
    public boolean injectOfMessage(IOFSwitch sw, OFMessage msg);

    /**
     * Re-injects an OFMessage back into the packet processing chain
     * @param sw The switch to use for the message
     * @param msg the message to inject
     * @param bContext a floodlight context to use if required
     * @return True if successfully re-injected, false otherwise
     */
    public boolean injectOfMessage(IOFSwitch sw, OFMessage msg, 
            FloodlightContext bContext);

    /**
     * Process written messages through the message listeners for the controller
     * @param sw The switch being written to
     * @param m the message 
     * @param bc any accompanying context object
     */
    public void handleOutgoingMessage(IOFSwitch sw, OFMessage m, 
            FloodlightContext bc);

    /**
     * Gets the BasicFactory
     * @return an OpenFlow message factory
     */
    public BasicFactory getOFMessageFactory();

    /**
     * Run the main I/O loop of the Controller.
     */
    public void run();

    /**
     * Add an info provider of a particular type
     * @param type
     * @param provider
     */
    public void addInfoProvider(String type, IInfoProvider provider);

   /**
    * Remove an info provider of a particular type
    * @param type
    * @param provider
    */
   public void removeInfoProvider(String type, IInfoProvider provider);
   
   /**
    * Return information of a particular type (for rest services)
    * @param type
    * @return
    */
   public Map<String, Object> getControllerInfo(String type);
   
   
   /**
    * Return the controller start time in  milliseconds
    * @return
    */
   public long getSystemStartTime();
   
   /**
    * Configure controller to always clear the flow table on the switch,
    * when it connects to controller. This will be true for first time switch
    * reconnect, as well as a switch re-attaching to Controller after HA
    * switch over to ACTIVE role
    */
   public void setAlwaysClearFlowsOnSwAdd(boolean value);
   
   /**
    * Get controller memory information
    */
   public Map<String, Long> getMemory();

   /**
    * returns the uptime of this controller.
    * @return
    */
   public Long getUptime();
   
   /**
    * Adds an OFSwitch driver
    * @param desc The starting portion of switch's manufacturer string
    * @param driver The object implementing OFSwitchDriver interface
    */
   public void addOFSwitchDriver(String desc, IOFSwitchDriver driver);

}
