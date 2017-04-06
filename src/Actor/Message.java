package Actor;

import OrderManager.Order;
import Ref.Instrument;

import java.io.Serializable;

/**
 *  Message is the class specifying the data send
 *  between each actors.
 *<br>
 *<br>  We set some base rules for a message:
 *<br>      1. All Messages show extends and only extend `Message`
 *<br>          - i.e not hierarchy, Why? To make extra explicit what is sent
 *<br>      2. All Messages should be nested inside `Message`
 *<br>          - It makes it easier to lookup messages
 *<br>      3. All Messages should only use basic types
 *<br>          - char/int/double/etc...
 *<br>          - no classes
 *<br>          - Easier to see what is actually sent, prevent sending useless data
 */
public class Message implements Serializable{
    public MessageKind op;
    public Order o;

    public enum MessageKind {
        // OrderManager -> SampleTrader
        REQNewOrder,
        REQPrice,
        REQFill,
        REQCross,

        // SampleTrader -> OrderManager
        ANSAcceptOrder,
        ANSSliceOrder,

        // OrderManager -> Router
        REQPriceAtSize,
        REQRouteOrder,
        REQCancel,

        // Router -> OrderManager
        ANSBestPrice,
        ANSNewFill,

        /*/ OrderManager -> Client
        // FIXMessage cover all those
        REQPartialFill,
        REQFullyFilled,
        REQCancelled, */
        FIXMessage,

        // Client -> OrderManager
        ANSNewOrder,
        ANSCancel,
    }

    static public class FIXMessageData{
        public int    OrderId	=	-1;
        public char   MsgType;
        public int    OrdStatus;
        public int    size;
        public double price;
    }

    static public FIXMessageData readOrderManagerAnswer(String msg){
        String[] fixTags = msg.split(";");
        FIXMessageData m = new FIXMessageData();

        for(int i = 0; i < fixTags.length; i++){
            String[] tag_value = fixTags[i].split("=");
            switch(tag_value[0]){
                case "11": // clientOrderID
                    m.OrderId = Integer.parseInt(tag_value[1]);
                    break;
                case "35": //
                    m.MsgType = tag_value[1].charAt(0);
                    break;
                case "38":  // size
                    m.size = Integer.parseInt(tag_value[1]);
                    break;
                case "39": // Status
                    m.OrdStatus = tag_value[1].charAt(0);
                    break;
                case "44": // Price
                    m.price = Double.parseDouble(tag_value[1]);
                    break;
            }
        }
        return m;
    }

    //      Client
    // ------------------------------------------------------------------------
    static public class ClientMessage extends Message{
        public int order_id;
    }

    static public class CancelOrder extends ClientMessage {
        public int order_id;

        public CancelOrder(int order){
            op       = MessageKind.ANSCancel;
            order_id = order;
        }
    }

    static public class NewOrderSingle extends Message{
        public int        client_order_id;
        public Instrument instrument;
        public int        size;
        public float      price;

        public NewOrderSingle(int client_order_id_, Instrument asset, int size_, float price_){
            op              = MessageKind.ANSNewOrder;
            client_order_id = client_order_id_;
            instrument      = asset;
            size            = size_;
            price           = price_;
        }
    }

    static public class FIXMessage extends Message{
        public String message;

        public FIXMessage(String m){
            op      = MessageKind.FIXMessage;
            message = m;
        }
    }

    //      Router
    // ------------------------------------------------------------------------

    static public class BestPrice extends Message{
        public int    order_id;
        public int    slice_id;
        public int    router_id;
        public double price;

        public BestPrice(int order, int slice, int router, double price_){
            op        = MessageKind.ANSBestPrice;
            order_id  = order;
            slice_id  = slice;
            router_id = router;
            price     = price_;
        }
    }

    static public class NewFill extends Message{
        public int    order_id;
        public int    slice_id;
        public int    router_id;
        public double price;
        public int    size;

        public NewFill(int order, int slice, int router, double price_, int size_){
            op        = MessageKind.ANSNewFill;
            order_id  = order;
            slice_id  = slice;
            router_id = router;
            price     = price_;
            size      = size_;
        }
    }

    static public class PriceAtSize extends Message{
        public int          order_id;
        public int          slice_id;
        public Instrument   asset;
        public int          size;


        public PriceAtSize(int order_id_, int slice_id_, Instrument ass, int size_){
            op       = MessageKind.REQPriceAtSize;
            order_id = order_id_;
            slice_id = slice_id_;
            asset    = ass;
            size     = size_;
        }
    }

    static public class RouteOrder extends Message{
        public int        order_id;
        public int        slice_id;
        public Instrument asset;
        public int        size;

        public RouteOrder(int order_id_, int slice_id_, Instrument ass, int size_){
            op       = MessageKind.REQRouteOrder;
            order_id = order_id_;
            slice_id = slice_id_;
            asset    = ass;
            size     = size_;
        }
    }

    //      Trader
    // ------------------------------------------------------------------------
    public static class TraderNewOrder extends Message{
        public int   order_id;
        public Order order;

        public TraderNewOrder(int order_id_, Order order_){
            op       = MessageKind.REQNewOrder;
            order_id = order_id_;
            order    = order_;
        }
    }

    public static class TraderPrice extends Message{
        public int   order_id;
        public Order order;

        public TraderPrice(int order_id_, Order order_){
            op       = MessageKind.REQPrice;
            order_id = order_id_;
            order    = order_;
        }
    }

    public static class SetPrice extends Message{

        public SetPrice(Order order_){
            op       = MessageKind.REQPrice;
            o   = order_;
        }
    }

    public static class TraderFill extends Message{
        public int   order_id;
        public Order order;

        public TraderFill(int order_id_, Order order_){
            op       = MessageKind.REQFill;
            order_id = order_id_;
            order    = order_;
        }
    }

    public static class TraderCross extends Message{
        public int   order_id;
        public Order order;

        public TraderCross(int order_id_, Order order_){
            op       = MessageKind.REQCross;
            order_id = order_id_;
            order    = order_;
        }
    }

    public static class TraderAcceptOrder extends Message{
        public int order_id;

        public TraderAcceptOrder(int order_id_){
            op       = MessageKind.ANSAcceptOrder;
            order_id = order_id_;
        }
    }

    public static class TraderSliceOrder extends Message{
        public int order_id;
        public int slice_size;

        public TraderSliceOrder(int order_id_, int slice_size_){
            op          = MessageKind.ANSSliceOrder;
            order_id    = order_id_;
            slice_size  = slice_size_;
        }
    }
}
