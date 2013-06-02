from mininet.topo import Topo
from mininet.net import Mininet
from mininet.util import dumpNodeConnections
from mininet.log import setLogLevel
from mininet.cli import CLI
from mininet.node import RemoteController

class MyTopo(Topo):
    "Single switch connected to n hosts."
    def __init__(self, **opts):
        # Initialize topology and default options
        Topo.__init__(self, **opts)
	# Add switch
        switch = self.addSwitch('s1')
        # Add hosts
        for h in range(4):
            host = self.addHost('h%s' % (h + 1), mac='00:00:00:00:00:0%s' % (h+1))
            self.addLink(host, switch , 1 , h+1)

def MyTest():
    "Create and test a simple network"
    topo = MyTopo()
    net = Mininet( topo=topo, controller=lambda name: RemoteController( name, ip='192.168.86.1' ) )
    net.start()
    host = net.get('h1')
    CLI(net)
    net.stop()

if __name__ == '__main__':
    # Tell mininet to print useful information
    setLogLevel('info')
    MyTest()
