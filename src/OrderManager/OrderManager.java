package OrderManager;

import Actor.Actor;
import Actor.Message;
import Database.Database;
import LiveMarketData.LiveMarketData;
import OrderManager.ClientThread.PendingNewOrder;
import OrderRouter.Router;
import Ref.Instrument;
import Utility.Connection.ConnectionType;
import Utility.HelperObject;
import com.sun.management.OperatingSystemMXBean;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.lang.management.ManagementFactory;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedList;
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
public class OrderManager extends Actor{
	// Orders being processed
    private ConcurrentHashMap<Integer, PendingOrder>
			orders = new ConcurrentHashMap<>();

    // New order being received
    private ConcurrentLinkedQueue<PendingNewOrder>
			pending_new_orders = new ConcurrentLinkedQueue<>();

    private HashMap<Instrument, LinkedList<Slice>> slices = new HashMap<>(10);

    private static LiveMarketData 	liveMarketData;
    private int                     print_delta = 100;
	private int 					id          = 0;
	private int                     next_trader = 0;
	private ArrayList<Socket> 		traders = new ArrayList<>(100);
	private ArrayList<Socket>		routers = new ArrayList<>(100);
    private ArrayList<ClientThread> client_threads = new ArrayList<>(100);
	private ServerSocketChannel 	serverSocket;
	private Statistics				stats = new Statistics();

    public static class Slice{
        public int size;
        public double price;
        public PendingOrder parent;

        public Slice(PendingOrder p, int s, double px){
            parent = p;
            price = px;
            size = s;
        }
    }

    public OrderManager(int port, LiveMarketData liveData)
	{
		try {
			serverSocket = ServerSocketChannel.open();
			serverSocket.bind(new InetSocketAddress(port));
			serverSocket.configureBlocking(false);
		} catch (IOException e){
			error("Could not bind socket to port");
			System.exit(1);
		}

        initLog(this.getClass().getName());
        liveMarketData = liveData ;

        // We probably have a fixed number of markets
        // connectToRouters(routers);
    }


	public Socket getTrader(){
	    Socket t = traders.get(next_trader);
	    next_trader += 1;
	    next_trader %= traders.size();
	    return t;
    }

