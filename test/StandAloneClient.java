import Utility.Configuration;
import Utility.Util;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.HashMap;
import java.util.HashSet;

public class StandAloneClient {

    /**
     *  Build a Client
     *      Argument supported:
     *          -port      Specify the port number the OrderManager is going to communicate with us
     *          -type      Specify the type of client we want to instantiate (Sample/Regular/Portfolio)
     *          -config    Specify the configuration file to load
     */
    public static void main(String[] args){

        SampleClient client = new SampleClient(
                "Client",
                10,
                10,
                new InetSocketAddress("localhost", 200));

        client.run();
    }
}
