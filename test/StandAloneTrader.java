import java.net.InetSocketAddress;

public class StandAloneTrader {

    /**
     *  Build a SampleTrader
     *      Argument supported:
     *          -port      Specify the port number the OrderManager is going to communicate with us
     *          -name      Specify the name of the trader
     *          -config    Specify the configuration file to load
     */

    public static void main(String[] args) {
        SampleTrader trader = new SampleTrader(
                new InetSocketAddress("localhost", 2000));

        trader.run();
    }
}
