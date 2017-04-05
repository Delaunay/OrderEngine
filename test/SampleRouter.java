import OrderRouter.Router;
import Ref.Instrument;
import Ref.Ric;
import Utility.HelperObject;
import org.apache.log4j.Level;
import org.apache.log4j.LogManager;
import org.apache.log4j.Logger;

import javax.net.ServerSocketFactory;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.Socket;

public class SampleRouter extends Thread implements Router {
    private static final Instrument[] INSTRUMENTS = {
            new Instrument(new Ric("VOD.L")),
            new Instrument(new Ric("BP.L")),
            new Instrument(new Ric("BT.L"))
    };

    public boolean sleep = true;
    private Socket omConn;
    private int    port;

    private ObjectInputStream  is;
    private ObjectOutputStream os;

    private Logger log;


    public SampleRouter(String name, int port) {
        this.setName(name);
        this.port = port;
        initLog();
    }

    public void initLog(){
        log = LogManager.getLogger("Debug");
        log.setLevel(Level.WARN);
    }


    public void connectToOrderManager() throws IOException {
        omConn = ServerSocketFactory.getDefault().createServerSocket(port).accept();
        omConn.setSendBufferSize(HelperObject.socket_buffer);
        omConn.setReceiveBufferSize(HelperObject.socket_buffer);
        log.info("Connected to OM" + port);
    }

    @Override
    public void run() {
        try {
            if (omConn == null)
                connectToOrderManager();

            while (true) {
                runOnce();

                HelperObject.sleep(HelperObject.waitTime);
            }
        } catch (ClassNotFoundException e) {
            log.error("Unknown message format, Could not read objectStream");

        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public boolean runOnce() throws IOException, ClassNotFoundException {
        while (omConn.getInputStream().available() > 0) {
            is = new ObjectInputStream(omConn.getInputStream());

            Router.MessageKind methodName = (Router.MessageKind) is.readObject();

            log.debug("Order Router received method call for: " + methodName);

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

        os = new ObjectOutputStream(omConn.getOutputStream());
            os.writeObject(MessageKind.ANSNewFill);
            os.writeInt(id);
            os.writeInt(sliceId);
            os.writeInt(fillSize);
            os.writeDouble(fillPrice);
            os.flush();
    }

    @Override
    public void priceAtSize(int id, int sliceId, Instrument i, int size) throws IOException {
        os = new ObjectOutputStream(omConn.getOutputStream());
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
