package TradeScreen;

import Actor.Message;

import java.io.IOException;

public interface TradeScreen {
    public void newOrder(Message.NewOrder m)
            throws IOException;

    public void acceptOrder(int id)
            throws IOException;

    public void sliceOrder(int id, int sliceSize)
            throws IOException;

    public void price(Message.Price m)
            throws IOException;
}
