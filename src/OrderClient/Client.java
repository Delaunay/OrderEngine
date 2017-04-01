package OrderClient;

import java.io.IOException;

import OrderManager.Order;

public interface Client {
    // Outgoing messages
    int sendOrder(Object par0) throws IOException;

    void sendCancel(int id);

    // Incoming messages
    void partialFill(Order order);

    void fullyFilled(Order order);

    void cancelled(Order order);

    // Read incoming messages from InputStream
    void messageHandler();
}