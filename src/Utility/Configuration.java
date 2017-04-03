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

        try {
            FileInputStream config = new FileInputStream(config_name);
            prop.load(config);
        } catch (IOException e) {
            System.out.println("Configuration file not found");
            e.printStackTrace();
        }
    }

    public int getPort(){
        String port = prop.getProperty("port");
        if (port == null)
            return -1;
        return Integer.parseInt(port);
    }

    private Properties prop = new Properties();
}
