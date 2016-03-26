import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.UnknownHostException;
import java.io.*;
import java.net.*;
import java.util.*;

import org.apache.zookeeper.KeeperException;
import org.apache.zookeeper.ZooKeeper;


public class JobTrackerThread extends Thread{

	private String zk_host;
	private Socket socket;
	private Socket workersocket;
	private ZkConnector zkc;
	private ZooKeeper Z;
	private ObjectInputStream input = null;
	private static String workerPath = "/Worker";
	
	private static ObjectOutputStream client_output = null;
	private static ObjectInputStream worker_input = null;
	private static ObjectOutputStream worker_output = null;
	private static String hash;
	private static String Dictionary_size = "/DictSize";	//set by file system
	private static String Worker_count = "/WorkerCount";	//periodically updated by each worker joining
	private static int job_id;
	private static int worker_id=0;
	private static int client_id=0;//ADDED
	public volatile static Map<Integer, Integer> job_finished=new HashMap<Integer, Integer>();	//ADDED2 1-finished , 2-progress
	public volatile static Map<Integer, ClientPacket> Query=new HashMap<Integer, ClientPacket>();//ADDED2
	
	public volatile static Map<Integer, Integer> jobID_tracker=new HashMap<Integer, Integer>();	

	public volatile static ArrayList<Integer> deadID_collector=new ArrayList<Integer>();	

	private static int numworkers=0;
	private static int cur_workerJobID=0;
	private int myOSid=0;
	
