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

    @Override
    public void run() {

    }
}
