/****************************************************************************************
 * Internet Technology Project Assignment #2                                            *
 * Spring Semester 2012                                                                 *
 * Group 12: Rohit Kumar (rsk120), Akhilesh Maddali (amaddali), Justin Rokisky (jrokisk)*
 ***************************************************************************************/

import java.io.*;
import java.net.*;
import java.util.*;
import java.nio.ByteBuffer;
import java.util.TimerTask;
import edu.rutgers.cs.cs352.bt.util.*;
import edu.rutgers.cs.cs352.bt.exceptions.*;

/**
 * Tracker class is reponsible for all communication with the tracker
 */
public class Tracker {
    private Torrent torrent;
    private int interval;
    
    /**
     * Creates a tracker object
     * @param torrent the Torrent object associated with this tracker
     */
    public Tracker(Torrent torrent) {
        this.torrent = torrent;
    }

    /**
     * Builds an HTTP URL String to be used in an HTTP GET request to the the 
     * tracker. Sends GET request to tracker and returns response from tracker
     * @return bytes of response from tracker
     */
    public byte[] sendRequestToTracker() throws IOException {
        StringBuilder sb = new StringBuilder();                                 //Build suffix of http get url
        sb.append('?');
        sb.append("info_hash=" + RUBTUtil.escapeBytes(torrent.getInfoHash().array()));      //Info hash disallowed characters need to be escaped in HTTP
        sb.append("&peer_id=" + torrent.getPeerID());
        sb.append("&port=" + torrent.getPort());
        sb.append("&uploaded=" + torrent.getUploaded());
        sb.append("&downloaded=" + torrent.getDownloaded());
        sb.append("&left=" + torrent.getLeft());
        if (torrent.getEvent() != null) {
            sb.append("&event=" + torrent.getEvent());
        }
        System.out.println(sb);
        URL trackerGET = new URL(torrent.getAnnounceURL() + sb.toString());                              
        URLConnection con = trackerGET.openConnection();
        int responseLen = con.getContentLength();
        DataInputStream is = new DataInputStream(con.getInputStream());                //Send HTTP Get request to Tracker using built string suffix
        byte[] buf = new byte[responseLen];
        is.readFully(buf);
        is.close();
       // RUBTUtil.dump(buf);
        return buf;
    }

	/**
	 * Gets the list of peers from the tracker's response
	 * @return a list of peer objects
	 */
    public List<Map<ByteBuffer, Object>> getPeerList() {
        List<Map<ByteBuffer, Object>> plist = null;
        try {
            byte[] response = sendRequestToTracker();
            plist = decodeTrackerResponse(response);
        } catch (IOException e) {
            System.out.println("Problem occured contacting tracker...");
            System.exit(1);
        } catch (BencodingException e) {
            System.out.println("Problem decoding tracker response...");
            System.exit(1);
        }
        return plist;
    }

    /**
     * Decode bytes of tracker response using Rob's Bencoder2. We expect the tracker to give us a 
     * dictionary which contains the peer list as a bencoded list of peers as dictionaries.
     * @param response Tracker's response as raw bytes
     * @return Bencoded Peer list
     */
    @SuppressWarnings(value = "unchecked")
        private List<Map<ByteBuffer, Object>> decodeTrackerResponse(byte[] response) throws BencodingException {
            Map<ByteBuffer, Object> data = (Map<ByteBuffer, Object>)Bencoder2.decode(response);
            interval = ((Integer)data.get(Protocol.KEY_INTERVAL)).intValue();           //set local instance to interval the tracker specifies 
            return (List<Map<ByteBuffer, Object>>)data.get(Protocol.KEY_PEERS);         //Tracker returns a dictionary with the peer list and interval, we only want peerlist
        }

	/**
	 * Returns the interval at which we should send regular tracker announces
	 * @return the interval at which we should send regular tracker announces
	 */
    public int getInterval() {
        return interval;
    }
}
