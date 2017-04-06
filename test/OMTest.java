import OrderManager.OrderManager;
import Utility.Util;
import org.apache.log4j.BasicConfigurator;
import org.junit.BeforeClass;
import org.junit.FixMethodOrder;
import org.junit.Test;
import org.junit.runners.MethodSorters;

import java.io.IOException;
import java.net.InetSocketAddress;

import static org.junit.Assert.assertEquals;

@FixMethodOrder(MethodSorters.NAME_ASCENDING)
public class OMTest {
/*/
    public OMTest(){
        try {
            init();
        } catch (IOException e) {
            e.printStackTrace();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    static public void print(String message){
        System.out.println(message);
    }

    @BeforeClass
    static public void init() throws IOException, InterruptedException {
        BasicConfigurator.configure();
        if (!setUpIsDone) {
            print("init clients");
            clients = new SampleClient[client_num];
            client_address = new InetSocketAddress[client_num];
            for (int i = 0; i < client_num; ++i) {
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
            for (int i = 0; i < router_num; ++i) {
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
            traders = new SampleTrader[trader_num];
            trader_address    = new InetSocketAddress[trader_num];
            int trader_port = router_port + router_num;
            for (int i = 0; i < trader_num; ++i) {
                traders[i] = new SampleTrader("Trader " + i, trader_port + i);
                int index = i;

                new Thread(() -> {
                    try {
                        traders[index].connectToOrderManager();
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }).start();

                trader_address[i] = new InetSocketAddress(address, trader_port + i);
            }

            marketData = new SampleLiveMarketData();

            print("init order manager");
            order_manager = new OrderManager(
                    router_address,
                    client_address,
                    trader_address,
                    marketData);
            print("Init done");
            Util.wait(1000);
            setUpIsDone = true;

            order_manager.spawnClients();
        }
    }

    public boolean runOnce() throws IOException, ClassNotFoundException, InterruptedException {
        boolean work = false;

        // We receive orders from clients
        work = work || order_manager.runOnce();

        // OM ask trader to accept each order or slice them
        for(int i = 0; i < trader_num; ++i) {
            work = work || traders[i].runOnce();
        }

        // Slicing
        work = work || order_manager.runOnce();

        // SampleTrader accepts sliced trade and sends confirmation to client
        for(int i = 0; i < client_num; ++i){
            work = work || clients[i].runOnce();
        }

        // get best price
        for(int i = 0; i < router_num; ++i) {
            work = work || routers[i].runOnce();
        }

        // SampleTrader fill orders
        for(int i = 0; i < trader_num; ++i) {
            work = work || traders[i].runOnce();
        }

        // Client receive Confirmation...
        for(int i = 0; i < client_num; ++i){
            work = work || clients[i].runOnce();
        }
        return work;
    }

    //* Why do we check for non blocking operation ?
    //* We want our test to end and not run forever
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
            clients[i].sendOrder();
        }

        boolean work = runOnce();
        assertEquals(true, work);
    }

    // Actors
    // ------------------------------------------------------------------------

    static OrderManager         order_manager;
    static SampleClient[]       clients;
    static SampleRouter[]       routers;
    static SampleTrader[]       traders;
    static SampleLiveMarketData marketData;

    static InetSocketAddress[] client_address;
    static InetSocketAddress[] router_address;
    static InetSocketAddress[] trader_address;

    // Config
    // ------------------------------------------------------------------------
    final static int client_port = 2000;
    final static int client_num  = 3;
    final static int router_num  = 3;
    final static int trader_num  = 3;
    final static String address  = "localhost";

    private static boolean setUpIsDone = false;//*/
}
