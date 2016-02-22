/*format of an element in the arraylist*/
public class Time_request {
	long timestamp;
	GamePacket packet;
	public int reply_count = 0;
	
	public Time_request(long timestamp, GamePacket packet){
		this.packet = packet;
		this.timestamp= timestamp;
		
	}
	
}
