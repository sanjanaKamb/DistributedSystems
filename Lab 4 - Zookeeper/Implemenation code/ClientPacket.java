import java.io.Serializable;


public class ClientPacket implements Serializable{

	public static final int PACKET_NULL = 0;
	public static final int JOB_CONNECT = 200;
	public static final int JOB_CONNECT_ACK = 201;
	public static final int JOB_REQUEST = 202;
	public static final int JOB_REQUEST_ACK = 203;
	public static final int JOB_QUERY = 204;
	public static final int JOB_IN_PROGRESS = 205;
	public static final int JOB_FINISHED = 206;
	public static final int PASSWORD_FOUND = 207;
	public static final int PASSWORD_NOT_FOUND = 208;
	public static final int PARTITION_REQUEST = 209;
	public static final int PARTITION_REPLY = 210;
	public static final int JOBTRACKER_REQUEST = 211;
	public static final int WORKER_REPLY = 212;
	public static final int JOB_CONNECT_WORKER = 213;
	public static final int JOB_CONNECT_ACK_WORKER = 214;
	
	public int type = ClientPacket.PACKET_NULL;
	public String password;
	public String hash;
	public String ans=null;
	public int job_id;
	public int part_id;
	public int os_id;
	String[] partition;
	int client_id; //ADDED
	
	
	public int fin;
	public int start;
	
}
