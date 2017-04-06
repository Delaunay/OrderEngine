import Actor.Message;
import OrderManager.Order;
import TradeScreen.TradeScreen;
import Utility.Connection.ConnectionType;
import Utility.HelperObject;

import java.io.IOException;
import java.io.InputStream;
import java.net.InetSocketAddress;
import java.util.HashMap;

/**
 * 		- Trader listens to the OrderManager
 *<br>      - Decides if an order can be directly fill or if it needs to be sliced
 *<br>      - Returns its choice to the OrderManager
 */
public class SampleTrader extends OrderManagerClient implements TradeScreen, Runnable{
	//private HashMap<Integer,Order> orders = new HashMap<>();
    private InputStream            is;
    private Integer                port = null;

    public SampleTrader(InetSocketAddress om_address){
        super(om_address);
        initLog(this.getClass().getName());
        //classLog.setLevel(Level.DEBUG);
    }

    public SampleTrader(int port_){
        super(null);
        initLog(this.getClass().getName());
        port = port_;
        //classLog.setLevel(Level.DEBUG);
    }

	public
    boolean readMessage() throws IOException, ClassNotFoundException{
        while(order_manager.getInputStream().available() > 0){

            Message message = readMessage(order_manager);

            debug("called " + message.op);

            switch(message.op){
                case REQNewOrder: newOrder((Message.TraderNewOrder) message); break;
                case REQPrice   :    price((Message.TraderPrice) message);    break;
                case REQCross   :    cross((Message.TraderCross) message);    break;
                case REQFill    :     fill((Message.TraderFill)  message);    break;
                default:
                    error("unsupported operation" + message.op);
                    break;
            }
        }
        return true;
    }

    public void connectToOrderManager(InetSocketAddress address) throws IOException{
        connectToOrderManager(ConnectionType.TraderConnection, address);
    }

	public void run(){
		//OM will connect to us
		try {
		    if (port != null)
                connectIndirectConnect(port);

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
    void cross(Message.TraderCross message){
        debug("CROSS");
    }

    // TODO
    void fill(Message.TraderFill message){
        debug("FILL");
    }

	@Override
	public void newOrder(Message.TraderNewOrder m) throws IOException{
		// TODO the order should go in a visual grid, but not needed for test purposes
		//orders.put(m.order_id, m.order);
		acceptOrder(m.order_id);
	}

	@Override
	public void acceptOrder(int id) throws IOException {
        debug("Accepting Orders");
        sendMessage(order_manager, new Message.TraderAcceptOrder(id));
	}

	@Override
	public void sliceOrder(int id, int sliceSize) throws IOException {
        debug("Slicing Orders");
        sendMessage(order_manager, new Message.TraderSliceOrder(id, sliceSize));
	}
	@Override
	public void price(Message.TraderPrice m) throws IOException {
		//TODO should update the trade screen
		//wait(2134);
        debug("Price");
		sliceOrder(m.order_id, MockConfig.slice_size);
	}
}
