package OrderManager;


import java.io.Serializable;
import java.util.ArrayList;

public class PendingOrder {
    Order            order;
    int              slice_num;
    int              size_remain;
    double           cost;
    public boolean   buy;
    ArrayList<Slice> slices = new ArrayList<Slice>();
    ArrayList<Fill>  fills  = new ArrayList<Fill>();

    PendingOrder(Order o){
        slice_num = 0;
        size_remain = o.size;
        order = o;
        buy = o.buy;
    }
}

class Slice implements Serializable{
    public PendingOrder parent;
    public int          size;
    public double       price;
    public double[]     best_prices;
    public int          best_price_count;
    public boolean      buy;

    public Slice(PendingOrder p, int s, double px){
        parent = p;
        price = px;
        size = s;
        buy = p.buy;
    }
}

class Fill implements Serializable {
    //long id;
    int size;
    double price;

    Fill(int size, double price) {
        this.size = size;
        this.price = price;
    }
}

