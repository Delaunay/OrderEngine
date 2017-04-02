import java.util.HashMap;
import java.util.HashSet;

public class Util {

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
                    continue;
            }
        }

        return options;
    }

    static public void print(String message){
        System.out.println(message);
    }
}
