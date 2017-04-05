package OrderManager;

import OrderClient.Client;
import OrderClient.NewOrderSingle;
import Utility.HelperObject;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.Socket;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * Created by user on 4/5/2017.
 */
class ClientThread extends HelperObject implements Runnable {
	private int client_id;
	private Socket client;
	private OrderManager order_manager;
	private ConcurrentLinkedQueue<String> pending_messages;


	ClientThread(int clientId, Socket client, OrderManager oM) {
		this.client_id = clientId;
		this.client = client;
		this.order_manager = oM;
		initLog(this.getClass().getName());
		pending_messages = new ConcurrentLinkedQueue<>();
	}

	void addMessage(String message){
		pending_messages.add(message);
	}

	void sendMessages(){
		// send message
		int k = 0;
		int size = pending_messages.size();
		String message = pending_messages.poll();

		while(message != null && k < size){
			try {
				ObjectOutputStream os = new ObjectOutputStream(client.getOutputStream());
				os.writeObject(message);
				os.flush();
			} catch (IOException e){
				error("Could not send message to client");
			}
			k += 1;
			message = pending_messages.poll();
		}
	}

	public static class PendingNewOrder{
		int client_id;
		int client_order_id;
		NewOrderSingle new_order;

		PendingNewOrder(int id, int oid, NewOrderSingle nos){
			client_id = id;
			client_order_id = oid;
			new_order = nos;
		}
	}

	@Override
	public void run() {
		while (true) {
			if (pending_messages.size() > 0)
				sendMessages();

			try {
				ObjectInputStream is = new ObjectInputStream(client.getInputStream());
				Client.MessageKind method = (Client.MessageKind) is.readObject();
				//debug(" calling " + method);

				switch (method) {
					case ANSNewOrder:
						order_manager.addNewOrder(
								new PendingNewOrder(client_id, is.readInt(), (NewOrderSingle) is.readObject()));
						break;
					case ANSCancel:
						break;
				}
			} catch (ClassNotFoundException e) {
				error("Client message was not understood! A Message must start with a `Client.MessageKind`");
			} catch (IOException e) {
				e.printStackTrace();
			}
			sleep(10);
		}
	}
}
