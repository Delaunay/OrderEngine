package OrderManager;

import Ref.Instrument;

import java.io.Serializable;

public class Order implements Serializable {

    //TODO these should all be longs
    public int      id;
    public int      client_order_id;
    public int      size;
    public int      clientid;
    public char     OrdStatus = 'A';    //OrdStatus is Fix 39, 'A' is 'Pending New'
    public boolean  buy;

    public Instrument instrument;
    public double     initialMarketPrice;

    public Order(int clientId, int order_id, Instrument instrument, int size, int client_order_id) {
        this.id = order_id;
        this.client_order_id = client_order_id;

        if (size < 0){
            buy = false;
            this.size = - size;
        } else {
            this.size = size;
            buy = true;
        }
        this.clientid = clientId;
        this.instrument = instrument;
    }
}

class Basket {
    Order[] orders;
}

