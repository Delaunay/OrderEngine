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
        int delta = 1;
        int inital_orders = 10;

        //start sample clients
        int client_port = conf.getPort("client");
        int num_client  = conf.getClientNumber();
        InetSocketAddress[] clients = new InetSocketAddress[num_client];
        for(int i  = 0; i < num_client; ++i) {
            (new SampleClient("Client " + i, client_port + i, delta, inital_orders)).start();
            clients[i] = new InetSocketAddress("localhost", client_port + i);
        }

        //start sample routers
        int router_port = client_port + num_client;
        int num_router  = conf.getRouterNumber();
        InetSocketAddress[] routers = new InetSocketAddress[num_router];
        for(int i  = 0; i < num_router; ++i) {
            Thread t = new Thread (new SampleRouter("Router " + i, router_port + i));
            t.setName("Router: " + i);
            t.start();
            routers[i] = new InetSocketAddress("localhost", router_port + i);
        }

        //start sample trader
        int trader_port = router_port + num_router;
        int num_traders  = conf.getTraderNumber();
        InetSocketAddress[] traders = new InetSocketAddress[num_traders];
        for(int i  = 0; i < num_traders; ++i) {
            Thread t = new Thread (new SampleTrader("Trader " + i, trader_port + i));
            t.setName("Trader: " + i);
            t.start();
            traders[i] = new InetSocketAddress("localhost", trader_port + i);
        }

        //start order manager
        int market_port = trader_port + num_traders;
        LiveMarketData liveMarketData = new SampleLiveMarketData();

        Thread om = new Thread(new SampleOrderManager(routers, clients, traders, liveMarketData));
        om.setName("Order Manager");
        om.start();
    }
}


