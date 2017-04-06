package TradeScreen;

import Actor.Message;

import java.io.IOException;

public interface TradeScreen {
    public void newOrder(Message.TraderNewOrder m)
            throws IOException;

    public void acceptOrder(int id)
            throws IOException;

    public void sliceOrder(int id, int sliceSize)
            throws IOException;

    public void price(Message.TraderPrice m)
            throws IOException;
}
