/****************************************************************************************
 * Internet Technology Project Assignment #2                                            *
 * Spring Semester 2012                                                                 *
 * Group 12: Rohit Kumar (rsk120), Akhilesh Maddali (amaddali), Justin Rokisky (jrokisk)*
 ***************************************************************************************/
 
import java.util.Arrays;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/**
 * Piece class is responsible for managing pieces of a torrent.
 * State information of a piece, verification of a piece, and temporary storage as a piece is being 
 * assembled is provided here.
 */
public class Piece {
    private byte[] buffer, correctHash;                        //Sha-1 hash from .torrent file for a piece, and temporary buffer
    private int bytesWritten, pieceSize;                       //this is the only way of telling if a Piece is done and ready to be hashed
    private boolean done;                                      //Piece assembly state
    /**
     * Constructs a Piece objects given the size of a piece and its correct SHA-1 hash
     * @param pieceSize Size of this piece
     * @param  correctHash Correct SHA-1 hash to be matched against the downloaded bytes
     */
    public Piece (int pieceSize, byte[] correctHash) {
        buffer = new byte[pieceSize];
        this.correctHash = correctHash;
        this.pieceSize = pieceSize;
        done = false;
    }
    /**
     * Returns current completion status of a Piece
     * @return Completion status of Piece.
     */
    public boolean isDone() {
        return done;
    }

    public void setDone(boolean done) {
        this.done = done;
    }

    /**
     * If a piece has been completed, verified, and written to the disk,
     * This method should be called in order to remove a reference to the temporary buffer
     * so that the garbage collector can get rid of this space as soon as a piece is done.
     */
    public void cleanup() {
        buffer = null;
    }
    
    /**
     * Writes some part of a piece to the temporary buffer. This is here to keep a piece
     * while it has not been completed or verified. 
     * @param subpiece piece data to be written to buffer
     * @param offset offset of the buffer where we should start writing
     */
    public void writePiece(byte[] subpiece, int offset) {
        if (buffer == null) {
            System.out.println("BUFFER NULL");
            System.exit(1);
        }
        System.arraycopy(subpiece, 0, buffer, offset, subpiece.length); 
        if ((bytesWritten += subpiece.length) == buffer.length)
            done = true;
        System.out.println("Piece status: " + bytesWritten + "/"  + buffer.length);
    }
    
    /**
     * If the piece ends up bad, you may want to restart a piece state while
     * keeping the correct hash. This method only reallocates a fresh buffer, and resets
     * done and byteswritten data
     */
    public void clearBuffer() {
        buffer = new byte[pieceSize];
        bytesWritten = 0;
        done = false;
    }
    
    /**
     * Verifies the piece data in the temporary buffer generating a SHA-1 hash,
     * and comparing it to the correct hash expected, which was passed when the Piece
     * object was first created.
     * @return True if piece is correct (matches SHA-1), False otherwise.
     */
    public boolean verifyPieceHash() {
        byte[] calculatedHash = null;
        try {
            MessageDigest sha1 = MessageDigest.getInstance("SHA-1");
            sha1.update(buffer);
            calculatedHash = sha1.digest();
            return Arrays.equals(calculatedHash, correctHash); 
        } catch (NoSuchAlgorithmException e) {
            System.out.println("Could not hash piece");
            e.printStackTrace();
            return false;
        }
    }
    
    /**
     * Returns size of piece.
     * @return Size of this piece.
     */
    public int getPieceLength() {
        return pieceSize;
    }

    /**
     * Returns bytes written to this piece so far.
     * @return number of bytes written so far
     */
    public int getBytesWritten() {
        return bytesWritten;
    }


    /**
     * Returns piece buffer
     * @return piece buffer
     */
    public byte[] getBuffer() {
        return buffer;
    }
}
