/****************************************************************************************
 * Internet Technology Project Assignment #2                                            *
 * Spring Semester 2012                                                                 *
 * Group 12: Rohit Kumar (rsk120), Akhilesh Maddali (amaddali), Justin Rokisky (jrokisk)*
 ***************************************************************************************/

import java.io.*;
import java.net.*;
import java.util.Map;
import java.util.HashMap;
import java.util.List;
import java.util.Timer;
import java.util.Arrays;
import java.util.Random;
import java.util.TimerTask;
import java.util.ArrayList;
import java.nio.ByteBuffer;
import java.util.concurrent.ExecutorService;
import java.util.Collections;
import java.util.concurrent.*;
import java.util.concurrent.Executors;
import edu.rutgers.cs.cs352.bt.TorrentInfo;
import edu.rutgers.cs.cs352.bt.util.*;
import edu.rutgers.cs.cs352.bt.exceptions.*;

/**
 * Torrent class is responsible for managing peers, pieces and the tracker in 
 * order to successfully download a file
 */
public class Torrent extends Thread {
    private final ExecutorService workers = Executors.newCachedThreadPool();
    private Tracker tracker;
    /**Tracker URL from Torrent metafile*/
    private URL announceURL;     
    /**Values pulled from metainfo torrent file*/           
    private ByteBuffer info_hash;            
    private ByteBuffer[] sha1Hashes;
    private List<Peer> peerList;
    private int interval, pieceLength, requestLength, fileLength, numPieces;
    private int downloaded, uploaded, left, port;
    /**Piece objects used to store piece before writing to disk, verifying sha1, and checking if done*/
    private Piece[] pieceList;              
    private ServerSocket listenPort;
    private String localPeerID, fileName, outputFileName;
    private String event;
    private FileHandler outFile;
    private final int DEFAULT_REQUEST_LEN = 32768;
    private Map<Integer, ArrayList<Peer>> peerMap;
    /**isRunning is true when the program is running, otherwise false*/
    public boolean isRunning;  //check scope
    private boolean[] piecesRequested;
    private int pieceDoneCount;
    private Timer timer;
    private boolean shuttingDownAlready;

    /**
     * Pulls torrent information from metafile
     @param torrentMetaFile Name of .torrent file to read from
     @param outputFile Name of file that will store downloaded data
     @param peerID PeerId of the client
     */
    public Torrent(String torrentMetaFile, String outputFileName, String peerID) {
        port = 6881;
        event = null;                                              //Null event state. We have done nothing. Tracker will completely exclude event when doing an announce
        downloaded = uploaded = 0;                                 //This should be okay unless we are supporting resuming a download...
        localPeerID = peerID;       
        getTorrentInfo(torrentMetaFile);           //Initialize torrent info from  .torrent metafile
        left = fileLength;
        requestLength = Math.min(DEFAULT_REQUEST_LEN, pieceLength); 
        System.out.println("Request Length: " + requestLength);
        this.outputFileName = outputFileName;
        this.shuttingDownAlready = false;
    }

    /**
     * Uses Rob's TorrentInfo to pull neccesary keys for Tracker communication and 
     * Peer handshake from the .torrent metafile. 
     * @param filename .torrent file path 
     */
    private void getTorrentInfo(String filename) { 
        try {
            File torrentFile = new File(filename);                                 //Open file and get bytes 
            FileInputStream torrentFileStream = new FileInputStream(torrentFile);
            byte[] torrentFileBytes = new byte[(int)(torrentFile.length())];
            torrentFileStream.read(torrentFileBytes);                          //Pass bytes of file to TorrentInfo 
            TorrentInfo metaInfo = new TorrentInfo(torrentFileBytes);          //TorrentInfo sets these public fields as soon as it has read
            fileName = metaInfo.file_name;                                     //Initialize local instance variables 
            fileLength = metaInfo.file_length;
            pieceLength = metaInfo.piece_length;
            sha1Hashes = metaInfo.piece_hashes;
            numPieces = sha1Hashes.length;
            announceURL = metaInfo.announce_url;
            info_hash = metaInfo.info_hash;
        } catch (FileNotFoundException e) {
            System.out.println("Could not find .torrent file: " + e.getMessage());
            System.exit(1);
        } catch (IOException e) {
            System.out.println("Problem occured reading .torrent file: " + e.getMessage());
            System.exit(1);
        } catch (BencodingException e) {
            System.out.println("Problem parsing .torrent file: " + e.getMessage());
            System.exit(1);
        }
    }        

