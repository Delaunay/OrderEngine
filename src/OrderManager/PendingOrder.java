package OrderManager;


import java.io.Serializable;
import java.util.ArrayList;

public class PendingOrder {
    Order order;
    int slice_num;
    int size_remain;
    double cost;

    ArrayList<Order> slices;
    ArrayList<Fill>  fills;

    PendingOrder(Order o){
        slice_num = 0;
        size_remain = o.size;
        order = o;
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

