import java.net.*;
import java.io.*;
import java.util.*;

public class LocalQueueThread extends Thread {
	private Socket socket = null;
    ServerSocket serverSocket = null;
    boolean listening = true;
    private int portnum;
    private String LocalName;
	//this thread always listens on the port
    
    public String ClientName = null;

  
    
	public LocalQueueThread(String name) {
		super("LocalQueueThread");
		LocalName=name;
		System.out.println("Created new LocalQueueThread");
	}

	public synchronized void run() {
        while(true){
					if (GameClientThread.request_local.size()!=0){
					Time_request k  = GameClientThread.request_local.get(0); //repetitively get head of queue
					//This implementation checks if all CS_REPLYs are received
					//while(k.reply_count != map.size()){	//if all the replies are received, broadcast to everyone that event can be performed
					//TODO: Vin: changed this condition
					if (k.reply_count == GameClientThread.map.size()){	//if all the replies are received, broadcast to everyone that event can be performed
						//TODO: VIN: There was bug, copying packet instead else it doesn't transmit properly
						GamePacket StorePkt = new GamePacket();
						StorePkt.name = LocalName;
						StorePkt.type = GamePacket.PACKET_STORE;
						StorePkt.key = k.packet.key;
						StorePkt.original_name = k.packet.name;
						//Format packet
						/*
							k.packet.type = GamePacket.PACKET_STORE;
							k.packet.original_name = k.packet.name;
							k.packet.name = LocalName;
						*/
						GameClientThread.broadcast(StorePkt);
						//TODO: VIN: This algorithm implies NO PACKET REORDERING
						//TODO: VIN: ADDED: Remove the packet from request local since its delivered
						GameClientThread.request_local.remove(0); //TODO: VIN: confirm: Java documentation implies we dont need to re-sort

							
							
							/*send replies to everyone in the */
							for(Time_request j: GameClientThread.request_array){
								 //if timestamp of requested packet (j) from another client is before than the next packet request(1) of this client, send a reply
								//do the same thing if the next request_local packet is null
								GamePacket CSreply = new GamePacket();
								CSreply.type = GamePacket.PACKET_CS_REPLY;
								CSreply.original_name = j.packet.name;//fromQueue.name; //name of person who requested entry
								CSreply.name = LocalName;	
							
								
								
								if (GameClientThread.request_local.get(1) == null){
									j.packet.type = GamePacket.PACKET_CS_REPLY;
									GameClientThread.broadcast(CSreply);
									/*try {
										clientsOS.get(j.packet.name).writeObject(j.packet); //TODO: Vin: doesn't work
									} catch (IOException e) {
										// TODO Auto-generated catch block
										e.printStackTrace();
									}*/ 										
									
								}else if( (j.timestamp < GameClientThread.request_local.get(1).timestamp)){
									j.packet.type = GamePacket.PACKET_CS_REPLY;
									GameClientThread.broadcast(CSreply);
									/*try {
										clientsOS.get(j.packet.name).writeObject(j.packet); //TODO: Vin: doesn't work
									} catch (IOException e) {
										// TODO Auto-generated catch block
										e.printStackTrace();
									}*/ 																
								}
								
								assert(j.timestamp==GameClientThread.request_local.get(1).timestamp); //this shouldnt happen
							}
							
							GameClientThread.request_local.remove(k);		//remove the local request from the array
							
					}
				}
				}
	}
}
