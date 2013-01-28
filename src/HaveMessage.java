/****************************************************************************************
 * Internet Technology Project Assignment #2                                           *
 * Spring Semester 2012                                                                 *
 * Group 12: Rohit Kumar (rsk120), Akhilesh Maddali (amaddali), Justin Rokisky (jrokisk)*
 ***************************************************************************************/

/**
 * Representation of a HAVE message in the BT protocol.
 * have: <len=0005><id=4><piece index>
 * The have message is fixed length. The payload is the zero-based
 * index of a piece that has just been successfully downloaded and 
 * verified via the hash.
 */
public class HaveMessage extends Message {

    /**
     * Zero-based piece index
     */
    private int index;

    /**
     * Constructs a HaveMessage with the given piece index
     */
    public HaveMessage(int index) {
        super(5, ID_HAVE);
        this.index = index;
    }

    /**
     * Returns the zero-based piece index in this HaveMessage
     */
    public int getIndex() {
        return index;
    }
}
    
