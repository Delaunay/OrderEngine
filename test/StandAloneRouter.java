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
     *          -name      Specify the name of the router
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

        int    router_port = 0;
        String port = options.get("port");

        if (port != null)
            router_port = Integer.parseInt(port);
        else
            router_port = conf.getPort("router");

        String name = options.get("name");

        if (name == null)
            name = "Router Default";

        SampleTrader trader;

        Util.print("Opening router : " + name + " on [port = " + router_port + "]");

        trader = new SampleTrader(name, router_port);

        trader.run();
    }
}
