import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.logging.Level;
import java.util.logging.Logger;

import LiveMarketData.LiveMarketData;
import OrderManager.OrderManager;
import org.apache.log4j.BasicConfigurator;
import Utility.Configuration;

public class Main {
    public static void main(String[] args) throws IOException {
        System.out.println("TEST: this program tests ordermanager");
        BasicConfigurator.configure();

// Load conf file if any
        Configuration conf = new Configuration();
        int delta = 7;

        //start sample clients
        int client_port = conf.getPort("client");
        int num_client  = conf.getClientNumber();
        InetSocketAddress[] clients = new InetSocketAddress[num_client];
        for(int i  = 0; i < num_client; ++i) {
            (new MockClient("Client " + i, client_port + i, 10, delta)).start();
            clients[i] = new InetSocketAddress("localhost", client_port + i);
        }

        //start sample routers
        int router_port = client_port + num_client;
        int num_router  = conf.getRouterNumber();
        InetSocketAddress[] routers = new InetSocketAddress[num_router];
        for(int i  = 0; i < num_client; ++i) {
            (new Thread (new SampleRouter("Router " + i, router_port + i))).start();
            routers[i] = new InetSocketAddress("localhost", router_port + i);
        }

        //start sample trader
        int trader_port = router_port + num_router;
        int num_traders = conf.getTraderNumber();
        (new Thread (new SampleTrader("Trader ", trader_port))).start();
        InetSocketAddress traders = new InetSocketAddress("localhost", trader_port);
        /*
        int num_traders  = 2;
        InetSocketAddress[] traders = new InetSocketAddress[2];
        for(int i  = 0; i < num_traders; ++i) {
            (new Thread (new SampleTrader("Trader " + i, trader_port + i))).start();
            traders[i] = new InetSocketAddress("localhost", trader_port + i);
        } */

        //start order manager
        int market_port = trader_port + num_traders;
        LiveMarketData liveMarketData = new SampleLiveMarketData();

        (new MockOM("Order Manager", routers, clients, traders, liveMarketData)).start();
    }
}

class MockClient extends Thread {
    int port;
    int orders;
    int milli;

    MockClient(String name, int port, int orders , int milli) {
        this.port = port;
        this.setName(name);
        this.orders = orders;
        this.milli  = milli ;
    }

    public void run() {
        try {
            SampleClient client = new SampleClient(port,milli);

            for (int i = 0; i < orders; ++i) {
                int id = client.sendOrder(null);
            }

            client.messageHandler();
        } catch (IOException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
    }
}

class MockOM extends Thread {
    InetSocketAddress[] clients;
    InetSocketAddress[] routers;
    InetSocketAddress trader;
    LiveMarketData liveMarketData;

    MockOM(String name,
           InetSocketAddress[] routers,
           InetSocketAddress[] clients,
           InetSocketAddress trader,
           LiveMarketData liveMarketData)
    {
        this.clients = clients;
        this.routers = routers;
        this.trader = trader;
        this.liveMarketData = liveMarketData;
        this.setName(name);
    }

    @Override
    public void run() {
        try {
            //In order to debug constructors you can do F5 F7 F5
            new OrderManager(routers, clients, trader, liveMarketData).run();
        } catch (InterruptedException ex) {
            Logger.getLogger(MockOM.class.getName()).log(Level.SEVERE, null, ex);
        }
    }
}