import Actor.Message;
import OrderManager.Order;
import TradeScreen.TradeScreen;
import Utility.Connection.ConnectionType;
import Utility.HelperObject;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.InetSocketAddress;
import java.util.HashMap;

public class SampleTrader extends OrderManagerClient implements TradeScreen, Runnable{
	private HashMap<Integer,Order> orders = new HashMap<>();

    private ObjectInputStream  	is;
    private ObjectOutputStream 	os;

    public SampleTrader(InetSocketAddress om_address){
		super(om_address);
        initLog(this.getClass().getName());
    }

	public
    boolean readMessage() throws IOException, ClassNotFoundException{
        while(isAvailable()){

            Message message = readMessage(order_manager);

            debug("called " + message.op);

            switch(message.op){
                case REQNewOrder: newOrder((Message.NewOrder) message); return true;
                case REQPrice   :    price((Message.Price) message); return true;
                case REQCross   :    cross((Message.Cross) message); return true;
                case REQFill    :     fill((Message.Fill)  message); return true;
            }
        }
        return false;
    }

    public void connectToOrderManager(InetSocketAddress address) throws IOException{
        connectToOrderManager(ConnectionType.TraderConnection, address);
    }

	public void run(){
		//OM will connect to us
		try {
			if (order_manager == null)
                connectToOrderManager(order_manager_address);

			while(true){
                readMessage();
                HelperObject.sleep(HelperObject.waitTime);
			}

        } catch (IOException e) {
            e.printStackTrace();
        } catch (ClassNotFoundException e) {
            error("Object Error");
        }
	}

    //      Actions
    // ------------------------------------------------------------------------

    // TODO
    void cross(Message.Cross message){
        debug("CROSS");
    }

    // TODO
    void fill(Message.Fill message){
        debug("FILL");
    }

	@Override
	public void newOrder(Message.NewOrder m) throws IOException{
		// TODO the order should go in a visual grid, but not needed for test purposes
		orders.put(m.order_id, m.order);
		acceptOrder(m.order_id);
	}

	@Override
	public void acceptOrder(int id) throws IOException {
        sendMessage(order_manager, new Message.AcceptOrder(id));
	}

	@Override
	public void sliceOrder(int id, int sliceSize) throws IOException {
        sendMessage(order_manager, new Message.SliceOrder(id, sliceSize));
	}
	@Override
	public void price(Message.Price m) throws IOException {
		//TODO should update the trade screen
		//wait(2134);
		sliceOrder(m.order_id, MockConfig.slice_size);
	}
}
