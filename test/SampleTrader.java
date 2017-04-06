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
        while(order_manager.getInputStream().available() > 0){
            is = new ObjectInputStream(order_manager.getInputStream());

            TradeScreen.MessageKind  method  = (TradeScreen.MessageKind) is.readObject();
            int   id                         = is.readInt();
            Order order                      = (Order) is.readObject();

            debug("called " + method);

            switch(method){
                case REQNewOrder: newOrder(id, order); return true;
                case REQPrice   :    price(id, order); return true;
                case REQCross   :    cross(id, order); return true;
                case REQFill    :     fill(id, order); return true;
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
    void cross(int id, Order o){
        debug("CROSS");
    }

    // TODO
    void fill(int id, Order o){
        debug("FILL");
    }

	@Override
	public void newOrder(int id,Order order) throws IOException{
		// TODO the order should go in a visual grid, but not needed for test purposes
		orders.put(id, order);
		acceptOrder(id);
	}

	@Override
	public void acceptOrder(int id) throws IOException {
        os = new ObjectOutputStream(order_manager.getOutputStream());
            os.writeObject(MessageKind.ANSAcceptOrder);
            os.writeInt(id);
            os.flush();
	}

	@Override
	public void sliceOrder(int id, int sliceSize) throws IOException {
		os = new ObjectOutputStream(order_manager.getOutputStream());
            os.writeObject(MessageKind.ANSSliceOrder);
            os.writeInt(id);
            os.writeInt(sliceSize);
            os.flush();
	}
	@Override
	public void price(int id, Order o) throws IOException {
		//TODO should update the trade screen
		//wait(2134);
		sliceOrder(id, MockConfig.slice_size);
	}
}
