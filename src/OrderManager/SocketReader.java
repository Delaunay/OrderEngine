package OrderManager;


import Actor.Message;
import Actor.Actor;

import java.io.IOException;
import java.net.Socket;
import java.util.ArrayList;
import java.util.concurrent.ConcurrentLinkedQueue;
import OrderManager.OrderManager.PendingMessage;

public class SocketReader extends Actor implements Runnable{
    OrderManager                          manager;
    ArrayList<Socket>                     sockets
            = new ArrayList<>(100);
    ConcurrentLinkedQueue<PendingMessage> pending_messages
            = new ConcurrentLinkedQueue<PendingMessage>();

    @Override
    public void run() {
        while (true){
            if (pending_messages.size() > 0)
                sendMessages();

            for (int id = 0; id < sockets.size(); id++){
                readMessages(id);
            }

            sleep(1);
        }
    }

    public void readMessages(int id){
        Socket socket = sockets.get(id);
        try {
            while (socket.getInputStream().available() > 0)
                manager.addIncomingMessage(id, readMessage(socket));
        } catch (IOException e){
            error("Could not read from socket");
        }
    }

    void sendMessages(){
        // send message
        int k = 0;
        int size = pending_messages.size();
        PendingMessage message = pending_messages.poll();

        while(message != null && k < size){
            Socket socket = sockets.get(message.actor_id);
            sendMessage(socket, message.message);
            message = pending_messages.poll();
            k += 1;
        }
    }

    void addMessage(PendingMessage m){
        pending_messages.add(m);
    }

    void addMessage(int actor_id, Message m){
        pending_messages.add(new PendingMessage(actor_id, m));
    }
}
