package Actor;


public abstract class ActorThread
        extends Actor implements Runnable {

    public ActorThread(MessageRouter r, Message m) {
        super(r);
        message = m;
    }

    @Override
    public boolean isRunnable() {
        return true;
    }

    @Override
    public void run() {
        runAndSend(message);
    }

    Message message;
}
