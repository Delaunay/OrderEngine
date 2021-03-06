package Ref;

import java.io.Serializable;
import java.util.Date;

public class Instrument implements Serializable {
    long id;
    String name;
    Ric ric;
    String isin;
    String sedol;
    String bbid;

    public Instrument(Ric ric) {
        this.ric = ric;
    }

    public String toString() {
        return ric.ric;
    }

    public Ric getRic() {
        return ric;
    }

    public boolean equals(Instrument i){
        return ric.ric.equals(i.ric.ric);
    }

    public int hashCode(){
        return ric.ric.hashCode();
    }
}

class EqInstrument extends Instrument {
    Date exDividend;

    public EqInstrument(Ric ric) {
        super(ric);
    }
}

class FutInstrument extends Instrument {
    Date expiry;
    Instrument underlier;

    public FutInstrument(Ric ric) {
        super(ric);
    }
}
/*TODO
Index
bond
methods
*/