/****************************************************************************************
 * Internet Technology Project Assignment #2                                            *
 * Spring Semester 2012                                                                 *
 * Group 12: Rohit Kumar (rsk120), Akhilesh Maddali (amaddali), Justin Rokisky (jrokisk)*
 ***************************************************************************************/

import java.util.Arrays;
import java.util.Queue;
import java.util.LinkedList;
import java.io.*;
import java.nio.*;
import java.net.*;

/**
 * The peer class maintains a connection for a peer, peer state, and peer data
 */
public class Peer extends Thread {
    public boolean requested;
    public int workingOn = -1;
    private String peerID;
    private String ip;
    private int port;
    private Socket connection;
    private boolean[] completedPieces;
    private boolean peer_choking, am_choking;
    private boolean peer_interested, am_interested;     
    private boolean isRunning, connected;    
    private Torrent torrent;
    private DataOutputStream dataOut;                       
    private PeerOutThread pouthread;
    private int completedcount;
    public Queue <RequestMessage> pendingReqs = new LinkedList<RequestMessage>();
    /**
     * creates a peer object.
     * We use this constructor whenever we are connecting to a peer.
     * @param peerID the peerId of this peer
     * @param ip the ip address of this peer
     * @param port the port number of this peer
     * @param numPieces the number of pieces in the torrent
     * @param torrent the torrent that is associated with this peer
     */
    public Peer(String peerID, String ip, int port, int numPieces, Torrent torrent) {
        this.peerID = peerID;
        this.ip = ip;
        this.port = port;
        completedPieces = new boolean[numPieces];
        peer_choking = am_choking = true;
        peer_interested = am_interested = false;
        this.torrent = torrent;
        connected = false;
        completedcount = 0;
    }

	/**
	 * Creates a peer object.
	 * We use this constructor whenever a peer is connecting to us
	 * @param connection the socket associated with the incoming peer
	 * @param torrent the torrent associated with this peer
	 */
    public Peer(Socket connection, Torrent torrent) {
        peerID = null;
        this.connection = connection;
        connected = true;
        completedPieces = new boolean[torrent.getNumPieces()];
        peer_choking = am_choking = true;
        peer_interested = am_interested = false;
        this.torrent = torrent;
        isRunning = true;
        completedcount = 0;
        try {
            DataOutputStream serverOut = new DataOutputStream(connection.getOutputStream());
            pouthread = new PeerOutThread(serverOut, this);
            pouthread.start();
        } catch (IOException e) {
            e.printStackTrace();
            System.exit(1);
        }
    }

    /**
     * Returns the peerId of this peer
     * @return the peerID of this peer
     */
    public String getPeerID() {
        return peerID;
    }

