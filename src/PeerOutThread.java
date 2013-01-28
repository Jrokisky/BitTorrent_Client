/****************************************************************************************
 * Internet Technology Project Assignment #2                                           *
 * Spring Semester 2012                                                                 *
 * Group 12: Rohit Kumar (rsk120), Akhilesh Maddali (amaddali), Justin Rokisky (jrokisk)*
 ***************************************************************************************/

import java.util.*;
import java.io.*;

/**
 * PeerOutThread is responsible for all outgoing messages to a peer.
 * Each peer has its own PeerOutThread.
 */
public class PeerOutThread extends Thread {
    private LinkedList<Message> requestQueue;
    private final DataOutputStream out;
    private final Peer peer;
    private boolean isRunning;
    private Timer timer;
    private keepAliveTimer keepAl;

	/**
	 * Creates a PeerOutThread object.
	 * @param out the DataOutputStream of the associated Peer
	 * @param peer the Peer object of the associated Peer
	 */
    public PeerOutThread(DataOutputStream out, Peer peer) {
        this.out = out;
        this.peer = peer;
        this.timer = new Timer();
        requestQueue = new LinkedList<Message>();
        isRunning = true;
    }

	/**
	 * Run works by keeping a queue of message objects we wish to send to a peer.
	 * When the queue is empty, the thread waits until there is a message.
	 * When a new message arrives, the PeerOutThread is notified, and the message gets
	 * sent to the associated peer. 
	 * It also schedules for keep alive messages to be sent if we haven't sent a message
	 * in a little under two minutes.
	 */
    public void run() {
        while (isRunning) {
            keepAl = new keepAliveTimer(this.peer);
            timer.schedule(keepAl, 110000); 
            synchronized (requestQueue) {
                while (isRunning && requestQueue.isEmpty()) {
                    try {
                        requestQueue.wait();
                    } catch (InterruptedException e) {
                        //ignore
                    }
                }
                try {
                    keepAl.cancel(); //message has been sent, cancel timertask
                    final Message message = requestQueue.removeFirst();
                    Message.encodeMessage(message, out);
                } catch (Exception e) {
                    if(isRunning)
                        System.out.println("\n========Error sending to: " + peer.getPeerID());
                }
            }
        }
    }
    
    /**
     * Getter Method for PeerOutThread's message queue
     * @return a LinkedList of messages in the queue
     */
    public LinkedList<Message> getQueue() {
        return requestQueue;
    }
    /**
     * Shuts down peerOutThread.
     */
    public void shutdown() {
        timer.cancel();
        keepAl.cancel();
        isRunning = false;
        synchronized (requestQueue) {
            requestQueue.notifyAll();
        }
    }

	/**
	 * KeepAliveTimer is responsible for sending keep alive messages to the
	 * associated peer.
	 * KeepAlive Timer objects are created whenever PeerOutThreads Timer
	 * reschedules. 
	 */
    private class keepAliveTimer extends TimerTask {
        private Peer peer;

		/**
		 * Creates a keepAliveTimer object with a reference to the
		 * associated Peer.
		 */
        public keepAliveTimer(Peer peer) {
            this.peer = peer;
        }
        
        /**
         * Sends a keepAlive message to the associated Peer
         */
        public void run() {
            System.out.println("Sending a Keep-Alive Message to Peer: " +this.peer.getPeerID());
            try {
                this.peer.sendKeepAliveMessage();
            } catch (IOException e) {
                System.out.println("Problem sending Keep Alive message");
            } 
        }		
    }
}
