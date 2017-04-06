import Utility.Connection.ConnectionType;
import Actor.Actor;
import Utility.HelperObject;

import java.io.IOException;
import java.io.ObjectOutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;

public class OrderManagerClient extends Actor{

    protected Socket order_manager;
    protected InetSocketAddress order_manager_address;

    OrderManagerClient(InetSocketAddress om_manager){
        order_manager_address = om_manager;
    }

    public Socket connection(){
        return order_manager;
    }

    public void connectToOrderManager(ConnectionType type, InetSocketAddress address) throws IOException {
        //OM will connect to us
        order_manager = new Socket();
        order_manager.setSendBufferSize(HelperObject.socket_buffer);
        order_manager.setReceiveBufferSize(HelperObject.socket_buffer);
        order_manager.setKeepAlive(true);

        order_manager.connect(address);

        // send handsake
        ObjectOutputStream os = new ObjectOutputStream(order_manager.getOutputStream());
        os.writeObject(type);
        os.flush();

        info("Connected to OM ");
    }
}
