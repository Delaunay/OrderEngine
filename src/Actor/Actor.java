package Actor;

/**
 * An Actor is an Object that read messages from
 * an ActorManager and perform an operation and send
 * back a new message to the Actor Manager
 * <p>
 * <pre>
 *          Actor       Actor          Actor
 *               \        |           /
 *                \       |         /
 *                .-----------------.
 *                |  Actor  Manager |
 *                '-----------------'
 * </pre>
 * </p>
 */
public interface Actor {
    /**
     * Read a message if any was sent
     *
     * @return true if a message was read
     */
    public boolean readMessage();


    /**
     * Keep reading messages. Equivalent to:
     *
     * <pre>
     * {@code
     *      while (true){
     *          readMessage()
     *      }
     * }</pre>
     */
    public void runForever();

    /**
     * Connect to the Actor manager from/to which the
     * actor receive/send messages
     */
    public void connect(int port);

    /**
     * Connect to the actor manager but spawn a thread
     * to wait for a connection to prevent blocking.
     * Equivalent to:
     *
     * <pre>
     * {@code
     *      new Thread(() -> {
     *          connect(port);
     *      }).start();
     * }</pre>
     */
    public void connect(int port, boolean block);
}
