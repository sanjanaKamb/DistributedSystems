import java.net.*;
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


public class Fileserver {

	private static String zk_host = null;
	private static ZkConnector zkc;
	private Watcher watcher;
	private static String myPath = "/FileServer";
	private static String host = null;
	private static int port = 7777;

	public Fileserver(String zk_host){
		System.out.println("\n File Server created\n");
		this.zk_host = zk_host;
		try{
			this.host = InetAddress.getLocalHost().getHostName();
		} catch (UnknownHostException e){}
		zkc = new ZkConnector();
		
		 try {
	            zkc.connect(zk_host);
	        } catch(Exception e) {
	            System.out.println("Zookeeper connect "+ e.getMessage());
	        }
		
		System.out.println("create watcher");
		 watcher = new Watcher() { // Anonymous Watcher
             @Override
             
             public void process(WatchedEvent event) {
            	
                 handleEvent(event);
                 
             } };
		
	}
	
	 private void handleEvent(WatchedEvent event) {
	       
  		    String path = event.getPath();
			//System.out.println("\nIn handleevent, path: "+path);
			EventType type = event.getType();
	        if(path.equalsIgnoreCase(myPath)) {
	            if (type == EventType.NodeDeleted) {
	                System.out.println(myPath + " deleted! Let's go!");       
	                checkpath(); // try to become the boss
	            }
	            if (type == EventType.NodeCreated) {
	                System.out.println(myPath + " created![No Action]");       
	                //try{ Thread.sleep(5000); } catch (Exception e) {}
	                //checkpath(); // re-enable the watch
	            }
	        }
	    }
	
	    private void checkpath() {
	    	//System.out.println("In checkpath");
	        Stat stat = zkc.exists(myPath, watcher);
	        if (stat == null) {              // znode doesn't exist; let's try creating it
	            System.out.println("Creating " + myPath);
	            Code ret = zkc.create(
	                        myPath,         // Path of znode
	                        host+" "+port,           // Send localhost IP address
	                        CreateMode.EPHEMERAL   // Znode type, set to EPHEMERAL.
	                        );
	            if (ret == Code.OK) System.out.println("\n PRIMARY listening at port: "+port);
	            
	        } else{
				try{
	        	System.out.println(myPath + "already exists, I'm backup");
	        	//see what port the primary has
				ZooKeeper Z = zkc.getZooKeeper();
				byte[] data = Z.getData(myPath, false, null);
				String line = new String (data);
				System.out.println("Before: raw data: "+line);
				String [] zkData = line.split(" ");
				port=(Integer.parseInt(zkData[1]))+1; //set backup port
				
				System.out.println("\n BACKUP listening at port: "+port);

				//port = 7778; //set backup port  
				}catch (KeeperException e) {
							// TODO Auto-generated catch block
							e.printStackTrace();
				}catch (InterruptedException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				} 				
	        }
	        
	        
	    }
	
	
    public static void main(String[] args) throws IOException {
        ServerSocket serverSocket = null;
        boolean listening = true;

		
			if (args.length != 1) {
            System.out.println("Usage: java -classpath lib/zookeeper-3.3.2.jar:lib/log4j-1.2.15.jar:. A zkServer:clientPort");
            return;
        }
		
		Fileserver F = new Fileserver(args[0]);	
		try {
			//System.out.println("Going to sleep for 5 secs");
            //Thread.sleep(5000);
        } catch (Exception e) {}
        
       F.checkpath();
        
        try {
        	System.out.println("socket");
			serverSocket = new ServerSocket(port);

        while (listening) {
        	new FileserverHandlerThread(zk_host,serverSocket.accept(), zkc).start();//TODO: start() not working
        }

        serverSocket.close();
		} catch (IOException e) {
            System.err.println("ERROR: Could not listen on port!");
            System.exit(-1);
        }
    }
}
