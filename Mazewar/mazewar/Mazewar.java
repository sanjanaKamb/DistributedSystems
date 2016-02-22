/*
Copyright (C) 2004 Geoffrey Alan Washburn
   
This program is free software; you can redistribute it and/or
modify it under the terms of the GNU General Public License
as published by the Free Software Foundation; either version 2
of the License, or (at your option) any later version.
   
This program is distributed in the hope that it will be useful,
but WITHOUT ANY WARRANTY; without even the implied warranty of
MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
GNU General Public License for more details.
   
You should have received a copy of the GNU General Public License
along with this program; if not, write to the Free Software
Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA  02111-1307,
USA.
*/
  
import javax.swing.JFrame;
import javax.swing.JScrollPane;
import javax.swing.JTextPane;
import javax.swing.JPanel;
import javax.swing.JTable;
import javax.swing.JOptionPane;
import java.awt.GridBagLayout;
import java.awt.GridBagConstraints;
import javax.swing.BorderFactory;

import java.io.IOException;
import java.io.Serializable;
import java.net.ServerSocket;

/**
 * The entry point and glue code for the game.  It also contains some helpful
 * global utility methods.
 * @author Geoffrey Washburn &lt;<a href="mailto:geoffw@cis.upenn.edu">geoffw@cis.upenn.edu</a>&gt;
 * @version $Id: Mazewar.java 371 2004-02-10 21:55:32Z geoffw $
 */

public class Mazewar extends JFrame {

        /**
         * The default width of the {@link Maze}.
         */
        private final int mazeWidth = 20;

        /**
         * The default height of the {@link Maze}.
         */
        private final int mazeHeight = 10;

        /**
         * The default random seed for the {@link Maze}.
         * All implementations of the same protocol must use 
         * the same seed value, or your mazes will be different.
         */
        private final int mazeSeed = 42;

        /**
         * The {@link Maze} that the game uses.
         */
        private Maze maze = null;
       private static volatile MazeImpl m = null;

        /**
         * The {@link GUIClient} for the game.
         */
        private GUIClient guiClient = null;

        /**
         * The panel that displays the {@link Maze}.
         */
        private OverheadMazePanel overheadPanel = null;

        /**
         * The table the displays the scores.
         */
        private JTable scoreTable = null;
        
        /** 
         * Create the textpane statically so that we can 
         * write to it globally using
         * the static consolePrint methods  
         */
        private static final JTextPane console = new JTextPane();
        
        
        
        private GameClientThread ClientThread = null;
        
      
        /** 
         * Write a message to the console followed by a newline.
         * @param msg The {@link String} to print.
         */ 
        public static synchronized void consolePrintLn(String msg) {
                console.setText(console.getText()+msg+"\n");
        }
        
        /** 
         * Write a message to the console.
         * @param msg The {@link String} to print.
         */ 
        public static synchronized void consolePrint(String msg) {
                console.setText(console.getText()+msg);
        }
        
        /** 
         * Clear the console. 
         */
        public static synchronized void clearConsole() {
           console.setText("");
        }
        
        /**
         * Static method for performing cleanup before exiting the game.
         */
        public static void quit() {
                // Put any network clean-up code you might have here.
                // (inform other implementations on the network that you have 
                //  left, etc.)
                

                System.exit(0);
        }
              
