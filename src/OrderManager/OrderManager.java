package OrderManager;

import Actor.Actor;
import Actor.Message;
import Database.Database;
import LiveMarketData.LiveMarketData;
import OrderManager.ClientThread.PendingNewOrder;
import OrderRouter.Router;
import Ref.Instrument;
import Utility.Connection.ConnectionType;
import Utility.Util;
import com.sun.management.OperatingSystemMXBean;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.lang.management.ManagementFactory;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;


/**
 *  <p>Order Manager listens to:</p>
 * 	<ul>
 * 		<li> Clients						</li>
 * 		<li> TradingEngine (SampleTrader)	</li>
 * 		<li> Routers       (Markets)		</li>
 * 	</ul>
 * 	
 * 	<pre>
 * 	{@code
 *		Clients -> newOrder -> SampleTrader
 *
 *		Router -> newFill -> SampleTrader
 *			   -> BestPrice -> ReallyRouteOrder -> Router
 *
 *		SampleTrader -> sliceOrder -> Cross -> RouteOrder
 *			         -> AcceptOrder -> Client}
 * 	</pre>
 * 	
 */
public class OrderManager extends Actor{
	// Orders being processed
    private ConcurrentHashMap<Integer, PendingOrder>
			orders = new ConcurrentHashMap<>();

    // New order being received
    private ConcurrentLinkedQueue<PendingNewOrder>
			pending_new_orders = new ConcurrentLinkedQueue<>();

    private HashMap<Instrument, LinkedList<Slice>> sliceTable = new HashMap<>(10);

    private LiveMarketData      			liveMarketData;
    private double                          eps         = 1e-6;
    private int                     		print_delta = 1000;
	private int 							id          = 0;
	private int                     		next_trader = 0;
	private ArrayList<Socket> 				traders = new ArrayList<>(100);
	private ArrayList<Socket>				routers = new ArrayList<>(100);
    private ArrayList<ClientThread> 		client_threads = new ArrayList<>(100);
	private ServerSocketChannel 			serverSocket;
	private Statistics						stats = new Statistics(this);

	private double                          processed_orders = 0;
	private double                          total_orders     = 0;

    void init(int port, LiveMarketData liveData){
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
    }

    public OrderManager(int port, LiveMarketData liveData)
    {
        init(port, liveData);
    }

    public OrderManager(int port, LiveMarketData liveData, InetSocketAddress[] traders)
    {
        init(port, liveData);
        try{
            connectToTraders(traders);
        } catch (InterruptedException e){}
    }

