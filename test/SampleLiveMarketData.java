import Actor.Message;
import LiveMarketData.LiveMarketData;
import OrderManager.Order;
import Utility.Connection;

import java.io.IOException;
import java.net.InetSocketAddress;

/**
 *<br>- LiveMarketData listens to the OrderManager
 *<br>- Set the current price of an order
 */
public class SampleLiveMarketData extends OrderManagerClient implements LiveMarketData, Runnable {

    public void setPrice(Order o) {
        o.initialMarketPrice = MockConfig.getFillPrice(o.instrument, 0);
    }

    public void setPrice(Message.SetPrice m) {
        setPrice(m.order);
    }

    public void connectToOrderManager(InetSocketAddress address) throws IOException {
        connectToOrderManager(Connection.ConnectionType.LiveMarketDataConnection, address);
    }

    SampleLiveMarketData(InetSocketAddress om_address){
        super(om_address);
        initLog(this.getClass().getName());
    }

    @Override
    public void run() {}

    /*
    public
    boolean readMessage() throws IOException, ClassNotFoundException{
        while(isAvailable()) {

            Message message = readMessage(order_manager);

            debug("called " + message.op);

            switch (message.op) {
                case REQPrice:
                    setPrice((Message.SetPrice) message);
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
    }*/
}
