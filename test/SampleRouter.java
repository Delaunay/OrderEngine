import OrderRouter.Router;
import Ref.Instrument;
import Ref.Ric;
import Utility.Connection.ConnectionType;
import Utility.HelperObject;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.InetSocketAddress;

public class SampleRouter extends OrderManagerClient implements Router, Runnable {
    private static final Instrument[] INSTRUMENTS = {
            new Instrument(new Ric("VOD.L")),
            new Instrument(new Ric("BP.L")),
            new Instrument(new Ric("BT.L"))
    };

    private ObjectInputStream  is;
    private ObjectOutputStream os;


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
            is = new ObjectInputStream(order_manager.getInputStream());

            Router.MessageKind methodName = (Router.MessageKind) is.readObject();

            debug("Order Router received method call for: " + methodName);

            // Order Dispatch
            switch (methodName) {
                case REQRouteOrder:
                    routeOrder(is.readInt(),
                            is.readInt(),
                            is.readInt(),
                            (Instrument) is.readObject());
                    return true;

                case REQPriceAtSize:
                    priceAtSize(is.readInt(),
                            is.readInt(),
                            (Instrument) is.readObject(),
                            is.readInt());
                    return true;
            }
        }
        return false;
    }

    @Override
    public void routeOrder(int id, int sliceId, int size, Instrument i) throws IOException {
        int    fillSize  =  getFillSize(i, size);
        double fillPrice = getFillPrice(i, size);

        os = new ObjectOutputStream(order_manager.getOutputStream());
            os.writeObject(MessageKind.ANSNewFill);
            os.writeInt(id);
            os.writeInt(sliceId);
            os.writeInt(fillSize);
            os.writeDouble(fillPrice);
            os.flush();
    }

    @Override
    public void priceAtSize(int id, int sliceId, Instrument i, int size) throws IOException {
        os = new ObjectOutputStream(order_manager.getOutputStream());
            os.writeObject(MessageKind.ANSBestPrice);
            os.writeInt(id);
            os.writeInt(sliceId);
            os.writeDouble(getPriceAtSize(i, size));
            os.flush();
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
