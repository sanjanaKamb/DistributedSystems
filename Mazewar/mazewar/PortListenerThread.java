import java.net.*;
import java.io.*;
import java.util.*;

public class PortListenerThread extends Thread {
	private Socket socket = null;
    ServerSocket serverSocket = null;
    boolean listening = true;
    private int portnum;
	//this thread always listens on the port
    
    public String ClientName = null;

  
    
	public PortListenerThread(int myportnum,String name) {
		super("PortListenerThreadThread");
		portnum=myportnum;
		this.ClientName = name;
		System.out.println("Created new PortListenerThread");
	}

	public void run() {
        try {
        	
        	serverSocket = new ServerSocket(portnum);
        	


        while (listening) {
        	new PortListenerHandlerThread(serverSocket.accept(),ClientName).start();
        }
        } catch (IOException e) {
            System.err.println("ERROR: Could not listen on port!");
            System.exit(-1);
        }
	}
}
