package Actor;

import Utility.HelperObject;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.Socket;


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
public abstract class Actor extends HelperObject{
    public boolean messageDispatcher(){
        return false;
    }

    public boolean isAvailable(){
        try {
            return connection().getInputStream().available() > 0;
        } catch (IOException e){
            return false;
        }
    }

    public Socket connection(){
        return null;
    }

    final public void sendMessage(Socket soc, Message m){
        try {
            ObjectOutputStream os = new ObjectOutputStream(soc.getOutputStream());
                os.writeObject(m);
                os.flush();
        } catch (IOException e){
            error("Could not write to Stream");
            e.printStackTrace();
        }
    }

    final public Message readMessage(Socket soc){
        try {
            ObjectInputStream is = new ObjectInputStream(soc.getInputStream());
            return (Message) is.readObject();
        } catch (IOException e){
            error("Could not read Stream");
            e.printStackTrace();
        } catch (ClassNotFoundException e){
            error("Could not cast object to message");
            e.printStackTrace();
        }
        return null;
    }



}
