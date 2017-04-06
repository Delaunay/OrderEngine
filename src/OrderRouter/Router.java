package OrderRouter;

import Actor.Message;
import Ref.Instrument;

import java.io.IOException;

public interface Router {
    public void routeOrder(Message.RouteOrder m)
            throws IOException;

    public void sendCancel(int id, int sliceId, int size, Instrument i);

    public void priceAtSize(Message.PriceAtSize m)
            throws IOException;
}
