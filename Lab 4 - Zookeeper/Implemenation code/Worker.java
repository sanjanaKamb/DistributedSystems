import java.io.*;
import java.net.Socket;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.security.MessageDigest;
import java.math.BigInteger;
import java.security.NoSuchAlgorithmException;
import java.io.*;
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


public class Worker {
	
	private static String myPath = "/JobTracker";
	private static String workerPath = "/Worker";
	private static String fsPath = "/FileServer";
	private static ZkConnector zkc;
	private Watcher watcher;
	private Watcher fswatcher;
	private ZooKeeper Z;
	private static String JobTracker_host = null;
	//private static Socket socket = null;
	private static Socket socket=null; //holds JT socket
	private static Socket fssocket=null;
	public static String FileServer_host = null;
	public static int FileServer_port = 7777;
	public static int mypart_id=0;
	public static int myos_id=0;
	
	
	private static int port = 5556;
	private static volatile ClientPacket waitFS=null;
	//public static ArrayList<ClientPacket> FSbuffer=new ArrayList<ClientPacket>();
	public volatile static Map<String, ClientPacket> FSbuffer=new LinkedHashMap<String, ClientPacket>();
	private static ObjectInputStream fromJT = null;
	private static ObjectOutputStream toJT = null;
	public static ObjectInputStream fromFS = null; //could be any client that is connecting (JT or FS)
	public static ObjectOutputStream toFS=null;
	
