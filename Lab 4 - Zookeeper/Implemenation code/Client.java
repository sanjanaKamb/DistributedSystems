import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.Socket;
import java.net.UnknownHostException;
import java.util.*;

import org.apache.zookeeper.KeeperException;
import org.apache.zookeeper.KeeperException.Code;
import org.apache.zookeeper.WatchedEvent;
import org.apache.zookeeper.Watcher;
import org.apache.zookeeper.ZooKeeper;
import org.apache.zookeeper.CreateMode;
import org.apache.zookeeper.ZooDefs.Ids;
import org.apache.zookeeper.data.Stat;
import org.apache.zookeeper.Watcher.Event.EventType;


public class Client {
	
	private static String myPath = "/JobTracker";
	private ZkConnector zkc;
	private Watcher watcher;
	private ZooKeeper Z;
	private static String JobTracker_host = null;
	private static Socket socket = null;
	private static int port = 5556;
	private static ObjectInputStream input = null;
	private static ObjectOutputStream output = null;
	private static int client_id;//ADDED
	public volatile static Map<String, ClientPacket> JTbuffer=new LinkedHashMap<String, ClientPacket>();
	public volatile static Map<String, Integer> job_finished=new HashMap<String, Integer>();	//ADDED2 1-finished , 2-progress
	public volatile static Map<String, ClientPacket> Query_hash = new HashMap<String, ClientPacket>();

