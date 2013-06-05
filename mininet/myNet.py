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
        s3 = self.addSwitch('s3')
        # Add hosts
        h1 = self.addHost('h1', mac='00:00:00:00:01:01', ip='10.0.1.1')
        h21 = self.addHost('h21', mac='00:00:00:00:02:01', ip='10.0.2.1')
        h22 = self.addHost('h22', mac='00:00:00:00:02:02',ip='10.0.2.2')
        h31 = self.addHost('h31', mac='00:00:00:00:03:01',ip='10.0.3.1')
        h32 = self.addHost('h32', mac='00:00:00:00:03:02',ip='10.0.3.2')
        # Add link
        self.addLink(s1, h1, 1, 1)
        self.addLink(s1, s2, 2, 1)
        self.addLink(s1, s3, 3, 1)
        self.addLink(s2, h21, 2, 1)
        self.addLink(s2, h22, 3, 1)
        self.addLink(s3, h31, 2, 1)
        self.addLink(s3, h32, 3, 1)

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
