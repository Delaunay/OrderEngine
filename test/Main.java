import LiveMarketData.LiveMarketData;
import OrderManager.OrderManager;
import Utility.Configuration;
import org.apache.log4j.BasicConfigurator;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.logging.Level;
import java.util.logging.Logger;

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

        LiveMarketData liveMarketData = new SampleLiveMarketData();

        // Starting Order Manager
        Thread om = new Thread(new SampleOrderManager(om_port, liveMarketData));
            om.setName("Order Manager");
            om.start();

        InetSocketAddress om_address = new InetSocketAddress(om_hostname, om_port);


        for(int i = 0; i < num_router; ++i) {
            SampleRouter rt = new SampleRouter("Router " + i, om_address);
            rt.start();
        }

        for(int i = 0; i < num_trader; ++i) {
            SampleTrader rt = new SampleTrader("Trader " + i, om_address);
            rt.start();
        }

        for(int i = 0; i < num_client; ++i) {
            SampleClient sc = new SampleClient("Client " + i, print_delta, initial_orders, om_address);
            sc.start();
        }
    }
}


