package Utility;

import java.io.FileInputStream;
import java.io.IOException;
import java.util.Properties;

public class Configuration {

    public Configuration(){ init(); }
    public Configuration(String config_name){ init(config_name); }

    void init() {   init("config.properties"); }

    void init(String config_name){
        // set default properties
        prop.setProperty("port", "2000");
        prop.setProperty("port-client", "2000");
        prop.setProperty("port-trader", "2100");
        prop.setProperty("port-router", "2200");
        prop.setProperty("num-client", "2");
        prop.setProperty("num-trader", "2");
        prop.setProperty("num-router", "2");

        try {
            FileInputStream config = new FileInputStream(config_name);
            prop.load(config);
        } catch (IOException e) {
            System.out.println("Configuration file not found");
            e.printStackTrace();
        }
    }

    public int getInt(String pro){
        String port = prop.getProperty(pro);
        if (port == null)
            return -1;
        return Integer.parseInt(port);
    }

    public int getPort(){
        return getInt("port");
    }

    public int getPort(String name){
        String port = prop.getProperty("port-" + name);
        if (port == null)
            return getPort();
        return Integer.parseInt(port);
    }

    public int getClientNumber(){   return getNum("client");}
    public int getRouterNumber(){   return getNum("router");}
    public int getTraderNumber(){   return getNum("trader");}


    public int getNum(String name){
        String port = prop.getProperty("num-" + name);
        if (port == null)
            return getPort();
        return Integer.parseInt(port);
    }
    private Properties prop = new Properties();
}
