import Utility.Configuration;
import Utility.Util;

import java.net.InetSocketAddress;
import java.util.HashMap;
import java.util.HashSet;

public class StandAloneTrader {

    /**
     *  Build a SampleTrader
     *      Argument supported:
     *          -port      Specify the port number the OrderManager is going to communicate with us
     *          -name      Specify the name of the trader
     *          -config    Specify the configuration file to load
     */

    public static void main(String[] args) {
        SampleTrader trader = new SampleTrader("Trader",
                new InetSocketAddress("localhost", 2000));

        trader.run();
    }
}
