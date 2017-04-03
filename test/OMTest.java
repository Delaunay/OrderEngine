import OrderManager.OrderManager;
import Utility.Util;
import org.junit.Before;
import org.junit.Test;
import org.omg.PortableInterceptor.SYSTEM_EXCEPTION;

import java.io.IOException;
import java.net.InetSocketAddress;

import static org.junit.Assert.assertEquals;

public class OMTest {

    public void print(String message){
        System.out.println(message);
    }

    @Before
    public void init() throws IOException, InterruptedException {
        print("init clients");
        clients = new SampleClient[client_num];
        client_address = new InetSocketAddress[client_num];
        for(int i = 0; i < client_num; ++i){
            clients[i] = new SampleClient();
            int index = i;

            // First time connection is blocking...
            // so we spawn a thread just to connect
            new Thread(() -> {
                try {
                    clients[index].connectToOrderManager(client_port + index);
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }).start();

            client_address[i] = new InetSocketAddress(address, client_port + i);
        }

        print("init routers");
        routers = new SampleRouter[router_num];
        router_address = new InetSocketAddress[router_num];
        int router_port = client_port + client_num;
        for(int i = 0; i < client_num; ++i){
            routers[i] = new SampleRouter("Router " + i, router_port + i);
            routers[i].sleep = false;
            int index = i;

            new Thread(() -> {
                try {
                    routers[index].connectToOrderManager();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }).start();

            router_address[i] = new InetSocketAddress(address, router_port + i);
        }

        print("init trader");
        int trader_port = router_port + client_num;
        trader = new Trader("Trader James", trader_port);
        trader.sleep = false;

        new Thread(() -> {
            try {
                trader.connectToOrderManager();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }).start();

        trader_address = new InetSocketAddress(address, trader_port);

        marketData = new SampleLiveMarketData();

        print("init order manager");
        order_manager = new OrderManager(
                router_address,
                client_address,
                trader_address,
                marketData);
        print("Init done");
        Util.wait(1000);
    }

    public boolean runOnce() throws IOException, ClassNotFoundException, InterruptedException {
        boolean work = false;

        // We receive orders from clients
        work = work || order_manager.runOnce();

        // OM ask trader to accept each order or slice them
        work = work || trader.runOnce();

        // Slicing
        work = work || order_manager.runOnce();

        // Trader accepts sliced trade and sends confirmation to client
        for(int i = 0; i < client_num; ++i){
            work = work || clients[i].runOnce();
        }

        // get best price
        for(int i = 0; i < router_num; ++i) {
            work = work || routers[i].runOnce();
        }

        // Trader fill orders
        work = work || trader.runOnce();

        /*/ Client receive Confirmation... or not (not implemented)
        for(int i = 0; i < client_num; ++i){
            work = work || clients[i].runOnce();
        } //*/
        return work;
    }

    /* Why do we check for non blocking operation ?
     * We want our test to end and not run forever      */
    @Test
    public void nonBlockingNothing() throws IOException, ClassNotFoundException, InterruptedException {
        // We check if all our actors are indeed non blocking
        // furthermore they, actually, have nothing to do, so there is nothing they
        // should block on
        boolean work = runOnce();
        assertEquals(false, work);
    }

    @Test
    public void nonBlockingSomething() throws IOException, ClassNotFoundException, InterruptedException {
        // Same as before but we are giving some work to process before hand

        for(int i = 0; i < client_num; ++i){
            clients[i].sendOrder(null);
        }

        boolean work = runOnce();
        assertEquals(true, work);
    }

    // Actors
    // ------------------------------------------------------------------------
    Trader               trader;
    OrderManager         order_manager;
    SampleClient[]       clients;
    SampleRouter[]       routers;
    SampleLiveMarketData marketData;

    InetSocketAddress[] client_address;
    InetSocketAddress[] router_address;
    InetSocketAddress   trader_address;

    // Config
    // ------------------------------------------------------------------------
    final static int client_port = 2000;
    final static int client_num  = 3;
    final static int router_num  = 3;
    final static String address  = "localhost";
}
