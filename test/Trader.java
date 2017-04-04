import OrderManager.Order;
import TradeScreen.TradeScreen;
import Utility.Util;
import org.apache.log4j.BasicConfigurator;
import org.apache.log4j.Level;
import org.apache.log4j.LogManager;
import org.apache.log4j.Logger;

import javax.net.ServerSocketFactory;
import java.io.IOException;
import java.io.InputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.Socket;
import java.util.HashMap;

public class Trader extends Thread implements TradeScreen{
	private HashMap<Integer,Order> orders = new HashMap<>();

    private InputStream          s;
    private ObjectInputStream  	is;
    private ObjectOutputStream 	os;

	private Socket 	omConn;
	private int 	port;
    public boolean  sleep = true;

    private Logger log;


    public Trader(String name, int port){
		this.setName(name);
		this.port = port;
        initLog();

    }

    public void initLog(){
        //BasicConfigurator.configure();
        log = LogManager.getLogger("Debug");
        log.setLevel(Level.WARN);
    }

    public void print(String m){
        log.info("T : " + Thread.currentThread().getName() + " calling: " + m);
    }

    public void print(api m){
        log.info("T : " + Thread.currentThread().getName() + " calling: " + m);
    }

    public
    boolean runOnce() throws IOException, ClassNotFoundException, InterruptedException {
        return readMessage();
    }

	public
    boolean readMessage() throws IOException, ClassNotFoundException, InterruptedException {
        if(0 < s.available()){

            // TODO check hwo to not create a new ObjectInputStream each time if possible
            //if (is == null) This does not work
            is = new ObjectInputStream(s);

            api   method  = (api) is.readObject();
            int   id      = is.readInt();
            Order order   = (Order) is.readObject();

            print(method);

            switch(method){
                case newOrder : newOrder(id, order); break;
                case price    :    price(id, order); break;
                case cross    :    cross(id, order); break;
                case fill     :     fill(id, order); break;
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
			
			//is = new ObjectInputStream(omConn.getInputStream());
            //if i try to create an objectinputstream before we have data it will block

			while(true){
                readMessage();

                //print("Trader Waiting for data to be available - sleep 1s");
                //Thread.sleep(10);
			}

        } catch (IOException e) {
            e.printStackTrace();
            //System.exit(1);
        } catch (InterruptedException e) {
            // We dont care
        } catch (ClassNotFoundException e) {
            print("Object Error");
        }
	}

    //      Actions
    // ------------------------------------------------------------------------

    //TODO
    void cross(int id, Order o){
        print("CROSS");
    }

    //TODO
    void fill(int id, Order o){
        print("FILL");
        //orders.remove(id);
    }

	@Override
	public void newOrder(int id,Order order) throws IOException, InterruptedException {
		//TODO the order should go in a visual grid, but not needed for test purposes
		//wait(2134);
		orders.put(id, order);
		acceptOrder(id);
	}

	@Override
	public void acceptOrder(int id) throws IOException {
        os = new ObjectOutputStream(omConn.getOutputStream());
            os.writeObject("acceptOrder");
            os.writeInt(id);
            os.flush();
	}

	@Override
	public void sliceOrder(int id, int sliceSize) throws IOException {
		os = new ObjectOutputStream(omConn.getOutputStream());
            os.writeObject("sliceOrder");
            os.writeInt(id);
            os.writeInt(sliceSize);
            os.flush();
	}
	@Override
	public void price(int id,Order o) throws InterruptedException, IOException {
		//TODO should update the trade screen
		//wait(2134);
		sliceOrder(id, orders.get(id).sizeRemaining() / 2);
	}

	public void wait(int millis){
        if (sleep)
            Util.wait(millis);
    }
}