	public JobTrackerThread(String zk_host, Socket socket, ZkConnector zkc, int job_id){
		//constructor
		
		System.out.println("Starting Init");
		try {
		this.socket = socket;
		this.zk_host = zk_host;
		this.zkc = zkc;
		Z = zkc.getZooKeeper();
		this.job_id = job_id;

		client_output = new ObjectOutputStream(socket.getOutputStream());
		//add all to broadcast queue
		JobTracker.OScount++; //OScount serves as ID
		myOSid=JobTracker.OScount;
		JobTracker.workersOS.put(JobTracker.OScount,client_output);
		
		input = new ObjectInputStream(socket.getInputStream());
	
		} catch (UnknownHostException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		System.out.println("Finishing Init, total OS: "+JobTracker.workersOS.size());
	}
	
	public static void broadcast(ClientPacket ToSend){
		
			System.out.println("Broadcasting packets to (num clients): "+JobTracker.workersOS.size());
			for (ObjectOutputStream output:JobTracker.workersOS.values()){
				try {
					output.flush();
					output.writeObject(ToSend);	
				} catch (SocketException e){
				
				} catch (IOException e) {
					//e.printStackTrace();
				} 
			}

		return;		
	}
	
	public void job_has_finished(ClientPacket packetFromClient){		//ADDED2
		if (packetFromClient.password!=null){ //if password found
			System.out.println("PW found from worker with part id: "+packetFromClient.part_id);
			JobTracker.reply_collector.remove(packetFromClient.job_id);
			job_finished.put(packetFromClient.client_id, 1);//VIN:ADDED
			job_id++; //advance job id
			packetFromClient.type=ClientPacket.PASSWORD_FOUND;
			System.out.println("Informing client that answer found!! :"+packetFromClient.password);
			broadcast (packetFromClient);
		}else if(JobTracker.reply_collector.containsKey(packetFromClient.job_id)){
			//password not found in partition
			int cur_numworkers=JobTracker.reply_collector.get(packetFromClient.job_id);
			cur_numworkers--;
			System.out.println("PW not found from worker with part id: "+packetFromClient.part_id +", num replies left:" +cur_numworkers);
			if (cur_numworkers<=0){
				JobTracker.reply_collector.remove(packetFromClient.job_id);
				job_finished.put(packetFromClient.client_id, 1);//VIN:ADDED
				job_id++; //advance job id
				packetFromClient.type=ClientPacket.PASSWORD_NOT_FOUND;
				packetFromClient.password = null;
				System.out.println("Informing client that answer not found");
				broadcast (packetFromClient);
			}
			else{
				JobTracker.reply_collector.put(packetFromClient.job_id,cur_numworkers);
			}
			
			
		}
		Query_set(packetFromClient.client_id, packetFromClient);
		
	}
	

	public synchronized ClientPacket Query_get(int id){
		
		return Query.get(id);
	}
	
	public synchronized void Query_set(int id, ClientPacket p){
		
		Query.put(id, p);
	}
	
	public int create_tasks(String hash, int id, int workerNum){	//ADDED

			int size = 265752;
			int partitionSize = (int) Math.ceil(size/workerNum);	//TODO: could mess up because of decimals. redo this!!
			
			int part_id = 0;
			int start = 0;
			int fin = partitionSize;
			for(int i =0; i<workerNum; i++){
			
				ClientPacket PacketToWorker = new ClientPacket();
				PacketToWorker.type = ClientPacket.JOBTRACKER_REQUEST;
				PacketToWorker.start = start;
				PacketToWorker.fin = fin;
				PacketToWorker.hash = hash;
				PacketToWorker.job_id = job_id;
				PacketToWorker.client_id = id; //ADDED
				part_id++;
				
				while (deadID_collector.contains(part_id)){
					System.out.println("skipped partition with id: "+part_id);
					part_id++; //skip dead ids	
				}
				
				PacketToWorker.part_id = part_id; //part_id starting at 1

				System.out.println("Sending packet to worker for processing with hash+"+PacketToWorker.hash+
					"part_id: "+PacketToWorker.part_id+" range from: "+PacketToWorker.start+" to: "+PacketToWorker.fin);
				
				
				broadcast(PacketToWorker);
				start = start +partitionSize;
				fin = fin + partitionSize;
				
			}
			return job_id;
	}		
	
	private static synchronized void incrementjobID(){
		cur_workerJobID++;
	}
	
	public synchronized void run(){
		try {
		ClientPacket packetFromClient = new ClientPacket();
		
		 
	        
		
		while((packetFromClient = (ClientPacket) input.readObject())!=null){
				//Implementation note: this loop logic is shared by all threads.
				//each thread may be spawned by a worker or a client but the packet type will be used to distinguish
				ClientPacket packetToClient = new ClientPacket();
				
				if(packetFromClient.type == ClientPacket.JOB_CONNECT){
					//packet from Client
					System.out.println("Connection from client ACKed at OSid "+myOSid);
					packetToClient.type = ClientPacket.JOB_CONNECT_ACK;
					packetToClient.client_id = client_id++;		//ADDED
					System.out.println("ID assigned to client "+packetToClient.client_id);//ADDED
					//client_output.writeObject(packetToClient);
					JobTracker.workersOS.get(myOSid).writeObject(packetToClient);
					//broadcast(packetToClient);
				}else if(packetFromClient.type == ClientPacket.JOB_CONNECT_WORKER){
					//increment number of workers
					incrementjobID();
					//packet from Client
					System.out.println("Connection from Workers ACKed, ID assigned: "+cur_workerJobID);
					//assign part ID to worker
					//packetToClient.part_id=cur_workerJobID; //starts at 1, just like part ID
					worker_id++;
					packetToClient.part_id=worker_id;
					packetToClient.os_id=myOSid;
					//record the part_id of this incase of failure 
					jobID_tracker.put(myOSid,worker_id);
					packetToClient.type = ClientPacket.JOB_CONNECT_ACK_WORKER;
					JobTracker.workersOS.get(myOSid).writeObject(packetToClient);
					numworkers++;
					//broadcast(packetToClient);
					System.out.println("Connection from worker ACK sent to OSid: "+myOSid+"total num workers"+numworkers);
					
					
					
					
				}else if(packetFromClient.type == ClientPacket.JOB_REQUEST){
					//packet from Client
					hash = packetFromClient.hash;
					/*
					if (hash.equals("r")){ //debug value for rainbow
					//rainbow's hash: c463be62fd5252c7568d7bafd3cc4a55
						hash="c463be62fd5252c7568d7bafd3cc4a55";
					}
					else if (hash.equals("f")){ //debug value for first (doorstop)
						hash="1d6571ec4951c984b78784b36b10c03e";
					}
					else if (hash.equals("l")){ //debug value for last (ladders)
						hash="dd81e2cc883fc988c91bd399f87dcb07";
					}
					*/
					
					//PacketToWorker.hash=hash;
					System.out.println("Request received with hash "+hash+" and client ID "+packetFromClient.client_id);
					//try{ Thread.sleep(5000); } catch (Exception e) {}
					job_finished.put(packetFromClient.client_id, 2); //ADDED
					int job_id_sent=create_tasks(hash,packetFromClient.client_id,numworkers); //ADDED2				
					
					JobTracker.reply_collector.put(job_id_sent,numworkers);
					job_id++; //advance job id
					ClientPacket p = new ClientPacket();
					p.type = ClientPacket.JOB_REQUEST_ACK;
					p.client_id=packetFromClient.client_id;//ADDED

				}else if (packetFromClient.type == ClientPacket.WORKER_REPLY){
					//only process if we are expecting this packet
					//job_finished.put(packetFromClient.client_id, 1);	//ADDED2
					job_has_finished(packetFromClient);
					
				}

			}
		} catch (EOFException e) {
			//handle invalidated part ID
			int dead_idnum=jobID_tracker.remove(myOSid);
			System.out.println("Handling dead worker with OSid: "+myOSid+" and id num: "+dead_idnum);
			//TODO: Reassign task of dead worker
			deadID_collector.add(dead_idnum);
			JobTracker.workersOS.remove(myOSid);

		
		} catch (ClassNotFoundException e) {
			e.printStackTrace();
		} catch (IOException e) {
			e.printStackTrace();
		}
		numworkers--;
		System.out.println("Handling lost jobs, remaining numworkers: "+numworkers);
		for(Map.Entry<Integer, Integer> entry : job_finished.entrySet()){
				//System.out.println("In Socket Excep: client_id:  "+entry.getKey()+" value: "+entry.getValue());//+ " size- "+list.size());
				if(entry.getValue()==2){
					int job_id=create_tasks(hash, entry.getKey(), numworkers);	
					JobTracker.reply_collector.put(job_id,numworkers);					//list.size()
					job_finished.put(entry.getKey(), 2);
				}				
		}
	}
	
}