    public OrderManager(int port, LiveMarketData liveData, InetSocketAddress[] traders, InetSocketAddress[] routers)
    {
        init(port, liveData);
        try{
            connectToTraders(traders);
            connectToRouters(routers);
        } catch (InterruptedException e){}
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
            if (pending_new_orders.size() > 0)
                runPendingNewOrders();

		    try {
                acceptConnection();
            } catch (IOException e){
		    	error("Failed to connect to a client");
            }

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
                        error("Router: unsupported operation: " + m.op);
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

            //info(" " + trader);

            if(trader.getInputStream().available() > 0)
            {
				Message m = readMessage(trader);

                debug(" calling " + m.op);

                switch (m.op) {
                    case ANSAcceptOrder:
                    	acceptOrder((Message.TraderAcceptOrder) m);
                        break;
                    case ANSSliceOrder:
                        sliceOrder((Message.TraderSliceOrder) m);
                        break;
                    default:
                        error("Trader: unsupported operation: " + m.op);
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
		total_orders += 1;
	}

	public void acceptOrder(Message.TraderAcceptOrder m) throws IOException {
		Order o = orders.get(m.order_id).order;

		if (o.OrdStatus != 'A') { // Pending New
			error("error accepting order that has already been accepted");
			return;
		}
		o.OrdStatus = '0'; // New

        sendMessageToClient(o.clientid, "11=" + o.client_order_id + ";35=A;39=0");
        price(m.order_id, o);
	}

	public void sliceOrder(Message.TraderSliceOrder m) throws IOException {
		PendingOrder po = orders.get(m.order_id);
		LinkedList<Slice> slice = sliceTable.get(po.order.instrument);

		if (slice == null) {
            slice = new LinkedList<Slice>();
            sliceTable.put(po.order.instrument, slice);
        }

		int order_size = po.size_remain;
		int slice_size = m.slice_size;
        Slice cslice;

		po.slice_num = 0;

		while(order_size > 0){
			if (order_size < slice_size)
				slice_size = order_size;

			cslice = new Slice(po, slice_size, po.order.initialMarketPrice);
			slice.add(cslice);      // we need the slices by instrument for cross

			po.slices.add(cslice);  // Each Order keep track of their slices
            // askBestPrice(m.order_id, po.slice_num, po.slices.get(po.slice_num));

			order_size -= slice_size;
			po.slice_num += 1;
		}

		debug("Order size: " + po.size_remain + " Slices: " + po.slice_num + " SliceSize:" + m.slice_size);

		//internalCross(po.order.instrument);

        if (po.size_remain > 0) {
            int slice_id = po.slices.size() - po.slice_num;
            Slice to_order = po.slices.get(slice_id);
            askBestPrice(m.order_id, slice_id, to_order);
        }
	}

	private void internalCross(Instrument asset) {
        List<Slice> slices = sliceTable.get(asset);

        // n ^ 2
        for(int i = 0; i < slices.size(); ++i){
            Slice a = slices.get(i);
            for(int k = i; k < slices.size(); ++k){
                Slice b = slices.get(k);

                // same sign move one
                if (a.buy == b.buy)
                    continue;

                // Consume both slices Does arrayList gets invalidated ?
                if (Math.abs(a.price - b.price) <= eps){
                    slices.remove(i);
                    slices.remove(k);

                    if (a.size < b.size){
                        b.parent.size_remain -= b.size;
                        a.parent.size_remain -= b.size;
                        b.parent.slice_num   -= 1;
                        a.parent.slice_num   -= 1;

                        // Fully Filled
                        sendSliceFill(b.parent, b.size ,a.price);
                        sendSliceFill(a.parent, b.size, a.price);

                        info("Internal cross worked");
                    } else if (a.size - b.size > 0){
                        b.parent.size_remain -= b.size;
                        a.parent.size_remain -= b.size;
                        b.parent.slice_num   -= 1;
                        a.parent.slice_num   -= 0;

                        // Partial Fill
                        sendSliceFill(b.parent, b.size, a.price);
                        sendSliceFill(a.parent, b.size, a.price);

                        Slice nslice = new Slice(a.parent, a.size - b.size, a.price);

                        a.parent.slices.add(nslice);
                        slices.add(nslice);
                        info("Internal cross worked");
                    } else {
                        b.parent.size_remain -= a.size;
                        a.parent.size_remain -= a.size;
                        b.parent.slice_num   -= 0;
                        a.parent.slice_num   -= 1;

                        sendSliceFill(a.parent, a.size, a.price);
                        sendSliceFill(b.parent, a.size, a.price);

                        Slice nslice = new Slice(b.parent, b.size - a.size, b.price);

                        b.parent.slices.add(nslice);
                        slices.add(nslice);
                        info("Internal cross worked");
                    }
                }
            }
        }
	}

	void sendSliceFill(PendingOrder po, int size, double price){
        String message = "11=" + po.order.client_order_id + ";38=" + size + ";44=" + price;
        po.fills.add(new Fill(size, price));

        if (po.size_remain == 0) {
            message = message + ";39=2";
            Database.write(po.order);
        } else {
            message = message + ";39=1";
        }
        sendMessageToClient(po.order.clientid, message);
    }

	Slice getNextSlice(PendingOrder order, int slice_id){
        ArrayList<Slice> slices = order.slices;

        if (slice_id + 1 < slices.size()){
            return slices.get(slice_id + 1);
        }
        return null;
    }

	// Incoming Router Messages
	// ------------------------------------------------------------------------
	private void newFill(Message.NewFill m) throws IOException {
        PendingOrder po = orders.get(m.order_id);

        po.fills.add(new Fill(m.size, m.price));
		po.slice_num -= 1;
		po.size_remain -= m.size;
		po.cost += m.size * m.price;

        String message = "11=" + po.order.client_order_id + ";38=" + m.size + ";44=" + m.price;

		if (po.size_remain == 0) {
            message = message + ";39=2";
			Database.write(po.order);
			orders.remove(po);
            processed_orders += 1;
		} else {
            message = message + ";39=1";
        }

        // We should execute next slice
        Slice next = getNextSlice(po, m.slice_id);
		if (next != null){
            askBestPrice(m.order_id, m.slice_id + 1, next);
        }

		sendMessage(getTrader(), new Message.TraderFill(id, po.order));
		sendMessageToClient(po.order.clientid, message);
	}

	void bestPrice(int router_id, Message.BestPrice m) throws IOException{
		// Order slice = orders.get(m.order_id).order.slices.get(m.slice_id);

        PendingOrder po = orders.get(m.order_id);
        Slice slice = po.slices.get(m.slice_id);

		slice.best_prices[router_id] = m.price;
		slice.best_price_count += 1;

		if (slice.best_price_count  == slice.best_prices.length)
			routeOrder(m.slice_id, po);
	}

	// Outcoming Router Messages
	// ------------------------------------------------------------------------

	/** Ask for the best price to each router
	 * 	@param id		Order id
	 * 	@param sliceId  Slice id
	 * 	@param slice 	Order object
	 * 	*/
	private void askBestPrice(int id, int sliceId, Slice slice) throws IOException {
		for (Socket r : routers) {
			sendMessage(r, new Message.PriceAtSize(id, sliceId, slice.parent.order.instrument, slice.size));
		}

		// need to wait for these prices to come back before routing
		slice.best_prices = new double[routers.size()];
		slice.best_price_count = 0;
	}

	/** Buy/Sell the instrument at the min/max price possible */
	private void routeOrder(int slice_id, PendingOrder po) throws IOException {
		// if o.size < 0 => We are selling
		int size = po.size_remain;
		Slice slice = po.slices.get(slice_id);
		int index = size > 0 ? getBestBuyPrice(slice) : getBestSellPrice(slice);

		sendMessage(routers.get(index), new Message.RouteOrder(po.order.id, slice_id, po.order.instrument, size));
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
		//sendMessage(liveData, new Message.SetPrice(o));
        liveMarketData.setPrice(o);
		sendMessage(getTrader(), new Message.TraderPrice(id, o));
	}

	// Utilities
	// ----------------------------------------------------------------
	static int getBestBuyPrice(Slice slice){
		int minIndex = 0;
		double min = slice.best_prices[0];

		for (int i = 1; i < slice.best_price_count; i++) {
			if (min > slice.best_prices[i]) {
				minIndex = i;
				min = slice.best_prices[i];
			}
		}
		return minIndex;
	}

	static int getBestSellPrice(Slice slice){
		int maxIndex = 0;
		double max = slice.best_prices[0];

		for (int i = 1; i < slice.best_price_count; i++) {
			if (max < slice.best_prices[i]) {
				maxIndex = i;
				max = slice.best_prices[i];
			}
		}
		return maxIndex;
	}


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
                    acceptConnection(con);
					addTraderConnection(con); break;
				case ClientConnection:
                    acceptConnection(con);
					addClientConnection(con); break;
				case RouterConnection:
                    acceptConnection(con);
					addRouterConnection(con); break;/*
				case LiveMarketDataConnection:
					addLiveMarketDataConnection(con);*/
			}
		} catch (ClassNotFoundException e){
			error("Connection Type unknown");
		}
	}

	void acceptConnection(Socket con){
	    try {
            ObjectOutputStream os = new ObjectOutputStream(con.getOutputStream());
            os.writeObject(ConnectionType.Accept);
        } catch (IOException e){
	        error("Could not send confirmation");
	        e.printStackTrace();
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

	/*
	void addLiveMarketDataConnection(Socket liveMData){
		info("Live Market has connected");
		liveData = liveMData;
	} */

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

    public void connectToTraders(InetSocketAddress[] orderRouters) throws InterruptedException{
        for (InetSocketAddress location : orderRouters) {
            traders.add(connect(location));
        }
    }

	//		Runtime statistics about OrderManager
	// ------------------------------------------------------------------------
	static class Statistics {
		long startingTime        = System.currentTimeMillis();
		long lastTime            = System.currentTimeMillis();
		long totalMemoryUsage    = 0;
		long memoryCount         = 0;
		long averageTimePerOrder = 0;
		long lastOrderSize       = 0;
		long numOrderPerSecond   = 0;
		double averageCPU        = 0;

		int k = 0;

        OrderManager manager;

		Statistics(OrderManager manager_){
			manager = manager_;
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
					averageTimePerOrder = (int) manager.processed_orders * 1000 / (now - startingTime);
					lastTime = now;
				}
			}

			long averageMemoryUsage = totalMemoryUsage / memoryCount;
			double cpu = cpuMeasure.getProcessCpuLoad();
			averageCPU += cpu;

			double processed = manager.processed_orders;
			double total_orders = manager.total_orders;
            double per = manager.processed_orders / manager.total_orders;

            String color = null;
            if (k % 3 == 0)
                color = Util.COLOR_WHITE;
            else if (k % 3 == 1)
                color = Util.COLOR_BLUE;
            else
                color = Util.COLOR_CYAN;
            int col_size = 6;

            System.out.print(color);
			manager.info("          Order: " + pad(order_size, col_size)
                                  + "    (AVG: " + averageTimePerOrder + ")");

            manager.info("         Memory: " + pad(memory, col_size)
                                  + " KB (AVG: " + averageMemoryUsage + " KB)");

            manager.info("       CPU load: " + pad(trunc(cpu * 100), col_size)
                                  + " %  (AVG: " + trunc(averageCPU * 100 / memoryCount) + "%)");

            manager.info(" OrderProcessed: " + pad((int) processed, col_size)
                                  + "    (   : " + trunc(per * 100) + "%)");

            k += 1;

			if(memory > 1048576 ){
				System.exit(0);
			}
		}
	}
    static String pad(int m, int col_size){
        return pad(Integer.toString(m), col_size);
    }

    static String pad(long m, int col_size){
        return pad(Long.toString(m), col_size);
    }

    static String pad(double m, int col_size){
        return pad(Double.toString(m), col_size);
    }

    static String pad(String m, int col_size){
	    if (m.length() >= col_size)
	        return m.substring(0, col_size);
	    String padding = new String(new char[col_size - m.length()]);
	    padding.replace('\0', ' ');
	    return m + padding;
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

