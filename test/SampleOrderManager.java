import LiveMarketData.LiveMarketData;
import OrderManager.OrderManager;


public class SampleOrderManager extends OrderManager implements Runnable{

    public SampleOrderManager(int port, LiveMarketData liveMarketData){
        super(port, liveMarketData);
    }
    @Override
    public void run() {
        super.run();
    }
}
