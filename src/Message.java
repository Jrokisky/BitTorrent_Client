/****************************************************************************************
 * Internet Technology Project Assignment #2                                            *
 * Spring Semester 2012                                                                 *
 * Group 12: Rohit Kumar (rsk120), Akhilesh Maddali (amaddali), Justin Rokisky (jrokisk)*
 ***************************************************************************************/

import java.io.*;
import java.net.SocketException;
import java.util.Arrays;
import java.io.UnsupportedEncodingException;

/**
 * Basic implementation for all messages between peers 
 * using the BT protocol.
 */
public abstract class Message {
    public static final byte ID_CHOKE = 0;
    public static final byte ID_UNCHOKE = 1;
    public static final byte ID_INTERESTED = 2;
    public static final byte ID_NOT_INTERESTED = 3;
    public static final byte ID_HAVE = 4;
    public static final byte ID_BITFIELD = 5;
    public static final byte ID_REQUEST = 6;
    public static final byte ID_PIECE = 7;
    public static final byte ID_KEEP_ALIVE=126;
    public static final ChokeMessage CHOKE_MESSAGE = new ChokeMessage();
    public static final UnchokeMessage UNCHOKE_MESSAGE = new UnchokeMessage();
    public static final InterestedMessage INTERESTED_MESSAGE = new InterestedMessage();
    public static final UninterestedMessage UNINTERESTED_MESSAGE = new UninterestedMessage();
    public static final KeepAliveMessage KEEP_ALIVE_MESSAGE = new KeepAliveMessage();

    /**
     *  Length of a message.
     */
    private final int length;

    /**
     * ID used to identify what type of BT protocol message a message is.
     */
    private final byte id;

    /**
     * Message constructor creates a new Abstract Message object
     * @param length The length of a message in bytes
     * @param id The ID of this message, denoting message type (single byte)
     */
    public Message(final int length, final byte id) {
        this.length = length;
        this.id = id;
    }

    /**
     * Returns the length of this message
     * @return The length of this message
     */
    public int getLength() {
        return length;
    }

    /**
     * Returns the message ID of this message
     * @return the message ID of this message
     */
    public byte getID() {
        return id;
    }

    /**
     * Message representing a CHOKE message in the BT protocol.
     * choke: <len=0001><id=0>
     * The choke message is fixed-length and has no payload.
     * All CHOKE messages are identical.
     */
    public static final class ChokeMessage extends Message {
        private ChokeMessage() {
            super(1, ID_CHOKE);
        }
    }

    /**
     * Message representing an UNCHOKE message in the BT protocol.
     * unchoke: <len=0001><id=1>
     * The unchoke message is fixed-length and has no payload.
     * All UNCHOKE messages are identical.
     */
    public static final class UnchokeMessage extends Message {
        private UnchokeMessage() {
            super(1, ID_UNCHOKE);
        }
    }

    /**
     * Message representing an INTERESTED Message in the BT protocol.
     * interested: <len=0001><id=2>
     * The interested message is fixed-length and has no payload.
     * All INTERESTED messages are identical.
     */
    public static final class InterestedMessage extends Message {
        private InterestedMessage() {
            super(1, ID_INTERESTED);
        }
    }

    /**
     * Message representing an NOT INTERESTED message in the BT protocol.
     * not interested: <len=0001><id=3>
     * The not interested message is fixed-length and has no payload.
     * All NOT INTERESTED messages are identical.
     */
    public static final class UninterestedMessage extends Message {
        private UninterestedMessage() {
            super(1, ID_NOT_INTERESTED);
        }
    }

    /**
     * Message representing a Keep Alive message in the BT protocol.
     * keep-alive: <len=0000>
     * All keep-alives are identical
     */
    public static final class KeepAliveMessage extends Message {
        private KeepAliveMessage() {
            super(0, ID_KEEP_ALIVE);
        }
    }

