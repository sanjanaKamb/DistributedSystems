import java.awt.event.KeyEvent;
import java.io.*;
import java.net.*;
import java.util.*;

/*GameClientThread: This class consists of a seperate thread that is responsible
 *for acting as the intermediate between the server and the GUI
 */

public class GameClientThread extends Thread {
	
	private Maze maze = null;
	private Socket LookupSocket = null;
	private ObjectOutputStream LookupOOS = null;
	private ObjectInputStream LookupOIS = null;
	public static volatile Map<String, Client> map = null; //TODO: changed type						//A hashmap with a client's name as they key and the Client object as the value
	private volatile static Queue<GamePacket> fifo = null;					//A static fifo queue consisting of all the packets received from the server
	private static String LocalName = null;
	private volatile static int cur_seqnum=-1;
	private boolean quitted=false;
	private MazeImpl mazeimpl = null;
	private GUIClient thisguiClient=null; //TODO: may cause bug
	private static volatile int num_addedclients=0;
	//create an object to store the output stream of registered clients
	//public volatile static HashSet<ObjectOutputStream> clientsOS=new HashSet<ObjectOutputStream>();
	public volatile static Map<String, ObjectOutputStream> clientsOS=new HashMap<String, ObjectOutputStream>();
	
	//create an object to store the input stream of registered clients
	//public volatile static HashSet<ObjectInputStream> clientsIS=new HashSet<ObjectInputStream>();
	public volatile static Map<String,ObjectInputStream> clientsIS=new HashMap<String,ObjectInputStream>();
	
	public volatile static int myport=0;
	
	public volatile static long clock = 0;
	public volatile static long clock_reply_count = 0;
	public volatile static long common_timestamp = 0;
	
