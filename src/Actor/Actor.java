package Actor;

/**
 * An Actor is an Object that implements a specific Operation
 * 1) receive message m
 * 2) run
 * 3) return new message n
 */
public abstract class Actor {
    public Actor(MessageRouter r) {
        router = r;
    }

    public boolean isRunnable() {
        return false;
    }

    public abstract Message run(Message m);

    void runAndSend(Message m) {
        router.sendMessage(run(m));
    }

    private MessageRouter router;
}
