package OrderManager;

import Actor.Actor;
import Actor.Message;

import java.net.Socket;
import java.util.concurrent.ConcurrentLinkedQueue;


class ClientThread extends Actor implements Runnable {
	private int client_id;
	private Socket client;
	private OrderManager order_manager;
	private ConcurrentLinkedQueue<Message> pending_messages;

	ClientThread(int clientId, Socket client, OrderManager oM) {
		this.client_id = clientId;
		this.client = client;
		this.order_manager = oM;
		initLog(this.getClass().getName());
		pending_messages = new ConcurrentLinkedQueue<>();
	}

	void addMessage(Message m){
		pending_messages.add(m);
	}

	void sendMessages(){
		// send message
		int k = 0;
		int size = pending_messages.size();
		Message message = pending_messages.poll();

		while(message != null && k < size){
			sendMessage(client, message);
			message = pending_messages.poll();
			k += 1;
		}
	}

	public static class PendingNewOrder{
		int client_id;
		int client_order_id;
		Message.NewOrderSingle new_order;

		PendingNewOrder(int id, Message.NewOrderSingle a){
			client_id = id;
			new_order = a;
		}
	}

	@Override
	public void run() {
		while (true) {
			if (pending_messages.size() > 0)
				sendMessages();

			Message m = readMessage(client);
			debug(" calling " + m.op);

			switch (m.op) {
				case ANSNewOrder:
					order_manager.addNewOrder(
							new PendingNewOrder(client_id, (Message.NewOrderSingle) m));
					break;
				case ANSCancel:
					break;
			}

			sleep(10);
		}
	}
}
