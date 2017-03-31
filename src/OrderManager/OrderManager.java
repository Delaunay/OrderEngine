package OrderManager;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.util.HashMap;
import java.util.Map;

import Database.Database;
import LiveMarketData.LiveMarketData;
import OrderClient.NewOrderSingle;
import OrderRouter.Router;
import TradeScreen.TradeScreen;


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

	private Socket connect(InetSocketAddress location) throws InterruptedException {
		boolean connected = false;
		int tryCounter = 0;
		while (!connected && tryCounter < 600) {
			try {
				Socket s = new Socket(location.getHostName(), location.getPort());
				s.setKeepAlive(true);
				return s;
			} catch (IOException e) {
				Thread.sleep(1000);
				tryCounter++;
			}
		}
		System.out.println("OM: Failed to connect to " + location.toString());
		return null;
	}

	public OrderManager(InetSocketAddress[] orderRouters, InetSocketAddress[] clients, InetSocketAddress trader,
			LiveMarketData liveData) throws InterruptedException {

		liveMarketData = liveData;

		connectToTrader(trader);

		connectToRouters(orderRouters);

		connectToClients(clients);

		try {
			run();
		} catch (IOException e) {
			e.printStackTrace();
		} catch (ClassNotFoundException e) {
			e.printStackTrace();
		} catch (InterruptedException e) {
			e.printStackTrace();
		}
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

	public void run() throws IOException, ClassNotFoundException, InterruptedException{
		while(true){
			// ----------------------------------------------------------------
			System.out.print("OM:  Orders: " + orders.size() + "\n");
			//System.gc();
            Runtime rt = Runtime.getRuntime();
            long memory = (rt.totalMemory() - rt.freeMemory()) / 1024 / 1024;
            System.out.print("OM:  Memory: " + memory + " MB \n");

			// ----------------------------------------------------------------
			getClientOrders();
			getRouterAnswer();
			receiveTradeActions();
		}
	}

	public void getClientOrders() throws IOException, ClassNotFoundException {
		int clientId = 0;
		Socket client;

		for (clientId = 0; clientId < this.clients.length; clientId++) {
			client = this.clients[clientId];

			if (0 < client.getInputStream().available()) {
				ObjectInputStream is = new ObjectInputStream(client.getInputStream());
				String method = (String) is.readObject();

				System.out.println("OM: " + Thread.currentThread().getName() + " calling " + method);

				switch (method) {
					case "newOrderSingle":
						newOrder(clientId, is.readInt(), (NewOrderSingle) is.readObject());
						break;
				}
			}
		}
	}

	public void getRouterAnswer() throws IOException, ClassNotFoundException{
		int routerId;
		Socket router;

		for (routerId = 0; routerId < this.orderRouters.length; routerId++) {
			router = this.orderRouters[routerId];
			if (0 < router.getInputStream().available()) {
				ObjectInputStream is = new ObjectInputStream(router.getInputStream());
				String method = (String) is.readObject();
				System.out.println("OM: " + Thread.currentThread().getName() + " calling " + method);
				switch (method) {
					case "bestPrice":
						int OrderId = is.readInt();
						int SliceId = is.readInt();
						Order slice = orders.get(OrderId).slices.get(SliceId);
						slice.bestPrices[routerId] = is.readDouble();
						slice.bestPriceCount += 1;
						if (slice.bestPriceCount == slice.bestPrices.length)
							reallyRouteOrder(SliceId, slice);
						break;
					case "newFill":
						newFill(is.readInt(), is.readInt(), is.readInt(), is.readDouble());
						break;
				}
			}
		}
	}

	public void receiveTradeActions() throws IOException, ClassNotFoundException{
		if (!(this.trader.getInputStream().available() > 0))
			return;

		ObjectInputStream is = new ObjectInputStream(this.trader.getInputStream());
		String method = (String) is.readObject();

		System.out.println("OM: " + Thread.currentThread().getName() + " calling " + method);
		switch (method) {
			case "acceptOrder":
				acceptOrder(is.readInt());
				break;
			case "sliceOrder":
				sliceOrder(is.readInt(), is.readInt());
		}
	}



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
			System.out.println("OM: error accepting order that has already been accepted");
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
			System.out.println("OM: error sliceSize is bigger than remaining size to be filled on the order");
			return;
		}
		int sliceId = o.newSlice(sliceSize);
		Order slice = o.slices.get(sliceId);

		internalCross(id, slice);

		int sizeRemaining = o.slices.get(sliceId).sizeRemaining();
		if (sizeRemaining > 0) {
			routeOrder(id, sliceId, sizeRemaining, slice);
		}
	}

	private void internalCross(int id, Order o) throws IOException {
		for (Map.Entry<Integer, Order> entry : orders.entrySet()) {
			if (entry.getKey().intValue() == id)
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

	private void cancelOrder() {

	}

	private void newFill(int id, int sliceId, int size, double price) throws IOException {
		Order o = orders.get(id);
		o.slices.get(sliceId).createFill(size, price);
		if (o.sizeRemaining() == 0) {
			Database.write(o);
		}
		sendOrderToTrader(id, o, TradeScreen.api.fill);
	}

	private void routeOrder(int id, int sliceId, int size, Order order) throws IOException {
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

	private void reallyRouteOrder(int sliceId, Order o) throws IOException {
		// TODO this assumes we are buying rather than selling
		int minIndex = 0;
		double min = o.bestPrices[0];
		for (int i = 1; i < o.bestPrices.length; i++) {
			if (min > o.bestPrices[i]) {
				minIndex = i;
				min = o.bestPrices[i];
			}
		}

		ObjectOutputStream os = new ObjectOutputStream(orderRouters[minIndex].getOutputStream());
			os.writeObject(Router.api.routeOrder);
			os.writeInt(o.id);
			os.writeInt(sliceId);
			os.writeInt(o.sizeRemaining());
			os.writeObject(o.instrument);
			os.flush();
	}

	private void sendCancel(Order order, Router orderRouter) {
		// orderRouter.sendCancel(order);
		// order.orderRouter.writeObject(order);
	}

	private void price(int id, Order o) throws IOException {
		liveMarketData.setPrice(o);
		sendOrderToTrader(id, o, TradeScreen.api.price);
	}
}