        /** 
         * The place where all the pieces are put together. 
         */
        public Mazewar() {
                super("ECE419 Mazewar");
                consolePrintLn("ECE419 Mazewar started!");
			              
			    //Make  a client thread
			                
			    String lookup_hostname = "ug229.eecg.utoronto.ca";
			    int lookup_port = 1111;
			    int my_port = 2222;			
			    String inputhostname = JOptionPane.showInputDialog("Set up 1: Enter naming server host name, cancel for "+lookup_hostname);
			    String inputport = JOptionPane.showInputDialog("Set up 2:Enter naming server port, cancel for "+lookup_port);
			    String myport = JOptionPane.showInputDialog("Set up 3:Enter port for this client to listen, cancel for "+my_port);
            
			    if (inputhostname!=null){
			    	lookup_hostname=inputhostname;
			    }
	                	
			    if (inputport!=null){
			    	lookup_port=Integer.parseInt(inputport);
			    }
			    
			    if (myport!=null){
			    	my_port=Integer.parseInt(myport);
			    }

			    System.out.println("Connecting to "+lookup_hostname+" at port: "+lookup_port);
			    String name = "UnnamedPlayer";
			    String inputname = JOptionPane.showInputDialog("Lets start: Enter your name");
		        if((name == null) || (name.length() == 0)) {
		                  Mazewar.quit();
		        }
		        if (inputname!=null){
			    	name=inputname.replaceAll("\\s+",""); //format name nicely
			    } 
			    
			    //start listening on my own port.Will create a thread for each client who connects
			     PortListenerThread p = new PortListenerThread(my_port,name);
			     p.start();
			    
			    
			    ClientThread = new GameClientThread(lookup_hostname,lookup_port,my_port);
			    			
                // Create the maze
			    m =new MazeImpl(new Point(mazeWidth, mazeHeight), mazeSeed);
                maze = m;
                ClientThread.addMaze(maze,m);
                assert(maze != null);
                
                // Have the ScoreTableModel listen to the maze to find
                // out how to adjust scores.
                ScoreTableModel scoreModel = new ScoreTableModel();
                assert(scoreModel != null);
                maze.addMazeListener(scoreModel);
		        //int NotDup = 0;
		        // Throw up a dialog to get the GUIClient name.
	                			                
		        
		                
		        // while(NotDup == 0){

			    // Create the GUIClient and connect it to the KeyListener queue
			    guiClient = new GUIClient(name);
			    int err=ClientThread.addGUIClient(guiClient);
			    //addGUIClient returns, implies that all ACKs received.

			    maze.addClient(guiClient); //TODO: client should be added to maze after successful addition
			    this.addKeyListener(guiClient);
				GameClientThread.map.put(guiClient.getName(), guiClient);
						
			    //broadcast its new location to all existing clients in the game
			    GamePacket toBroadcast=new GamePacket();
			    toBroadcast.type=GamePacket.PACKET_ADD_CLIENT_MYLOC;
			    toBroadcast.name=guiClient.getName();
			    toBroadcast.mylocation=new location(guiClient.getPoint(),guiClient.getOrientation()); //TODO: get current point and direction
				System.out.println("I'm new client, broadcasting MYLOC, my name "+toBroadcast.name+" at position "+toBroadcast.mylocation.p+" with orientation "+toBroadcast.mylocation.d);
			    GameClientThread.broadcast(toBroadcast);//???????

			    
			        		/*
			                
			                if(err == 111){								 //if duplicate name, ask for new name again
			                	maze.removeClient(guiClient); 
			                	this.removeKeyListener(guiClient);
				                // Throw up a dialog to get the GUIClient name.
				                name = JOptionPane.showInputDialog("Duplicated name, Re-enter your name");
				                if((name == null) || (name.length() == 0)) {
				                  Mazewar.quit();
				                }
			                	continue;
			                }
			                else if (err==222){
			                	maze.removeClient(guiClient); 
			                	this.removeKeyListener(guiClient);
			                	continue;
			                }
			                else {
			                	NotDup = 123;
			                }*/
		        		
                	
                             
                // Create the panel that will display the maze.
                overheadPanel = new OverheadMazePanel(maze, guiClient);
                assert(overheadPanel != null);
                maze.addMazeListener(overheadPanel);
                
                // Don't allow editing the console from the GUI
                console.setEditable(false);
                console.setFocusable(false);
                console.setBorder(BorderFactory.createTitledBorder(BorderFactory.createEtchedBorder()));
               
                // Allow the console to scroll by putting it in a scrollpane
                JScrollPane consoleScrollPane = new JScrollPane(console);
                assert(consoleScrollPane != null);
                consoleScrollPane.setBorder(BorderFactory.createTitledBorder(BorderFactory.createEtchedBorder(), "Console"));
                
                // Create the score table
                scoreTable = new JTable(scoreModel);
                assert(scoreTable != null);
                scoreTable.setFocusable(false);
                scoreTable.setRowSelectionAllowed(false);

                // Allow the score table to scroll too.
                JScrollPane scoreScrollPane = new JScrollPane(scoreTable);
                assert(scoreScrollPane != null);
                scoreScrollPane.setBorder(BorderFactory.createTitledBorder(BorderFactory.createEtchedBorder(), "Scores"));
                
                // Create the layout manager
                GridBagLayout layout = new GridBagLayout();
                GridBagConstraints c = new GridBagConstraints();
                getContentPane().setLayout(layout);
                
                // Define the constraints on the components.
                c.fill = GridBagConstraints.BOTH;
                c.weightx = 1.0;
                c.weighty = 3.0;
                c.gridwidth = GridBagConstraints.REMAINDER;
                layout.setConstraints(overheadPanel, c);
                c.gridwidth = GridBagConstraints.RELATIVE;
                c.weightx = 2.0;
                c.weighty = 1.0;
                layout.setConstraints(consoleScrollPane, c);
                c.gridwidth = GridBagConstraints.REMAINDER;
                c.weightx = 1.0;
                layout.setConstraints(scoreScrollPane, c);
                                
                // Add the components
                getContentPane().add(overheadPanel);
                getContentPane().add(consoleScrollPane);
                getContentPane().add(scoreScrollPane);
                
                // Pack everything neatly.
                pack();

                // Let the magic begin.
                setVisible(true);
                overheadPanel.repaint();
                this.requestFocusInWindow();
                
		                //client thread
		               
		    			guiClient.addClientThread(ClientThread);
		    			ClientThread.start();		//start thread
                
                
                
        }

        
        /**
         * Entry point for the game.  
         * @param args Command-line arguments.
         */
        public static void main(String args[]) {

                /* Create the GUI */
                new Mazewar();
        }

}
