import Utility.Configuration;
import Utility.Util;

import java.util.HashMap;
import java.util.HashSet;

/**
 * Created by user on 4/3/2017.
 */
public class StandAloneRouter {
    /**
     *  Build a Router
     *      Argument supported:
     *          -port      Specify the port number the OrderManager is going to communicate with us
     *          -name      Specify the name of the trader
     *          -config    Specify the configuration file to load
     */

    public static void main(String[] args) {
        HashSet<String> opt_name = new HashSet<>();
        opt_name.add("port");
        opt_name.add("name");
        opt_name.add("config");

        HashMap<String, String> options = Util.readArgs(args, opt_name);

        // Load conf file if any
        Configuration conf;
        String conf_file = options.get("config");

        if (conf_file != null)
            conf = new Configuration(conf_file);
        else
            conf = new Configuration();

        int    trader_port = 0;
        String port = options.get("port");

        if (port != null)
            trader_port = Integer.parseInt(port);
        else
            trader_port = conf.getPort("trader");

        String name = options.get("name");

        if (name == null)
            name = "James";

        Trader trader;

        Util.print("Opening trader : " + name + " on [port = " + trader_port + "]");

        trader = new Trader(name, trader_port);

        trader.run();
    }
}
