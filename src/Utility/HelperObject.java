package Utility;


import org.apache.log4j.Level;
import org.apache.log4j.LogManager;
import org.apache.log4j.Logger;

import java.util.concurrent.TimeUnit;

public class HelperObject {
    public static Level logLevel = Level.INFO;
    public static int   waitTime = 1;
    public static int   socket_buffer = 1024 * 1024;


    public void info(String m) {    classLog.info (m);  }
    public void error(String m){    classLog.error(m);  }
    public void warn(String m) {    classLog.warn (m);  }
    public void debug(String m){    classLog.debug(m);  }

    protected void initLog(String class_name){
        classLog = LogManager.getLogger(class_name);
        classLog.setLevel(logLevel);
    }

    private Logger classLog;

    static public void sleep(long time){
        try {
            TimeUnit.MILLISECONDS.sleep(time);
        } catch (InterruptedException e){
            ignore();
        }
    }

    public static void ignore(){}
}
