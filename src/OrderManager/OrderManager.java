package OrderManager;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;


import OrderClient.Client;
import org.apache.log4j.Level;
import org.apache.log4j.LogManager;

import Database.Database;
import LiveMarketData.LiveMarketData;
import OrderClient.NewOrderSingle;
import OrderRouter.Router;
import TradeScreen.TradeScreen;
import org.apache.log4j.Logger;


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
public class OrderManager {
	private static LiveMarketData 	liveMarketData;
	private HashMap<Integer, Order> orders = new HashMap<Integer, Order>();
	private int 					id = 0;
	private Socket	 				trader;
	private Socket[] 				orderRouters;
	private Socket[] 				clients;
	private Logger					log;
	private InternalCross           internalCross;

	// Stats
    static long totalMemoryUsage =0;
    static long memoryCount =0;
    static long startingTime = System.currentTimeMillis();
    static long averageTimePerOrder =0;
    static long lastOrderSize=0;

    public
	OrderManager(InetSocketAddress[] orderRouters,
				 InetSocketAddress[] clients,
				 InetSocketAddress trader,
				 LiveMarketData liveData) throws InterruptedException
	{
		initLog();
		liveMarketData = liveData ;
		connectToTrader(trader);
		connectToRouters(orderRouters);
		connectToClients(clients);
	}

	protected void print(String msg){
		log.info("OM: " + Thread.currentThread().getName() + msg);
	}


	/** Return true if some work has been done */
	public boolean runOnce() throws IOException, ClassNotFoundException, InterruptedException{
		return processRouterMessages()
            || processTraderMessages();
	}

    void summary(){

        System.gc();
        Runtime rt = Runtime.getRuntime();
        long memory = (rt.totalMemory() - rt.freeMemory()) / 1024 ;
        totalMemoryUsage += memory;
        memoryCount ++;
        if(orders.size()> 0){
            long now = System.currentTimeMillis();
            long diffOrders = orders.size() - lastOrderSize;
            lastOrderSize = orders.size();
            if (now > startingTime){
                averageTimePerOrder = (diffOrders/(now-startingTime));
                startingTime = now;
            }
            System.out.println(averageTimePerOrder + " average per second" );
        }
        long averageMemoryUsage = totalMemoryUsage / memoryCount;
        System.out.println(averageMemoryUsage + " average KB");
        log.warn("    Memory: " + memory + " KB ");


        log.warn("    Orders: " + orders.size());
    }

	public void run(){
        // Print the summary from time to time
        ScheduledPrint sp = new ScheduledPrint(10, this);

		try {
			processClientMessages();
		}
		catch (ClassNotFoundException e){
			e.printStackTrace();
		}
		catch (IOException e){
			e.printStackTrace();
		}

		while(true){

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

			try {
				TimeUnit.MILLISECONDS.sleep(1); // Don't use 100% CPU
			} catch (InterruptedException e) {
			}

            sp.run();
		}
	}

	// Read Incoming Messages
	// ------------------------------------------------------------------------

