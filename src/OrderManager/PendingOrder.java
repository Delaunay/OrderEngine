package OrderManager;


public class PendingOrder {
    Order order;
    int slice_num;
    int size_remain;
    double cost;

    PendingOrder(int slice_num_, int size_remain_, Order o){
        slice_num = slice_num_;
        size_remain = size_remain_;
        order = o;
    }

    PendingOrder(Order o){
        slice_num = 0;
        size_remain = o.size;
        order = o;
    }
}
