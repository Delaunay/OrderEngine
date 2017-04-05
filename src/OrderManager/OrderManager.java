package OrderManager;

import Database.Database;
import LiveMarketData.LiveMarketData;
import OrderClient.NewOrderSingle;
import OrderManager.ClientThread.PendingNewOrder;
import OrderRouter.Router;
import TradeScreen.TradeScreen;
import Utility.HelperObject;
import com.sun.management.OperatingSystemMXBean;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.lang.management.ManagementFactory;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.channels.ServerSocketChannel;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;


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
    private ConcurrentHashMap<Integer, PendingOrder> orders = new ConcurrentHashMap<>();
    private ConcurrentLinkedQueue<PendingNewOrder>   pending_new_orders = new ConcurrentLinkedQueue<>();

    private static LiveMarketData 	liveMarketData;
	private int 					id          = 0;
	private int                     next_trader = 0;
	private Socket[]	 		    traders;
	private Socket[] 				orderRouters;
	private Socket[] 		     	clients;
    private ClientThread[]          clientWorkers;

	long totalMemoryUsage    = 0;
	long memoryCount         = 0;
	long startingTime        = System.currentTimeMillis();
	long lastTime            = System.currentTimeMillis();
	long averageTimePerOrder = 0;
	long lastOrderSize       = 0;
	long numOrderPerSecond   = 0;
	double averageCPU        = 0;

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

	public synchronized Socket getTrader(){
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
        memoryCount += 1;

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

    void addNewOrder(PendingNewOrder nos){
        pending_new_orders.add(nos);
    }

    public void runPendingNewOrders(){
        // send message
        int k = 0;
        int size = pending_new_orders.size();
        PendingNewOrder pno = pending_new_orders.poll();

        debug("PendingOrders: " + size);

        // avoid sending too much in one shot so Trader's buffer dont get fill
        while(pno != null && k < 50){
            try {
                this.newOrder(pno.client_id, pno.client_order_id, pno.new_order);
            } catch (IOException e){
                error("Could not send message to client");
            }
            k += 1;
            pno = pending_new_orders.poll();
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

            if (pending_new_orders.size() > 0)
                runPendingNewOrders();

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
        clientWorkers = new ClientThread[clients.length];

	    for (int clientId = 0; clientId < this.clients.length; clientId++) {
			Socket client = this.clients[clientId];

			clientWorkers[clientId] = new ClientThread(clientId, client, this);
			Thread t = new Thread(clientWorkers[clientId]);
            t.setName("ClientThread " + clientId);
            t.start();

			debug("client ID : " + clientId);
		}
		clients = null; // clients socket should never been access by Order Manager ever again
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
						//debug("====> order_id " + OrderId);
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

    void sendMessageToClient(int client_id, String message){
	    /*
		ObjectOutputStream os = new ObjectOutputStream(client.getOutputStream());
			os.writeObject(message);
			os.flush(); //*/

        ClientThread client = clientWorkers[client_id];
        client.addMessage(message);
    }



	// Actions
	// ----------------------------------------------------------------
	protected void newOrder(int clientId, int clientOrderId, NewOrderSingle nos) throws IOException {

		orders.put(id, new PendingOrder(new Order(clientId, id, nos.instrument, nos.size, clientOrderId)));

		sendMessageToClient(clientId, "11=" + clientOrderId + ";35=A;39=A;");
		sendOrderToTrader  (id, orders.get(id).order, TradeScreen.MessageKind.REQNewOrder);
		id++;
	}

	private void sendOrderToTrader(int id, Order o, Object method) throws IOException {
		ObjectOutputStream ost = new ObjectOutputStream(getTrader().getOutputStream());

			ost.writeObject(method);
			ost.writeInt(id);
			ost.writeObject(o);
			ost.flush();
	}

	public void acceptOrder(int id) throws IOException {
		Order o = orders.get(id).order;
		if (o.OrdStatus != 'A') { // Pending New
			error("error accepting order that has already been accepted");
			return;
		}
		o.OrdStatus = '0'; // New

        sendMessageToClient(o.clientid, "11=" + o.client_order_id + ";35=A;39=0");
		price(id, o);
	}

	public void sliceOrder(int id, int sliceSize) throws IOException {
		Order o = orders.get(id).order;
		if (sliceSize > o.sizeRemaining() - o.sliceSizes()) {
			sliceSize = o.sizeRemaining() - o.sliceSizes();
		    //error("error sliceSize is bigger than remaining size to be filled on the order");
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
        PendingOrder po = orders.get(id);

	    Order o = po.order;
		o.slices.get(sliceId).createFill(size, price);

		po.slice_num -= 1;
		po.size_remain -= size;
        String message = "11=" + o.client_order_id + ";38=" + size + ";44=" + price;

		if (o.sizeRemaining() == 0) {
            message = message + ";39=2";
			Database.write(o);
			debug("------------DONE--------------");
		} else {
            message = message + ";39=1";
        }

		sendOrderToTrader(id, o, TradeScreen.MessageKind.REQFill);
        sendMessageToClient(o.clientid, message);
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
    /*
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
    } */

	// Connection
    // ------------------------------------------------------------------------
	private Socket connect(InetSocketAddress location) throws InterruptedException {
		int tryCounter = 0;
		while (tryCounter < 600) {
			try {
				Socket s = new Socket(location.getHostName(), location.getPort());
				s.setSendBufferSize(socket_buffer);
				s.setReceiveBufferSize(socket_buffer);
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
            info("Connected to Trader: " + i);
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

