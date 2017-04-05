package OrderClient;

import OrderManager.Order;

import java.io.IOException;

public interface Client {
    public enum MessageKind {
        // OrderManager -> Client
        REQPartialFill,
        REQFullyFilled,
        REQCancelled,
        FIXMessage,

        // Client -> OrderManager
        ANSNewOrder,
        ANSCancel,
    }
    // Outgoing messages
    int sendOrder(NewOrderSingle nos) throws IOException;

    void sendCancel(int id);

    // Incoming messages
    void partialFill(Order order);

    void fullyFilled(Order order);

    void cancelled(Order order);

    // Read incoming messages from InputStream
    void messageHandler();
}