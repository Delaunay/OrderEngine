package OrderManager;


public class PendingOrder {
    Order order;
    int slice_num;
    int size_remain;
    double cost;

    PendingOrder(Order o){
        slice_num = 0;
        size_remain = o.size;
        order = o;
    }
}
