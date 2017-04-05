package OrderManager;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;

import com.sun.management.OperatingSystemMXBean;

import java.lang.management.ManagementFactory;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.TimeUnit;


import OrderClient.Client;
import Utility.HelperObject;
import org.apache.log4j.BasicConfigurator;
import org.apache.log4j.Level;
import org.apache.log4j.LogManager;

import Database.Database;
import LiveMarketData.LiveMarketData;
import OrderClient.NewOrderSingle;
import OrderRouter.Router;
import TradeScreen.TradeScreen;
import org.apache.log4j.Logger;

import Utility.Util;
import org.omg.Messaging.SYNC_WITH_TRANSPORT;
import sun.plugin2.message.Message;

import static java.lang.Thread.sleep;


/**
 * 			Order Manager listens to
 * 				- Clients
 * 				- TradingEngine (SampleTrader)
 * 				- Routers       (Markets)
 *
 *		Clients -> newOrder -> SampleTrader
 *
 *		Router -> newFill -> SampleTrader
 *			   -> BestPrice -> ReallyRouteOrder -> Router
 *
 *		SampleTrader -> sliceOrder -> Cross -> RouteOrder
 *			   -> AcceptOrder -> Client
 */
public class OrderManager extends HelperObject{
	private static LiveMarketData 	liveMarketData;
	private HashMap<Integer, PendingOrder> orders = new HashMap<>();
	private int 					id = 0;
	private int                     next_trader = 0;
	private Socket[]	 		    traders;
	private Socket[] 				orderRouters;
	private Socket[] 				clients;

	long totalMemoryUsage    = 0;
	long memoryCount         = 0;
	long startingTime        = System.currentTimeMillis();
	long lastTime            = System.currentTimeMillis();
	long averageTimePerOrder = 0;
	long lastOrderSize       = 0;
	long numOrderPerSecond   = 0;
	double averageCPU          = 0;

    public
    OrderManager(InetSocketAddress[] routers,
                 InetSocketAddress[] clients,
                 InetSocketAddress[] traders,
                 LiveMarketData liveData) throws InterruptedException
    {
        initLog(this.getClass().getName());
        liveMarketData = liveData ;
        connectToTraders(traders);
        connectToRouters(routers);
        connectToClients(clients);
    }

    ServerSocketChannel serverSocket;

    /*
    public void acceptConnection() throws IOException{
        serverSocket.configureBlocking(false);
        SocketChannel channel = serverSocket.accept();

        if (channel == null)
            return;

        Socket con = channel.socket();

        if (con.getInputStream().available() > 0){
            ObjectInputStream is = new ObjectInputStream(con.getInputStream());
                ConnectionType type = (ConnectionType) is.readObject();
                switch(type){
                    case TraderConnection:  addTrader(con); return;
                    case ClientConnection:  addClient(con); return;
                    case RouterConnection:  addRouter(con); return;
                }
        }
    }*/

	public Socket getTrader(){
	    Socket t = traders[next_trader];
	    next_trader += 1;
	    next_trader %= traders.length;
	    return t;
    }

	/** Return true if some work has been done */
	public boolean runOnce() throws IOException, ClassNotFoundException, InterruptedException{
		return processRouterMessages()
            || processTraderMessages();
	}

	void summary(){
		OperatingSystemMXBean cpuMeasure = (com.sun.management.OperatingSystemMXBean) ManagementFactory.getOperatingSystemMXBean();
        System.gc();
        Runtime rt = Runtime.getRuntime();
        long memory = (rt.totalMemory() - rt.freeMemory()) / 1024 ;

        totalMemoryUsage += memory;
        memoryCount ++;

        if(orders.size() > 0){
            long now = System.currentTimeMillis();
            long diffOrders = orders.size() - lastOrderSize;

            lastOrderSize = orders.size();
            if (now > startingTime){
                numOrderPerSecond = (diffOrders  * 1000 /(now - lastTime));
                averageTimePerOrder = orders.size() * 1000 / (now - startingTime);
                lastTime = now;
            }

        }
        long averageMemoryUsage = totalMemoryUsage / memoryCount;
        averageCPU += cpuMeasure.getProcessCpuLoad();

        info("       Order: " + orders.size() + "  " + "(AVG " + averageTimePerOrder + ")");
        info("      Memory: " + memory + " KB (AVG: " + averageMemoryUsage + " KB)");
        info("    Cpu load: " + averageCPU * 100 / memoryCount + "%");

		if(memory > 1048576 ){
			System.exit(0);
		}
    }

	public void run(){
        // Print the summary from time to time
        ScheduledPrint sp = new ScheduledPrint(1000, this);

        spawnClients();

		while(true){
            /*
		    try {
                acceptConnection();
            } catch (IOException e){
            }*/

			try {
				runOnce();
			} catch (IOException e) {
				e.printStackTrace();
				break;
			} catch (ClassNotFoundException e) {
				e.printStackTrace();
				break;
			} catch (InterruptedException e) {
				e.printStackTrace();
				break;
			}

            sp.run();
			sleep(1);
		}
	}

