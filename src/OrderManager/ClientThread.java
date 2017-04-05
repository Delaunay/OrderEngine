package OrderManager;

import OrderClient.Client;
import OrderClient.NewOrderSingle;
import Utility.HelperObject;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.net.Socket;

import static java.lang.Thread.sleep;

/**
 * Created by user on 4/5/2017.
 */
class ClientThread extends HelperObject implements Runnable{
	private int clientId;
	private Socket client;
	private  OrderManager oM;

	ClientThread(int clientId, Socket client, OrderManager oM){
		this.clientId = clientId;
		this.client = client;
		this.oM = oM;
		initLog(this.getClass().getName());
	}

	@Override
	public void run() {
		while (true){
			try {
				ObjectInputStream is = new ObjectInputStream(client.getInputStream());
				Client.MessageKind method = (Client.MessageKind) is.readObject();

				debug(" calling " + method);

				switch (method) {
					case ANSNewOrder:
                        oM.newOrder(clientId, is.readInt(), (NewOrderSingle) is.readObject());
                        break;
                    case ANSCancel:
                        //Order o = oM.orders.get(is.readInt());
                        //oM.sendCancel(o,);
                        break;
				}
            } catch (ClassNotFoundException e) {
                error("Client message was not understood! A Message must start with a `Client.MessageKind`");
			} catch(IOException e){
				e.printStackTrace();
            }

            sleep(1);
		}
	}
}
