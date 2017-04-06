import Utility.Configuration;
import Utility.Util;
import org.apache.log4j.BasicConfigurator;

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
        BasicConfigurator.configure();
        /*HashSet<String> options = new HashSet<>();
            options.add("port");
            options.add("name");
            options.add("config");

        HashMap opt = Util.readArgs(args, options); */
        Configuration conf = new Configuration();

        String om_hostname = conf.getHostname();
        int    om_port     = conf.getOrderManagerPort();

        SampleTrader trader = new SampleTrader(
                new InetSocketAddress(om_hostname, om_port));

        trader.run();
    }
}
