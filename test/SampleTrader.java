import OrderManager.Order;
import TradeScreen.TradeScreen;
import Utility.Connection;
import Utility.HelperObject;
import org.apache.log4j.LogManager;
import org.apache.log4j.Logger;

import javax.net.ServerSocketFactory;
import java.io.IOException;
import java.io.InputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.util.HashMap;

public class SampleTrader extends Thread implements TradeScreen{
	private HashMap<Integer,Order> orders = new HashMap<>();

    private ObjectInputStream  	is;
    private ObjectOutputStream 	os;

    private InetSocketAddress   order_manager_address;
	private Socket              omConn;
    private Logger              log;


    public SampleTrader(String name, InetSocketAddress om_address){
		this.setName(name);
		order_manager_address = om_address;
        initLog();
    }

    public void initLog(){
        //BasicConfigurator.configure();
        log = LogManager.getLogger(this.getClass().getName());
        log.setLevel(HelperObject.logLevel);
    }

    public  boolean runOnce() throws IOException, ClassNotFoundException {
        return readMessage();
    }

	public
    boolean readMessage() throws IOException, ClassNotFoundException{
        while(omConn.getInputStream().available() > 0){
            is = new ObjectInputStream(omConn.getInputStream());

            TradeScreen.MessageKind  method  = (TradeScreen.MessageKind) is.readObject();
            int   id                         = is.readInt();
            Order order                      = (Order) is.readObject();

            log.debug(method.toString());

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
        //OM will connect to us
        omConn = new Socket();
        omConn.setSendBufferSize(HelperObject.socket_buffer);
        omConn.setReceiveBufferSize(HelperObject.socket_buffer);
        omConn.setKeepAlive(true);

        omConn.connect(address);

        ObjectOutputStream os = new ObjectOutputStream(omConn.getOutputStream());
            os.writeObject(Connection.ConnectionType.TraderConnection);
            os.flush();

        log.info("Connected to OM ");
    }

	public void run(){
		//OM will connect to us
		try {
			if (omConn == null)
                connectToOrderManager(order_manager_address);

			while(true){
                readMessage();
                HelperObject.sleep(HelperObject.waitTime);
			}

        } catch (IOException e) {
            e.printStackTrace();
        } catch (ClassNotFoundException e) {
            log.error("Object Error");
        }
	}

    //      Actions
    // ------------------------------------------------------------------------

    // TODO
    void cross(int id, Order o){
        log.debug("CROSS");
    }

    // TODO
    void fill(int id, Order o){
        log.debug("FILL");
    }

	@Override
	public void newOrder(int id,Order order) throws IOException{
		// TODO the order should go in a visual grid, but not needed for test purposes
		orders.put(id, order);
		acceptOrder(id);
	}

	@Override
	public void acceptOrder(int id) throws IOException {
        os = new ObjectOutputStream(omConn.getOutputStream());
            os.writeObject(MessageKind.ANSAcceptOrder);
            os.writeInt(id);
            os.flush();
	}

	@Override
	public void sliceOrder(int id, int sliceSize) throws IOException {
		os = new ObjectOutputStream(omConn.getOutputStream());
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