    /**
     * Debug method to print torrent info
     */
    public void printTorrentInfo() {
        System.out.println("File name: " + fileName);
        System.out.println("File length: " + fileLength);
        System.out.println("Piece length: " + pieceLength);
        System.out.println("Number of Pieces: " + numPieces);
    }

    /**
     * Initializes listenPort, peerList, pieceList, FileHandler, and Tracker object for tracker
     * communication.
     */
    public void init() {
        pieceDoneCount = 0;
        this.initPieceList();
        outFile = new FileHandler(this.fileLength, this.pieceLength, this.outputFileName, this.sha1Hashes,this);
        outFile.initializeFile();
        boolean[] completedFromRehash = outFile.getBooleanArray();
        for(int i = 0; i < numPieces; i++) {
            pieceList[i].setDone(completedFromRehash[i]);
            if (pieceList[i].isDone())
                incrementDoneCount();
        }
        event = "started";
        tracker = new Tracker(this);
        this.initPeerList();
        interval = tracker.getInterval(); //Needs to be after we initialize peerList or interval hasnt been initialized yet
        interval *= 1000;
        System.out.println(interval);
        isRunning = true;
        event = null;
    }

    /**
     * Using instance variables from the .torrent file, send request for peer list
     * to tracker, then build a list of Peer objects from the peer list that the 
     * tracker responds with. 
     */
    @SuppressWarnings(value = "unchecked")
        private void initPeerList() {
            peerList = Collections.synchronizedList(new ArrayList<Peer>());                                                                       //List of Peer objects (instance variable)
            try {
                List<Map<ByteBuffer, Object>> plist = tracker.getPeerList();
                for (Map<ByteBuffer, Object> peerMap : plist) {                                                         //Build peer objects from each Bencoded Map (Peer)
                    String id = new String(((ByteBuffer) peerMap.get(Protocol.KEY_PEER_ID)).array(), "ASCII");           
                    String ip = new String(((ByteBuffer) peerMap.get(Protocol.KEY_IP)).array(), "ASCII"); 
                    //check that ip is one of the allowed addresses. Theres no
                    //point keeping a list of peers we wont use.
                    int port = ((Integer) peerMap.get(Protocol.KEY_PORT)).intValue();
                    if ((ip.equals("128.6.5.130") || ip.equals("128.6.5.131"))) {
                        peerList.add(new Peer(id, ip, port, numPieces, this));
                        System.out.format("Adding peer: %s, %s\n", id, ip);
                    }
                }
                System.out.format("Number of Peers found: %d\n", peerList.size());
            } catch(UnsupportedEncodingException e) {
                System.out.println("ASCII not supported?: " + e.getMessage());
                e.printStackTrace();
                System.exit(1);
            }
        }

    /**
     * Initialize Piece Object list. 
     */
    private void initPieceList() {
        int x;
        pieceList = new Piece[numPieces];
        for (x = 0; x < numPieces - 1; x++) {               //All pieces except the last should the be the same size (piece size)
            pieceList[x] = new Piece(pieceLength, sha1Hashes[x].array());
        }
        pieceList[x] = new Piece(fileLength - (pieceLength * (numPieces - 1)), sha1Hashes[x].array()); //Last piece should be whatever is left, not piece size
    }

    /**
     * Handshake with a peer when it is trying to connect to us.
     * If the handshake is a success, add the peer to peerList.
     * @param incomingConn the socket of the incoming Peer
     */
    public void addPeer(final Socket incomingConn) {
        Peer incomingPeer = new Peer(incomingConn, this);
        if(!incomingPeer.answerHandshake(localPeerID, info_hash)) {
            System.out.println("handshake fail on local");
            incomingPeer.disconnect();
            return;
        }
        System.out.println("Peer " + incomingPeer.getPeerID() + " found on local port!");
        if (checkForDuplicatePeer(incomingPeer)) {
            System.out.println("***DUPLICATE PEER. Disconnecting.");
            incomingPeer.disconnect();
            return;
        } else {
            System.out.println(incomingPeer.getPeerID() + " is not a duplicate.");
        }
        peerList.add(incomingPeer);
        incomingPeer.start();
        sendHaves(incomingPeer);
    }

