import java.net.*;
import java.io.*;
import java.util.*;

public class LookupServerHandlerThread extends Thread {
	private Socket socket = null;

	public LookupServerHandlerThread(Socket socket) {
		super("EchoServerHandlerThread");
		this.socket = socket;
		System.out.println("Created new Thread to handle client");
	}

	public void run() {

		boolean gotByePacket = false;
		
		try {
			/* stream to read from client */
			ObjectInputStream fromClient = new ObjectInputStream(socket.getInputStream());
			GamePacket packetFromClient;
			
			/* stream to write back to client */
			ObjectOutputStream toClient = new ObjectOutputStream(socket.getOutputStream());
			
			while (( packetFromClient = (GamePacket) fromClient.readObject()) != null) {
				/* create a packet to send reply back to client */
				GamePacket packetToClient = new GamePacket();

				/*check if incoming packet is a lookup request*/
				if (packetFromClient.type==GamePacket.LOOKUP_REQUEST){
				// Lookup Request done in 2 phases: 
					//1) return the hostname and ports of all existing players to client
					//2) add new client to the file that holds all hostnames and ports
					
					System.out.println("Lookup Request from client: " + packetFromClient.name + " with port: "+packetFromClient.port+", at host:"+packetFromClient.hostname);
					/*Read file*/
					BufferedReader br = new BufferedReader(new FileReader("naming"));
					/*Parse the file for symbol*/					
					String line, name="", quote="",port="",host="";
					String[] result;
					/* Set up reply to client */
					packetToClient.type=GamePacket.LOOKUP_REPLY; //send reply back to client
					//packetToClient.addrOSmap= new HashMap<String,ObjectOutputStream>(NamingLookupServer.clientsOS);
					//packetToClient.addrISmap= new HashMap<String,ObjectInputStream>(NamingLookupServer.clientsIS);
					packetToClient.addrmap=new HashMap<String,String[]>();
					packetToClient.name = "namingserver";
					//populate addrmap
					do{
						line=br.readLine();
						if (line!=null){
							result=line.split(" ");
							name = result[0];//client name
							port = result[1];//port
							host = result[2];//host
							//populate client_locations with the new result
							System.out.println("Adding to addrmap "+name);
							packetToClient.addrmap.put(name,result); 
						}	
					} while (line!=null); 
							
					if (line==null){ //requested server name not found in file
						port = "0"; //error handling
					}
					
					/* send reply back to client */
					System.out.println("Sending reply to joining client "+packetFromClient.name+" current num of players: "+packetToClient.addrmap.size());
					toClient.writeObject(packetToClient);
					
					//TODO: add this new client's OS to the global map (which TODO needs to be created)
					//NamingLookupServer.clientsOS.put(packetFromClient.name,toClient);
					//NamingLookupServer.clientsIS.put(packetFromClient.name,fromClient);
					
					//2) registers new client				
					
					File currFile = new File("naming"); 									//Open file
					File tempFile = new File("TempNamingFile"); 							//Create a new temporary file
					br = new BufferedReader(new FileReader("naming"));
					BufferedWriter bw = new BufferedWriter(new FileWriter("TempNamingFile",true));
					//extract required fields
					String[] name_arr= new String[3];
					name_arr[0]=packetFromClient.name;
					name_arr[1]=Integer.toString(packetFromClient.port);
					name_arr[2]=packetFromClient.hostname;

					String symbolToUpdate = name_arr[0].toLowerCase();										
					//reset variables
					line="";
					name="";
					String[] result_write;
					boolean symbolFound = false;
					//we append the different fields to prepare to write to file
					//this appropriately formats the data
					String writevalue= name_arr[0] + " " + name_arr[1] + " " + name_arr[2];
					
					System.out.println("Registering Game Client: " + name_arr[0] + ", with port: "+name_arr[1]+", at host:"+name_arr[2]);
					if ((line=br.readLine())==null){
						bw.close();
						br.close();
						BufferedWriter out = new BufferedWriter(new FileWriter("naming", true)); 
						String str = new String (writevalue);
						out.write(str.toLowerCase()+"\n");
						out.close();
						System.out.println("readline is null");
						//Success, send reply to OnlineBroker
						//packetToClient.type = GamePacket.LOOKUP_REPLY;
						//end reply back to client 
						//toClient.writeObject(packetToClient);
						continue;
					}
					else{ //file not empty
						do{ 						
							result_write=line.split(" ");
							name = result_write[0];
							if(name.equals(symbolToUpdate)==false){ //Add all the symbols into temp file, except the symbol to update
								bw.write(line+"\n");
								//System.out.println("Iterated "+symbolToUpdate);
							}else{//Update the symbol line with new quote and add into temp file
								symbolFound = true;
								bw.write(writevalue+"\n");
								//System.out.println("Write Not-detected: "+symbolToUpdate);
							}										 
						}while((line=br.readLine())!=null);
											
						if ((line==null)&&(symbolFound==false)){ //symbol not in file
							symbolFound = true;
							bw.write(writevalue+"\n");
						}
					}
					bw.close();
					br.close();
					
					if(symbolFound==false){//if symbol not found, send error
						System.out.println("New client registered");
						boolean successDelete = tempFile.delete(); //delete temporary file
						//Success, send reply to OnlineBroker
						//packetToClient.type = GamePacket.LOOKUP_REPLY;
						//end reply back to client 
						//toClient.writeObject(packetToClient);						//send reply back to client 
						
					
					}
					else{	
						System.out.println("Server registration data updated");
						boolean successDelete = currFile.delete(); 					//delete current nasdaq file
						boolean successRename = tempFile.renameTo(currFile); //rename the temporary file to nasdaq
						//packetToClient.type = GamePacket.LOOKUP_REPLY;
						//toClient.writeObject(packetToClient);//send reply back to client 
						
					}
					
					/* wait for next packet */
					continue;	
				}
				//incoming message is server registration
				else if (packetFromClient.type==GamePacket.LOOKUP_REGISTER){ //handle register
					//write to file/memory

				}

				
				/* Sending an BROKER_NULL || BROKER_BYE means quit */
				else if (packetFromClient.type == GamePacket.PACKET_QUIT) { //TODO: match this with lab2 implementation
					gotByePacket = true;
					packetToClient = new GamePacket();
					packetToClient.type = GamePacket.PACKET_QUIT_ACK;
					//packetToClient.message = "Bye!";
					toClient.writeObject(packetToClient);
					break;
				}
				
				/* if code comes here, there is an error in the packet */
				System.err.println("ERROR: Unknown packet!!");
				System.exit(-1);
			}
			
			/* cleanup when client exits */
			fromClient.close();
			toClient.close();
			socket.close();

		}catch (EOFException e){
			//end of file, no problem
		} catch (IOException e) {
			if(!gotByePacket)
				e.printStackTrace();
		} catch (ClassNotFoundException e) {
			if(!gotByePacket)
				e.printStackTrace();
		}
	}
}