    /**
     * Encodes a message according to BT protocol, and writes to the output stream.
     * The purpose of this is to centralize IO 
     * @param message Message object to be encoded according to BT protocol and sent
     * @param out DataOutputStream to send the encoded message along.
     */
    public static void encodeMessage(final Message message, final DataOutputStream out) throws IOException {
        int msg_len = message.getLength();                   //Message length is always sent first as 4 byte int
        byte message_id = message.getID();                 //Read message_id byte
        out.writeInt(msg_len);                             //This covers Chokes, Interesteds, and Keep-alives
        if (message_id != ID_KEEP_ALIVE)
            out.writeByte(message_id);
        if (msg_len > 1) {
            switch (message_id) {
                case Message.ID_HAVE:
                    HaveMessage have = (HaveMessage) message;
                    out.writeInt(have.getIndex());
                    break;
                case Message.ID_BITFIELD:
                    BitfieldMessage bitfield = (BitfieldMessage) message;
                    out.write(bitfield.getBitfield());
                    break;
                case Message.ID_REQUEST:
                    RequestMessage request = (RequestMessage) message;
                    out.writeInt(request.getIndex());
                    out.writeInt(request.getBegin());
                    out.writeInt(request.getRequestLength());
                    break;
                case Message.ID_PIECE:
                    PieceMessage piece = (PieceMessage) message;
                    out.writeInt(piece.getIndex());
                    out.writeInt(piece.getBegin());
                    out.write(piece.getBlock());
                    break;
                default:
                    System.out.println("Unknown Message in encodeMessage()");
                    break;
            }
        }
        out.flush();
    }

    /**
     * Decodes a Message sent according to BT protocol and returns the appropriate Message.
     * @param in DataInputStream to read messages from 
     * @return returns appropriate Message object depeneding on message id received.
     */
    public static Message decodeMessage(final DataInputStream in) throws IOException {
        if (in == null || in.available() < 0) 
            throw new SocketException("Peer disconnected us: Socket closed");
        int msg_len = in.readInt();                   //Message length is always sent first as 4 byte int
        if (msg_len == 0) {
            return Message.KEEP_ALIVE_MESSAGE;
        }
        byte message_id = in.readByte();                 //Read message_id byte
        Message message = null;
        switch (message_id) {
            case Message.ID_CHOKE:
                message = Message.CHOKE_MESSAGE;
                break;
            case Message.ID_UNCHOKE:
                message = Message.UNCHOKE_MESSAGE;
                break;
            case Message.ID_INTERESTED:
                message = Message.INTERESTED_MESSAGE;
                break;
            case Message.ID_NOT_INTERESTED:
                message = Message.UNINTERESTED_MESSAGE;
                break;
            case Message.ID_HAVE:
                message = new HaveMessage(in.readInt());
                break;
            case Message.ID_BITFIELD:
                byte[] bitfield = new byte[msg_len -1];
                in.readFully(bitfield);
                message = new BitfieldMessage(bitfield);
                break;
            case Message.ID_REQUEST:
                message = new RequestMessage(in.readInt(), in.readInt(), in.readInt());
                break;
            case Message.ID_PIECE:
                int index = in.readInt();
                int begin = in.readInt();
                byte[] block = new byte[msg_len -9];
                in.readFully(block);
                message = new PieceMessage(index, begin, block);
                break;
            default:
                System.out.println("Unsupported/Unknown Message received");
                break;
        }
        return message;
    }

    /**
     * Builds the handshake to be sent to a peer based on BitTorrent protocol
     * specifications
     * @returns bytes of handshake wrapped in a ByteBuffer
     */
    private static byte[] buildHandshake(final String PROTOCOL_STRING, byte[] info_hash, String peer_id) {
        byte[] handshake_bytes = new byte[68];
        handshake_bytes[0] = (char)19;
        try {
            byte[] ptsr = PROTOCOL_STRING.getBytes("ASCII");
            System.arraycopy(ptsr, 0, handshake_bytes, 1, ptsr.length);
            Arrays.fill(handshake_bytes, 20, 28, (byte)0);
            System.arraycopy(info_hash, 0, handshake_bytes, 28, 20);
            System.arraycopy(peer_id.getBytes("ASCII"), 0, handshake_bytes, 48, 20);  
        } catch (UnsupportedEncodingException e) {
            e.printStackTrace();
            System.exit(1);
        }
        return handshake_bytes;
    }
}