    public void addNewPeer(Peer incomingPeer) {
        if (checkForDuplicatePeer(incomingPeer)) {
            System.out.println("***DUPLICATE PEER. Disconnecting.");
            return;
        } else {
            System.out.println(incomingPeer.getPeerID() + " is not a duplicate.");
        }
        if(!incomingPeer.initiateHandshake(localPeerID, info_hash)) {
            System.out.println("handshake fail on local");
            incomingPeer.disconnect();
            return;
        }
        System.out.println("Peer " + incomingPeer.getPeerID() + " added from tracker scrape");
        peerList.add(incomingPeer);
        incomingPeer.start();
        sendHaves(incomingPeer);
    }

    /**
     * Sends have messages for all pieces that we have completed dling to 
     * a given peer.
     * @param peer the peer that we are sending have messages to
     */
    public void sendHaves(Peer peer) {
        System.out.println("Sending haves to " + peer.getPeerID());
        for (int x = 0; x < numPieces; x++) {
            if (pieceList[x].isDone())
                peer.sendHaveMessage(x);
        }
    }

    /**
     * Handshakes with the peers we got from the tracker response and 
     * starts their message listening threads.
     */
    public void startPeerThreads() {
        Peer temp;
        for(int i = 0; i < peerList.size(); i++) {
            temp = peerList.get(i);
            if(temp.initiateHandshake(localPeerID, info_hash)) {
                temp.start();				
                sendHaves(temp);
            } else {
                peerList.remove(i);
                System.out.println("Could not connect to peer: " + temp.getPeerID() + " Removing from peerList.");
            }
        }
        System.out.println("Finished starting peer threads");
        System.out.println("Connected to " + peerList.size() + " peers");
    }

    /**
     * Selects a port to listen on and listens for incoming connections.
     * Schedules regular tracker communication.
     */
    public void run() {
        boolean listening = false;
        timer = new Timer();
        timer.scheduleAtFixedRate(new AnnounceTask(tracker), interval, interval);
        for (int i = 0; i < 9; i++) {
            try {
                this.listenPort = new ServerSocket(this.port + i);
                this.listenPort.setSoTimeout(250);
                System.out.println("Listening on port " + (this.port + i));
                listening = true;
                this.port = this.port + i;
                break;
            } catch (IOException e) {
                System.out.println("Error binding port. Trying another");
                continue;
            }
        }
        if (!listening) {
            System.out.println("Could not bind to any port");
            System.exit(1);
        }
        while (isRunning) {
            try {
                final Socket incomingConn = listenPort.accept();
                System.out.println("=============================================");
                System.out.println("==============Got incoming peer==============");
                System.out.println("=============================================");
                workers.execute(new Runnable() {
                    @Override
                    public void run() {
                        addPeer(incomingConn);
                    }
                });
            } catch (SocketTimeoutException e) {
                //do nothing
            } catch (IOException e) {
                System.out.println("Problem in run");
                e.printStackTrace();
            }
        }
        System.out.println("Stopped listening for incoming connections");
    }

