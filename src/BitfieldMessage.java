/****************************************************************************************
 * Internet Technology Project Assignment #2                                           *
 * Spring Semester 2012                                                                 *
 * Group 12: Rohit Kumar (rsk120), Akhilesh Maddali (amaddali), Justin Rokisky (jrokisk)*
 ***************************************************************************************/

/**
 * Bitfield Messages are exchanged between peers to tell other peers what pieces the sending
 * peer has.
 * bitfield: <len=0001+X><id=5><bitfield>
 * The bitfield message is variable length, where X is the length of the
 * bitfield. The payload is a bitfield representing the pieces that have been
 * successfully downloaded. The high bit in the first byte corresponds to piece
 * index 0. Bits that are cleared indicated a missing piece, and set bits
 * indicate a valid and available piece. Spare bits at the end are set to zero.
 * Some clients (Deluge for example) send bitfield with missing pieces even if
 * it has all data. Then it sends rest of pieces as have messages. They are
 * saying this helps against ISP filtering of BitTorrent protocol. It is called
 * lazy bitfield.
 * A bitfield of the wrong length is considered an error. Clients should drop
 * the connection if they receive bitfields that are not of the correct size,
 * or if the bitfield has any of the spare bits set.
 *
 */
public class BitfieldMessage extends Message {

    /**
     * Bitfield representation
     */
    private byte[] bitfield; 

    /**
     * Creates a BitField Message with the given bitfield
     * @param bitfield byte array that contains the peer's bitfield
     */
    public BitfieldMessage(byte[] bitfield) {
        super(1 + bitfield.length, Message.ID_BITFIELD);
        this.bitfield = bitfield;
    }	

    /**
     * Returns the bitfield contained in this BitField Message
     * @return the bitfield contained in this BitField Message
     */
    public byte[] getBitfield(){
        return this.bitfield;
    }
}
