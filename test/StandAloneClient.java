import Utility.Configuration;
import org.apache.log4j.BasicConfigurator;

import java.net.InetSocketAddress;

public class StandAloneClient {

    /**
     *  Build a Client
     *      Argument supported:
     *          -port      Specify the port number the OrderManager is going to communicate with us
     *          -type      Specify the type of client we want to instantiate (Sample/Regular/Portfolio)
     *          -config    Specify the configuration file to load
     */
    public static void main(String[] args){
        BasicConfigurator.configure();
        /*HashSet<String> options = new HashSet<>();
            options.add("port");
            options.add("name");
            options.add("config");

        HashMap opt = Util.readArgs(args, options); */
        Configuration conf = new Configuration();

        String om_hostname = "10.83.16.19";// conf.getHostname();
        int    om_port     = conf.getOrderManagerPort();


        SampleClient client = new SampleClient(
                10,10,
                new InetSocketAddress(om_hostname, om_port));

        client.run();
    }
}
