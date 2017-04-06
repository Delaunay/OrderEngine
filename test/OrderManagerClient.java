import Actor.Actor;
import Utility.Connection.ConnectionType;
import Utility.HelperObject;

import javax.net.ServerSocketFactory;
import java.io.IOException;
import java.io.ObjectInputStream;
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

        try {
            ObjectInputStream is = new ObjectInputStream(order_manager.getInputStream());
            ConnectionType accept = (ConnectionType) is.readObject();
            if (accept == ConnectionType.Accept){
                info("Connected to OM ");
                return;
            }
        } catch (ClassNotFoundException e){
            e.printStackTrace();
            error("Server refused connection");
        }
    }

    public void connectIndirectConnect(int port) throws IOException{
        info("Connecting to OM " + port);
        order_manager = ServerSocketFactory.getDefault().createServerSocket(port).accept();
        order_manager.setSendBufferSize(HelperObject.socket_buffer);
        order_manager.setReceiveBufferSize(HelperObject.socket_buffer);
        info("Connected to OM " + port);
    }

}
