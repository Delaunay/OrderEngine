package Actor;

import OrderManager.Order;
import Ref.Instrument;

import java.io.Serializable;

/**
 *  Message is the class specifying the data send
 *  between each actors.
 *
 *  We set some base rules for a message:
 *      1. All Messages show extends and only extend `Message`
 *          - i.e not hierarchy, Why? To make extra explicit what is sent
 *      2. All Messages should be nested inside `Message`
 *          - It makes it easier to lookup messages
 *      3. All Messages should only use basic types
 *          - char/int/double/etc...
 *          - no classes
 *          - Easier to see what is actually sent, prevent sending useless data
 */
public class Message implements Serializable{
    public MessageKind op;

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

    static class FIXMessageData{
        int     OrderId	=	-1;
        char    MsgType;
        int     OrdStatus;
    }

    static FIXMessageData readOrderManagerAnswer(String msg){
        String[] fixTags = msg.split(";");
        FIXMessageData m = new FIXMessageData();

        for(int i = 0; i < fixTags.length; i++){
            String[] tag_value = fixTags[i].split("=");
            switch(tag_value[0]){
                case "11":
                    m.OrderId = Integer.parseInt(tag_value[1]);
                    break;
                case "35":
                    m.MsgType = tag_value[1].charAt(0);
                    break;
                case "39":
                    m.OrdStatus = tag_value[1].charAt(0);
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
        public int        order_id;
        public Instrument instrument;
        public int        size;
        public float      price;

        public NewOrderSingle(int order, Instrument asset, int size_, float price_){
            op          = MessageKind.ANSNewOrder;
            order_id    = order;
            instrument  = asset;
            size        = size_;
            price       = price_;
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
    public static class NewOrder extends Message{
        public int   order_id;
        public Order order;

        public NewOrder(int order_id_, Order order_){
            order_id = order_id_;
            order    = order_;
        }
    }

    public static class Price extends Message{
        public int   order_id;
        public Order order;

        public Price(int order_id_, Order order_){
            op       = MessageKind.REQPrice;
            order_id = order_id_;
            order    = order_;
        }
    }

    public static class Fill extends Message{
        public int   order_id;
        public Order order;

        public Fill(int order_id_, Order order_){
            op       = MessageKind.REQFill;
            order_id = order_id_;
            order    = order_;
        }
    }

    public static class Cross extends Message{
        public int   order_id;
        public Order order;

        public Cross(int order_id_, Order order_){
            op       = MessageKind.REQCross;
            order_id = order_id_;
            order    = order_;
        }
    }

    public static class AcceptOrder extends Message{
        public int order_id;

        public AcceptOrder(int order_id_){
            op       = MessageKind.ANSAcceptOrder;
            order_id = order_id_;
        }
    }

    public static class SliceOrder extends Message{
        public int order_id;
        public int slice_size;

        public SliceOrder(int order_id_, int slice_size_){
            op          = MessageKind.ANSSliceOrder;
            order_id    = order_id_;
            slice_size  = slice_size_;
        }
    }
}