	/** Return true if some work has been done */
	public boolean runOnce() throws IOException, ClassNotFoundException, InterruptedException{
		boolean rm = processRouterMessages();
		boolean tm = processTraderMessages();
	    return rm || tm;
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
        ScheduledPrint sp = new ScheduledPrint(print_delta, this);

		while(true){

		    try {
                acceptConnection();
            } catch (IOException e){
		    	error("Failed to connect to a client");
            }

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

	public boolean processRouterMessages() throws IOException, ClassNotFoundException{
		Socket router;
		boolean work_was_done = false;

		for (int router_id = 0; router_id < routers.size(); router_id++) {
			router = routers.get(router_id);

			while (router.getInputStream().available() > 0)
            {
				Message m = readMessage(router);

				debug(" calling " + m.op);

				switch (m.op) {
					case ANSBestPrice:
						bestPrice(router_id, (Message.BestPrice) m);
						break;

					case ANSNewFill:
						newFill((Message.NewFill) m);
						break;

                    default:
                        error("unsupported operation");
                        break;
				}
                work_was_done = true;
			}
		}
		return work_was_done;
	}

	public boolean processTraderMessages() throws IOException, ClassNotFoundException{
        boolean work_was_done = false;

        for (int trader_id = 0; trader_id < traders.size(); trader_id++) {
            Socket trader = traders.get(trader_id);
            
            if(trader.getInputStream().available() > 0)
            {
				Message m = readMessage(trader);

                info(" calling " + m.op);

                switch (m.op) {
                    case ANSAcceptOrder:
                    	acceptOrder((Message.TraderAcceptOrder) m);
                        break;
                    case ANSSliceOrder:
                        sliceOrder((Message.TraderSliceOrder) m);
                    default:
                        error("unsupported operation");
                        break;
                }
                work_was_done = true;
            }
	    }
		return work_was_done;
	}

    void sendMessageToClient(int client_id, String message){
        ClientThread client = client_threads.get(client_id);
        client.addMessage(new Message.FIXMessage(message));
    }
    
	// Actions
	// ----------------------------------------------------------------


	protected void newOrder(int clientId, int clientOrderId, Message.NewOrderSingle nos) throws IOException {
		PendingOrder po =  new PendingOrder(new Order(clientId, id, nos.instrument, nos.size, clientOrderId));
        orders.put(id, po);
		sendMessageToClient(clientId, "11=" + clientOrderId + ";35=A;39=A;");
		sendMessage(getTrader(), new Message.TraderNewOrder(id, po.order));
		id++;
	}


	public void acceptOrder(Message.TraderAcceptOrder m) throws IOException {
		Order o = orders.get(m.order_id).order;

		if (o.OrdStatus != 'A') { // Pending New
			error("error accepting order that has already been accepted");
			return;
		}
		o.OrdStatus = '0'; // New

        sendMessageToClient(o.clientid, "11=" + o.client_order_id + ";35=A;39=0");
        info("GEtting the price for the order");
        price(m.order_id, o);
	}

	public void sliceOrder(Message.TraderSliceOrder m) throws IOException {
		PendingOrder po = orders.get(id);
		LinkedList<Slice> slice = slices.get(po.order.instrument);

		if (slice == null) {
            slice = new LinkedList<Slice>();
            slices.put(po.order.instrument, slice);
        }

		int order_size = po.size_remain;
		int slice_size = m.order_id;

		po.slice_num = 0;

		while(order_size > 0){

			if (order_size < slice_size)
				slice_size = order_size;

			slice.add(new Slice(po, slice_size, po.order.initialMarketPrice));
			order_size -= slice_size;
			po.slice_num += 1;
		}

		info("Order size: " + po.size_remain + " Slices: " + po.slice_num + " SliceSize:" + m.slice_size);

		/*
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
		}*/
	}

	private void internalCross(int id, Order o) throws IOException {
        PendingOrder po = orders.get(id);
        LinkedList<Slice> slice = slices.get(po.order.instrument);

        if (slice == null)
            return;

        if (po.slice_num == slice.size()){

        }


        /*
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
				sendMessage(getTrader(), new Message.Cross(id, o));
			}
		} */
	}

	Order getNextSlice(Order order, int slice_id){
        ArrayList<Order> slices = order.slices;

        if (slice_id < slices.size()){
            return slices.get(slice_id + 1);
        }
        return null;
    }

	// Incoming Router Messages
	// ------------------------------------------------------------------------
	private void newFill(Message.NewFill m) throws IOException {
        PendingOrder po = orders.get(m.order_id);

	    Order o = po.order;
		o.slices.get(m.slice_id).createFill(m.size, m.price);

		po.slice_num -= 1;
		po.size_remain -= m.size;

        String message = "11=" + o.client_order_id + ";38=" + m.size + ";44=" + m.price;

		if (o.sizeRemaining() == 0) {
            message = message + ";39=2";
			Database.write(o);
			debug("------------DONE--------------");
		} else {
            message = message + ";39=1";
        }
        // We should execute next slice
        Order next = getNextSlice(o, m.slice_id);


		sendMessage(getTrader(), new Message.TraderFill(id, o));
		sendMessageToClient(o.clientid, message);
	}

	void bestPrice(int router_id, Message.BestPrice m) throws IOException{
		Order slice = orders.get(m.order_id).order.slices.get(m.slice_id);

		slice.bestPrices[router_id] = m.price;
		slice.bestPriceCount += 1;

		if (slice.bestPriceCount == slice.bestPrices.length)
			routeOrder(m.slice_id, slice);
	}

	// Outcoming Router Messages
	// ------------------------------------------------------------------------

	/** Ask for the best price to each router
	 * 	@param id		Order id
	 * 	@param sliceId  Slice id
	 * 	@param size		size remaining to full the current slice
	 * 	@param order	Order object
	 * 	*/
	private void askBestPrice(int id, int sliceId, int size, Order order) throws IOException {
		for (Socket r : routers) {
			sendMessage(r, new Message.PriceAtSize(id, sliceId, order.instrument, size));
		}

		// need to wait for these prices to come back before routing
		order.bestPrices = new double[routers.size()];
		order.bestPriceCount = 0;
	}

	/** Buy/Sell the instrument at the min/max price possible */
	private void routeOrder(int sliceId, Order o) throws IOException {
		// if o.size < 0 => We are selling
		int size = o.sizeRemaining();
		int index = size > 0 ? getBestBuyPrice(o) : getBestSellPrice(o);

		sendMessage(routers.get(index), new Message.RouteOrder(o.id, sliceId, o.instrument, size));
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
		sendMessage(getTrader(), new Message.TraderPrice(id, o));
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
	public void acceptConnection() throws IOException{
		SocketChannel channel = serverSocket.accept();

		if (channel == null)
			return;

		Socket con = channel.socket();
		con.setSendBufferSize(socket_buffer);
		con.setReceiveBufferSize(socket_buffer);
		//con.setKeepAlive(true);

		try {
			ObjectInputStream is = new ObjectInputStream(con.getInputStream());
			ConnectionType type = (ConnectionType) is.readObject();
			switch (type) {
				case TraderConnection:
					addTraderConnection(con);
					return;
				case ClientConnection:
					addClientConnection(con);
					return;
				case RouterConnection:
					addRouterConnection(con);
					return;
			}
		} catch (ClassNotFoundException e){
			error("Connection Type unknown");
		}

	}

	void addTraderConnection(Socket trader){
		int size = traders.size();
		info("Trader (" + size + ") has connected");
		traders.add(trader);
	}

	void addRouterConnection(Socket router){
		int size = routers.size();
		info("Router (" + size + ") has connected");
		routers.add(router);
	}

	void addClientConnection(Socket client){
		int size = client_threads.size();
		info("Client (" + size + ") has connected");
		spawnClient(size, client);
	}

	public void spawnClient(int client_id, Socket client) {
		ClientThread ct = new ClientThread(client_id, client, this);
		client_threads.add(ct);

		Thread t = new Thread(ct);
		t.setName("ClientThread " + client_id);
		t.start();
	}

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

	public void connectToRouters(InetSocketAddress[] orderRouters) throws InterruptedException{
		for (InetSocketAddress location : orderRouters) {
			routers.add(connect(location));
		}
	}

	//		Runtime statistics about OrderManager
	// ------------------------------------------------------------------------
	static class Statistics extends HelperObject{
		long startingTime        = System.currentTimeMillis();
		long lastTime            = System.currentTimeMillis();
		long totalMemoryUsage    = 0;
		long memoryCount         = 0;
		long averageTimePerOrder = 0;
		long lastOrderSize       = 0;
		long numOrderPerSecond   = 0;
		double averageCPU        = 0;

		Statistics(){
			initLog(this.getClass().getName());
		}

		void summary(int order_size){
			OperatingSystemMXBean cpuMeasure =
					(com.sun.management.OperatingSystemMXBean)
							ManagementFactory.getOperatingSystemMXBean();
			System.gc();
			Runtime rt = Runtime.getRuntime();
			long memory = (rt.totalMemory() - rt.freeMemory()) / 1024 ;

			totalMemoryUsage += memory;
			memoryCount += 1;

			if(order_size > 0){
				long now = System.currentTimeMillis();
				long diffOrders = order_size - lastOrderSize;

				lastOrderSize = order_size;
				if (now > startingTime){
					numOrderPerSecond = (diffOrders  * 1000 /(now - lastTime));
					averageTimePerOrder = order_size * 1000 / (now - startingTime);
					lastTime = now;
				}
			}

			long averageMemoryUsage = totalMemoryUsage / memoryCount;
			double cpu = cpuMeasure.getProcessCpuLoad();
			averageCPU += cpu;

			info("       Order: " + order_size + " \t" + "(AVG: " + averageTimePerOrder + ")");
			info("      Memory: " + memory + " KB (AVG: " + averageMemoryUsage + " KB)");
			info("    CPU load: " + trunc(cpu * 100) + " \t(AVG: " + trunc(averageCPU * 100 / memoryCount) + "%)");

			if(memory > 1048576 ){
				System.exit(0);
			}
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
            manager.stats.summary(manager.orders.size());
        }
    }
}

