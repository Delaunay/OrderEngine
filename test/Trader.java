import java.io.IOException;
import java.io.InputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.Socket;
import java.util.HashMap;

import javax.net.ServerSocketFactory;

import OrderManager.Order;
import TradeScreen.TradeScreen;

public class Trader extends Thread implements TradeScreen{
	private HashMap<Integer,Order> orders = new HashMap<>();

    private InputStream          s;
    private ObjectInputStream  	is;
    private ObjectOutputStream 	os;

	private Socket 	omConn;
	private int 	port;

	Trader(String name, int port){
		this.setName(name);
		this.port = port;
	}

    public void print(String m){
        System.out.println("T : " + Thread.currentThread().getName() + " calling: " + m);
    }

    public void print(api m){
        System.out.println("T : " + Thread.currentThread().getName() + " calling: " + m);
    }

	public void readMessage() throws IOException, ClassNotFoundException, InterruptedException {
        if(0 < s.available()){

            // TODO check not create a new ObjectInputStream each time
            //if (is == null) This does not work
            is = new ObjectInputStream(s);
            is.close();

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
        }else{
            //print("Trader Waiting for data to be available - sleep 1s");
            Thread.sleep(100);
        }
    }

	public void run(){
		//OM will connect to us
		try {
			omConn = ServerSocketFactory.getDefault().createServerSocket(port).accept();
			
			//is = new ObjectInputStream(omConn.getInputStream());
            //if i try to create an objectinputstream before we have data it will block
			s = omConn.getInputStream();

			while(true){
                readMessage();
			}

        } catch (IOException e) {
            e.printStackTrace();
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
    }

	@Override
	public void newOrder(int id,Order order) throws IOException, InterruptedException {
		//TODO the order should go in a visual grid, but not needed for test purposes
		Thread.sleep(2134);
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
		Thread.sleep(2134);
		sliceOrder(id, orders.get(id).sizeRemaining() / 2);
	}
}
