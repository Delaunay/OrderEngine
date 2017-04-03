import LiveMarketData.LiveMarketData;
import Utility.Configuration;
import Utility.Util;

import java.net.InetSocketAddress;
import java.util.HashMap;
import java.util.HashSet;

/**
 * Created by user on 4/3/2017.
 */
public class StandAloneOM {

    /**
     *  Build a OrderManager
     *      Argument supported:
     *          -routers            Specify the routers' hostname and port number
     *          -clients            Specify the clients' hostname and port number
     *          -traders            Specify the traders' hostname and port number
     *          -liveData           Specify the live Market Data
     *          -config             Specify the configuration file to load
     */

    public static void main(String[] args){
        HashSet<String> opt_name = new HashSet<>();
        opt_name.add("routers");
        opt_name.add("clients");
        opt_name.add("traders");
        opt_name.add("liveData");
        opt_name.add("config");

        HashMap<String, String> options = Util.readArgs(args, opt_name);

        InetSocketAddress[] routers;
        InetSocketAddress[] clients;
        InetSocketAddress[] traders;
        LiveMarketData liveData;

        // Load conf file if any
        Configuration conf;
        String conf_file = options.get("config");

        if(conf_file != null)
            conf = new Configuration(conf_file);
        else
            conf = new Configuration();


        //OrderManager om;
        //om = new OrderManager(routers, clients, traders, liveData);
    }
}