	public Client(String hosts){
		zkc = new ZkConnector();
		
		 try {
	            zkc.connect(hosts);
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
    EventType type = event.getType();
    if(path.equalsIgnoreCase(myPath)) {
	//Handles events from JobTracker
        if (type == EventType.NodeDeleted) {
            System.out.println(myPath + " deleted! Let's go!");       
            checkpath(); // try to become the boss Q: needed?

		}
        if (type == EventType.NodeCreated) {
            System.out.println(myPath + " created!");       
            //try{ Thread.sleep(5000); } catch (Exception e) {}
            
			checkpath(); // re-enable the watch, reconfigure host Vin: why comment out?
			//reconnect to new JT
			if(socket!=null){
			try {
				Client.input.close();
				Client.input=null;
				Client.output.close();
				//reopen new streams
				Client.socket = new Socket(JobTracker_host,port);
				Client.input = new ObjectInputStream(socket.getInputStream());
				Client.output = new ObjectOutputStream(socket.getOutputStream());
		 
				final ClientPacket packetToJobTracker22 = new ClientPacket();
				packetToJobTracker22.type = ClientPacket.JOB_CONNECT;
	        /*try {
			
			
	        System.out.println("Sent Packet to client\n");
	        //block till connection ack received
	        ClientPacket packetFromJobTracker22 = new ClientPacket();

			while((packetFromJobTracker22 = (ClientPacket) Client.input.readObject())!=null){
					 System.out.println("2\n");

					if(packetFromJobTracker22.type == ClientPacket.JOB_CONNECT_ACK){
						client_id = packetFromJobTracker22.client_id;//ADDED
						System.out.println("Connected to Job Tracker successfully!! id: "+client_id);

					}else{
						System.out.println("Error: Connection to Job Tracker failed");
						return;
					}
					break;
			}

			} catch (ClassNotFoundException e) {
				e.printStackTrace();
			} catch (IOException e) {
				e.printStackTrace();
			}
			*/
			
			client_id=-1;
			/*Always read incoming packets*/	
	        (new Thread("New Receive packets"){
				public synchronized void run(){	
					ClientPacket packetFromJobTracker = new ClientPacket();
					try {
						while((packetFromJobTracker = (ClientPacket) Client.input.readObject())!=null){
							//System.out.println("New received packet in new handler hash: "+packetFromJobTracker.hash);
							if (packetFromJobTracker.hash!=null){
								JTbuffer.remove(packetFromJobTracker.hash);
							}	
							
							if((Client.client_id==-1)&&(packetFromJobTracker.type == ClientPacket.JOB_CONNECT_ACK)){
								Client.client_id = packetFromJobTracker.client_id;//ADDED
								System.out.println("Connected to new Job Tracker successfully!! id: "+client_id);
								System.out.print("> ");
								//Re-send lost packets (from JT failure)
								for (ClientPacket packet : JTbuffer.values()){
									//I was waiting for a response from FS, but that is lost. Will re send packets
									//System.out.println("[waitJT]Resending lost packet");
									packet.client_id = Client.client_id;
									output.writeObject(packet);
								}
								JTbuffer.clear(); //wipe clean
							}
	
							else if((packetFromJobTracker.client_id == client_id)){
									/*process packet*/
									//System.out.println("That packet meant for me ");
								if(packetFromJobTracker.type == ClientPacket.JOB_REQUEST_ACK){
									System.out.println("Job submitted for processing");
								}else if(packetFromJobTracker.type == ClientPacket.JOB_IN_PROGRESS){
									Query_hash.put(packetFromJobTracker.hash,packetFromJobTracker);
									job_finished.put(packetFromJobTracker.hash, 2);
									//System.out.println("Job in progress");
								}else if(packetFromJobTracker.type == ClientPacket.JOB_FINISHED){
									Query_hash.put(packetFromJobTracker.hash,packetFromJobTracker);
									job_finished.put(packetFromJobTracker.hash, 1);
									//System.out.println("Job Finished");
								}else if(packetFromJobTracker.type == ClientPacket.PASSWORD_FOUND){
									Query_hash.put(packetFromJobTracker.hash,packetFromJobTracker);
									job_finished.put(packetFromJobTracker.hash, 3);
									//System.out.println("Password found is: "+packetFromJobTracker.password);
								}else if(packetFromJobTracker.type == ClientPacket.PASSWORD_NOT_FOUND){
									Query_hash.put(packetFromJobTracker.hash,packetFromJobTracker);
									job_finished.put(packetFromJobTracker.hash, 4);
									//System.out.println("Password not found");
								}
						}}
					} catch (ClassNotFoundException e) {
						// TODO Auto-generated catch block
						e.printStackTrace();
					} catch (IOException e) {
						// TODO Auto-generated catch block
						e.printStackTrace();
					}
				}				
			}).start();
			
					
			output.writeObject(packetToJobTracker22);	
			
			
			} catch (UnknownHostException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
			} catch (IOException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
			}				
			}
		}
    }
}

private void checkpath() {
    Stat stat = zkc.exists(myPath, watcher);
    
    if (stat == null) {              // znode doesn't exist; 
        System.out.println("Error: JobTracker does not exist " + myPath);
        return;
        
    }else{
	    //if JobTracker exists, get data - hostname
	    Z = zkc.getZooKeeper();
	    try {
	    	System.out.println("getdata");
			byte[] data = Z.getData(myPath, false, null);
			String line = new String (data);
			//System.out.println("Before: raw data: "+line);
			String [] zkData = line.split(" ");
			JobTracker_host=zkData[0];
			port=Integer.parseInt(zkData[1]);
			System.out.println("Connected to: getdata host,port"+JobTracker_host+port);
		} catch (KeeperException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (InterruptedException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} //stat?
    
    }
    
}
	
	
	 public static void main(String[] args){
		 
		 if (args.length != 1) {
	            System.out.println("Usage: java -classpath lib/zookeeper-3.3.2.jar:lib/log4j-1.2.15.jar:. A zkServer:clientPort");
	            return;
	        }
		 Client C = new Client(args[0]);
		 

	    System.out.println("checkpath");
	    C.checkpath(); // re-enable the watch
	        

	        
	    System.out.println("socket stuff");
	    /*set up input and output streams of JobTracker*/
	    try {
				socket = new Socket(JobTracker_host,port);
				input = new ObjectInputStream(socket.getInputStream());
				output = new ObjectOutputStream(socket.getOutputStream());
						
		} catch (UnknownHostException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
		} catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
		}
	        
	       // System.out.println("connect with jobtracker");
	        /*Attempt to connect to JobTracker*/
	        ClientPacket packetToJobTracker = new ClientPacket();
	        packetToJobTracker.type = ClientPacket.JOB_CONNECT;
	        try {
				output.writeObject(packetToJobTracker);
			} catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
	        
	        /*block till connection ack received*/
	        ClientPacket packetFromJobTracker = new ClientPacket();
	        try {
				packetFromJobTracker = (ClientPacket) input.readObject();
			} catch (ClassNotFoundException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			} catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
	        
	        if(packetFromJobTracker.type == ClientPacket.JOB_CONNECT_ACK){
	        	System.out.println("Connected to Job Tracker successfully!!");
			client_id = packetFromJobTracker.client_id;//ADDED
	        }else{
	        	System.out.println("Error: Connection to Job Tracker failed");
	        	return;
	        }
	        
	        
	        /*Always read incoming packets*/
	        (new Thread("Receive packets"){
				public synchronized void run(){	
					ClientPacket packetFromJobTracker = new ClientPacket();
					try {
						while((packetFromJobTracker = (ClientPacket) input.readObject())!=null){
							JTbuffer.remove(packetFromJobTracker.hash);
							
							if((packetFromJobTracker.client_id == client_id)){
									/*process packet*/
								if(packetFromJobTracker.type == ClientPacket.JOB_REQUEST_ACK){
									System.out.println("Job submitted for processing");
								}else if(packetFromJobTracker.type == ClientPacket.JOB_IN_PROGRESS){
									Query_hash.put(packetFromJobTracker.hash,packetFromJobTracker);
									job_finished.put(packetFromJobTracker.hash, 2);
									//System.out.println("Job in progress");
								}else if(packetFromJobTracker.type == ClientPacket.JOB_FINISHED){
									Query_hash.put(packetFromJobTracker.hash,packetFromJobTracker);
									job_finished.put(packetFromJobTracker.hash, 1);
									//System.out.println("Job Finished");
								}else if(packetFromJobTracker.type == ClientPacket.PASSWORD_FOUND){
									Query_hash.put(packetFromJobTracker.hash,packetFromJobTracker);
									job_finished.put(packetFromJobTracker.hash, 3);
									//System.out.println("Password found is: "+packetFromJobTracker.password);
								}else if(packetFromJobTracker.type == ClientPacket.PASSWORD_NOT_FOUND){
									Query_hash.put(packetFromJobTracker.hash,packetFromJobTracker);
									job_finished.put(packetFromJobTracker.hash, 4);
									//System.out.println("Password not found");
								}
						}}
					} catch (ClassNotFoundException e) {
						// TODO Auto-generated catch block
						e.printStackTrace();
					} catch (IOException e) {
						// TODO Auto-generated catch block
						e.printStackTrace();
					}
				}				
			}).start();
	        
	        
	        /*After connection, UI with client begins*/
	        System.out.println("WELCOME");
	        System.out.println("Please enter one of the following options in the specified format");
	        System.out.println("1. For submitting a job request enter 'request <hash>'");
	        System.out.println("2. For querying status of a job enter 'query <hash>'");
	        System.out.println("Enter input:");
	        // System.out.println("3. To leave enter 'quit'");????
	        
	        BufferedReader br = new BufferedReader( new InputStreamReader(System.in));
	        String s;
	        try {
				while((s =br.readLine())!=null){
					String[] result = s.split(" ");
					ClientPacket packetToJobTracker2 = new ClientPacket();
					packetToJobTracker2.client_id = client_id;		//ADDED
					
					
					if(result.length == 2){
							if(result[0].equals("request")){
							packetToJobTracker2.type = ClientPacket.JOB_REQUEST;
							packetToJobTracker2.hash = result[1];
							Query_hash.put(result[1], packetToJobTracker2);
							job_finished.put(result[1],2);
							System.out.println("Job submitted for processing");
							System.out.print("> ");
							
						}else if(result[0].equals("query")){
							packetToJobTracker2.type = ClientPacket.JOB_QUERY;
							packetToJobTracker2.hash = result[1];
							int val=0;
							try{
								val = job_finished.get(result[1]);
							} catch (NullPointerException e){
								System.out.println("Invalid Job");
								System.out.print("> ");
								continue;
							}
							if(val==3||val==4){
								System.out.println("Job Finished");
								ClientPacket p =Query_hash.get(result[1]);
								if(p.type == ClientPacket.PASSWORD_FOUND){
									
									System.out.println("Password found is: "+p.password);
									System.out.print(">");
								}else if(p.type == ClientPacket.PASSWORD_NOT_FOUND){
									
									System.out.println("Password not found");
									System.out.print("> ");
								}
								
							}
							if(val==2){
								System.out.println("Job in progress");			
								System.out.print("> ");
							}
							
						} else{
						System.out.println("Error: Unknown command entered");
						System.out.print("> ");
						//System.out.println("Enter input:");
						continue;
					}
					}else{
						System.out.println("Error: Unknown command entered");
						System.out.print("> ");
						//System.out.println("Enter input:");
						continue;
					}
					
					output.writeObject(packetToJobTracker2);	//send packet
				//	System.out.println("[waitJT]set with hash: "+packetToJobTracker2.hash);
					JTbuffer.put(packetToJobTracker2.hash,packetToJobTracker2);
					//System.out.println("Enter input:");
				}
			} catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}    
	        
	        
		 
	 }
}
