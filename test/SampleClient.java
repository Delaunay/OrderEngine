import Actor.Message;
import OrderClient.Client;
import OrderClient.NewOrderSingle;
import OrderManager.Order;
import Ref.Instrument;
import Utility.Connection.ConnectionType;
import Utility.HelperObject;
import Utility.Util;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.HashMap;

/**
 * 		- Client listens and writes to the OrderManager
 *<br>      - Generate and send orders to the OrderManager
 *<br>      - Receive updates from the OrderManager about the state of its orders
 */
public class SampleClient extends OrderManagerClient implements Client, Runnable {
    // queue for outgoing orders
    private HashMap<Integer, NewOrderSingle> OUT_QUEUE = new HashMap<>();
    // message id number used as a `primary key` thats why it is static
    // Although this needs to change, the id should be given by the OrderManager not the client
    private static int 		     id = 0;
    private int                  print_delta = 1000;
    private int                  initial_orders = 0;

    class FIXMessage{
        int     OrderId	=	-1;
        char    MsgType;
        int     OrdStatus;
        int     size;
        double  price;
    }

    public SampleClient(int print_delta_,
                        int initial_orders_,
                        InetSocketAddress om_address) {
        super(om_address);
        initLog(this.getClass().getName());
        print_delta = print_delta_;
        initial_orders = initial_orders_;
    }
    
    void connectToOrderManager(InetSocketAddress address) throws IOException{
        connectToOrderManager(ConnectionType.ClientConnection, address);
    }

    @Override
    public void run(){

        try {
            connectToOrderManager(order_manager_address);

            for (int i = 0; i < initial_orders; ++i) {
                sendOrder();
            }

            messageHandler();

        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public int sendOrder() throws IOException{
        int size   = MockConfig.getClientOrderSize();
        int instid = MockConfig.getAssetID();

        Instrument instrument = MockConfig.getInstrument(instid);
        NewOrderSingle nos = new NewOrderSingle(size, instid, instrument);
        return sendOrder(nos);
    }

    @Override
    public int sendOrder(NewOrderSingle nos)throws IOException{
        debug("SC: sendOrder: id=" + id + " size=" + nos.size + " instrument=" + nos.instrument.toString());

        OUT_QUEUE.put(id, nos);

        if(order_manager.isConnected()){
            sendMessage(order_manager, new Message.NewOrderSingle(id, nos.instrument, nos.size, nos.price));
        }
        return id++;
    }

    @Override
    public void sendCancel (int idToCancel) {
        debug("SC: sendCancel: id=" + idToCancel);

        if(order_manager.isConnected()){
            sendMessage(order_manager, new Message.CancelOrder(idToCancel));
        }
    }

    @Override
    public void partialFill(Order order){
        debug(" PartialFill " + order);
    }

    @Override
    public void fullyFilled(Order order){
        debug(" FullFill" + order);
        OUT_QUEUE.remove(order.id);
    }

    @Override
    public void cancelled(Order order){
        debug(" Cancelled" + order);
        OUT_QUEUE.remove(order.id);
    }

    /** Print out a summary of the Client, outstanding orders*/
    public void summary(){
        debug(Thread.currentThread().getName() + " has: " + OUT_QUEUE.size() + " outstanding orders");
    }

    public boolean readMessage() throws IOException, ClassNotFoundException{
        while(order_manager.getInputStream().available() > 0){

            Message m = readMessage(order_manager);

            if (m == null || m.op != Message.MessageKind.FIXMessage){
                error("unsupported operation" + m.op);
            }

            Message.FIXMessage fm = (Message.FIXMessage) m;
            Message.FIXMessageData ret = Message.readOrderManagerAnswer(fm.message);

            debug("SC: " + Thread.currentThread().getName() + " received fix message: " + fm.message);

            switch (ret.OrdStatus){
                case '0': newOrderPending (fm); return true;
                case '1': orderPartialFill(fm); return true;
                case '2': orderfullFill   (fm); return true;
            }
        }
        return false;
    }

    void newOrderPending(Message.FIXMessage m ){
        //info("-----NewOrderPending-----");
    }
    void orderPartialFill(Message.FIXMessage m ){
        info("-----PartialFill-----");
    }
    void orderfullFill(Message.FIXMessage m ){
        info("-----FullFill-----");
    }

    @Override
    public void messageHandler(){
        // Print the summary from time to time
        ScheduledPrint sp = new ScheduledPrint(print_delta, this);

        // Create a new order every X millisecond
        ScheduledOrder so = new ScheduledOrder(print_delta, this);

        try {
            while (true){
                //is.wait(); //this throws an exception!!
                readMessage();

                so.run();
                sp.run();

                HelperObject.sleep(HelperObject.waitTime);
            }
        } catch (ClassNotFoundException e){
            error("Received an weird message");

        } catch (IOException e){
            e.printStackTrace();
        }
    }

    static class ScheduledPrint extends Util.ScheduledTask{
        SampleClient client;

        ScheduledPrint(long delta, SampleClient client_){
            super(delta);
            client = client_;
        }

        @Override
        public void scheduledJob(){
            client.summary();
        }
    }

    static class ScheduledOrder extends Util.ScheduledTask{
        SampleClient client;

        ScheduledOrder(long delta, SampleClient client_){
            super(delta);
            client = client_;
        }

        @Override
        public void scheduledJob(){
            try {
                client.sendOrder();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }


/*listen for connections
once order manager has connected, then send and cancel orders randomly
listen for messages from order manager and print them to stdout.*/
}