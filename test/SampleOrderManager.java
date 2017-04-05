import LiveMarketData.LiveMarketData;
import OrderManager.OrderManager;

import java.net.InetSocketAddress;


public class SampleOrderManager extends OrderManager implements Runnable{

    public SampleOrderManager(InetSocketAddress[] routers,
                       InetSocketAddress[] clients,
                       InetSocketAddress[] traders,
                       LiveMarketData liveMarketData){
        super(routers, clients, traders, liveMarketData);
    }
    @Override
    public void run() {
        super.run();
    }
}
