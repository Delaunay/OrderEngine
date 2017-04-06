import java.net.InetSocketAddress;

public class StandAloneLiveMarketData {

    /**
     *  Build a live market
     */
    public static void main(String[] args) {
        SampleLiveMarketData router =  new SampleLiveMarketData(
                new InetSocketAddress("localhost", 2000));
        router.run();
    }
}
