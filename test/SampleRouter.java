import Actor.Message;
import OrderRouter.Router;
import Ref.Instrument;
import Utility.Connection.ConnectionType;
import Utility.HelperObject;

import java.io.IOException;
import java.net.InetSocketAddress;

/**
 * 		- Router listens to the OrderManager
 *      - If called using the bestPrice method, returns the price for the order as given in MockConfig
 *      - If called using the newFill method, informs the OrderManager of the price of a new fill for the order
 */
public class SampleRouter extends OrderManagerClient implements Router, Runnable {
    public SampleRouter(InetSocketAddress om_address) {
        super(om_address);
        initLog(this.getClass().getName());
    }

    public void connectToOrderManager(InetSocketAddress address) throws IOException{
        connectToOrderManager(ConnectionType.RouterConnection, address);
    }

    @Override
    public void run() {
        try {
            if (order_manager == null)
                connectToOrderManager(order_manager_address);

            while (true) {
                runOnce();

                HelperObject.sleep(HelperObject.waitTime);
            }
        } catch (ClassNotFoundException e) {
            error("Unknown message format, Could not read objectStream");

        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public boolean runOnce() throws IOException, ClassNotFoundException {
        while (order_manager.getInputStream().available() > 0) {
            Message m = readMessage(order_manager);

            debug("Order Router received method call for: " + m.op);

            // Order Dispatch
            switch (m.op) {
                case REQRouteOrder:
                    routeOrder((Message.RouteOrder) m);
                    break;

                case REQPriceAtSize:
                    priceAtSize((Message.PriceAtSize) m);
                    break;

                default:
                    error("unsupported operation");
                    break;
            }
        }
        return false;
    }

    @Override
    public void routeOrder(Message.RouteOrder m) throws IOException {
        int id = m.order_id;
        int sliceId = m.slice_id;
        int size = m.size;
        Instrument i = m.asset;

        int    fillSize  =  getFillSize(i, size);
        double fillPrice = getFillPrice(i, size);

        sendMessage(order_manager,
                new Message.NewFill(id, sliceId, 0, fillPrice, fillSize));
    }

    @Override
    public void priceAtSize(Message.PriceAtSize m) throws IOException {
        int id = m.order_id;
        int sliceId = m.slice_id;
        int size = m.size;
        Instrument i = m.asset;

        sendMessage(order_manager, new Message.BestPrice(id, sliceId, 0, getPriceAtSize(i, size)));
    }

    // TODO
    @Override
    public void sendCancel(int id, int sliceId, int size, Instrument i) {
    }

    // Utilities
    // ------------------------------------------------------------------------
    private double getPriceAtSize(Instrument i, int size) {
        return MockConfig.getPriceAtSize(i, size);
    }

    private double getFillPrice(Instrument i, int size) {
        return MockConfig.getFillPrice(i, size);
    }

    private int getFillSize(Instrument i, int size) {
        return MockConfig.getFillSize(i, size);
    }
}
