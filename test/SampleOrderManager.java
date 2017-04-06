import LiveMarketData.LiveMarketData;
import OrderManager.OrderManager;

import java.net.InetSocketAddress;


public class SampleOrderManager extends OrderManager implements Runnable{

    public SampleOrderManager(int port, LiveMarketData liveMarketData){
        super(port, liveMarketData);
    }
    @Override
    public void run() {
        super.run();
    }
}
