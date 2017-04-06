import OrderManager.OrderManager;

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
        //LiveMarketData liveMarketData = new SampleLiveMarketData();
        OrderManager order_manager = new OrderManager(2000);
        order_manager.run();
    }
}
