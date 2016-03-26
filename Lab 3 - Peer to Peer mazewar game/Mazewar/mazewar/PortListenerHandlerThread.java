import java.net.*;
import java.io.*;
import java.util.*;

public class PortListenerHandlerThread extends Thread {
	private Socket socket = null;
    ServerSocket serverSocket = null;
    boolean listening = true;
    private int portnum;
   private String name; 
	public PortListenerHandlerThread(Socket socket, String name) {
		super("PortListenerHandlerThread");
		this.socket = socket;
		this.name = name;
		System.out.println("Created new PortListenerHandlerThread to handle client for player named " +name);
	}

	public void run() {
		try {
		ObjectInputStream fromClient = new ObjectInputStream(socket.getInputStream());
		//TODO: VIN: Made changes, no longer add client to clientsOS/IS. This is done in GameClientThread 
		/* stream to write back to client */
		ObjectOutputStream toClient = new ObjectOutputStream(socket.getOutputStream());
		//add to broadcast queue
        //add OS and IS to hashmap
        //GameClientThread.clientsOS.put(name,toClient);
        //GameClientThread.clientsIS.put(name,fromClient);
		GamePacket prevpacketFromServer; 
		while(true){
			GamePacket packetFromServer = null;
			prevpacketFromServer=packetFromServer;
			packetFromServer = (GamePacket) fromClient.readObject();
			if(packetFromServer!=null){
				//System.out.println("Received packet from "+ packetFromServer.name+ " of type "+packetFromServer.type+ "key val:" + packetFromServer.key );
				GameClientThread.addToQueue(packetFromServer);
			}
		}
		}catch (ClassCastException e) {
			// TODO: VIN: NOTE: Sometimes get a Direction Packet and this .
			e.printStackTrace();

		}catch (ClassNotFoundException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}
}
