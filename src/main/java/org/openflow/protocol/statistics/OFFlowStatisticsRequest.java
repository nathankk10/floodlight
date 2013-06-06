/**
*    Copyright (c) 2008 The Board of Trustees of The Leland Stanford Junior
*    University
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

package org.openflow.protocol.statistics;


import org.jboss.netty.buffer.ChannelBuffer;
import org.openflow.protocol.OFMatch;

/**
 * Represents an ofp_flow_stats_request structure<br>
 * 生成一个ofp_flow_stats_request，用以获得每条flow的信息<br>
 * 下一步：setMatch，setTableId，setOutPort<br>
 * 最后：加入OFStatisticsRequest中，并修改其长度！
 * @author David Erickson (daviderickson@cs.stanford.edu)
 */
public class OFFlowStatisticsRequest implements OFStatistics {
    protected OFMatch match;
    protected byte tableId;
    protected short outPort;

    /**
     * @return the match
     */
    public OFMatch getMatch() {
        return match;
    }

    /**
     * @param match the match to set
     */
    public void setMatch(OFMatch match) {
        this.match = match;
    }

    /**
     * @return the tableId
     */
    public byte getTableId() {
        return tableId;
    }

    /**ID of table to read (from ofp_table_stats)
     * @param tableId the tableId to set, 0xFF for all tables
     */
    public void setTableId(byte tableId) {
        this.tableId = tableId;
    }

    /**
     * @return the outPort
     */
    public short getOutPort() {
        return outPort;
    }

    /**
     * Require matching entries to include this as an output port.  A value of OFPP_ANY indicates no restriction.
     * @param outPort the outPort to set<br>
     * 输出端口，OFPort.OFPP_NONE则没有限制
     */
    public void setOutPort(short outPort) {
        this.outPort = outPort;
    }

    @Override
    public int getLength() {
        return 44;
    }

    @Override
    public void readFrom(ChannelBuffer data) {
        if (this.match == null)
            this.match = new OFMatch();
        this.match.readFrom(data);
        this.tableId = data.readByte();
        data.readByte(); // pad
        this.outPort = data.readShort();
    }

    @Override
    public void writeTo(ChannelBuffer data) {
        this.match.writeTo(data);
        data.writeByte(this.tableId);
        data.writeByte((byte) 0);
        data.writeShort(this.outPort);
    }

    @Override
    public int hashCode() {
        final int prime = 421;
        int result = 1;
        result = prime * result + ((match == null) ? 0 : match.hashCode());
        result = prime * result + outPort;
        result = prime * result + tableId;
        return result;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj == null) {
            return false;
        }
        if (!(obj instanceof OFFlowStatisticsRequest)) {
            return false;
        }
        OFFlowStatisticsRequest other = (OFFlowStatisticsRequest) obj;
        if (match == null) {
            if (other.match != null) {
                return false;
            }
        } else if (!match.equals(other.match)) {
            return false;
        }
        if (outPort != other.outPort) {
            return false;
        }
        if (tableId != other.tableId) {
            return false;
        }
        return true;
    }
}
