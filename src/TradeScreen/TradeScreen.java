package TradeScreen;

import OrderManager.Order;

import java.io.IOException;

public interface TradeScreen {
    public enum MessageKind {
        // OrderManager -> SampleTrader
        REQNewOrder,
        REQPrice,
        REQFill,
        REQCross,

        // SampleTrader -> OrderManager
        ANSAcceptOrder,
        ANSSliceOrder,
    }

    public void newOrder(int id, Order order)
            throws IOException;

    public void acceptOrder(int id)
            throws IOException;

    public void sliceOrder(int id, int sliceSize)
            throws IOException;

    public void price(int id, Order o)
            throws IOException;
}
