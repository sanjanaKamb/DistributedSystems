import java.net.*;
import java.util.HashMap;
import java.util.HashSet;
import java.io.*;

public class NamingLookupServer {
	//create an object to store the output stream of registered clients
    public volatile static HashMap<String,ObjectOutputStream> clientsOS=new HashMap<String,ObjectOutputStream>();
	//create an object to store the output stream of registered clients
	public volatile  static HashMap<String,ObjectInputStream> clientsIS=new HashMap<String,ObjectInputStream>();
    
	public static void main(String[] args) throws IOException {
        ServerSocket serverSocket = null;
        boolean listening = true;

	//Create file (if none exist)
	File f1 =new File("naming");
	f1.delete(); //ensure file deletion
	File f =new File("naming");
	if (!f.createNewFile()){
		//no new file created
	}


        try {
        	if(args.length == 1) {
        		serverSocket = new ServerSocket(Integer.parseInt(args[0]));
        	} else {
        		System.err.println("ERROR: Invalid arguments!");
        		System.exit(-1);
        	}
        } catch (IOException e) {
            System.err.println("ERROR: Could not listen on port!");
            System.exit(-1);
        }

        while (listening) {
        	new LookupServerHandlerThread(serverSocket.accept()).start();
        }

        serverSocket.close();
    }
}
