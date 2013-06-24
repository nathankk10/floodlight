from mininet.topo import Topo
from mininet.net import Mininet
from mininet.util import dumpNodeConnections
from mininet.log import setLogLevel
from mininet.cli import CLI
from mininet.node import RemoteController
import sys

class MyTopo(Topo):
    "Single switch connected to n hosts."
    def __init__(self, **opts):
        # Initialize topology and default options
        Topo.__init__(self, **opts)
		# Add switch
        s1 = self.addSwitch('s1')
	s2 = self.addSwitch('s2')
        s31 = self.addSwitch('s31')
	s32 = self.addSwitch('s32')
        s4 = self.addSwitch('s4')
	s5 = self.addSwitch('s5')
	s6 = self.addSwitch('s6')
	s7 = self.addSwitch('s7')
        # Add hosts
        h1 = self.addHost('h1', mac='00:00:00:00:01:01', ip='10.0.1.1')
	h5 = self.addHost('h5', mac='00:00:00:00:05:01', ip='10.0.5.1')
        # Add link
        self.addLink(s1, h1)
	self.addLink(s5, h5)
	self.addLink(s1, s2)
	self.addLink(s2, s31)
	self.addLink(s2, s32)
	self.addLink(s31, s4)
	self.addLink(s32, s4)
	self.addLink(s4,s5)
	self.addLink(s5,s6)
	self.addLink(s4,s6)
	self.addLink(s5,s7)
	self.addLink(s7,s1)


def MyTest(_ip='192.168.221.1'):
    print 'controller is ' + _ip
    topo = MyTopo()
    net = Mininet( topo=topo, controller=lambda name: RemoteController( name, ip=_ip ) )
    net.start()
    host = net.get('h1')
    CLI(net)
    net.stop()

if __name__ == '__main__':
    # Tell mininet to print useful information
    setLogLevel('info')
    ip = sys.argv[1]
    MyTest(ip)
