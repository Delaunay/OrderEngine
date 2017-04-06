import LiveMarketData.LiveMarketData;
import OrderManager.OrderManager;

import java.net.InetSocketAddress;


public class SampleOrderManager extends OrderManager implements Runnable{

    public SampleOrderManager(int port, LiveMarketData liveMarketData, InetSocketAddress[] traders){
        super(port, liveMarketData, traders);
    }

    public SampleOrderManager(int port, LiveMarketData liveMarketData,
                              InetSocketAddress[] traders,
                              InetSocketAddress[] routers){
        super(port, liveMarketData, traders, routers);
    }

    public SampleOrderManager(int port, LiveMarketData liveMarketData){
        super(port, liveMarketData);
    }

    @Override
    public void run() {
        super.run();
    }
}
