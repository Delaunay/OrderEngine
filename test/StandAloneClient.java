import Utility.Util;

import java.io.IOException;
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
        HashSet<String> opt_name = new HashSet<>();
            opt_name.add("port");
            opt_name.add("type");
            opt_name.add("config");

        HashMap<String, String> options = Util.readArgs(args, opt_name);

        int    client_port = 0;
        String port = options.get("port");

        if (port != null)
            client_port = Integer.parseInt(port);

        String client_type = options.get("type");
        SampleClient client;

        Util.print("Opening " + client_type + ": [port = " + client_port + "]");

        try {
            // could make a client factory for that
            if (client_type == null || client_type.equals("Sample"))
                client = new SampleClient(client_port);
            else if (client_type.equals("Regular"))
                client = new RegularClient(client_port);
            else if (client_type.equals("Portfolio"))
                client = new PortfolioClient(client_port);
            else if (client_type.equals("Basket"))
                client = new BasketClient(client_port);
            else // to silence an error
                client = new SampleClient(client_port);

            client.messageHandler();
        } catch (IOException e){
            Util.print("Could not initialize client");
        }
    }


}
