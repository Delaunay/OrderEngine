import Utility.Configuration;
import org.apache.log4j.BasicConfigurator;

import java.net.InetSocketAddress;

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
        BasicConfigurator.configure();
        /*HashSet<String> options = new HashSet<>();
            options.add("port");
            options.add("name");
            options.add("config");

        HashMap opt = Util.readArgs(args, options); */
        Configuration conf = new Configuration();

        String om_hostname = conf.getHostname();
        int    om_port     = conf.getOrderManagerPort();

        SampleRouter router =  new SampleRouter(
                new InetSocketAddress(om_hostname, om_port));
        router.run();
    }
}
