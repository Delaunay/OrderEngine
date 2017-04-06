import LiveMarketData.LiveMarketData;
import Utility.Configuration;
import org.apache.log4j.BasicConfigurator;

import java.io.IOException;
import java.net.InetSocketAddress;

public class Main {
    public static void main(String[] args) throws IOException {
        System.out.println("TEST: this program tests ordermanager");
        BasicConfigurator.configure();

        // Load conf file if any
        Configuration conf = new Configuration();

        String om_hostname = "localhost";
        int om_port        = conf.getOrderManagerPort();
        int num_client     = conf.getClientNumber();
        int num_router     = conf.getRouterNumber();
        int num_trader     = conf.getTraderNumber();
        int initial_orders = 10;
        int print_delta    = 10;


        //LiveMarketData liveMarketData = new SampleLiveMarketData();

        InetSocketAddress[] traders = new InetSocketAddress[num_trader];
        for(int i = 0; i < num_trader; ++i) {
            traders[i] = new InetSocketAddress(om_port + 1 + i);
            Thread t = new Thread(new SampleTrader(om_port + 1 + i));
            t.setName("Trader " + i);
            t.start();
        }

        InetSocketAddress[] routers = new InetSocketAddress[num_router];
        for(int i = 0; i < num_router; ++i) {
            int port = om_port + 1 + num_trader + i;
            routers[i] = new InetSocketAddress(port);
            Thread t = new Thread(new SampleRouter(port));
            t.setName("Router" + i);
            t.start();
        }


        LiveMarketData lmd = new SampleLiveMarketData(null);

        // Starting Order Manager
        SampleOrderManager m = new SampleOrderManager(om_port, lmd, traders, routers);

        Thread om = new Thread(m);
            om.setName("Order Manager");
            om.start();

        InetSocketAddress om_address = new InetSocketAddress(om_hostname, om_port);

        for(int i = 0; i < num_client; ++i) {
            Thread t = new Thread( new SampleClient(print_delta, initial_orders, om_address));
                t.setName("Client " + i);
                t.start();
        }
    }
}