	public volatile static List<Time_request> request_array = new ArrayList<Time_request>(); 			//create an array of requests after own timestamp..
	public volatile static List<Time_request> request_local = new ArrayList<Time_request>(); 			//create an array of local requests
	
	
	/*Broadcasts packet to all clients*/
	public static void broadcast(GamePacket ToSend){
		assert(ToSend.name==LocalName); //TODO this is to prevent bugs
		
		/* broadcast packets to client */
		try {
			for (ObjectOutputStream output:clientsOS.values()){
					//System.out.println("Sending Packet from "+ToSend.name+" with type: "+ToSend.type);
					output.writeObject(ToSend);
			}
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		
		return;
	}
	
	
	/*sends local clock timestamp to all clocks*/
	public static void clock_check(){
		GamePacket packet = new GamePacket();
		packet.type = GamePacket.PACKET_CHECK_CLOCK;
		packet.name = LocalName;
		common_timestamp = clock;
		packet.clock_timestamp = common_timestamp;			//broadcast current timestamp of clock
		System.out.println("Broadcasting clock check from "+packet.name+" with timestamp" + common_timestamp);
		broadcast(packet);
		
	}
	
	public static void check_my_clock(GamePacket fromQueue){
		
		//called on receiver side to check clock
		GamePacket packet = new GamePacket();
		packet.name = LocalName;
		if (fromQueue.clock_timestamp < clock){				//if the clock of another client has a higher value that this client									
			packet.clock_timestamp = clock; 				//replace timestamp with the latest one/current client clock's
			packet.type =GamePacket.PACKET_CHECK_CLOCK_DACK ;			//send a reject/DACK							
			System.out.println("Clock check received, sending DACK with my higher clock: "+ clock);
			//>
		}else{ //=
			//clock fromQueue higher or equal to mine. If higher, my clock will be synched upon receiving PACKET_NEW_TIME
			System.out.println("Clock check received, sending ACK");
			packet.type =GamePacket.PACKET_CHECK_CLOCK_ACK ;			//send an ACK								
		}
		
		System.out.println("Sending clock ACK packet back to "+fromQueue.name);
		packet.original_name=fromQueue.name;
		broadcast (packet);
		//(clientsOS.get(fromQueue.name)).writeObject(packet); //TODOMEEE: broadcast ?? //TODO: Vin: This doesn't work.			
		
	}
	
	public static void request_received(GamePacket fromQueue){
		
		//element on top of queue request_local.get(0).timestamp
		if(!request_local.isEmpty()){
			if(request_local.get(0).timestamp < fromQueue.clock_timestamp){				//if the request in the packet was after any of the local requests, add it to request_array
				System.out.println("QUEUEing CS REPLY");
				request_array.add(new Time_request(fromQueue.clock_timestamp, fromQueue));
				
				//sort the request array
				Collections.sort(request_array, new Comparator<Time_request>() {
					@Override
					public int compare(Time_request o1, Time_request o2) {
						// TODO Auto-generated method stub
						return ((Long)o1.timestamp).compareTo(o2.timestamp);
					}			
				});
				
				//need to reverse to sort ascending order									
				Collections.reverse(request_array);	
				
				return;
			}
		}
		/*else if(request_local.get(0).timestamp == fromQueue.clock_timestamp){
			//assert(request_local.get(0).timestamp != fromQueue.clock_timestamp); //this shoudnt happen
			//this may happen when broadcast to self
		}*///else{
			/*
			GamePacket csReplyPacket= new GamePacket();
			csReplyPacket.type = GamePacket.PACKET_CS_REPLY;
			csReplyPacket.name = LocalName;								//the timestamp remains the same, which is crucial
			csReplyPacket.original_name=fromQueue.name;
			csReplyPacket.clock_timestamp = fromQueue.clock_timestamp;
			*/
			fromQueue.type = GamePacket.PACKET_CS_REPLY;
			fromQueue.original_name = fromQueue.name;
			fromQueue.name = LocalName;	
			System.out.println("Sending CS REPLY");
			broadcast(fromQueue);
			/*try { //TODO: Vin: This is not sending packets correctly. Im changeing it to broadcast instead
				clientsOS.get(oldname).writeObject(fromQueue);	//get the outputstream of corressponding client and send back a reply
			} catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();								
			}*/
		//}
		
	}
	
	public static void reply_received(GamePacket fromQueue){
		//This function handles an OK request to enter CS and increments the reply count
		for(Time_request m: request_local){
			if(m.timestamp==fromQueue.clock_timestamp){
				int index = request_local.indexOf(m);				//increment the counter that counts the number of replies received
				m.reply_count++;
				request_local.set(index, m);
				break;
			}								
		}
		
		//TODO: VIN: added: each time reply received, check the queue head if it can be broadcasted
		boolean wasBroadcasted=false;
		wasBroadcasted=checkQueueHead();
		if (wasBroadcasted){
			System.out.println("All ACKs received, head of queue broadcasted");
		}
	}
	
	/*adds the local GUIClient to the hashmap after notifying the server*/
	public int addGUIClient(GUIClient guiClient){		
		
		/*Section1: Always removes packets from the queue, if available. Start this thread early*/
		(new Thread("Queue Reader"){
			public synchronized void run(){
				
				while(true){
					//System.out.println("checking fifo queue");
					GamePacket fromQueue = null;
					if((fromQueue = removeFromQueue())!=null){
						//System.out.println("Removed a packet from queue of type: "+fromQueue.type);
						if(fromQueue.error!=GamePacket.PACKET_NULL)	//if packet is null for errors
							checkForErrors(fromQueue);
						else if(fromQueue.type==GamePacket.PACKET_ADD_CLIENT){ //received by clients in game
							System.out.println("PACKET_ADD_CLIENT from"+fromQueue.name);
							respondToNewClient(fromQueue);
						}else if(fromQueue.type==GamePacket.PACKET_ADD_CLIENT_ACK){ //received when I want to join game	
							System.out.println("PACKET_ADD_CLIENT_ACK from"+fromQueue.name);
							addRemoteClients(fromQueue);
						}else if((fromQueue.type==GamePacket.PACKET_ADD_CLIENT_MYLOC)&&(!fromQueue.name.equalsIgnoreCase(LocalName))){ //received when I want to join game	
							System.out.println("PACKET_ADD_CLIENT_MYLOC from"+fromQueue.name);
							addOneRemoteClient(fromQueue);
						}else if(fromQueue.type==GamePacket.PACKET_STORE){ 			//if packet contains keypress
							System.out.println("PACKET_STORE from"+fromQueue.name);
							FromServer(fromQueue);
						}else if(fromQueue.type==GamePacket.PACKET_ADD_CLIENT){		//if packet contains information to add new remote client
							System.out.println("PACKET_ADD_CLIENT from"+fromQueue.name);
							addRemoteClient(fromQueue);
						}else if(fromQueue.type == GamePacket.PACKET_DEAD_CLIENT){	//if client is dead
							System.out.println("PACKET_DEAD_CLIENT Updating location of dead client to "+fromQueue.point.getX()+" "+fromQueue.point.getY());
							Client target = map.get(fromQueue.target);			
							Client source = map.get(fromQueue.source);
							//syncs the clients on different machines
							mazeimpl.UpdateClientMap(source, target, fromQueue); //this new function is implemented in MazeImpl that updates scoreboard and maze
							
						}else if (fromQueue.type == GamePacket.PACKET_CLIENT_QUIT){	//if client quits
							//remove remote client from map and hashmap
							Client clientremove= map.get(fromQueue.name);
							map.remove(clientremove);
							mazeimpl.removeClient(clientremove);
							
						}else if(fromQueue.type == GamePacket.PACKET_CHECK_CLOCK){
							System.out.println("PACKET_CHECK_CLOCK from"+fromQueue.name);
							check_my_clock(fromQueue);													//TODOMEEEE: Need to send name when sending
						}else if((fromQueue.type == GamePacket.PACKET_CHECK_CLOCK_ACK)&&(fromQueue.original_name.equalsIgnoreCase(LocalName))){
							System.out.println("PACKET_CHECK_CLOCK_ACK from"+fromQueue.name);
							clock_reply_count++;												//keep track of the number of ACKs received from clients
							
						}else if((fromQueue.type == GamePacket.PACKET_CHECK_CLOCK_DACK)&&(fromQueue.original_name.equalsIgnoreCase(LocalName))){
							System.out.println("PACKET_CHECK_CLOCK_D_ACK from"+fromQueue.name);
							if (common_timestamp<fromQueue.clock_timestamp){ //TODO: Vin: ADDED
								common_timestamp = fromQueue.clock_timestamp; //calculate the highest clock timestamp
							}
							clock_reply_count++;
							
						}else if(fromQueue.type == GamePacket.PACKET_NEW_TIME){
							System.out.println("PACKET_NEW_TIME from"+fromQueue.name);
							clock = fromQueue.clock_timestamp;
							System.out.println(LocalName+" recv new time, updating clock to: " +clock); //should be harmless if broadcast to self
							
						} else if(fromQueue.type == GamePacket.PACKET_CS_REQUEST){
							System.out.println("PACKET_CS_REQUEST from"+fromQueue.name);
							request_received(fromQueue);
							
						}else if((fromQueue.type == GamePacket.PACKET_CS_REPLY)&&(fromQueue.original_name.equalsIgnoreCase(LocalName))){
							System.out.println("PACKET_CS_REPLY from"+fromQueue.name);
							//check if this reply is for me
							reply_received(fromQueue);
						}
						
					}
					else{
						assert(fifo.isEmpty());
						//System.out.println("Queue Empty");
					}
					
				}								
				
			}				
		}).start();
		    	
		LocalName =  guiClient.getName();

		try {
		thisguiClient=guiClient;
		LocalName =  guiClient.getName();
		System.out.println("My name is "+LocalName);
		//Client wants to join. First connects to naming service to request list of port num and hostnames
		//packetToServer.point = guiClient.getPoint();
		//packetToServer.orientation = guiClient.getOrientation();
		GamePacket packetToServer = new GamePacket();
		packetToServer.name = guiClient.getName();
		packetToServer.type= GamePacket.LOOKUP_REQUEST; //sends a lookup request
		packetToServer.hostname = InetAddress.getLocalHost().getHostName();
		packetToServer.port = myport;
		int received_ack = 0;
		
		while(received_ack == 0){
				LookupOOS.writeObject(packetToServer); //send packet to lookup server							//sends packet to ther server about the new client
				GamePacket packetFromServer = new GamePacket();
				while((packetFromServer = (GamePacket) LookupOIS.readObject()) !=null){ //read from lookup input stream
					
					
					if (packetFromServer.type == GamePacket.LOOKUP_REPLY){//if receive lookup reply from servers
											
						if (packetFromServer.addrmap==null){//checks if I'm first client
							return 0; //if so, there is nothing to do
						}
						
						//there are other clients in the game, add clients to clientsOS, clientsIS	
						Map <String,String[]> addrmapcpy= new HashMap<String,String[]>(packetFromServer.addrmap); //create a local copy
						Iterator it = addrmapcpy.entrySet().iterator();
						while (it.hasNext()) {
								//For each client, create OS and IS
						        Map.Entry pairs = (Map.Entry)it.next();
						        String clientname= (String) pairs.getKey();
						        String[] inetAddr= (String[]) pairs.getValue(); 
						        assert (inetAddr[0]==clientname);
						        //Implementation note: did not use the client name, since the protocol will be broadcasting
						        String hostname=inetAddr[2];

						        int port=Integer.parseInt(inetAddr[1]);
						        System.out.println("I'm new client, Adding to OS/IS new client named "+clientname+ " at host"+hostname+" at port: "+port);

								Socket clientSocket = new Socket(hostname,port); //TODO: check: creates new socket for each client
						    	ObjectOutputStream ClientOS = new ObjectOutputStream(clientSocket.getOutputStream());
						        ObjectInputStream ClientIS = new ObjectInputStream(clientSocket.getInputStream());
						        
						        
						        //create listening IS threads for each client
						        //new ClientListenerThread(ClientIS).start(); //TODO: do we?

						        //add OS and IS to hashmap
						        clientsOS.put(clientname,ClientOS);
						        clientsIS.put(clientname,ClientIS);
						        it.remove();
						        System.out.println("I'm new client, Adding to OS/IS new client named "+clientname+ " at host"+hostname+" at port: "+port+" total num OS/IS: "+clientsOS.size()+" , "+clientsIS.size());
						        //Implementation note: clientsOS and clientsIS is one client less than total num of clients in game because they dont include  GUI player
						}
						assert (clientsOS.size()==clientsIS.size()); //TODO: Potential bug: size of these two are larger than expected.
				        System.out.println("I'm new client, total remote clients in game: "+clientsIS.size());

						//broadcast that a new client (me) is joining the game
						GamePacket packetToClients = new GamePacket();
						packetToClients.name = guiClient.getName();
						packetToClients.type = GamePacket.PACKET_ADD_CLIENT; //set type to add client
						//report my address
						packetToClients.hostname = InetAddress.getLocalHost().getHostName();
						packetToClients.port = myport;
						//when other clients receive this packet, it is expected that they return their location
						broadcast(packetToClients);
						//this parts completes and continues when the client received ADD_CLIENT_ACKs from all other clients
				        System.out.println("I'm new client, Waiting for all ACKs");

						//spin until all ACKs received
						while (num_addedclients!=packetFromServer.addrmap.size());
		        	
						//reset counter. Note that this counter should never be used again
		        		//num_addedclients=0;
		        		System.out.println("I'm new client, All ACKs Received");
		        		
					    //Add myself to output stream to ensure broadcast to self
		    			//Socket mysocket = new Socket(InetAddress.getLocalHost().getHostName(), myport);
		    			//ObjectOutputStream myOOS = new ObjectOutputStream(mysocket.getOutputStream());
					    //clientsOS.put(LocalName,myOOS);
				        
				        
						received_ack = 123;
						break;
					}
				}
		}
		} catch (IOException e) {
			e.printStackTrace();
		} catch (ClassNotFoundException e) {
			e.printStackTrace();
		}
		
		//ADDED: before leaving, add self to broadcast queue
		try {
			Socket mySocket = new Socket(InetAddress.getLocalHost().getHostName(),myport); //TODO: check: creates new socket for each client
			ObjectOutputStream ClientOS = new ObjectOutputStream(mySocket.getOutputStream());
			clientsOS.put(LocalName,ClientOS);
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		
		
	return 0;
		
	}
		
	private void addRemoteClients(GamePacket fromExistingClients){
		//called each time when receive ADD_CLIENT_ACK from other clients
		System.out.println("ADD_CLIENT_ACK detected");
		if (!map.containsKey(fromExistingClients.name)){//only add client that doesn't already exist
			//TODO: assumed here that direction==orientation. confirm.
			RemoteClient rm = new RemoteClient(fromExistingClients.name);
			maze.addRemoteClient(rm,fromExistingClients.mylocation.p,fromExistingClients.mylocation.d);//also, add them onto the maze		
			map.put(fromExistingClients.name, (Client)rm);
			num_addedclients++;
			System.out.println("I'm new client, ADD_CLIENT_ACK received, added remote client "+fromExistingClients.name);
			//System.out.println("Total number of added clients is "+num_addedclients+" Expecting: "+packetFromServer.addrmap.size());
		}
		
	}

	private void respondToNewClient(GamePacket fromNewClient){
		System.out.println("I'm old client, 1. ADD_CLIENT received, responding to client "+fromNewClient.name+" at host "+fromNewClient.hostname+"at port "+fromNewClient.port);

		try {
			String hostname=fromNewClient.hostname;
	        int port=fromNewClient.port;
	        //Handled in PortListenerHandlerThread //TODO: Did we duplicate????
	        //create output and input streams for client
			Socket clientSocket;		
			clientSocket = new Socket(hostname,port); //TODO: check: creates new socket for each client
	    	ObjectOutputStream newClientOS = new ObjectOutputStream(clientSocket.getOutputStream());
	        ObjectInputStream newClientIS = new ObjectInputStream(clientSocket.getInputStream());
	        
	        
	        //create new thread to listen to this new client
	        //new ClientListenerThread(newClientIS).start();

	        //add OS and IS to hashmap
	        clientsOS.put(fromNewClient.name, newClientOS);
	        clientsIS.put(fromNewClient.name, newClientIS);
	        
	        //create response packet with my location
	        GamePacket toNewClient=new GamePacket();
	        toNewClient.type = GamePacket.PACKET_ADD_CLIENT_ACK;
	        toNewClient.name = LocalName;
	        toNewClient.mylocation=new location(map.get(LocalName).getPoint(),map.get(LocalName).getOrientation()); //TODO: get current point and direction
	        broadcast(toNewClient);
	        
		} catch (UnknownHostException e) {
			e.printStackTrace();
		} catch (IOException e) {
			e.printStackTrace();
		}

		
		
	}
	
	
	private void addOneRemoteClient(GamePacket fromNewClient){
		System.out.println("I'm old client, 2. ADD_CLIENT_MYLOC received, adding client "+fromNewClient.name+"at position "+fromNewClient.mylocation.p+"with orientation "+fromNewClient.mylocation.d);

		//called when receive ADD_CLIENT from new joining client
		RemoteClient rm = new RemoteClient(fromNewClient.name);
		//TODO: assumed here that orientation=direction
		maze.addRemoteClient(rm,fromNewClient.mylocation.p,fromNewClient.mylocation.d);//also, add them onto the maze		
		map.put(fromNewClient.name, (Client)rm);
	}
	
	
	public void addMaze(Maze maze,MazeImpl mazeimpl){				//add maze and mazeimp objects to class from mazewar

		this.maze = maze;
		this.mazeimpl = mazeimpl;
	}
	
	public GameClientThread(String hostname, int port, int my_port){				//Constructor - initializes sockets, I/O streams, hashmap, fifo
		
		
		map = new HashMap<String, Client>();
		fifo = new LinkedList<GamePacket>();
		myport=my_port;
	
		try {
			LookupSocket = new Socket(hostname, port);
			LookupOOS = new ObjectOutputStream(LookupSocket.getOutputStream());
			LookupOIS = new ObjectInputStream(LookupSocket.getInputStream());
			
			
		} catch (UnknownHostException e) {			
			System.err.println("ERROR: Don't know where to connect!!");
			e.printStackTrace();
			System.exit(1);
			
		} catch (IOException e) {
			System.err.println("ERROR: Couldn't get I/O for the connection.");
			e.printStackTrace();
			System.exit(1);
		}
		
		
	}

	
	public void addRemoteClient(GamePacket packetFromServer){						//if a new client adds to the game, server sends packet and this method is called
		
	if(!packetFromServer.packet_loc.isEmpty()){										//if map is not empty, add new client
		
		Set<String> keyset = packetFromServer.packet_loc.keySet();
		
			for(String key: keyset){
				location loc = packetFromServer.packet_loc.get(key);
				RemoteClient rm = new RemoteClient(key);
				Point point = loc.p;
				Direction orientation = loc.d;
				if(!key.equalsIgnoreCase(LocalName)&&!(map.containsKey(key))){ 
					//if not me and if not already exist (prevents double add on start up)
					maze.addRemoteClient(rm,point,orientation);				
					map.put(key, (Client)rm);	
				}
				
			}
		}
	}
	
	
	public void SendToServer(KeyEvent e, String name) {					//sends a packet to server containing the keypressed by localclient
		
		GamePacket packetToServer = new GamePacket();
		packetToServer.name = name;
		packetToServer.point = map.get(name).getPoint(); 				//ERROR: Sends CUR position
		packetToServer.orientation = map.get(name).getOrientation(); 	//ERROR: Sends CUR orientation		
															
		if((e.getKeyChar()== 'q') || (e.getKeyChar() == 'Q')) {			//If the user pressed Q, invoke the cleanup code and quit. 
        	packetToServer.key = GamePacket.PACKET_QUIT; 				
            packetToServer.name = name;
            packetToServer.type = GamePacket.PACKET_QUIT;
            try {
	            //TODO: notify all clients that im quitting
	            //notify server im quitting	            
	            LookupOOS.writeObject(packetToServer); 
	            quitted=true;
            	LookupOOS.writeObject(packetToServer); 						//tell server I'm quitting
            	LookupOOS.close(); 											//close I/O streams
            	LookupOIS.close();
            } catch (IOException ex) {
                // TODO Auto-generated catch block
                ex.printStackTrace();
            }
            System.out.println("Shut Down Complete ");
            Mazewar.quit(); 
        	
        // Up-arrow moves forward.
        } else if(e.getKeyCode() == KeyEvent.VK_UP) {
        	packetToServer.key = GamePacket.PACKET_FOWARD;     
                
        // Down-arrow moves backward.
        } else if(e.getKeyCode() == KeyEvent.VK_DOWN) {        	
        	packetToServer.key = GamePacket.PACKET_BACKWARD;
                
        // Left-arrow turns left.
        } else if(e.getKeyCode() == KeyEvent.VK_LEFT) {
        	packetToServer.key = GamePacket.PACKET_LEFT;	
        	
        // Right-arrow turns right.
        } else if(e.getKeyCode()== KeyEvent.VK_RIGHT) {
        	packetToServer.key = GamePacket.PACKET_RIGHT;
        	
        // Spacebar fires.
        } else if(e.getKeyCode() == KeyEvent.VK_SPACE) {
        	packetToServer.key = GamePacket.PACKET_FIRE;
        	
        }else
        	assert(false);
		
      
		//First, check the clock
        clock_check(); //broadcasts current clock timestamp
        System.out.println("waiting for clock acks");
        while(clock_reply_count != map.size());	 //this thread blocks, other receiving thread still run				//make sure the clocks are consistent before broadcast and check if the received ACKs have been received from all clients
        clock_reply_count = 0;
        System.out.println("all clock acks received");
        //common_timestamp = 0; ...?
        
        GamePacket time_packet = new GamePacket();
        time_packet.type = GamePacket.PACKET_NEW_TIME;
        time_packet.clock_timestamp = common_timestamp + 1;
        time_packet.name = name;
        clock = time_packet.clock_timestamp; //clock is the advanced clock					//update local clock
        System.out.println("Sending PACKET_NEW_TIME with time: " + clock);
        broadcast(time_packet);									//broadcast updated timestamp to everyone
        
        //Broadcast Intent to enter CS
        packetToServer.clock_timestamp = clock; //time stamp CS request packet     			
        packetToServer.type = GamePacket.PACKET_CS_REQUEST;		 // packetToServer.type = GamePacket.PACKET_STORE;(BEFORE)

        
        request_local.add(new Time_request(packetToServer.clock_timestamp, packetToServer));	//add request to local request
      
        //sort the request array
		Collections.sort(request_local, new Comparator<Time_request>() {
			@Override
			public int compare(Time_request o1, Time_request o2) {
				// TODO Auto-generated method stub
				return ((Long)o1.timestamp).compareTo(o2.timestamp);
			}			
		});
		
		//need to reverse to sort ascending order									
		Collections.reverse(request_local);	    

        //clock++; //TODO: Vin: no need to double increment the clock?
        broadcast(packetToServer);
        
        
}

	
	public synchronized void FromServer(GamePacket packetFromServer) {			//If packet received from server with keypress, notify GUI of respective clients
		
		String name = packetFromServer.original_name;
		int key = packetFromServer.key;
		Client client = map.get(name);
		GamePacket packetToServer = new GamePacket();
		
        if(key == GamePacket.PACKET_QUIT){										// If the user pressed Q, invoke the cleanup code and quit. 
                Mazewar.quit(); 												//if not quit already, quit
        // Up-arrow moves forward.
        } else if(key == GamePacket.PACKET_FOWARD) {
                client.forward();
             //send updated location to the server
                
        		packetToServer.name = packetFromServer.name;
        		packetToServer.point = map.get(name).getPoint(); 			   	//ERROR: Sends CUR position
        		packetToServer.orientation = map.get(name).getOrientation(); 	//ERROR: Sends CUR orientation
        		packetToServer.type=GamePacket.PACKET_UPDATE_POS;

               // broadcast(packetToServer);																	//TODOMEE: IS THIS EVEN NEEDED??

        // Down-arrow moves backward.
        } else if(key == GamePacket.PACKET_BACKWARD) {
               client.backup();
             //send updated location to the server
	            
	       		packetToServer.name = packetFromServer.name;
	       		packetToServer.point = map.get(name).getPoint(); 				//ERROR: Sends CUR position
	       		packetToServer.orientation = map.get(name).getOrientation(); 	//ERROR: Sends CUR orientation
	       		packetToServer.type=GamePacket.PACKET_UPDATE_POS;
	       		//broadcast(packetToServer);
        // Left-arrow turns left.
        } else if(key == GamePacket.PACKET_LEFT) {
                client.turnLeft();
              //send updated location to the server
                
        		packetToServer.name = packetFromServer.name;
        		packetToServer.point = map.get(name).getPoint(); 				//ERROR: Sends CUR position
        		packetToServer.orientation = map.get(name).getOrientation(); 	//ERROR: Sends CUR orientation
        		packetToServer.type=GamePacket.PACKET_UPDATE_POS;
        		//broadcast(packetToServer);
        // Right-arrow turns right.
        } else if(key == GamePacket.PACKET_RIGHT) {
                client.turnRight();
              //send updated location to the server
                
        		packetToServer.name = packetFromServer.name;
        		packetToServer.point = map.get(name).getPoint(); 				//ERROR: Sends CUR position
        		packetToServer.orientation = map.get(name).getOrientation(); 	//ERROR: Sends CUR orientation
        		packetToServer.type=GamePacket.PACKET_UPDATE_POS;
        		//broadcast(packetToServer);
        // Spacebar fires.
        } else if(key == GamePacket.PACKET_FIRE ) {
        	System.out.println(packetFromServer.name+" fired!");
        	//enforce fire to complete before proceeding
        	client.fire();
        	
        	System.out.println(packetFromServer.name+" firing complete.");   	       
                	
			System.out.println("Broadcasting kill from killer- "+packetFromServer.name);
                	
                	
                	packetToServer.name=LocalName; 						//rename packet to broadcaster
                	packetToServer.source = client.getName(); 			//set killer to the client who fired
                	packetToServer.type = GamePacket.PACKET_DEAD_CLIENT;		
                	packetToServer.target = MazeImpl.dead_player;
                	System.out.println("Target:"+packetToServer.target+" someone died?: "+MazeImpl.someone_died);
                	if (MazeImpl.dead_player_pt==null){
                		//it is okay if deadplayer isn't initialized if I'm not the killer
                		assert(!packetToServer.source.equals(LocalName));
                	}
                	else{
                    	packetToServer.point = new Point (MazeImpl.dead_player_pt); //copy constructor
                    	packetToServer.orientation = map.get(mazeimpl.dead_player).getOrientation();
                	}

																	//Packet sent to server, clean up variables
                	MazeImpl.someone_died = false; 					//set flag down
                	MazeImpl.dead_player = null; 					//reset dead player
                	MazeImpl.dead_player_pt = null;       	
                	//broadcast(packetToServer);                           
        }

}
	

	private void checkForErrors(GamePacket packet){
		if(packet.error == GamePacket.ERR_DUPL_NAME){
            System.out.println("Duplicate name detected.");	
		}
	}
	
	public synchronized static void addToQueue(GamePacket packet){			//Adds packet received from server into fifo
		fifo.add(packet);		
	}
	
	private synchronized GamePacket removeFromQueue(){					//Removes packet from server to process. Packet number sequencing is implemented.
        
		if(!fifo.isEmpty()){
            GamePacket nextpacket=fifo.remove();
            //System.out.println("Removed packet from queue from "+nextpacket.name+" of type: " + nextpacket.type);
            if (nextpacket.error!=0){
                System.out.println("Error detected in packet, but packet will be processed. ERR: "+nextpacket.error);
            }
                     
           /* 
            if ((cur_seqnum==-1) || (((nextpacket.seqnum==0))&&(cur_seqnum==999999999))){
                //initialize the sequence number (cur_seqnum=-1)
            	//re-update cur_seqnum if the sequence number from server wraps around (=0)
                cur_seqnum=nextpacket.seqnum;
                return nextpacket;
            
            }
            else if (nextpacket.seqnum==(cur_seqnum+1)){
                //update current sequence number
                cur_seqnum=nextpacket.seqnum;
                //send packet
                return nextpacket;
            }
            else if (nextpacket.seqnum<=cur_seqnum){
                System.out.println("Error: illogical seqnum");
                //do nothing: drop the packet
               
           
            	return null;
            }
            else if (nextpacket.seqnum>(cur_seqnum+1)){ //future packet 
                //valid sequence number, but out of order.
                //add packet back to end queue
                fifo.add(nextpacket);
                System.out.println("Out of order packet detected, waiting for packet "+(cur_seqnum+1));
                //return null, and wait to extract from queue again
                return null;
                
            }
            else if (nextpacket.seqnum==0){ //cur_seqnum!=max int value
            	//waits for packet with seqnum=max int value
            	fifo.add(nextpacket);
                System.out.println("Out of order packet detected at max int val, waiting for packet "+(cur_seqnum+1));
            	return null;
            }
            else{
                //catches undefined sequence numbers
                System.out.println("ERROR: sequence number overflow");                
                assert(false); //TODO: remove before submission, for debug.
                return null;
            }*/
            
            return nextpacket;
        }
        else{
            return null;
        }
	}
	
	
	public static boolean checkQueueHead(){
			boolean broadcasted=false;
			if (request_local.size()!=0){
			Time_request k  = request_local.get(0); //repetitively get head of queue
			//This implementation checks if all CS_REPLYs are received
			//while(k.reply_count != map.size()){	//if all the replies are received, broadcast to everyone that event can be performed
			//TODO: Vin: changed this condition
			if (k.reply_count == map.size()){	//if all the replies are received, broadcast to everyone that event can be performed
				//TODO: VIN: There was bug, copying packet instead else it doesn't transmit properly
				GamePacket StorePkt = new GamePacket();
				StorePkt.name = LocalName;
				StorePkt.type = GamePacket.PACKET_STORE;
				StorePkt.key = k.packet.key;
				StorePkt.original_name = k.packet.name;
				//Format packet
				
					//k.packet.type = GamePacket.PACKET_STORE;
					//k.packet.original_name = k.packet.name;
					//k.packet.name = LocalName;
				
				broadcast(StorePkt);
				//TODO: VIN: This algorithm implies NO PACKET REORDERING
				//TODO: VIN: ADDED: Remove the packet from request local since its delivered
				request_local.remove(0); //TODO: VIN: confirm: Java documentation implies we dont need to re-sort
				broadcasted=true;
					
					
				//*send replies to everyone in the 
				for(Time_request j: request_array){
					 //if timestamp of requested packet (j) from another client is before than the next packet request(1) of this client, send a reply
					//do the same thing if the next request_local packet is null
					GamePacket CSreply = new GamePacket();
					CSreply.type = GamePacket.PACKET_CS_REPLY;
					CSreply.original_name = j.packet.name;//fromQueue.name; //name of person who requested entry
					CSreply.name = LocalName;	
				
					
					
					if (request_local.get(1) == null){
						j.packet.type = GamePacket.PACKET_CS_REPLY;
						broadcast(CSreply);
						///*try {
						//	clientsOS.get(j.packet.name).writeObject(j.packet); //TODO: Vin: doesn't work
						//} catch (IOException e) {
						//	// TODO Auto-generated catch block
						//	e.printStackTrace();
						//}									
						
					}else if( (j.timestamp < request_local.get(1).timestamp)){
						j.packet.type = GamePacket.PACKET_CS_REPLY;
						broadcast(CSreply);
						///*try {
						//	clientsOS.get(j.packet.name).writeObject(j.packet); //TODO: Vin: doesn't work
						//} catch (IOException e) {
							// TODO Auto-generated catch block
						//	e.printStackTrace();
						//}															
					}
					
					assert(j.timestamp==request_local.get(1).timestamp); //this shouldnt happen
					}
					request_local.remove(k);		//remove the local request from the array
			}
			
		}
		return broadcasted;
	}
	
	
	
	
	public void run(){ //synchronize? synchronizing blocks QueueReader read
		
		/*
				while(true){
					if (request_local.size()!=0){
					Time_request k  = request_local.get(0); //repetitively get head of queue
					//This implementation checks if all CS_REPLYs are received
					//while(k.reply_count != map.size()){	//if all the replies are received, broadcast to everyone that event can be performed
					//TODO: Vin: changed this condition
					if (k.reply_count == map.size()){	//if all the replies are received, broadcast to everyone that event can be performed
						//TODO: VIN: There was bug, copying packet instead else it doesn't transmit properly
						GamePacket StorePkt = new GamePacket();
						StorePkt.name = LocalName;
						StorePkt.type = GamePacket.PACKET_STORE;
						StorePkt.key = k.packet.key;
						StorePkt.original_name = k.packet.name;
						//Format packet
						
							//k.packet.type = GamePacket.PACKET_STORE;
							//k.packet.original_name = k.packet.name;
							//k.packet.name = LocalName;
						
						broadcast(StorePkt);
						//TODO: VIN: This algorithm implies NO PACKET REORDERING
						//TODO: VIN: ADDED: Remove the packet from request local since its delivered
						request_local.remove(0); //TODO: VIN: confirm: Java documentation implies we dont need to re-sort

							
							
							//*send replies to everyone in the 
							for(Time_request j: request_array){
								 //if timestamp of requested packet (j) from another client is before than the next packet request(1) of this client, send a reply
								//do the same thing if the next request_local packet is null
								GamePacket CSreply = new GamePacket();
								CSreply.type = GamePacket.PACKET_CS_REPLY;
								CSreply.original_name = j.packet.name;//fromQueue.name; //name of person who requested entry
								CSreply.name = LocalName;	
							
								
								
								if (request_local.get(1) == null){
									j.packet.type = GamePacket.PACKET_CS_REPLY;
									broadcast(CSreply);
									///*try {
									//	clientsOS.get(j.packet.name).writeObject(j.packet); //TODO: Vin: doesn't work
									//} catch (IOException e) {
									//	// TODO Auto-generated catch block
									//	e.printStackTrace();
									//}									
									
								}else if( (j.timestamp < request_local.get(1).timestamp)){
									j.packet.type = GamePacket.PACKET_CS_REPLY;
									broadcast(CSreply);
									///*try {
									//	clientsOS.get(j.packet.name).writeObject(j.packet); //TODO: Vin: doesn't work
									//} catch (IOException e) {
										// TODO Auto-generated catch block
									//	e.printStackTrace();
									//}															
								}
								
								assert(j.timestamp==request_local.get(1).timestamp); //this shouldnt happen
							}
							
							request_local.remove(k);		//remove the local request from the array
							
					}
				}
				}*/
			
		
			

		
	}
	
	
}
