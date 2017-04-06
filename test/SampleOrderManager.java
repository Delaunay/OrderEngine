import OrderManager.OrderManager;


public class SampleOrderManager extends OrderManager implements Runnable{

    public SampleOrderManager(int port){
        super(port);
    }
    @Override
    public void run() {
        super.run();
    }
}