    /**
     * Listens for messages from a peer and handles them.
     */
    @Override
        public void run() {
            boolean firstmsg = true;
            try {
                this.connection.setSoTimeout(130000);
            } catch (SocketException e) {
                System.out.println("Problem setting the SO timeout.");				
            }
            while (this.isRunning) {
                try {
                    final Message message = Message.decodeMessage(new DataInputStream(this.connection.getInputStream()));

                    if (message == null) {
                        System.out.println("Peer sent an unsupported message. Disconnecting.");
                        disconnect();
                    }
                    switch (message.getID()) {
                        case Message.ID_CHOKE:
                            this.peer_choking = true;
                            System.out.println("CHOKE from " + peerID);
                            break;
                        case Message.ID_UNCHOKE:
                            this.peer_choking = false;
                            System.out.println("UNCHOKE from " + peerID);
                            if (requested) {
                                requestNext();
                            }
                            break;
                        case Message.ID_INTERESTED:
                            this.peer_interested = true;
                            setPeerInterested(true);
                            //this should really be in torrent, but we always
                            //unchoke so...
                            setAmChoking(false);
                            sendUnchokeMessage();
                            System.out.println("INTERESTED from " + peerID);
                            break;
                        case Message.ID_NOT_INTERESTED:
                            this.peer_interested = false;
                            System.out.println("NOT_INTERESTED from " + peerID);
                            break;
                        case Message.ID_HAVE:
                            this.completedPieces[((HaveMessage)message).getIndex()] = true;
                            completedcount++;
                            System.out.println("HAVE from " + peerID);
                            break;
                        case Message.ID_BITFIELD:
                            System.out.println("BITFIELD from " + peerID);
                            if (firstmsg)
                                setCompletedFromBitfield(((BitfieldMessage)message).getBitfield());
                            else {
                                System.out.println("Bitfield not first msg. DCing");
                                disconnect();
                            }
                            break;
                        case Message.ID_REQUEST:
                            RequestMessage request = (RequestMessage) message;
                            if (!am_choking) {
                                torrent.requestReceived(request, this);
                            }
                            System.out.println("REQUEST from : " + peerID + request.getIndex() + ", " + request.getBegin() + ", " + request.getRequestLength());
                            break;
                        case Message.ID_PIECE:
                            //System.out.println("PIECE from " + peerID);
                            PieceMessage piece = (PieceMessage) message;
                            torrent.pieceReceived(piece, this);
                            break;
                        case Message.ID_KEEP_ALIVE:
                            System.out.println("Keep-Alive received");
                            break;
                        default:
                            System.out.println("Wait. What happened here......");
                    }
                    firstmsg = false;
                } catch (SocketTimeoutException e) {
                    System.out.println("Peer: " + peerID + " hasn't sent a message in a while. DISCONNECTED!!!!!!!");
                    disconnect();				
                } catch (EOFException e) {
                    System.out.println("Peer: " + peerID +  " Disconnected Us");
                    if (workingOn != -1) {
                        torrent.rerequest(workingOn);
                    }
                    disconnect();
                } catch (IOException e) {
                    if (!isRunning)
                        return;
                    System.out.println("=============Exception in Peer's switch DCing: " + e.getMessage());
                    if (workingOn != -1) {
                        torrent.rerequest(workingOn);
                    }
                    disconnect();
                }
                if ((torrent.getDoneCount() == torrent.getNumPieces()) && (completedcount == torrent.getNumPieces())) {
                    System.out.println(peerID + " is a seed, and so are we. Disconnecting");
                    disconnect();
                }
            }
        }

    //***************************************
    //SEND MESSAGE METHODS
    //***************************************

    /**
     * Builds the handshake to be sent to a peer based on BitTorrent protocol
     * specifications
     * @returns bytes of handshake wrapped in a ByteBuffer
     */
    private ByteBuffer buildHandshake(String localPeerID, ByteBuffer info_hash) {
        byte[] handshake_bytes = new byte[68];
        handshake_bytes[0] = (char)19;
        try {
            byte[] ptsr = "BitTorrent protocol".getBytes("ASCII");
            System.arraycopy(ptsr, 0, handshake_bytes, 1, ptsr.length);
            Arrays.fill(handshake_bytes, 20, 28, (byte)0);
            System.arraycopy(info_hash.array(), 0, handshake_bytes, 28, 20);
            System.arraycopy(localPeerID.getBytes("ASCII"), 0, handshake_bytes, 48, 20);  
        } catch (UnsupportedEncodingException e) {
            e.printStackTrace();
            System.exit(1);
        }
        return ByteBuffer.wrap(handshake_bytes);
    }
	
	/**
	 * Sends a handshake to a peer
	 * @param localPeerID the peerID of the client
	 * @param info_hash the info_hash associated with the torrent
	 */
    private void sendHandshake(String localPeerID, ByteBuffer info_hash) {
        try {
            ByteBuffer handshake = buildHandshake(localPeerID, info_hash);
            if(!connected) {
                this.connect();
            }
            DataOutputStream serverOut = new DataOutputStream(connection.getOutputStream());
            serverOut.write(handshake.array());
            serverOut.flush();
        } catch(IOException e) {
            System.out.println("Problem sending handshake to " + peerID);
            e.printStackTrace();
            //System.exit(1);
        }
    }

