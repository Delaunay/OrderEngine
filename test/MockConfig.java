import Ref.Instrument;
import Ref.Ric;

import java.util.Random;

/**
 *  Simulation configuration
 *
 *
 */
public class MockConfig {
    private static final Random RANDOM_NUM_GENERATOR = new Random();
    public static final Instrument[] INSTRUMENTS = {
            new Instrument(new Ric("VOD.L")),
            new Instrument(new Ric("BP.L")),
            new Instrument(new Ric("BT.L"))
    };

    public static int allow_randomness = 0;
    public static int slice_size = 500;
    public static int fill_size = 500;

    public static double getPriceAtSize(Instrument i, int size) {
        return getFillPrice(i, size);
    }

    public static double getFillPrice(Instrument i, int size) {
        double eps = RANDOM_NUM_GENERATOR.nextGaussian() * allow_randomness;

        switch (i.getRic().ric) {
            case "VOD.L":
                return 230 + (20 * eps) ;
            case "BT.L":
                return 330 + (30 * eps);
            default:
                return 460 + (60 * eps);
        }
    }

    public static int getFillSize(Instrument i, int size) {
        return fill_size * (1 - allow_randomness) + RANDOM_NUM_GENERATOR.nextInt(size) * allow_randomness;
    }


    static int getClientOrderSize(){
        if (client_count++ % 2 == 0)
            return -500;
        return 500;
        //return allow_randomness * (RANDOM_NUM_GENERATOR.nextInt(1000) - 500);
    }

    static int getAssetID(){
        return 0 + RANDOM_NUM_GENERATOR.nextInt(3) * allow_randomness;   // instrument id
    }

    static Instrument getInstrument(int id){
        return INSTRUMENTS[id];
    }


    static int client_count = 0;
}
