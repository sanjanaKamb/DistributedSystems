import java.net.*;
import java.io.*;
import java.util.*;

public class ClientListenerThread extends Thread {
	private ObjectInputStream clientinputstream = null;

	public ClientListenerThread(ObjectInputStream inputstream) {
		clientinputstream=inputstream;
		System.out.println("Created new Thread to listen to client");
	}

	public void run() {
		try {
		while(true){
			GamePacket packetFromServer = null;

			packetFromServer = (GamePacket) clientinputstream.readObject();
			if(packetFromServer!=null)
					GameClientThread.addToQueue(packetFromServer);
		}
			
		/* cleanup when client exits */ //TODO: add condition to quit
		//clientinputstream.close();
		}catch (EOFException e){
			//end of file, no problem
		} catch (IOException e) {
				e.printStackTrace();
		} catch (ClassNotFoundException e) {
				e.printStackTrace();
		}
	}
}