	/**
	 * Verifies an incoming handshake by checking the info hash and the peerID against
	 * the expected info Hash and PeerID.
	 * @return true if the PeerID and info hash match, otherwise false
	 */
    private boolean verifyHandshake() {
        try {
            DataInputStream serverIn = new DataInputStream(connection.getInputStream());
            byte[] response = new byte[68];
            connection.setSoTimeout(500);
            System.out.println("Bytes received (handshake): " + serverIn.read(response));
            connection.setSoTimeout(0);
            if (Arrays.equals(torrent.getInfoHash().array(), Arrays.copyOfRange(response, 28, 48)))
                if (Arrays.equals(peerID.getBytes("ASCII"), Arrays.copyOfRange(response, 48, 68)))
                    return true;
        } catch (SocketTimeoutException e) {
            System.out.println("Timeout while trying to handshake " + peerID);
            disconnect();
            return false;
        } catch (IOException e) {
            System.out.println("Problem verifying handshake");
            disconnect();
            e.printStackTrace();
        }
        return false;
    }

	/**
	 * InitiateHandshake is called when we are connecting to another peer
	 * and sending the handshake.
	 * @param localPeerID the peerID of the client
	 * @param info_hash the info_hash of the client
	 * @return true if the handshake is verified, otherwise false
	 */
    public boolean initiateHandshake(String localPeerID, ByteBuffer info_hash) {
        sendHandshake(localPeerID, info_hash);
        return verifyHandshake();
    }
	
	/**
	 * AnswerHandshake is called when we need to respond to a connecting peer's
	 * handshake.
	 * @param localPeerID the peerID of the client
	 * @param info_hash the info_hash of the client
	 * @return true if the handshake is successful, otherwise false
	 */
    public boolean answerHandshake(String localPeerID, ByteBuffer info_hash) {
        try {
            if(initPeerFromHandshake(info_hash.array())) {
                System.out.println("incoming handshake okay");
                sendHandshake(localPeerID, info_hash);
                return true;
            }
            System.out.println("incoming handshake failed");
        } catch(IOException e) {
            e.printStackTrace();
        }
        return false;
    }

