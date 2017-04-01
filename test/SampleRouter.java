import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.Socket;
import java.util.Random;
import java.util.concurrent.TimeUnit;

import javax.net.ServerSocketFactory;

import OrderManager.Order;
import OrderRouter.Router;
import Ref.Instrument;
import Ref.Ric;

public class SampleRouter extends Thread implements Router {
    private static final Random RANDOM_NUM_GENERATOR = new Random();

    private static final Instrument[] INSTRUMENTS = {
            new Instrument(new Ric("VOD.L")),
            new Instrument(new Ric("BP.L")),
            new Instrument(new Ric("BT.L"))
    };

    private Socket omConn;
    private int    port;

    private ObjectInputStream  is;
    private ObjectOutputStream os;


    public SampleRouter(String name, int port) {
        this.setName(name);
        this.port = port;
    }

    @Override
    public void run() {
        try {
            omConn = ServerSocketFactory.getDefault().createServerSocket(port).accept();
            while (true) {
                runOnce();

                TimeUnit.MILLISECONDS.sleep(10);
            }
        } catch (ClassNotFoundException e) {
            print("Unknown message format, Could not read objectStream");

        } catch (IOException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        } catch (InterruptedException e) {
            // We dont really care about InterruptException
        }
    }

    private void runOnce() throws IOException, ClassNotFoundException, InterruptedException {
        if (0 < omConn.getInputStream().available()) {
            is = new ObjectInputStream(omConn.getInputStream());

            Router.api methodName = (Router.api) is.readObject();

            print("Order Router received method call for: " + methodName);

            // Order Dispatch
            switch (methodName) {
                case routeOrder:
                    routeOrder(is.readInt(),
                            is.readInt(),
                            is.readInt(),
                            (Instrument) is.readObject());
                    break;

                case priceAtSize:
                    priceAtSize(is.readInt(),
                            is.readInt(),
                            (Instrument) is.readObject(),
                            is.readInt());
                    break;
            }
        }
    }

    @Override
    public void routeOrder(int id, int sliceId, int size, Instrument i) throws IOException, InterruptedException { //MockI.show(""+order);
        //TODO have this similar to the market price of the instrument

        int    fillSize  =  getFillSize(i, size);
        double fillPrice = getFillPrice(i, size);

        Thread.sleep(42);

        os = new ObjectOutputStream(omConn.getOutputStream());
            os.writeObject("newFill");
            os.writeInt(id);
            os.writeInt(sliceId);
            os.writeInt(fillSize);
            os.writeDouble(fillPrice);
            os.flush();
    }

    @Override
    public void priceAtSize(int id, int sliceId, Instrument i, int size) throws IOException {
        os = new ObjectOutputStream(omConn.getOutputStream());
            os.writeObject("bestPrice");
            os.writeInt(id);
            os.writeInt(sliceId);
            os.writeDouble(getPriceAtSize(i, size));
            os.flush();
    }

    // TODO
    @Override
    public void sendCancel(int id, int sliceId, int size, Instrument i) {
        //MockI.show(""+order);
    }

    // Utilities
    // ------------------------------------------------------------------------
    private double getPriceAtSize(Instrument i, int size) {
        return 199 * RANDOM_NUM_GENERATOR.nextDouble();
    }

    private double getFillPrice(Instrument i, int size) {
        return 199 * RANDOM_NUM_GENERATOR.nextDouble();
    }

    private int getFillSize(Instrument i, int size) {
        return RANDOM_NUM_GENERATOR.nextInt(size);
    }

    void print(String msg) {
        System.out.println("R : " + msg);
    }
}
