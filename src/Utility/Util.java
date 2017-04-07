package Utility;

import java.util.HashMap;
import java.util.HashSet;
import java.util.concurrent.TimeUnit;

public class Util {
    static public String COLOR_NC            = (char)27 + "[0m'; ";
    static public String COLOR_WHITE         = (char)27 + "[1;37m";
    static public String COLOR_BLACK         = (char)27 + "[0;30m";
    static public String COLOR_BLUE          = (char)27 + "[0;34m";
    static public String COLOR_LIGHT_BLUE    = (char)27 + "[1;34m";
    static public String COLOR_GREEN         = (char)27 + "[0;32m";
    static public String COLOR_LIGHT_GREEN   = (char)27 + "[1;32m";
    static public String COLOR_CYAN          = (char)27 + "[0;36m";
    static public String COLOR_LIGHT_CYAN    = (char)27 + "[1;36m";
    static public String COLOR_RED           = (char)27 + "[0;31m";
    static public String COLOR_LIGHT_RED     = (char)27 + "[1;31m";
    static public String COLOR_PURPLE        = (char)27 + "[0;35m";
    static public String COLOR_LIGHT_PURPLE  = (char)27 + "[1;35m";
    static public String COLOR_BROWN         = (char)27 + "[0;33m";
    static public String COLOR_YELLOW        = (char)27 + "[1;33m";
    static public String COLOR_GRAY          = (char)27 + "[0;30m";
    static public String COLOR_LIGHT_GRAY    = (char)27 + "[0;37m";

    public static abstract class ScheduledTask{
        private long start = System.currentTimeMillis();
        private long end;
        protected long delta;

        public ScheduledTask(long delta_){
            delta = delta_;
        }

        public void run(){
            end = System.currentTimeMillis();
            if (end - start > delta){
                start = end;
                scheduledJob();
            }
        }

        abstract public void scheduledJob();
    }

    public static HashMap<String, String> readArgs(String[] args, HashSet<String> opt_name){

        HashMap<String, String> options = new HashMap<>();

        for(int i = 0; i < args.length; ++i){
            switch (args[i].charAt(i)){
                case '-':
                    String opt = args[i].substring(1);
                    if (opt_name.contains(opt)){
                        options.put(opt, args[i + 1]);
                        i += 1;
                        continue;
                    } else {
                        print("Argument \"" + opt + "\" not recognized");
                        continue;
                    }
                default:
                    print("Value \"" + args[i] + "\" not recognized");
            }
        }

        return options;
    }

    static public void print(String message){
        System.out.println(message);
    }

    // We dont really care if waiting fails
    static public void wait(int i){
        try {
            TimeUnit.MILLISECONDS.sleep(i);
        } catch (InterruptedException e) {}
    }


}