	/**
	 * Creates a Peer object from an incoming peer's handshake
	 * @param ih the bytes of the incoming peer's handshake
	 * @return true if the peer has been initialized, otherwise false
	 */
    private boolean initPeerFromHandshake(byte[] ih) throws IOException {
        DataInputStream serverIn = new DataInputStream(connection.getInputStream());
        if(serverIn.readByte() == (byte)19) {
            System.out.println("first byte okay");
            byte ptsr[] = new byte[19];
            serverIn.readFully(ptsr);
            if (Arrays.equals("BitTorrent protocol".getBytes("ASCII"), ptsr)) {
                System.out.println("ptsr okay");
                byte zeroBytes[] = new byte[8];
                serverIn.readFully(zeroBytes); //do we need to verify these are zero?
                byte info_hash[] = new byte[20];
                byte peerid[] = new byte[20];
                serverIn.readFully(info_hash);
                if (Arrays.equals(info_hash, ih)) {
                    serverIn.readFully(peerid);
                    peerID = new String(peerid, "ASCII");
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * Sends a choke message to this peer
     * @throws IOException if there is a problem writing the data to the output stream                    //Rob does this in his code, i guess i should too?
     */
    public synchronized void sendChokeMessage() throws IOException {
        Message.encodeMessage(Message.CHOKE_MESSAGE, dataOut);
    }
    /**
     * Sends an interested message to this peer
     * @throws IOException if there is a problem writing the data to the output stream
     */
    public void sendInterestedMessage() {
        LinkedList<Message> requestQueue = getRequestQueue();
        synchronized(requestQueue) {
            requestQueue.addLast(Message.INTERESTED_MESSAGE);
            requestQueue.notify();
        }
    }
    /**
     * Sends an uninterested message to this peer
     * @throws IOException if there is a problem writing the data to the output stream
     */
    public synchronized void sendUninterestedMessage() throws IOException {
        Message.encodeMessage(Message.UNINTERESTED_MESSAGE, dataOut);
    }
    /**
     * Sends a keep-alive message to this peer
     * @throws IOException if there is a problem writing the data to the output stream
     */
    public synchronized void sendKeepAliveMessage() throws IOException {
        LinkedList<Message> requestQueue = getRequestQueue();
        synchronized(requestQueue) {
            requestQueue.addLast(Message.KEEP_ALIVE_MESSAGE);
            requestQueue.notify();
        }
    }

    /**
     * Sends a bitfield message to this peer
     * @param bitfield a byte array that contains information as to which pieces a peer has
     * @throws IOException if there is a problem writing the data to the output stream
     */
    public synchronized void sendBitfieldMessage(byte[] bitfield) throws IOException {
        Message.encodeMessage(new BitfieldMessage(bitfield), dataOut);
    }

    /**
     * Sends a request message to this peer
     * @param index The zero-based piece index denoting the piece requested
     * @param pieceOffset The zero-based byte offset within the piece requested 
     * @param requestLength The number of bytes requested
     * @throws IOException if there is a problem writing the data to the output stream
     */
    public synchronized void sendRequestMessage(int index, int pieceOffset, int requestLength) throws IOException {
        //System.out.println("adding request to queue");
        synchronized(pendingReqs) {
            pendingReqs.offer(new RequestMessage(index, pieceOffset, requestLength));
        }
    }

	/**
	 * RequestNext grabs the next message out of pending Request and adds it to 
	 * pOutThreads message queue.
	 */
    public synchronized void requestNext() {
        synchronized(pendingReqs) {
            if (pendingReqs.isEmpty() == false) {
                LinkedList<Message> outQueue = getRequestQueue();
                synchronized(outQueue) {
                    outQueue.addFirst(pendingReqs.peek());
                    outQueue.notify();
                }
            }
        }
    }

	/**
	 * StartRequests adds the first message in this Peer's pending request queue
	 * to pOutThreads message queue.
	 */
    public void startRequests() {
        synchronized(pendingReqs) {
            LinkedList<Message> requestQueue = getRequestQueue();
            synchronized(requestQueue) {
                requestQueue.addFirst(pendingReqs.peek());
                requestQueue.notify();
            }
        }
    }
    /**
     * Sends an unchoke message to this peer
     * @throws IOException if there is a problem writing the data to the output stream
     */
    public synchronized void sendUnchokeMessage() {
        //System.out.println("adding request to queue");
        LinkedList<Message> requestQueue = getRequestQueue();
        synchronized(requestQueue) {
            requestQueue.add(Message.UNCHOKE_MESSAGE);
            //System.out.println(peerID + "Unchoking");
            requestQueue.notifyAll();
        }

    }

    /**
     * Sends a have message to this peer
     * @param index the index of the piece that the client has
     * @throws IOException if there is a problem writing the data to the output stream
     */
    public synchronized void sendHaveMessage(int index) {
        LinkedList<Message> requestQueue = getRequestQueue();
        synchronized(requestQueue) {
            requestQueue.add(new HaveMessage(index));
            //System.out.println(peerID + " sending have message");
            requestQueue.notifyAll();
        }
    }

    /**
     * Sends a piece message to this peer
     * @param index zero-based integer specifying which piece is being sent
     * @param begin zero-based integer specifying the offset within a piece
     * @param data byte array that contains the data block of the piece
     * @throws IOException if there is a problem writing the data to the output stream
     */
    public synchronized void sendPieceMessage(int index, int begin, byte[] data) {
        LinkedList<Message> requestQueue = getRequestQueue();
        synchronized(requestQueue) {
            requestQueue.add(new PieceMessage(index, begin, data));
            System.out.println("\n" + peerID + "sending piece message");
            requestQueue.notifyAll();
        }
    }

    //****************************************
    //CONNECTION METHODS
    //****************************************
    /**
     * Returns the IP of this peer
     * @return the IP of this peer
     */
    public String getIP() {
        return ip;
    }

    /**
     * Returns the Port of this peer
     * @return the port of this peer
     */
    public int getPort() {
        return port;
    }

    /**
     * Disconnect from this peer
     */
    public void disconnect() {
        if (connected) {
            System.out.println("Disconnect called on " + peerID);
            this.isRunning = false;
            this.connected = false;
            this.pouthread.shutdown();
            if(torrent.isRunning)
				torrent.removePeer(this);
            try{
                this.connection.close();
            } catch (IOException e) {
                //ignore because we are closing
            }
        }
    }

    /**
     * Initializes this peer's Socket object
     * Initializes the dataOutputStream to this peer
     */
    public void connect() {
        try {
            connection = new Socket(ip, port);
            this.dataOut = new DataOutputStream(this.connection.getOutputStream());
            pouthread = new PeerOutThread(dataOut, this);
            pouthread.start();
            this.isRunning = true;
            connected = true;
        } catch (IOException e) {
            torrent.removePeer(this);
            System.out.println(peerID + " could not connect.");
        }
    }

    /**
     * returns this peer's Socket object
     * @return the Socket object of this peer
     */
    public Socket getConnection() {
        return connection;
    }

    /**
     * Returns the whether this peer is choking the client
     * @return choked 
     */
    public synchronized boolean peerChoking() {
        return peer_choking;
    }

    /**
     * Returns the whether the client is choking the peer
     * @return am_choking
     */
    public synchronized boolean amChoking() {
        return am_choking;
    }

    /**
     *Returns whether the client is interested in the peer
     *@returns am_interested 
     */
    public synchronized boolean amInterested() {
        return am_interested;
    }

    /**
     *Returns whether the peer is interested in the client
     *@returns peer_interested 
     */
    public synchronized boolean peerInterested() {
        return peer_interested;
    }
    public synchronized void setPeerInterested(boolean peer_interested) {
        this.peer_interested = peer_interested;
    }

    /**
     *Sets whether the client is choking the peer
     *@param am_hoking  true if the client is choking the peer, otherwise false
     */
    public synchronized void setAmChoking(boolean am_choking) {
        this.am_choking = am_choking;
    }
    /**
     *Sets whether the client is interested the peer.
     *@param interested  true if the client is interested in the peer, otherwise false
     */
    public synchronized void setAmInterested(boolean am_interested) {
        this.am_interested = am_interested;
    }

    //*****************************
    //PIECE METHODS
    //*****************************
    /**
     * Syncs this Peer objects CompletedPieces boolean array with a given bitfield
     * @param bitfield byte array recieved from peer that contains which pieces the peer has
     */
    public void setCompletedFromBitfield(byte[] bitfield) {
        for (int x = 0; x < completedPieces.length; x++) {
            if (isSet(bitfield, x)) {
                completedPieces[x] = true;
                completedcount++;
            }
        }
    }
    /**
     * Checks whether a value in a bitfield is set to one or zero
     * @param bitfield byte array recieved from peer that contains which pieces the peer has
     * @param bit which we are checking
     * @return true if bit is one
     * 			false if bit is zero
     */
    private boolean isSet(byte[] bitfield, int bit) {
        int index = bit / 8;
        byte b = bitfield[index];
        return (b >> (7 - bit) & 1) == 1;
    }
    /**
     * Sets the completedPieces boolean array to true based on the given index. <br>
     * This function will be used when receiving have messages instead of a bitfield.
     * @param pieceIndex the index in the boolean array will be set to true
     */
    public void setCompletedByIndex(int pieceIndex) {
        completedPieces[pieceIndex] = true;
    }
    /**
     * Returns the boolean array that represents which pieces this peer has
     * @return the boolean array where if an index is true, the peer has that piece, and if the index
     * is false, the peer does not have that piece
     */
    public boolean[] getHaves() {
        return completedPieces;
    }
    /**
     * Prints which pieces the peer has
     */
    public void printHaves() {
        System.out.println(Arrays.toString(completedPieces));
    }
    
    /**
     * Returns whether or not the client is connected to this peer
     * @return true if the client is connected to the peer, otherwise false
     */
    public boolean isConnected() {
        return connected;
    }

	/** 
	 * Returns the message queue of this peers PeerOutThread
	 * @return the message queue of this peers PeerOutThread
	 */
    public LinkedList<Message> getRequestQueue() {
        return pouthread.getQueue();
    }
    /**
     * Returns whether or not this peer is running
     * @return true if this peer is running, otherwise false
     */
    public boolean isRunning() {
        return this.isRunning;
    }
}
