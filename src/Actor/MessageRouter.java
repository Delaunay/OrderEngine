package Actor;

import java.util.HashMap;


public abstract class MessageRouter {

    public MessageRouter() {
        actorPool = new HashMap<>();
    }

    public void sendMessage(Message m) {
        if (m != null) {
            Actor a = actorPool.get(m.to());
            if (a.isRunnable()) {
                ActorThread t = (ActorThread) a;
                (new Thread(t)).start();
            } else {
                a.runAndSend(m);
            }
        }
    }

    private HashMap<Integer, Actor> actorPool;
}
