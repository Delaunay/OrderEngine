import OrderManager.OrderManager;
import Utility.Configuration;
import org.apache.log4j.BasicConfigurator;

public class StandAloneOM {

    /**
     *  Build a OrderManager
     *<br>      Argument supported:
     *<br>          -routers            Specify the routers' hostname and port number
     *<br>          -clients            Specify the clients' hostname and port number
     *<br>          -traders            Specify the traders' hostname and port number
     *<br>          -liveData           Specify the live Market Data
     *<br>          -config             Specify the configuration file to load
     */

    public static void main(String[] args){
        BasicConfigurator.configure();
        /*HashSet<String> options = new HashSet<>();
            options.add("port");
            options.add("name");
            options.add("config");

        HashMap opt = Util.readArgs(args, options); */
        Configuration conf = new Configuration();

        String om_hostname = conf.getHostname();
        int    om_port     = conf.getOrderManagerPort();


        OrderManager order_manager = new OrderManager(om_port);
            order_manager.run();
    }
}
