import Actor.Message;
import LiveMarketData.LiveMarketData;
import OrderManager.Order;
import Utility.Connection;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.util.Random;

/**
 *<br>- LiveMarketData listens to the OrderManager
 *<br>- Set the current price of an order
 */
public class SampleLiveMarketData extends OrderManagerClient implements LiveMarketData, Runnable {
    private static final Random RANDOM_NUM_GENERATOR = new Random();
    private Socket omConn;

    public void setPrice(Order o) {
        if(o.instrument.getRic().ric == "VOD.L") {
            o.initialMarketPrice = 230+ (20 * RANDOM_NUM_GENERATOR.nextGaussian());
        }
        else if(o.instrument.getRic().ric == "BT.L"){
            o.initialMarketPrice = 330 + (30 * RANDOM_NUM_GENERATOR.nextGaussian());
        }
        else{
            o.initialMarketPrice = 460 + (60 * RANDOM_NUM_GENERATOR.nextGaussian());
        }
    }
    public void connectToOrderManager(InetSocketAddress address) throws IOException {
        connectToOrderManager(Connection.ConnectionType.LiveMarketDataConnection, address);
    }

    /*SampleLiveMarketData(){
        super(new InetSocketAddress(2000));
    }*/

    SampleLiveMarketData(InetSocketAddress om_address){
        super(om_address);
        initLog(this.getClass().getName());
    }

    public
    boolean readMessage() throws IOException, ClassNotFoundException{
        while(isAvailable()) {

            Message message = readMessage(order_manager);

            debug("called " + message.op);

            switch (message.op) {
                case REQPrice:
                    setPrice(message.o);
                    return true;
            }
        }
        return false;
    }

    @Override
    public void run() {
        try {

            connectToOrderManager(order_manager_address);

            readMessage();

        } catch (IOException | ClassNotFoundException e){
            e.printStackTrace();
        }
    }
}
