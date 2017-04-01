import java.util.Random;

import LiveMarketData.LiveMarketData;
import OrderManager.Order;
import Ref.Instrument;

//TODO this should really be in its own thread
// Why ? It is not like it is doing anything...
// no event loop
public class SampleLiveMarketData extends Thread implements LiveMarketData {
    private static final Random RANDOM_NUM_GENERATOR = new Random();

    public void setPrice(Order o) {
        o.initialMarketPrice = 199 * RANDOM_NUM_GENERATOR.nextDouble();
    }

    @Override
    public void run() {

    }
}
