package Utility;

import java.util.HashMap;
import java.util.HashSet;
import java.util.concurrent.TimeUnit;

public class Util {

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
