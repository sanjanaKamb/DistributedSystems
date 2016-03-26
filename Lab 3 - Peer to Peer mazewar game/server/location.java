import java.util.Map;



public class location implements java.io.Serializable{
	
	/**
	 * 
	 */
	private static final long serialVersionUID = 1L;
	public Point p;
	public Direction d;
	
	public location (Point p_new, Direction d_new){
		p=p_new;
		d=d_new;
	}
	
}