import Utility.Configuration;
import Utility.Util;

import java.util.HashMap;
import java.util.HashSet;

/**
 * Created by user on 4/3/2017.
 */
public class StandAloneLMD {

    /**
     *  Build a Client
     *      Argument supported:
     *          -port      Specify the port number the OrderManager is going to communicate with us
     *          -database  Specify the database used to get info on the current value and availability of stocks on the Market
     *          -config    Specify the configuration file to load
     */

    public static void main(String[] args){
        HashSet<String> opt_name = new HashSet<>();
        opt_name.add("port");
        opt_name.add("database");
        opt_name.add("config");

        HashMap<String, String> options = Util.readArgs(args, opt_name);

        // Load conf file if any
        Configuration conf;
        String conf_file = options.get("config");

        if (conf_file != null)
            conf = new Configuration(conf_file);
        else
            conf = new Configuration();

        // Get client port
        int lmd_port = 0;
        String port = options.get("port");

        if (port != null)
            lmd_port = Integer.parseInt(port);
        else
            lmd_port = conf.getPort("liveMarketData");

        //
        String lmd_database = options.get("database");

        /*if (lmd_database == null)
            lmd_database = conf.getDatabase;*/

        SampleLiveMarketData liveMarketData = new SampleLiveMarketData();

        Util.print("Opening Live Market Database " + lmd_database + ": [port = " + lmd_port + "]");

        liveMarketData.run();
    }
}
