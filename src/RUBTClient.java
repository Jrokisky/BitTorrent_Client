/****************************************************************************************
 * Internet Technology Project Assignment #2                                            *
 * Spring Semester 2012                                                                 *
 * Group 12: Rohit Kumar (rsk120), Akhilesh Maddali (amaddali), Justin Rokisky (jrokisk)*
 ***************************************************************************************/


import java.util.Random;
import java.io.*;

/** 
 * RUBTClient is the main class of our program. 
 * Creates Torrent object, and downloads file.
 */
public class RUBTClient {
    private static Torrent myTorrent;  
     
    /**
    * Main method of RUBTClient. <br>
	* Checks to make sure that inputs are valid. <br>
	* If so, creates a Torrent object, and attempts to download the file.
	*/
    public static void main(String[] args) {
        if (args.length != 2) {
            printUsage();                                                     
            System.exit(1);
        }
        RUBTClient client = new RUBTClient(args[0], args[1]);
        System.out.println("Enter <q> then enter to exit the program and save progress.");
        try {
			Thread.currentThread().sleep(1500);
		} catch(InterruptedException ie) {
			//
		}		
        ShutdownHook sHook = new ShutdownHook();
        Runtime.getRuntime().addShutdownHook(sHook);
    	myTorrent.printTorrentInfo();
		myTorrent.init();
		myTorrent.startPeerThreads();
		new Thread( new Runnable() {
		    public void run() {
		        myTorrent.download();
            }
        }).start();
		myTorrent.start();
		InputStreamReader sysIn = new InputStreamReader(System.in);
		BufferedReader buffReader = new BufferedReader(sysIn);
		boolean isRunning = true;
		while(isRunning) {
			try {
				if(buffReader.readLine().equals("q")) {
					myTorrent.shutdown();
					isRunning = false;	
				}
			} catch(IOException e) {
				System.out.println("Problem reading from system in. Butts.");
			}
		}
		myTorrent.printSummary();
    }
    
    /**
     * Shutdown hook to shut the program down gracefully
     */
    private static class ShutdownHook extends Thread {
		public void run() {
			myTorrent.shutdown();		
		}		
	}
	
	/** Prints proper usage of RUBTClient.java*/
    protected static void printUsage() {
        System.out.println("Usage: RUBTClient <torrent file> <save file>");
        System.out.println("Enter <q> to exit the program and save progress.");
    }
    
    /** 
     * Calls methods to generate a PeerId, create a Torrent object, and attempt to download file <br>
     * @param metaFile name of the Torrent file
     * @param outFile location where download will be saved
     */
    public RUBTClient(String metaFile, String outFile) {
        String peerID = RUBTUtil.generatePeerId();
        System.out.format("Your Peer ID: %s : %d\n", peerID, peerID.length());
		myTorrent = new Torrent(metaFile, outFile, peerID);
    }
}
