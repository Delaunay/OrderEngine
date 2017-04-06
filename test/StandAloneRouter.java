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
        SampleRouter router =  new SampleRouter(
                new InetSocketAddress("localhost", 2000));
        router.run();
    }
}
