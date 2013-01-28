/****************************************************************************************
 * Internet Technology Project Assignment #2                                            *
 * Spring Semester 2012                                                                 *
 * Group 12: Rohit Kumar (rsk120), Akhilesh Maddali (amaddali), Justin Rokisky (jrokisk)*
 ***************************************************************************************/
 
import java.nio.ByteBuffer;
/**
 * Key Constants used by BitTorrent protocol
 */
public class Protocol {
    public static final ByteBuffer KEY_INTERVAL = ByteBuffer.wrap(new byte[] {'i', 'n', 't', 'e', 'r', 'v', 'a', 'l'});
    public static final ByteBuffer KEY_PEERS = ByteBuffer.wrap(new byte[] {'p', 'e', 'e', 'r', 's'});
    public static final ByteBuffer KEY_PEER_ID= ByteBuffer.wrap(new byte[] {'p', 'e', 'e', 'r', ' ', 'i', 'd'});
    public static final ByteBuffer KEY_IP = ByteBuffer.wrap(new byte[] {'i', 'p'});
    public static final ByteBuffer KEY_PORT = ByteBuffer.wrap(new byte[] {'p', 'o', 'r', 't'});
}

