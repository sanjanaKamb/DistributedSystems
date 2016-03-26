import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.ServerSocket;

import org.apache.zookeeper.KeeperException;
import org.apache.zookeeper.KeeperException.Code;
import org.apache.zookeeper.WatchedEvent;
import org.apache.zookeeper.Watcher;
import org.apache.zookeeper.ZooKeeper;
import org.apache.zookeeper.CreateMode;
import org.apache.zookeeper.ZooDefs.Ids;
import org.apache.zookeeper.data.Stat;
import org.apache.zookeeper.Watcher.Event.EventType;


import java.io.*;
import java.net.*;
import java.util.*;

public class JobTracker {
	
	private static String zk_host = null;
	private static ZkConnector zkc;
	private Watcher watcher;
	private static String myPath = "/JobTracker";
	private static String workerPath = "/Worker";
	private static String host = null;
	private static int port = 5556;
	private static ServerSocket socket = null;
	static boolean listening = true;
	private static int job_id = 1;
	public volatile static Map<Integer, ObjectOutputStream> workersOS=new HashMap<Integer, ObjectOutputStream>();	
	public volatile static int OScount=0;
	public volatile static Map<Integer, Integer> reply_collector=new HashMap<Integer, Integer>();	
	private volatile static int numWorkerWatched=0; //VIN:ADDED	 
	private Watcher workerwatcher;

	
	
	
	public JobTracker(String zk_host){
		System.out.println("\nNew job tracker created\n");
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
				//VIN:ADDED	 
		workerwatcher = new Watcher() { // Anonymous Watcher
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
	                System.out.println(myPath + " created! [No action]");       
	                //try{ Thread.sleep(5000); } catch (Exception e) {}
	                //checkpath(); // re-enable the watch
	            }
	        }
			else if (path.equalsIgnoreCase(workerPath)) {
				//System.out.println("Handle changes to Worker/");       
				if (type == EventType.NodeChildrenChanged ){
					//System.out.println(" NodeChildrenChanged detected");       
					//checkworkerpath(); 
					// re-enable the watch
					
					try{
						ZooKeeper zk = zkc.getZooKeeper();
						List<String> list = zk.getChildren(workerPath, workerwatcher);

						int oldnumWorkerWatched=numWorkerWatched;
						numWorkerWatched=list.size();
						
						if (numWorkerWatched==(oldnumWorkerWatched+1)){
							System.out.println(" Node Added!");       
						}else if (numWorkerWatched==(oldnumWorkerWatched-1)){
							System.out.println(" Node Deleted!");  
						}
						else{
							//System.out.println(" Handle Concurrent Bugs!"); 
						}
					} catch (Exception e){}	
					
				}
				if (type == EventType.NodeDeleted) {
	                System.out.println(workerPath + " deleted! Let's go!");       
	                //checkpath(); // try to become the boss
					checkworkerpath(); // re-enable the watch
	            }
	            if (type == EventType.NodeCreated) {
	                System.out.println(workerPath + " created!");       
	                //try{ Thread.sleep(5000); } catch (Exception e) {}
	                checkworkerpath(); // re-enable the watch
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
		
	private void checkworkerpath() { //VIN:ADDED	 
		try{
			String workerPath="/Worker";
	    	//System.out.println("In worker's checkpath");
			Stat stat = zkc.exists(workerPath, workerwatcher);
		if (stat == null) { 
			
	        System.out.println("Creating worker group" + workerPath);
			//create a worker group
			Code ret = zkc.create(
	                    workerPath,         // Path of znode
	                    "",           // Send localhost IP address
	                    //ZooDefs.Ids.OPEN_ACL_UNSAFE,
						CreateMode.PERSISTENT);
	        if (ret == Code.OK) System.out.println("New /Worker node created!");
			
		}else{
	        //System.out.println(myPath + "already exists, I'm backup");
	        //port=5558;       	
	    }
		//set watcher on path
		ZooKeeper zk = zkc.getZooKeeper();
		List<String> list = zk.getChildren(workerPath, workerwatcher);
		//System.out.println("Watch placed on worker");

	    } catch (Exception e){}
	        
	    }		
	
	public static void main(String[] args){
		
		if (args.length != 1) {
            System.out.println("Usage: java -classpath lib/zookeeper-3.3.2.jar:lib/log4j-1.2.15.jar:. A zkServer:clientPort");
            return;
        }
		
		JobTracker J = new JobTracker(args[0]);	
		try {
			//System.out.println("Going to sleep for 5 secs");
            //Thread.sleep(5000);
        } catch (Exception e) {}
        try {
       J.checkpath();
	   J.checkworkerpath();
	   } catch (Exception e) {}
       try {
			System.out.println("socket");
			socket = new ServerSocket(port);
			while(listening){
				new JobTrackerThread(zk_host, socket.accept(), zkc, (job_id++)).start();
				
			}
			
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		
		
	}
	
	
}
