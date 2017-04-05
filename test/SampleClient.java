import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.HashMap;
import java.util.Random;
import java.util.concurrent.TimeUnit;

import OrderClient.Client;
import OrderClient.NewOrderSingle;
import OrderManager.Order;
import Ref.Instrument;
import Ref.Ric;
import Utility.Util;

import com.sun.xml.internal.ws.policy.privateutil.PolicyUtils;
import org.apache.log4j.BasicConfigurator;
import org.apache.log4j.Level;
import org.apache.log4j.LogManager;
import org.apache.log4j.Logger;

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
    private Socket 				 omConn; // connection to order manager
    private ObjectInputStream    is;
    private Logger log;
    private int delta = 1000;
    private int initial_orders = 0;


    public SampleClient(){}

    public SampleClient(int port) throws IOException{
        initLog();
        connectToOrderManager(port);
    }

    public SampleClient(String name, int port, int milli, int initial_orders_) throws IOException{
        this.setName(name);
        initLog();
        connectToOrderManager(port);
        delta = milli;
        initial_orders = initial_orders_;

    }
    public void initLog(){

        log = LogManager.getLogger("Debug");
        log.setLevel(Level.WARN);
    }


    public void connectToOrderManager(int port) throws IOException{
        //OM will connect to us
        omConn = new ServerSocket(port).accept();
        log.info("SC: OM connected to client port " + port);
    }

    @Override
    public void run(){
        try {
            for (int i = 0; i < initial_orders; ++i) {
                sendOrder();
            }

            messageHandler();
        } catch (IOException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
    }

    public int sendOrder()throws IOException{
        int size   = RANDOM_NUM_GENERATOR.nextInt(5000);
        int instid = RANDOM_NUM_GENERATOR.nextInt(3);   // instrument id

        Instrument instrument = INSTRUMENTS[instid];
        NewOrderSingle nos = new NewOrderSingle(size, instid, instrument);
        return sendOrder(nos);
    }

    @Override
    public int sendOrder(NewOrderSingle nos)throws IOException{

        log.info("SC: sendOrder: id=" + id + " size=" + nos.size + " instrument=" + nos.instrument.toString());

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
        show("SC: sendCancel: id=" + idToCancel);
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
        show(" PartialFill " + order);
    }

    @Override
    public void fullyFilled(Order order){
        show(" FullFill" + order);
        OUT_QUEUE.remove(order.clientOrderID);
    }

    @Override
    public void cancelled(Order order){
        show(" Cancelled" + order);
        OUT_QUEUE.remove(order.clientOrderID);
    }

    public void show(String m){
        log.debug(m);
    }

    FIXMessage readOrderManagerAnswer(String[] fixTags){
        FIXMessage m = new FIXMessage();

        for(int i = 0; i < fixTags.length; i++){
            String[] tag_value = fixTags[i].split("=");
            switch(tag_value[0]){
                case "11":
                    m.OrderId = Integer.parseInt(tag_value[1]);
                    break;
                case "35":
                    m.MsgType=tag_value[1].charAt(0);
                    break;
                case "39":
                    m.OrdStatus = tag_value[1].charAt(0);
                    break;
            }
        }

        return m;
    }

    /** Print out a summary of the Client, outstanding orders*/
    public void summary(){
        log.info(Thread.currentThread().getName() + " has: " + OUT_QUEUE.size() + " outstanding orders");

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

            log.info("SC: " + Thread.currentThread().getName() + " received fix message: " + fix);

            String[] fixTags=fix.split(";");

            FIXMessage m = readOrderManagerAnswer(fixTags);

            switch(m.MsgType){
                case 'A':
                    newOrderSingleAcknowledgement(m.OrderId);
                    break;
                default:
                    show("Not handled case: " + " " + m.OrdStatus);
            }

            /*message=connection.getMessage();
            char type;
            switch(m.type){
                case 'C':cancelled(message);break;
                case 'P':partialFill(message);break;
                case 'F':fullyFilled(message);
            }*/
            log.info("DONE");
            return true;
        }
        return false;
    }

    @Override
    public void messageHandler(){
        // Print the summary from time to time
        ScheduledPrint sp = new ScheduledPrint(delta, this);

        // Create a new order every X millisecond
        ScheduledOrder so = new ScheduledOrder(delta, this);

        try {
            while (true){
                //is.wait(); //this throws an exception!!
                readMessage();

                so.run();
                sp.run();

                TimeUnit.MILLISECONDS.sleep(1);
            }
        } catch (ClassNotFoundException e){
            log.info("Received an weird message");

        } catch (IOException e){
            e.printStackTrace();
        } catch (InterruptedException e){
            // dont care
        }
    }

    void newOrderSingleAcknowledgement(int OrderId){
        log.info(Thread.currentThread().getName()+" called newOrderSingleAcknowledgement");
        //do nothing, as not recording so much state in the NOS class at present
    }

    class FIXMessage{
        int     OrderId	=	-1;
        char    MsgType;
        int     OrdStatus;
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
                client.sendOrder(null);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }


/*listen for connections
once order manager has connected, then send and cancel orders randomly
listen for messages from order manager and print them to stdout.*/
}