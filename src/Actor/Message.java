package Actor;


public abstract class Message {
    /**
     * returns the actor's ID that sent the message
     */
    abstract int from();

    /**
     * returns the actor's ID destination
     */
    abstract int to();

    /**
     * returns the type of message being sent
     */
    abstract int messageID();
}