    /**
     * Method to add requests for pieces onto request queues of peers.
     * At this point we should have a list of peers that we are connected to
     * inside peerList. 
     */
    public void download() {
        if (pieceDoneCount == numPieces) {
            System.out.println("Skipping download(), file already done");
            printDone();
            printDone();
            printDone();
            return;
        }
        //Creates a map that links each piece with the peers that have that piece
        peerMap = new HashMap<Integer, ArrayList<Peer>>();
        for (int x = 0; x < numPieces; x++) 
            peerMap.put(x, new ArrayList<Peer>());
        synchronized(peerList) {
            for (Peer peer : peerList) {
                for (int x = 0; x < peer.getHaves().length; x++)
                    if (peer.getHaves()[x] == true) {
                        peerMap.get(x).add(peer);
                    }
            }
        }
        //Send an interested message to every peer that has pieces that we need
        for (int x = 0; x < numPieces; x++) {
            System.out.println("Piece " + x + ": " + peerMap.get(x).size());
            if (!pieceList[x].isDone()) {
                for (Peer peer : peerMap.get(x)) {
                    peer.setAmInterested(true);
                    peer.sendInterestedMessage();
                }
            }
        }

        piecesRequested = new boolean[numPieces];
        boolean done = false;
        while(!done && isRunning) {
            if (getDoneCount() == numPieces) {
                done = true;
                break;
            }
            for (int x = 0; x < numPieces; x++) {
                if (piecesRequested[x] || pieceList[x].isDone())
                    continue;
                ArrayList<Peer> piecePeers = peerMap.get(x);
                if (piecePeers.isEmpty()) {
                    System.out.println("No one has piece " + x);
                } else {
                    int freePeerIndex = -1;
                    for (int i = 0; i < piecePeers.size(); i++) {
                        if (piecePeers.get(i).isConnected() && !piecePeers.get(i).requested && !piecePeers.get(i).peerChoking()) {
                            freePeerIndex = i;
                            break;
                        }
                    }
                    if (freePeerIndex == -1) {
                        continue;
                    } else {
                        piecesRequested[x] = true;
                        requestPiece(piecePeers.remove(freePeerIndex), x);
                    }
                }
            }
            try {
                Thread.sleep(5);
            } catch(Exception e) {
                e.printStackTrace();
                //SHUTDOWN HOOK HERE
            }
        }

        if (getDoneCount() == numPieces) {
            System.out.println("We've finished downloading the file");
            printDone();
            printDone();
            printDone();
            event = "completed";
            try {
                tracker.sendRequestToTracker();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        event = null; //if we sent completed, we dont want to send again
    }

    private void printDone() {
        System.out.flush();
        System.out.println("");
        System.out.println("d8888b.  .d88b.  d8b   db d88888b");
        System.out.println("88  `8D .8P  Y8. 888o  88 88'    ");
        System.out.println("88   88 88    88 88V8o 88 88ooooo");
        System.out.println("88   88 88    88 88 V8o88 88~~~~~");
        System.out.println("88  .8D `8b  d8' 88  V888 88.    "); 
        System.out.println("Y8888D'  `Y88P'  VP   V8P Y88888");
        System.out.println("");
        System.out.flush();
    }

    /**
     * Adds request messages for every subPiece of the desired piece, to 
     * the given peer's pending request queue. These will be sent later.
     * @param peer the peer that we are trying to download the piece from
     * @param index the piece that we are trying to download
     */
    public void requestPiece(Peer peer, int index) {
        try {
            System.out.println("Requesting subpieces of piece #" + index + " from " + peer.getPeerID());
            int currentPieceLength = pieceList[index].getPieceLength();  //(Not all pieces are the same length ... last one isnt)
            int numBlocks= currentPieceLength / requestLength;                 //Number of blocks of requestLength size in a piece (subpieces) that go in evenly 
            peer.requested = true;
            peer.workingOn = index;
            for (int x = 0; x < numBlocks; x++) {
                peer.sendRequestMessage(index, x * requestLength, requestLength); //Request each block of a piece 
                //System.out.println("Requesting piece" + "(" + (x + 1) + "/" + numBlocks +"): "  + index + ", " + x*requestLength + ", " + requestLength);
            }
            if ((currentPieceLength % requestLength) > 0) {                           //Request size that did not go in evenly (if at all).
                peer.sendRequestMessage(index, currentPieceLength - (currentPieceLength % requestLength), currentPieceLength % requestLength);
                //System.out.println("Requesting piece (last): " + index + ", " + (currentPieceLength - (currentPieceLength % requestLength)) +  ", " + currentPieceLength % requestLength);
            }
            peer.startRequests();
        } catch(IOException e) {
            System.out.println("FUCK");
            e.printStackTrace();
        }
    }

    /**
     * Sends have messages for the piece at the given index to every peer
     * in the peerList
     * @param index the index of the piece
     */
    public void broadcastHave(int index) {
        System.out.println("Broadcasting have message for piece#: " + index);
        synchronized(peerList) {
            for (Peer peer : peerList) {
                if (peer.isConnected())
                    peer.sendHaveMessage(index);
            }
        }
    }
    
    /**
     * Clears the current piece data from the piece object at the given index,
     * and marks it to be re-attempted for download.
     * @param index the index of the piece
     */
    public void rerequest(int index) {
        System.out.println("Rerequesting piece #" + index + " from another Peer");
        System.out.flush();
        //System.exit(1);
        pieceList[index].clearBuffer();
        piecesRequested[index] = false;
    }

    /**
     * Handles an incoming piece message.
     * If the piece is complete and the SHA1 hash is correct, writes the piece to file.
     * @param piecemsg the incoming Piece Message
     * @param peer the Peer we are receiving the piece from
     */
    public void pieceReceived(PieceMessage piecemsg, Peer peer) {
        synchronized(peer.pendingReqs) {
            RequestMessage expectedReq = peer.pendingReqs.peek();
            if (piecemsg.getIndex() == expectedReq.getIndex() && piecemsg.getBegin() == expectedReq.getBegin()) {
                if (peer.pendingReqs.isEmpty() == false)
                    peer.pendingReqs.poll();
            } else return;
        }
        peer.requestNext();
        int index = piecemsg.getIndex();
        pieceList[index].writePiece(piecemsg.getBlock(), piecemsg.getBegin());
        if (pieceList[index].isDone()) {
            boolean match = pieceList[index].verifyPieceHash();
            updateDownloaded(pieceList[index].getPieceLength());
            peer.requested = false;
            peer.workingOn = -1;
            System.out.println("Piece #" + index + " done. Does hash match? " + match);
            if (match) {
                outFile.writePiece(index, pieceList[index].getBuffer());
                updateLeft(pieceList[index].getPieceLength());
                pieceList[index].cleanup();
                incrementDoneCount();
                broadcastHave(index);
            } else {
                pieceList[index].clearBuffer();
                piecesRequested[index] = false;
                //requestPiece(peerMap.get(index).remove(0), index);
            }
        }
    }
    
    /**
     * Updates the value of downloaded
     * @param pieceSize the size of the downloaded piece
     */
    public synchronized void updateDownloaded(int pieceSize) {
        this.downloaded += pieceSize;
    }
    /**
     * Updates the value of left
     * @param pieceSize the size of the downloaded piece
     */
    public synchronized void updateLeft(int pieceSize) {
        this.left -= pieceSize;
    }
    /**
     * Updates the value of uploaded
     * @param pieceSize the size of the downloaded piece
     */
    public synchronized void updateUploaded(int pieceSize) {
        this.uploaded += pieceSize;
    }
    /**
     * Sets the value of uploaded
     * @param uploaded the value of data uploaded
     */
    public void setUploaded(int uploaded) {
        this.uploaded = uploaded;
    }
    /**
     * Sets the value of downloaded
     * @param downloaded the value of data downloaded
     */
     public void setDownloaded(int downloaded) {
		 this.downloaded = downloaded;
	}

    /**
     * Handles when we receive a request.
     * Pulls and sends the requested piece from file.
     * @param request the Request Message received
     * @param peer the Peer that sent the Request message
     */
    public void requestReceived(RequestMessage request, Peer peer) {
        int index = request.getIndex();
        //check if the request is valid
        if (index >= 0 && index < numPieces) {
            if (pieceList[index].isDone()) {
                byte[] piece = outFile.getSubPiece(index, request.getBegin(), request.getRequestLength());
                if (piece == null) {
                    System.out.println("************Problem serving request: " + index + ", " + request.getBegin() + ", " + request.getRequestLength());
                    System.exit(1);
                    peer.disconnect();
                    return;
                }
                PieceMessage pm = new PieceMessage(index, request.getBegin(), piece);
                peer.sendPieceMessage(index, request.getBegin(), piece);
                updateUploaded(request.getRequestLength());
            }
        } else {
            System.out.println("Invalid REQUEST. Disconnecting Peer");
            peer.disconnect();
        }
    }

    /**
     * Cleanly shuts down the program
     */
    public void shutdown() {
        //This is here so when we end the program normally the shutdown hook doesnt run as well
        if(!shuttingDownAlready) {
            shuttingDownAlready = true;
            this.isRunning = false;
            System.out.println("");
            System.out.println("========================================");
            System.out.println("=============SHUTTING DOWN==============");
            System.out.println("========================================");
            if(timer != null)
                timer.cancel();
            event = "stopped";
            try {
                tracker.sendRequestToTracker();
            } catch (IOException e) {
                System.out.println("Problem telling tracker we're done...");
            }
            System.out.println("Disconnecting from peers and shutting down.");
            synchronized(peerList) {
                for (Peer peer : peerList) {
                    peer.disconnect();
                }
            }
            outFile.close();
            outFile.writeUploadedAndDownloaded();
        }
    }

    private void announceAndAddNewPeers() {
        try {
            List<Map<ByteBuffer, Object>> plist = tracker.getPeerList();
            for (Map<ByteBuffer, Object> peerMap : plist) {                                                         //Build peer objects from each Bencoded Map (Peer)
                String id = new String(((ByteBuffer) peerMap.get(Protocol.KEY_PEER_ID)).array(), "ASCII");           
                String ip = new String(((ByteBuffer) peerMap.get(Protocol.KEY_IP)).array(), "ASCII"); 
                //check that ip is one of the allowed addresses. Theres no
                //point keeping a list of peers we wont use.
                int port = ((Integer) peerMap.get(Protocol.KEY_PORT)).intValue();
                if ((ip.equals("128.6.5.130") || ip.equals("128.6.5.131")) && id.startsWith("RUBT")) {
                    addNewPeer(new Peer(id, ip, port, numPieces, this));
                }
            }
        } catch(UnsupportedEncodingException e) {
            System.out.println("ASCII not supported?: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }       
    }

    /**
     * AnnounceTask is responsible for sending regular announces to the tracker.
     */
    private class AnnounceTask extends TimerTask {
        private Tracker tracker;
        /**
         * Creates an AnnounceTask object
         * @param tracker the Tracker object
         */
        public AnnounceTask(Tracker tracker) {
            this.tracker = tracker;
        }
        /**
         * Sends a request to the tracker
         */
        public void run() {
            byte[] response = null;
            System.out.println("Sending regular announce to tracker.");
            announceAndAddNewPeers();
        }
    }

    /**
     * Gets the info_hash
     * @return the info_hash
     */
    public ByteBuffer getInfoHash() {
        return info_hash;
    }
    /**
     * Gets the PeerID
     * @return the PeerID
     */
    public String getPeerID() {
        return localPeerID;
    }
    /**
     * Gets the announceURl
     * @return announceURL
     */
    public URL getAnnounceURL() {
        return announceURL;
    }
    /**
     * Gets the uploaded value
     * @return the amount that has been uploaded
     */
    public int getUploaded() {
        return uploaded;
    }
    /**
     * Gets the downloaded value
     * @return the amount that has been downloaded
     */
    public int getDownloaded() {
        return downloaded;
    }
    /**
     * Gets the left value
     * @return the amount that is left to be downloaded
     */
    public int getLeft() {
        return left;
    }
    /**
     * Gets the tracker event
     * @return the event
     */
    public String getEvent() {
        return event;
    }
    /**
     * Gets the local port number
     * @return the local port number
     */
    public int getPort() {
        return port;
    }
    /**
     * Gets the number of Pieces
     * @return the number of pieces
     */
    public int getNumPieces() {
        return numPieces;
    }
    /**
     * Gets the array of piece objects
     * @return the array of piece objects
     */
    public Piece[] getPieceList() {
        return pieceList;
    }
    /**
     * Gets the fileHandler object
     * @return the fileHandler object
     */
    public FileHandler getFileHandler() {
        return outFile;
    }
    /**
     * Returns the amount of pieces that have been dled
     * @return pieceDoneCount the amount of pieces that have been dled
     */
    public synchronized int getDoneCount() {
        return pieceDoneCount;
    }
    /**
     * Increments the amount of pieces that have been dled
     */
    private synchronized void incrementDoneCount() {
        pieceDoneCount++;
    }
    /**
     * Removes the given peer from the peerList
     * @param p the peer to be removed from the PeerList
     */
    public void removePeer(Peer p) {
        peerList.remove(p);
    }

    private boolean checkForDuplicatePeer(Peer peer) {
        synchronized (peerList) {
            for (Peer connectedPeer : peerList) {
                if (connectedPeer.getPeerID().equals(peer.getPeerID())) {
                    System.out.println("***Duplicate: " + peer.getPeerID());
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * Prints out the amount of pieces that are completed
     */
    public void printSummary() {
        System.out.println("Completed: " + getDoneCount() + " pieces");
    }
}
