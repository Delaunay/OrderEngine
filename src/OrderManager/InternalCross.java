package OrderManager;

import Ref.Instrument;
import TradeScreen.TradeScreen;

import java.io.IOException;
import java.util.*;

public class InternalCross {
    public enum MessageKind {
        // OrderManager -> InternalCross
        REQCrossOrder,

        // InternalCross -> OrderManager
        ANSFilledOrder
    }

    class Slice{
        int    size;
        double price;
        Order  parent;

        Slice(int size_, double price_, Order parent_){
            size = size_;
            price = price_;
            parent = parent_;
        }
    }

    void sliceOrder(Order order, int slice_size){
        // Get the slices of the same instrument
        ArrayList<Slice> slices = pending_slices.get(order.instrument);

        if (slices == null){
            slices = new ArrayList<Slice>();
            pending_slices.put(order.instrument, slices);
        }

        // create X slices of slice_size from Order order
        int order_size = order.size;
        while (order_size > 0){
            if (order_size > slice_size) {
                slices.add(new Slice(slice_size, order.initialMarketPrice, order));
                order_size -= slice_size;
            } else {
                slices.add(new Slice(order_size, order.initialMarketPrice, order));
            }
        }
    }

    void internalCross(Instrument asset) throws IOException {
        List<Slice> slices = pending_slices.get(asset);

       for(int i = 0; i < slices.size(); ++i){
           Slice a = slices.get(i);
           for(int k = i; k < slices.size(); ++k){
               Slice b = slices.get(k);

               // Consume both slices Does arrayList gets invalidated ?
               if (Math.abs(a.price - b.price) <= eps){
                   slices.remove(i);
                   slices.remove(k);

                   if (a.size == b.size){
                        // Fully Filled
                       sendSliceFill(b.parent, b.size);
                       sendSliceFill(a.parent, b.size);
                   } else if (a.size > b.size){
                       // Partial Fill
                       sendSliceFill(b.parent, b.size);
                       sendSliceFill(a.parent, b.size);
                       slices.add(new Slice(a.size - b.size, a.price, a.parent));
                   } else {
                       sendSliceFill(a.parent, a.size);
                       sendSliceFill(b.parent, a.size);
                       slices.add(new Slice(b.size - a.size, b.price, b.parent));
                   }
               }
           }
           // We have not found any matching orders
           routeSlice(a.parent, a.size);
       }
    }

    // Incoming messages
    void processOrder(Order o){

    }

    // Outgoing messages
    // The order was filled internally
    void sendSliceFill(Order order, int size){

    }

    // send an order to the market
    void routeSlice(Order order, int size){

    }

    Hashtable<Instrument, ArrayList<Slice>> pending_slices;
    double                                  eps = 1e-6;
}