	// Read Incoming Messages
	// ------------------------------------------------------------------------

	public void spawnClients() {
		for (int clientId = 0; clientId < this.clients.length; clientId++) {
			Socket client = this.clients[clientId];
			new Thread(new ClientThread(clientId, client, this)).start();
			debug("client ID : " + clientId);
		}
	}

	public boolean processRouterMessages() throws IOException, ClassNotFoundException{
		int routerId;
		Socket router;
		boolean work_was_done = false;

		for (routerId = 0; routerId < this.orderRouters.length; routerId++) {
			router = this.orderRouters[routerId];

			while (router.getInputStream().available() > 0) {

				ObjectInputStream is = new ObjectInputStream(router.getInputStream());
				Router.MessageKind method = (Router.MessageKind) is.readObject();

				debug(" calling " + method);

				switch (method) {
					case ANSBestPrice:
						int OrderId = is.readInt();
						int SliceId = is.readInt();
						Order slice = orders.get(OrderId).order.slices.get(SliceId);
						slice.bestPrices[routerId] = is.readDouble();
						slice.bestPriceCount += 1;
						if (slice.bestPriceCount == slice.bestPrices.length)
							routeOrder(SliceId, slice);
						break;

					case ANSNewFill:
						newFill(is.readInt(), is.readInt(), is.readInt(), is.readDouble());
						break;
				}
                work_was_done = true;
			}
		}
		return work_was_done;
	}

	public boolean processTraderMessages() throws IOException, ClassNotFoundException{
        boolean work_was_done = false;
	    for(Socket trader : traders){
            if (trader.getInputStream().available() <= 0)
                continue;

            while(trader.getInputStream().available() > 0) {
                ObjectInputStream is = new ObjectInputStream(trader.getInputStream());
                TradeScreen.MessageKind method = (TradeScreen.MessageKind) is.readObject();

                debug(" calling " + method);

                switch (method) {
                    case ANSAcceptOrder:
                        acceptOrder(is.readInt());
                        break;
                    case ANSSliceOrder:
                        sliceOrder(is.readInt(), is.readInt());
                }
                work_was_done = true;
            }
	    }
		return work_was_done;
	}


	// Actions
	// ----------------------------------------------------------------
	protected synchronized void newOrder(int clientId, int clientOrderId, NewOrderSingle nos) throws IOException {

		orders.put(id, new PendingOrder(new Order(clientId, clientOrderId, nos.instrument, nos.size)));

		ObjectOutputStream os = new ObjectOutputStream(clients[clientId].getOutputStream());
			os.writeObject("11=" + clientOrderId + ";35=A;39=A;");
			os.flush();

		sendOrderToTrader(id, orders.get(id).order, TradeScreen.MessageKind.REQNewOrder);
		id++;
	}

	private synchronized void sendOrderToTrader(int id, Order o, Object method) throws IOException {
		ObjectOutputStream ost = new ObjectOutputStream(getTrader().getOutputStream());

			ost.writeObject(method);
			ost.writeInt(id);
			ost.writeObject(o);
			ost.flush();
	}

	public synchronized void acceptOrder(int id) throws IOException {
		Order o = orders.get(id).order;
		if (o.OrdStatus != 'A') { // Pending New
			error("error accepting order that has already been accepted");
			return;
		}
		o.OrdStatus = '0'; // New

		ObjectOutputStream os = new ObjectOutputStream(clients[o.clientid].getOutputStream());
			os.writeObject("11=" + o.clientOrderID + ";35=A;39=0");
			os.flush();

		price(id, o);
	}

	public void sliceOrder(int id, int sliceSize) throws IOException {
		Order o = orders.get(id).order;
		if (sliceSize > o.sizeRemaining() - o.sliceSizes()) {
			error("error sliceSize is bigger than remaining size to be filled on the order");
			return;
		}

		int sliceId = o.newSlice(sliceSize);
		Order slice = o.slices.get(sliceId);

		internalCross(id, slice);

		int sizeRemaining = o.slices.get(sliceId).sizeRemaining();
		if (sizeRemaining > 0) {
			askBestPrice(id, sliceId, sizeRemaining, slice);
		}
	}

	private synchronized void internalCross(int id, Order o) throws IOException {
		for (Map.Entry<Integer, PendingOrder> entry : orders.entrySet()) {
			if (entry.getKey() == id)
				continue;

			Order matchingOrder = entry.getValue().order;

			if (!(matchingOrder.instrument.equals(o.instrument)
					&& matchingOrder.initialMarketPrice == o.initialMarketPrice))
				continue;

			int sizeBefore = o.sizeRemaining();

			o.cross(matchingOrder);

			if (sizeBefore != o.sizeRemaining()) {
				sendOrderToTrader(id, o, TradeScreen.MessageKind.REQCross);
			}
		}
	}

