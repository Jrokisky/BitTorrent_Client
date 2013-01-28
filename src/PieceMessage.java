/****************************************************************************************
 * Internet Technology Project Assignment #2                                            *
 * Spring Semester 2012                                                                 *
 * Group 12: Rohit Kumar (rsk120), Akhilesh Maddali (amaddali), Justin Rokisky (jrokisk)*
 ***************************************************************************************/

/**
 * Piece Messages send a specified piece of data from one peer to another
 * piece: <len=0009+X><id=7><index><begin><block>
 * The piece message is variable length, where X is the length of the block.
 * The payload contains the following information:
 * - index: integer specifying the zero-based piece index
 * - begin: integer specifying the zero-based byte offset within the piece
 * - block: block of data, which is a subset of the piece specified by index.
 */
public class PieceMessage extends Message {
    /**
     * The zero based index denoting the piece in this PieceMessage
     */
    private int index; 

    /**
     * The zero based byte offset of the data for the piece
     */
    private int begin; 

    /**
     * The piece data itself
     */
    private byte[] block;

    /**
     * Creates a Piece Message with the given index, begin offset, and data block
     * @param index zero-based integer specifying which piece is being sent
     * @param begin zero-based integer specifying the offset within a piece
     * @param block byte array that contains the data block of the piece
     */
    public PieceMessage(int index, int begin, byte[] block) {
        super(9 + block.length, Message.ID_PIECE);
        this.index = index;
        this.begin = begin;
        this.block = block;
    }

    /**
     * Returns the index of the piece being sent
     * @return the index of the piece being sent
     */
    public int getIndex() {
        return this.index;
    }
    /**
     * Returns the offset of the data block stored in this Piece Message
     * @return the offset of the data block stored in this Piece Message
     */
    public int getBegin() {
        return this.begin;
    }
    /**
     * Returns the data block contained in this Piece Message
     * @return the data block contained in this Piece Message
     */
    public byte[] getBlock() {
        return this.block;
    } 
}
