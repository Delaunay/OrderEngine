import OrderManager.Order;
import TradeScreen.TradeScreen;
import Utility.HelperObject;
import Utility.Util;
import org.apache.log4j.LogManager;
import org.apache.log4j.Logger;

import javax.net.ServerSocketFactory;
import java.io.IOException;
import java.io.InputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.Socket;
import java.util.HashMap;

public class SampleTrader extends Thread implements TradeScreen{
	private HashMap<Integer,Order> orders = new HashMap<>();

    private InputStream          s;
    private ObjectInputStream  	is;
    private ObjectOutputStream 	os;

	private Socket 	omConn;
	private int 	port;
    public boolean  sleep = true;

    private Logger log;


    public SampleTrader(String name, int port){
		this.setName(name);
		this.port = port;
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
        if(0 < s.available()){
            is = new ObjectInputStream(s);

            TradeScreen.MessageKind  method  = (TradeScreen.MessageKind) is.readObject();
            int   id                         = is.readInt();
            Order order                      = (Order) is.readObject();

            log.debug(method.toString());

            switch(method){
                case REQNewOrder: newOrder(id, order); break;
                case REQPrice   :    price(id, order); break;
                case REQCross   :    cross(id, order); break;
                case REQFill    :     fill(id, order); break;
            }
            return true;
        }
        return false;
    }

    public void connectToOrderManager() throws IOException {
        omConn = ServerSocketFactory.getDefault().createServerSocket(port).accept();
        s = omConn.getInputStream();
    }

	public void run(){
		//OM will connect to us
		try {
			if (omConn == null)
                connectToOrderManager();

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
	public void price(int id,Order o) throws IOException {
		//TODO should update the trade screen
		//wait(2134);
		//sliceOrder(id, orders.get(id).sizeRemaining() / 2);
	}
}
