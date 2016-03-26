import java.io.BufferedReader;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.Socket;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.io.*;

import org.apache.zookeeper.KeeperException;
import org.apache.zookeeper.KeeperException.Code;
import org.apache.zookeeper.WatchedEvent;
import org.apache.zookeeper.Watcher;
import org.apache.zookeeper.ZooKeeper;
import org.apache.zookeeper.CreateMode;
import org.apache.zookeeper.ZooDefs.Ids;
import org.apache.zookeeper.data.Stat;
import org.apache.zookeeper.Watcher.Event.EventType;



public class FileserverHandlerThread extends Thread {
	private Socket socket = null;

	public FileserverHandlerThread(String zk_host, Socket socket, ZkConnector zkc) {
		//super("FileserverHanderThread");
		this.socket = socket;
		System.out.println("Created new Thread to handle client");
	}

	public void run() {

		boolean gotByePacket = false;
		
		try {
			/* stream to write back to client */
			ObjectOutputStream toClient = new ObjectOutputStream(socket.getOutputStream());
			toClient.flush();
			/* stream to read from client */
			ObjectInputStream fromClient = new ObjectInputStream(socket.getInputStream());
			//EchoPacket packetFromClient;
			

			ClientPacket packetFromWorker;
			System.out.println("File Server waiting for packets");
			while (( packetFromWorker = (ClientPacket) fromClient.readObject()) != null) {
				
				System.out.println("File Server received packet from worker");

				//TODO: extract init and fin values from packet
				int init=packetFromWorker.start;
				int fin=packetFromWorker.fin;
				
			    String[] fullpartition = readfile(); //read file
			    if (fullpartition==null){
			    	return;
			    }
			    
			    //copy range of array (file server)
			    String[] mypartition=Arrays.copyOfRange(fullpartition, init, fin);
			    
				//return mypartition in a packet
			    
			    //Note: we are echoing back the packet from the client so the hash to be looked up is still there
			    packetFromWorker.partition=mypartition;
			    //packetFromWorker.partition=null;
				packetFromWorker.type=ClientPacket.PARTITION_REPLY;
			    
				
				//try{ Thread.sleep(5000); } catch (Exception e) {}
				
				System.out.println("File Server about to respond with partition size: "+ packetFromWorker.partition.length);
				toClient.flush();
			    toClient.writeObject(packetFromWorker);
				toClient.flush();
				System.out.println("File Server responded to worker");


			}
				/* cleanup when client exits */
			/*fromClient.close();
			toClient.close();
			socket.close();*/

		} catch(EOFException e) {
		
		} catch (IOException e) {
			if(!gotByePacket)
				e.printStackTrace();
		} catch (ClassNotFoundException e) {
			if(!gotByePacket)
				e.printStackTrace();
		}
	}
	
    public static String[] readfile(){
    	String[] retval=null; //null is default value and error value
    	//Citation: templated from: http://stackoverflow.com/questions/285712/java-reading-a-file-into-an-array
    	
    	try {

	        FileReader dictionary = new FileReader("lowercase.rand");
	
	        BufferedReader br = new BufferedReader(dictionary);
	        List<String> lines = new ArrayList<String>();
	        String line = null;
	        
	        while ((line = br.readLine()) != null) {
	            lines.add(line);
	        }
	        
	        br.close();
	        
	        retval= lines.toArray(new String[lines.size()]);
    	
		} catch (FileNotFoundException e) {
			e.printStackTrace();
		} catch (IOException e) {
			e.printStackTrace();
		}
		return retval;
    }
	
}