	public Worker(String hosts){
		//host holds address to zk
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
		 fswatcher = new Watcher() { // Anonymous Watcher
             @Override
             public void process(WatchedEvent event) {
                 handleEvent(event);
         
             } };
	}
	

private void handleEvent(WatchedEvent event) {
    String path = event.getPath();
    EventType type = event.getType();
	System.out.println("In Handle event, with path: "+path );
    if(path.equalsIgnoreCase(myPath)) {
	//Event from job tracker
        if (type == EventType.NodeDeleted) {
            System.out.println(myPath + " deleted! Let's go!");       
            checkpath(); // try to become the boss

        }
        if (type == EventType.NodeCreated) {
            System.out.println(myPath + " created!");       
            //try{ Thread.sleep(5000); } catch (Exception e) {}
            checkpath(); // re-enable the watch, update server info
			if(socket!=null){
				try {
				//Worker.fromJT.close();
				Worker.fromJT=null;
				//Worker.toJT.close();
				//reopen new streams
				Worker.socket = new Socket(JobTracker_host,port);
				Worker.fromJT = new ObjectInputStream(socket.getInputStream());
				Worker.toJT = new ObjectOutputStream(socket.getOutputStream());
				
					
				System.out.println("send JOB_CONNECT_WORKER to jobtracker");
				//Attempt to connect to JobTracker
				ClientPacket packetToJobTracker = new ClientPacket();
				packetToJobTracker.type = ClientPacket.JOB_CONNECT_WORKER;
				
				mypart_id=-1;
				//start new thread to listen
				(new Thread("New Receive From JT"){
				public synchronized void run(){	
					System.out.println("New thread to handle JT created");
					ClientPacket packetFromClient = new ClientPacket();
					try {
					while (( packetFromClient = (ClientPacket) Worker.fromJT.readObject()) != null) {
						System.out.println("Packet Received from JT");

						if((Worker.mypart_id==-1)&&(packetFromClient.type == ClientPacket.JOB_CONNECT_ACK_WORKER)){
							Worker.mypart_id = packetFromClient.part_id;//ADDED
							Worker.myos_id=packetFromClient.os_id;
							System.out.println("Connected to Job Tracker successfully!! id: "+Worker.mypart_id);

						}		
	
						else if ((packetFromClient.part_id==Worker.mypart_id)&&(packetFromClient.type==ClientPacket.JOBTRACKER_REQUEST)){
							//forwards packet to file server
							System.out.println("job detected for id: "+mypart_id);
							Worker.toFS.writeObject(packetFromClient);
							//System.out.println("[waitFS]set with hash: "+packetFromClient.hash);
							FSbuffer.put(packetFromClient.hash,waitFS);
							System.out.println("Sent packet to FS");
						}

					}
					}catch (NullPointerException e) {
						e.printStackTrace();
					}catch (ClassNotFoundException e) {
						// TODO Auto-generated catch block
						e.printStackTrace();
					} catch (IOException e) {
						// TODO Auto-generated catch block
						e.printStackTrace();
					}catch (Exception e) {
						// TODO Auto-generated catch block
						e.printStackTrace();
					}
					System.out.println("New thread to handle JT EXIT\n");
				}				
				}).start();

				toJT.writeObject(packetToJobTracker);
				
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
	else if(path.equalsIgnoreCase(fsPath)) {
        if (type == EventType.NodeDeleted) {
            System.out.println(fsPath + " deleted! Let's go!");       
			fscheckpath();
        }
        if (type == EventType.NodeCreated) {
            System.out.println(fsPath + " created!");       
            //try{ Thread.sleep(5000); } catch (Exception e) {}
            fscheckpath(); // re-enable the watch
			if(fssocket!=null){
				try {
					//Worker.fromFS.close();
					Worker.fromFS=null;
					//Worker.toFS.close();
					//reopen new streams
					Worker.fssocket = new Socket(FileServer_host,FileServer_port);
					Worker.fromFS = new ObjectInputStream(Worker.fssocket.getInputStream());
					Worker.toFS = new ObjectOutputStream(Worker.fssocket.getOutputStream());
					
					/*handle incoming packets from FS*/
					(new Thread("Receive From FS"){
						public synchronized void run(){
							System.out.println("New thread to listen to FS created!");						
							ClientPacket packetFromFS = new ClientPacket();
							try {
								while((packetFromFS = (ClientPacket) Worker.fromFS.readObject())!=null){
									/*process packet*/
									if(packetFromFS.type==ClientPacket.PARTITION_REPLY){
										//if(waitFS.hash.equals(packetFromFS.hash)){
										//System.out.println("[waitFS]Cleared: "+packetFromFS.hash);
										waitFS=null;
										FSbuffer.remove(packetFromFS.hash);
										//}
										System.out.println("Received Response from FS");
										//This else if block is entered by a different thread
										//extract partition from FS in packet.
										String[] mypartition = packetFromFS.partition;
										//extract hash from packet, forwarded by File Server
										String targethash = packetFromFS.hash;
										
										//mypartition is padded with nulls
										String ans=null;    
										for (String word: mypartition){
											if (word==null){
												break; //catches early termination
											}
											String hash = getHash(word);
											if (hash.equals(targethash)){
												System.out.println("Hash found for requested hash, word is "+word);
												//TODO: Respond to JobTracker
												//System.out.println("Hash of r41nb0w: "+hash);
												ans=word;
												break;//assume no repeat
											}
										}
										
										if (ans==null){
											System.out.println("Hash not found");
											
										}
										packetFromFS.partition=null; //wipe partition to save space
										packetFromFS.type=ClientPacket.WORKER_REPLY;
										packetFromFS.password=ans;								
										Worker.toJT.writeObject(packetFromFS);
										System.out.println("Responded to Job Tracker with ans = "+ ans);
									}
								}
							} catch (NullPointerException e){
								e.printStackTrace();
							} catch (ClassNotFoundException e) {
								// TODO Auto-generated catch block
								e.printStackTrace();
							} catch (IOException e) {
								// TODO Auto-generated catch block
								e.printStackTrace();
							} catch (Exception e) {
								// TODO Auto-generated catch block
								e.printStackTrace();
							}
							System.out.println("New thread to listen to FS EXIT \n");						

						}				
					}).start();
					
					//Re-send lost packets (from FS failure)
					for (ClientPacket packet : FSbuffer.values()){
						//I was waiting for a response from FS, but that is lost. Will resent
						//System.out.println("[waitFS]Resending lost packet");
						toFS.writeObject(packet);
					}
					FSbuffer.clear(); //wipe clean

					
					
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
			System.out.println("Before: raw data: "+line);
			String [] zkData = line.split(" ");
			JobTracker_host=zkData[0];
			port=Integer.parseInt(zkData[1]);
			System.out.println("After: getdata host,port"+JobTracker_host+port);
		} catch (KeeperException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (InterruptedException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} //stat?
    
    }
    
}
private void fscheckpath() {
    Stat stat = zkc.exists(fsPath, fswatcher);
    
    if (stat == null) {              // znode doesn't exist; 
        System.out.println("Error: FileServer does not exist " + myPath);
        return;
        
    }else{
	    //if JobTracker exists, get data - hostname
	    Z = zkc.getZooKeeper();
	    try {
	    	System.out.println("getdata");
			byte[] data = Z.getData(fsPath, false, null);			
			String line = new String (data);
			System.out.println("Before: raw data: "+line);
			String [] zkData = line.split(" ");
			FileServer_host=zkData[0];
			FileServer_port=Integer.parseInt(zkData[1]);
			System.out.println("After: getdata host,port"+FileServer_host+FileServer_port);
		
		
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
	Worker W = new Worker(args[0]);

	try {
	            //Thread.sleep(5000);
	 } catch (Exception e) {}
	 System.out.println("checkpath");
	 W.checkpath(); // re-enable the watch
	 W.fscheckpath(); // re-enable the watch       
	        
	 System.out.println("socket connection to JobTracker");
	        /*set up input and output streams of JobTracker*/
	        try {
				socket = new Socket(JobTracker_host,port); //JT socket
				fromJT = new ObjectInputStream(socket.getInputStream());
				toJT = new ObjectOutputStream(socket.getOutputStream());
						
			} catch (UnknownHostException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			} catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
	        
	        System.out.println("send JOB_CONNECT_WORKER to jobtracker");
	        /*Attempt to connect to JobTracker*/
	        ClientPacket packetToJobTracker = new ClientPacket();
	        packetToJobTracker.type = ClientPacket.JOB_CONNECT_WORKER;
	        try {
				toJT.writeObject(packetToJobTracker);

	        
				/*block till connection ack received*/
				ClientPacket packetFromJobTracker = new ClientPacket();
				
				packetFromJobTracker = (ClientPacket) fromJT.readObject();

				if(packetFromJobTracker.type == ClientPacket.JOB_CONNECT_ACK_WORKER){
					System.out.println("Connected to Job Tracker successfully!!");
					//record my part_id
					mypart_id=packetFromJobTracker.part_id;
					myos_id=packetFromJobTracker.os_id;
					System.out.println("part ID assigned: "+mypart_id+"OS id:"+myos_id);
					//create node:
					
					try {
					ZooKeeper zk = zkc.getZooKeeper();
					String workerpath=workerPath+"/"+mypart_id + "-";
					System.out.println("Creating worker with path: " + workerpath);//ADDED
					Code ret = zkc.create(
							workerpath,         // Path of znode
							null,//myos_id.toString(),           // Data not needed.
							CreateMode.EPHEMERAL   // Znode type, set to EPHEMERAL.
							);
					if (ret == Code.OK) System.out.println("Worker Node created");
					else System.out.println("Worker node creation error");
					} catch(Exception e) {
						System.out.println("Make node:" + e.getMessage());
					}

				}else{
					System.out.println("Error: Connection to Job Tracker failed");
					return;
				}
	        
	        //connect to file server
			System.out.println("Set up file server connection");
			fssocket = new Socket(FileServer_host,FileServer_port);
			fromFS = new ObjectInputStream(fssocket.getInputStream());
			toFS = new ObjectOutputStream(fssocket.getOutputStream());
			System.out.println("FS connection set up done");
			} catch (ClassNotFoundException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			} catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
			
			
			/*handle incoming packets from FS*/
	        (new Thread("Receive From FS"){
				public synchronized void run(){	
					ClientPacket packetFromFS = new ClientPacket();
					System.out.println("Original thread to listen to FS created!");						

					try {
						while((packetFromFS = (ClientPacket) Worker.fromFS.readObject())!=null){
							/*process packet*/
							if(packetFromFS.type==ClientPacket.PARTITION_REPLY){
								//if(waitFS.hash.equals(packetFromFS.hash)){
								//System.out.println("[waitFS]Cleared, hash: "+packetFromFS.hash);
								waitFS=null;
								FSbuffer.remove(packetFromFS.hash);

								//}
								
								System.out.println("Received Response from FS");
								//try{ Thread.sleep(5000); } catch (Exception e) {}
								//This else if block is entered by a different thread
								//extract partition from FS in packet.
								String[] mypartition = packetFromFS.partition;
								//extract hash from packet, forwarded by File Server
								String targethash = packetFromFS.hash;
								
								//mypartition is padded with nulls
								String ans=null;    
								for (String word: mypartition){
									if (word==null){
										break; //catches early termination
									}
									String hash = getHash(word);
									if (hash.equals(targethash)){
										System.out.println("Hash found for requested hash, word is "+word);
										//TODO: Respond to JobTracker
										//System.out.println("Hash of r41nb0w: "+hash);
										ans=word;
										break;//assume no repeat
									}
								}
								
								if (ans==null){
									System.out.println("Hash not found");
									
								}
								packetFromFS.partition=null; //wipe partition to save space
								packetFromFS.type=ClientPacket.WORKER_REPLY;
								packetFromFS.password=ans;	
								
								
								Worker.toJT.writeObject(packetFromFS);
								System.out.println("Responded to Job Tracker with ans = "+ ans);
							}
						}
					} catch (SocketException e) {
						// TODO Auto-generated catch block
					
						e.printStackTrace();
					}catch (EOFException e) {
						// TODO Auto-generated catch block
						//FS disconnected
						e.printStackTrace();
					} catch (ClassNotFoundException e) {
						// TODO Auto-generated catch block
						e.printStackTrace();
					} catch (IOException e) {
						// TODO Auto-generated catch block
						
						e.printStackTrace();
					} catch (NullPointerException e) {
						// TODO Auto-generated catch block
						e.printStackTrace();
					} catch (Exception e) {
						// TODO Auto-generated catch block
						e.printStackTrace();
					} 
					System.out.println("Original thread to listen to FS EXIT\n");
				}				
			}).start();
		ClientPacket packetFromClient;
		
		//while (( packetFromClient = (ClientPacket) fromJT.readObject()) != null) {
		while (true){
		try {
		while (( packetFromClient = (ClientPacket) fromJT.readObject()) != null) {
			System.out.println("Packet Received from JT");
			if ((packetFromClient.part_id==mypart_id)&&(packetFromClient.type==ClientPacket.JOBTRACKER_REQUEST)){
				//forwards packet to file server
		    	System.out.println("job detected for id: "+mypart_id);

				toFS.writeObject(packetFromClient);
				//System.out.println("[waitFS]set with hash: "+packetFromClient.hash);
				waitFS=packetFromClient;
				FSbuffer.put(packetFromClient.hash,waitFS);
		    	System.out.println("Sent packet to FS");

		    }

		}
		}  catch (NullPointerException e){
			break;
		} catch (StreamCorruptedException e){
			try{
			fromJT.close();
			break;
			}
			catch (IOException ioe){
				e.printStackTrace();
			}
		} catch (EOFException e){
		} catch (ClassNotFoundException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
			break;
		} catch (IOException e) {
			e.printStackTrace();
			break;
			
		}
		
		}
		
		System.out.println("Main JT listener thread exiting! \n");
		/*
		try{
			//cleanup when client exits
			fromJT.close();
			toJT.close();
			fromFS.close();
			toFS.close();
			socket.close();
		} catch (IOException e){
		
		}
		*/
		while(true); //keep worker thread alive
	}
	public static String getHash(String word) {

        String hash = null;
        try {
            MessageDigest md5 = MessageDigest.getInstance("MD5");
            BigInteger hashint = new BigInteger(1, md5.digest(word.getBytes()));
            hash = hashint.toString(16);
            while (hash.length() < 32) hash = "0" + hash;
        } catch (NoSuchAlgorithmException nsae) {
            // ignore
        }
        return hash;
    } 
	 
}
