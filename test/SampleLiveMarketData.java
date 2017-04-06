import LiveMarketData.LiveMarketData;
import OrderManager.Order;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.Random;

//TODO this should really be in its own thread
// Why ? It is not like it is doing anything...
// no event loop
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
    public void connectToOrderManager(int port) throws IOException {
        //OM will connect to us
        omConn = new ServerSocket(port).accept();
    }

    SampleLiveMarketData(){
        super(new InetSocketAddress(2000));
    }

    @Override
    public void run() {

    }
}
