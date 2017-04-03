package Actor;


public class Message {
    // This is an enumeration of all possible operation code (OpCode)
    // for each defined actor
    enum Trader{
        newOrder,
        acceptOrder,
        sliceOrder,
        price
    }

    enum Client{
        sendOrder,
        sendCancel,
        partialFill,
        fullyFilled,
        cancelled,
    }

    enum Router{
        routeOrder,
        sendCancel,
        priceAtSize
    }

    enum Market{
        setPrice
    }
}
