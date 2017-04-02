package OrderManager;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import org.apache.log4j.LogManager;

import Database.Database;
import LiveMarketData.LiveMarketData;
import OrderClient.NewOrderSingle;
import OrderRouter.Router;
import TradeScreen.TradeScreen;
import org.apache.log4j.Logger;

import Utility.Util;

import static org.apache.log4j.Level.DEBUG;


/**
 * 			Order Manager listens to
 * 				- Clients
 * 				- TradingEngine (Trader)
 * 				- Routers       (Markets)
 *
 *		Clients -> newOrder -> Trader
 *
 *		Router -> newFill -> Trader
 *			   -> BestPrice -> ReallyRouteOrder -> Router
 *
 *		Trader -> sliceOrder -> Cross -> RouteOrder
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


	public
	OrderManager(InetSocketAddress[] orderRouters,
				 InetSocketAddress[] clients,
				 InetSocketAddress trader,
				 LiveMarketData liveData) throws InterruptedException
	{
		liveMarketData = liveData;

		initLog();

		connectToTrader(trader);
		connectToRouters(orderRouters);
		connectToClients(clients);

		run();
	}

	void print(String msg){
		System.out.println("OM: " + Thread.currentThread().getName() + msg);
	}


	public void runOnce() throws IOException, ClassNotFoundException, InterruptedException{
		processClientMessages();
		processRouterMessages();
		processTraderMessages();
	}

	void summary(){
        /*/System.gc();
        Runtime rt = Runtime.getRuntime();
        long memory = (rt.totalMemory() - rt.freeMemory()) / 1024 / 1024;
        print("    Memory: " + memory + " MB ");
        /*/
        print("    Orders: " + orders.size());
    }

	public void run(){
        // Print the summary from time to time
        ScheduledPrint sp = new ScheduledPrint(1000, this);

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

			if (0 < client.getInputStream().available()) {
				ObjectInputStream is = new ObjectInputStream(client.getInputStream());
				String method = (String) is.readObject();

				print(" calling " + method);

				switch (method) {
					case "newOrderSingle":
						newOrder(clientId, is.readInt(), (NewOrderSingle) is.readObject());
						break;
				}
			}
		}
	}

	public void processRouterMessages() throws IOException, ClassNotFoundException{
		int routerId;
		Socket router;

		for (routerId = 0; routerId < this.orderRouters.length; routerId++) {
			router = this.orderRouters[routerId];

			if (0 < router.getInputStream().available()) {

				ObjectInputStream is = new ObjectInputStream(router.getInputStream());
				String method = (String) is.readObject();

				print(" calling " + method);

				switch (method) {
					case "bestPrice":
						int OrderId = is.readInt();
						int SliceId = is.readInt();
						Order slice = orders.get(OrderId).slices.get(SliceId);
						slice.bestPrices[routerId] = is.readDouble();
						slice.bestPriceCount += 1;
						if (slice.bestPriceCount == slice.bestPrices.length)
							routeOrder(SliceId, slice);
						break;

					case "newFill":
						newFill(is.readInt(), is.readInt(), is.readInt(), is.readDouble());
						break;
				}
			}
		}
	}

	public void processTraderMessages() throws IOException, ClassNotFoundException{
		if (!(this.trader.getInputStream().available() > 0))
			return;

		ObjectInputStream is = new ObjectInputStream(this.trader.getInputStream());
		String method = (String) is.readObject();

		print(" calling " + method);

		switch (method) {
			case "acceptOrder":
				acceptOrder(is.readInt());
				break;
			case "sliceOrder":
				sliceOrder(is.readInt(), is.readInt());
		}
	}


	// Actions
	// ----------------------------------------------------------------
	private void newOrder(int clientId, int clientOrderId, NewOrderSingle nos) throws IOException {
		orders.put(id, new Order(clientId, clientOrderId, nos.instrument, nos.size));

		ObjectOutputStream os = new ObjectOutputStream(clients[clientId].getOutputStream());
			os.writeObject("11=" + clientOrderId + ";35=A;39=A;");
			os.flush();

		sendOrderToTrader(id, orders.get(id), TradeScreen.api.newOrder);
		id++;
	}

	private void sendOrderToTrader(int id, Order o, Object method) throws IOException {
		ObjectOutputStream ost = new ObjectOutputStream(trader.getOutputStream());
			ost.writeObject(method);
			ost.writeInt(id);
			ost.writeObject(o);
			ost.flush();
	}

	public void acceptOrder(int id) throws IOException {
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
		Order o = orders.get(id);
		if (sliceSize > o.sizeRemaining() - o.sliceSizes()) {
			print("error sliceSize is bigger than remaining size to be filled on the order");
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

	private void internalCross(int id, Order o) throws IOException {
		for (Map.Entry<Integer, Order> entry : orders.entrySet()) {
			if (entry.getKey() == id)
				continue;

			Order matchingOrder = entry.getValue();

			if (!(matchingOrder.instrument.equals(o.instrument)
					&& matchingOrder.initialMarketPrice == o.initialMarketPrice))
				continue;

			int sizeBefore = o.sizeRemaining();

			o.cross(matchingOrder);

			if (sizeBefore != o.sizeRemaining()) {
				sendOrderToTrader(id, o, TradeScreen.api.cross);
			}
		}
	}

	private void newFill(int id, int sliceId, int size, double price) throws IOException {
		Order o = orders.get(id);
		o.slices.get(sliceId).createFill(size, price);

		if (o.sizeRemaining() == 0) {
			Database.write(o);
		}

		sendOrderToTrader(id, o, TradeScreen.api.fill);
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
				os.writeObject(Router.api.priceAtSize);
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
			os.writeObject(Router.api.routeOrder);
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
		sendOrderToTrader(id, o, TradeScreen.api.price);
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
		log = LogManager.getLogger("Debug");
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