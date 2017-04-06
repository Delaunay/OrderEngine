import OrderClient.Client;
import OrderClient.NewOrderSingle;
import OrderManager.Order;
import Ref.Instrument;
import Ref.Ric;
import Utility.Connection;
import Utility.HelperObject;
import Utility.Util;
import org.apache.log4j.Level;
import org.apache.log4j.LogManager;
import org.apache.log4j.Logger;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.HashMap;
import java.util.Random;
import Utility.Connection.ConnectionType;

public class SampleClient extends Thread implements Client {
    private static final Random RANDOM_NUM_GENERATOR = new Random();
    private static final Instrument[] INSTRUMENTS = {
            new Instrument(new Ric("VOD.L")),
            new Instrument(new Ric("BP.L")),
            new Instrument(new Ric("BT.L"))
    };

    // queue for outgoing orders
    private HashMap<Integer, NewOrderSingle> OUT_QUEUE = new HashMap<>();
    // message id number used as a `primary key` thats why it is static
    // Although this needs to change, the id should be given by the OrderManager not the client
    private static int 		     id = 0;
    private ObjectInputStream    is;
    private Logger               log;
    private int                  print_delta = 1000;
    private int                  initial_orders = 0;

    private Socket 				 omConn;
    private InetSocketAddress    order_manager_address;

    class FIXMessage{
        int     OrderId	=	-1;
        char    MsgType;
        int     OrdStatus;
        int     size;
        double  price;
    }


    public SampleClient(){
        initLog();
    }

    public SampleClient(String name,
                        int print_delta_,
                        int initial_orders_,
                        InetSocketAddress om_address)
    {
        this.setName(name);
        initLog();
        print_delta = print_delta_;
        initial_orders = initial_orders_;
        order_manager_address = om_address;
    }

    public void initLog(){
        log = LogManager.getLogger(this.getClass().getName());
        log.setLevel(Level.INFO);
    }

    public void connectToOrderManager(InetSocketAddress address) throws IOException{
        //OM will connect to us
        omConn = new Socket();
        omConn.setSendBufferSize(HelperObject.socket_buffer);
        omConn.setReceiveBufferSize(HelperObject.socket_buffer);
        omConn.setKeepAlive(true);

        omConn.connect(address);

        // send handshake
        ObjectOutputStream os = new ObjectOutputStream(omConn.getOutputStream());
            os.writeObject(ConnectionType.ClientConnection);
            os.flush();

        log.info("Connected to OM ");
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
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
    }

    public int sendOrder() throws IOException{
        int size   = 1000; //RANDOM_NUM_GENERATOR.nextInt(5000);
        int instid = RANDOM_NUM_GENERATOR.nextInt(3);   // instrument id

        Instrument instrument = INSTRUMENTS[instid];
        NewOrderSingle nos = new NewOrderSingle(size, instid, instrument);
        return sendOrder(nos);
    }

    @Override
    public int sendOrder(NewOrderSingle nos)throws IOException{
        log.debug("SC: sendOrder: id=" + id + " size=" + nos.size + " instrument=" + nos.instrument.toString());

        OUT_QUEUE.put(id, nos);

        if(omConn.isConnected()){
            ObjectOutputStream os = new ObjectOutputStream(omConn.getOutputStream());
            os.writeObject(MessageKind.ANSNewOrder);
            //os.writeObject("35=D;"); // is this a delete ?
            os.writeInt(id);
            os.writeObject(nos);
            os.flush();
        }
        return id++;
    }

    @Override
    public void sendCancel (int idToCancel) {
        log.debug("SC: sendCancel: id=" + idToCancel);

        if(omConn.isConnected()){
            try {
                ObjectOutputStream os = new ObjectOutputStream(omConn.getOutputStream());
                os.writeObject(MessageKind.ANSCancel);
                os.writeInt(idToCancel);
                //OMconnection.sendMessage("cancel",idToCancel);
            }
            catch (IOException e){
                e.printStackTrace();
            }
        }
    }

    @Override
    public void partialFill(Order order){
        log.debug(" PartialFill " + order);
    }

    @Override
    public void fullyFilled(Order order){
        log.debug(" FullFill" + order);
        OUT_QUEUE.remove(order.id);
    }

    @Override
    public void cancelled(Order order){
        log.debug(" Cancelled" + order);
        OUT_QUEUE.remove(order.id);
    }

    FIXMessage readOrderManagerAnswer(String[] fixTags){
        FIXMessage m = new FIXMessage();

        for(int i = 0; i < fixTags.length; i++){
            String[] tag_value = fixTags[i].split("=");
            switch(tag_value[0]){
                case "11": // clientOrderID
                    m.OrderId = Integer.parseInt(tag_value[1]);
                    break;
                case "35": //
                    m.MsgType = tag_value[1].charAt(0);
                    break;
                case "38":  // size
                    m.size = Integer.parseInt(tag_value[1]);
                    break;
                case "39": // Status
                    m.OrdStatus = tag_value[1].charAt(0);
                    break;
                case "44": // Price
                    m.price = Double.parseDouble(tag_value[1]);
                    break;


            }
        }

        return m;
    }

    /** Print out a summary of the Client, outstanding orders*/
    public void summary(){
        log.debug(Thread.currentThread().getName() + " has: " + OUT_QUEUE.size() + " outstanding orders");

        /*
        for(Map.Entry item : OUT_QUEUE.entrySet()){
            Utility.Util.print("id = " + item.getKey() + ", Order = " + item.getValue());
        } */
    }

    public boolean runOnce() throws IOException, ClassNotFoundException{
        return readMessage();
    }

    public boolean readMessage() throws IOException, ClassNotFoundException{
        while(omConn.getInputStream().available() > 0){
            is = new ObjectInputStream(omConn.getInputStream());

            String fix = (String) is.readObject();

            log.debug("SC: " + Thread.currentThread().getName() + " received fix message: " + fix);

            String[] fixTags=fix.split(";");

            FIXMessage m = readOrderManagerAnswer(fixTags);

            switch (m.OrdStatus){
                case '0': newOrderPending (m); return true;
                case '1': orderPartialFill(m); return true;
                case '2': orderfullFill   (m); return true;
            }
        }
        return false;
    }

    void newOrderPending(FIXMessage m ){
        //log.info("-----NewOrderPending-----");
    }
    void orderPartialFill(FIXMessage m ){
        //log.info("-----PartialFill-----");
    }
    void orderfullFill(FIXMessage m ){
        //log.info("-----FullFill-----");
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
            log.error("Received an weird message");

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