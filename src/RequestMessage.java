/****************************************************************************************
 * Internet Technology Project Assignment #2                                            *
 * Spring Semester 2012                                                                 *
 * Group 12: Rohit Kumar (rsk120), Akhilesh Maddali (amaddali), Justin Rokisky (jrokisk)*
 ***************************************************************************************/

/**
 * Representation of a REQUEST message from a Peer. 
 * A Request message is defined as follows, by the BitTorrent Protocol:
 * request: <len=0013><id=6><index><begin><length>
 * The request message is fixed length, and is used to request a block. The
 *
 * payload contains the following information:
 * - index: integer specifying the zero-based piece index
 * - begin: integer specifying the zero-based byte offset within the piece
 * - length: integer specifying the requested length.
 */
public class RequestMessage extends Message {
    /**
     * Zero based index denoting the requested piece.
     */
    private final int index;

    /**
     * Zero based byte offset within the piece requested.
     */
    private final int begin;

    /**
     * The number of bytes requested
     */
    private final int requestLength;

    /**
     * Creates a new RequestMessage for the specified piece index and piece
     * offset.
     * @param index The zero-based piece index denoting the piece requested
     * @param pieceOffset The zero-based byte offset within the piece requested
     */
    public RequestMessage(final int index, final int begin, final int requestLength) {
       super(13, ID_REQUEST); 
       this.index = index;
       this.begin = begin;
       this.requestLength = requestLength;
    }
    
    /**
     * Returns the zero-based piece index
     * @return Zero based index denoting the requested piece.
     */
    public int getIndex() {
        return index;
    }

    /**
     * Returns the zero-based byte offset within a piece
     * @return the zero based byte index within a piece
     */
    public int getBegin() {
        return begin;
    }
    
    /**
     * Returns the number of bytes requested.
     * @return the number of bytes requested.
     */
    public int getRequestLength() {
        return requestLength;
    }
}
