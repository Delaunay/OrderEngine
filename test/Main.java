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

        // Starting Order Manager
        Thread om = new Thread(new SampleOrderManager(om_port));
            om.setName("Order Manager");
            om.start();

        InetSocketAddress om_address = new InetSocketAddress(om_hostname, om_port);

        Thread lm = new Thread(new SampleLiveMarketData(om_address));
            lm.setName("LiveMarketData");
            lm.start();

        for(int i = 0; i < num_router; ++i) {
            Thread t = new Thread(new SampleRouter(om_address));
                t.setName("Router " + i);
                t.start();
        }

        for(int i = 0; i < num_trader; ++i) {
            Thread t = new Thread(new SampleTrader(om_address));
                t.setName("Trader " + i);
                t.start();
        }

        for(int i = 0; i < num_client; ++i) {
            Thread t = new Thread( new SampleClient(print_delta, initial_orders, om_address));
                t.setName("Client " + i);
                t.start();
        }
    }
}