	private void newFill(int id, int sliceId, int size, double price) throws IOException {
		Order o = orders.get(id).order;
		o.slices.get(sliceId).createFill(size, price);

		if (o.sizeRemaining() == 0) {
			Database.write(o);
		}

		sendOrderToTrader(id, o, TradeScreen.MessageKind.REQFill);
	}

	/** Ask for the best price to each router
	 * 	@param id		Order id
	 * 	@param sliceId  Slice id
	 * 	@param size		size remaining to full the current slice
	 * 	@param order	Order object
	 * 	*/
	private void askBestPrice(int id, int sliceId, int size, Order order) throws IOException {
		for (Socket r : orderRouters) {

			ObjectOutputStream os = new ObjectOutputStream(r.getOutputStream());
				os.writeObject(Router.MessageKind.REQPriceAtSize);
				os.writeInt(id);
				os.writeInt(sliceId);
				os.writeObject(order.instrument);
				os.writeInt(order.sizeRemaining());
				os.flush();
		}

		// need to wait for these prices to come back before routing
		order.bestPrices = new double[orderRouters.length];
		order.bestPriceCount = 0;
	}

	/** Buy/Sell the instrument at the min/max price possible */
	private void routeOrder(int sliceId, Order o) throws IOException {
		// if o.size < 0 => We are selling
		int index = o.size > 0 ? getBestBuyPrice(o) : getBestSellPrice(o);

		ObjectOutputStream os = new ObjectOutputStream(orderRouters[index].getOutputStream());
			os.writeObject(Router.MessageKind.REQRouteOrder);
			os.writeInt(o.id);
			os.writeInt(sliceId);
			os.writeInt(o.sizeRemaining());
			os.writeObject(o.instrument);
			os.flush();
	}

	// TODO
	void sendCancel(Order order, Router orderRouter) {
		// orderRouter.sendCancel(order);
		// order.orderRouter.writeObject(order);
	}

	// TODO
	void cancelOrder() {

	}

	private void price(int id, Order o) throws IOException {
		liveMarketData.setPrice(o);
		sendOrderToTrader(id, o, TradeScreen.MessageKind.REQPrice);
	}

	// Utilities
	// ----------------------------------------------------------------
	static int getBestBuyPrice(Order o){
		int minIndex = 0;
		double min = o.bestPrices[0];

		for (int i = 1; i < o.bestPrices.length; i++) {
			if (min > o.bestPrices[i]) {
				minIndex = i;
				min = o.bestPrices[i];
			}
		}
		return minIndex;
	}

	static int getBestSellPrice(Order o){
		int maxIndex = 0;
		double max = o.bestPrices[0];

		for (int i = 1; i < o.bestPrices.length; i++) {
			if (max < o.bestPrices[i]) {
				maxIndex = i;
				max = o.bestPrices[i];
			}
		}
		return maxIndex;
	}


	// Order Filling
    // ------------------------------------------------------------------------
    void sliceFill(int order_id, int size, int price){
	    PendingOrder order = orders.get(order_id);
            order.size_remain -= size;
            order.slice_num -= 1;
	        order.cost += size * price;

        // send info about the last fill
        sendPartialFill(order.order, size, price);

        if (order.size_remain == 0) {
            // if done tell the client too
            sendFullFill(order.order, order.cost);
        }
    }

    void sendPartialFill(Order order, int size, double price){

    }

    void sendFullFill(Order order, double cost){

    }


	// Connection
    // ------------------------------------------------------------------------
	private Socket connect(InetSocketAddress location) throws InterruptedException {
		int tryCounter = 0;
		while (tryCounter < 600) {
			try {
				Socket s = new Socket(location.getHostName(), location.getPort());
				s.setKeepAlive(true);
				return s;
			} catch (IOException e) {
				sleep(1000);
				tryCounter++;
			}
		}
		error("Failed to connect to " + location.toString());
		return null;
	}


	public void connectToTraders(InetSocketAddress[] traders_) throws InterruptedException{
        int i = 0;
        traders = new Socket[traders_.length];
        for (InetSocketAddress add : traders_) {
            traders[i] = connect(add);
            i += 1;
        }
	}

	public void connectToRouters(InetSocketAddress[] orderRouters) throws InterruptedException{
		int i = 0;
		this.orderRouters = new Socket[orderRouters.length];
		for (InetSocketAddress location : orderRouters) {
			this.orderRouters[i] = connect(location);
			i += 1;
		}
	}
	public void connectToClients(InetSocketAddress[] clients) throws InterruptedException{
		this.clients = new Socket[clients.length];
		int i = 0;
		for (InetSocketAddress location : clients) {
			this.clients[i] = connect(location);
			i += 1;
		}
	}

    static class ScheduledPrint extends Utility.Util.ScheduledTask{
        OrderManager manager;

        ScheduledPrint(long delta, OrderManager manager_){
            super(delta);
            manager = manager_;
        }

        @Override
        public void scheduledJob(){
            manager.summary();
        }
    }
}

