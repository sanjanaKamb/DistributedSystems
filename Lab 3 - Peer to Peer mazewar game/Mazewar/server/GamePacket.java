import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.util.*;
 /**
 * BrokerPacket
 * ============
 * 
 * Packet format of the packets exchanged between the Broker and the Client
 * 
 */


/* inline class to describe host/port combo */
class ServerLocation implements Serializable {
	public String  broker_host;
	public Integer broker_port;
	
	/* constructor */
	public ServerLocation(String host, Integer port) {
		this.broker_host = host;
		this.broker_port = port;
	}
	
	/* printable output */
	public String toString() {
		return " HOST: " + broker_host + " PORT: " + broker_port; 
	}
	
}

public class GamePacket implements Serializable {

	/* define constants */
		
	/* types */
	public static final int PACKET_NULL    = 0;
	public static final int PACKET_KILL     = 198;
	public static final int PACKET_KILL_ACK = 199;
	public static final int PACKET_REPLY  = 204;
	public static final int PACKET_STORE  = 205;
	public static final int PACKET_ADD_CLIENT  = 206;
	public static final int PACKET_ADD_CLIENT_ACK  = 207;
	public static final int PACKET_ADD_CLIENT_INV = 208;
	public static final int PACKET_UPDATE_POS = 209;
	public static final int PACKET_DEAD_CLIENT = 210;
	public static final int PACKET_CLIENT_QUIT = 211;
	public static final int PACKET_ADD_CLIENT_MYLOC = 212;
	public static final int PACKET_CHECK_CLOCK = 213;
	public static final int PACKET_CHECK_CLOCK_ACK = 214;
	public static final int PACKET_CHECK_CLOCK_DACK = 215;
	public static final int PACKET_CS_REQUEST = 216;
	public static final int PACKET_CS_REPLY = 217;
	public static final int PACKET_NEW_TIME = 218;
	
	/* keys */
	public static final int PACKET_QUIT = 300;
	public static final int PACKET_QUIT_ACK = 306;
	public static final int PACKET_FOWARD = 301;
	public static final int PACKET_BACKWARD = 302;
	public static final int PACKET_LEFT = 303;
	public static final int PACKET_RIGHT = 304;
	public static final int PACKET_FIRE = 305;
	
	/*error codes*/
	public static final int ERR_DUPL_NAME = 400;
	public static final int ERR_UNKNOWN_TYPE = 401;
	public static final int ERR_UNKNOWN_KEY = 402;
	
	/* constants for server registration*/
	public static final int LOOKUP_REQUEST = 500;
	public static final int LOOKUP_REGISTER = 501;
	public static final int LOOKUP_REPLY = 502;
	
	public Map <String,ObjectOutputStream> addrOSmap=null; //holds map to all clients	
	public Map <String,ObjectInputStream> addrISmap=null; //holds map to all clients
	
	public Map <String,String[]> addrmap=null; //holds map to all clients	
	//for client registration
	public String[] myAddr=null;
		/* Define
		myAddr[0] holds name
		myAddr[1] holds port
		myAddr[3] holds hostname
		*/
	public location mylocation; //used when adding for existing clients to report their location
	
	public long clock_timestamp; //used to synch clocks
	
	public String hostname;
	public int port;
	/* message header */
	public int type = GamePacket.PACKET_NULL;
	
	/*key pressed*/
	public int key = GamePacket.PACKET_NULL;
	
	/*error*/
	public int error = GamePacket.PACKET_NULL;
	
	/*Client Name, point and orientation*/
	public String name;
	public String original_name;
	public String source;
	public String target;
	
	public Point point;
	
	public Direction orientation;
	
	/* location */
	
	public Map <String,location> packet_loc=null;
	
	
	/* request symbol */
	public String symbol;
	
	/* Sequence number and timestamp*/
	public int seqnum;
	public Date timestamp; 
	

	
}