	public void processClientMessages() throws IOException, ClassNotFoundException {
	    int clientId = 0;
		Socket client;

		for (clientId = 0; clientId < this.clients.length; clientId++) {
			client = this.clients[clientId];
			new Thread(new ClientThread(clientId, client, this)).start();
			System.out.println("client ID : "+clientId);
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

				print(" calling " + method);

				switch (method) {
					case ANSBestPrice:
						int OrderId = is.readInt();
						int SliceId = is.readInt();
						Order slice = orders.get(OrderId).slices.get(SliceId);
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
		if (this.trader.getInputStream().available() <= 0)
			return false;

		while(this.trader.getInputStream().available() > 0) {
			ObjectInputStream is = new ObjectInputStream(this.trader.getInputStream());
			TradeScreen.MessageKind method = (TradeScreen.MessageKind) is.readObject();

			print(" calling " + method);

			switch (method) {
                case ANSAcceptOrder:
					acceptOrder(is.readInt());
					break;
                case ANSSliceOrder:
					sliceOrder(is.readInt(), is.readInt());
			}
		}
		return true;
	}


	// Actions
	// ----------------------------------------------------------------
	protected synchronized void newOrder(int clientId, int clientOrderId, NewOrderSingle nos) throws IOException {
		orders.put(id, new Order(clientId, clientOrderId, nos.instrument, nos.size));

		ObjectOutputStream os = new ObjectOutputStream(clients[clientId].getOutputStream());
			os.writeObject("11=" + clientOrderId + ";35=A;39=A;");
			os.flush();

		sendOrderToTrader(id, orders.get(id), TradeScreen.MessageKind.REQNewOrder);
		id++;
	}

	private synchronized void sendOrderToTrader(int id, Order o, Object method) throws IOException {
		ObjectOutputStream ost = new ObjectOutputStream(trader.getOutputStream());

			ost.writeObject(method);
			ost.writeInt(id);
			ost.writeObject(o);
			ost.flush();
	}

	public synchronized void acceptOrder(int id) throws IOException {
		Order o = orders.get(id);
		if (o.OrdStatus != 'A') { // Pending New
			print("error accepting order that has already been accepted");
			return;
		}
		o.OrdStatus = '0'; // New

		ObjectOutputStream os = new ObjectOutputStream(clients[o.clientid].getOutputStream());
			os.writeObject("11=" + o.clientOrderID + ";35=A;39=0");
			os.flush();

		price(id, o);
	}

	public void sliceOrder(int id, int sliceSize) throws IOException {
        Order order = orders.get(id);
	    internalCross.sliceOrder(order, sliceSize);

	    /*Order o = orders.get(id);
		if (sliceSize > o.sizeRemaining() - o.sliceSizes()) {
			print("error sliceSize is bigger than remaining size to be filled on the order");
			return;
		}

		int sliceId = o.newSlice(sliceSize);
		Order slice = o.slices.get(sliceId);

		internalCross(id, slice);

		int sizeRemaining = o.slices.get(sliceId).sizeRemaining();
		if (sizeRemaining > 0) {
			askBestPri*=ce(id, sliceId, sizeRemaining, slice);
		}*/
	}

	private synchronized void internalCross(int id, Order o) throws IOException {
		internalCross.internalCross(o.instrument);


	    /* for (Map.Entry<Integer, Order> entry : orders.entrySet()) {
			if (entry.getKey() == id)
				continue;

			Order matchingOrder = entry.getValue();

			if (!(matchingOrder.instrument.equals(o.instrument)
					&& matchingOrder.initialMarketPrice == o.initialMarketPrice))
				continue;

			int sizeBefore = o.sizeRemaining();

			o.cross(matchingOrder);

			if (sizeBefore != o.sizeRemaining()) {
				sendOrderToTrader(id, o, TradeScreen.MessageKind.REQCross);
			}
		} */
	}

	private void newFill(int id, int sliceId, int size, double price) throws IOException {
		Order o = orders.get(id);
		o.slices.get(sliceId).createFill(size, price);

		if (o.sizeRemaining() == 0) {
			Database.write(o);
		}

		// FIXME the trader does not do anything interesting
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
	private void sendCancel(Order order, Router orderRouter) {
		// orderRouter.sendCancel(order);
		// order.orderRouter.writeObject(order);
	}

	// TODO
	private void cancelOrder() {

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

	// Connection
	// ----------------------------------------------------------------
	private Socket connect(InetSocketAddress location) throws InterruptedException {
		int tryCounter = 0;
		while (tryCounter < 600) {
			try {
				Socket s = new Socket(location.getHostName(), location.getPort());
				s.setKeepAlive(true);
				return s;
			} catch (IOException e) {
				Thread.sleep(1000);
				tryCounter++;
			}
		}
		print("Failed to connect to " + location.toString());
		return null;
	}


	public void connectToTrader(InetSocketAddress trader) throws InterruptedException{
		this.trader = connect(trader);
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

	// Init Script
	// ----------------------------------------------------------------
	public void initLog(){
		//BasicConfigurator.configure();
		log = LogManager.getLogger("Debug");
		log.setLevel(Level.WARN);
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


class ClientThread implements Runnable{
	private int clientId;
	private Socket client;
	private  OrderManager oM;

	ClientThread(int clientId, Socket client, OrderManager oM){
		this.clientId = clientId;
		this.client = client;
		this.oM = oM;
	}

	@Override
	public void run() {
		while (true){
			try {

				ObjectInputStream is = new ObjectInputStream(client.getInputStream());
				Client.MessageKind method = (Client.MessageKind) is.readObject();

				oM.print(" calling " + method);

				switch (method) {
					case ANSNewOrder:
						oM.newOrder(clientId, is.readInt(), (NewOrderSingle) is.readObject());
						break;
				}

			}

			catch(IOException e){
				e.printStackTrace();
			}
			catch (ClassNotFoundException e) {
				e.printStackTrace();
			}
		}
	}
}