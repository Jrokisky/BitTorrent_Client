/****************************************************************************************
 * Internet Technology Project Assignment #2                                            *
 * Spring Semester 2012                                                                 *
 * Group 12: Rohit Kumar (rsk120), Akhilesh Maddali (amaddali), Justin Rokisky (jrokisk)*
 ***************************************************************************************/
 
 import java.io.*;
 import java.util.Arrays;
 import java.nio.ByteBuffer;
 import java.security.*;
 import java.lang.Integer;

 /**
  * FileHandler class is responsible for all interactions with 
  * our outputted files
  */
 
 public class FileHandler {
	 private int fileLength;
	 private int pieceLength;
	 private int numberPieces;
	 private File fileName;
	 private File uploadedFile;
	 private RandomAccessFile raFile;
	 private ByteBuffer[] sha1Hashes;
	 private boolean[] completedPieces;	
	 private Torrent torrent; 
		  
	/**
	 * FileHandler constructor creates a new FileHandler object with 
	 * given size and name.
	 * @param fileLength length of the file
	 * @param pieceLength length of each piece
	 * @param fileName name of the file
	 * @param sha1Hashes the sha1 hashes for each piece
	 * @param torrent the torrent associated with the files we are trying to dl
	 */
	public FileHandler(int fileLength, int pieceLength, String fileName, ByteBuffer[] sha1Hashes, Torrent torrent) {
		this.fileLength = fileLength;
		this.pieceLength = pieceLength;
		this.numberPieces = sha1Hashes.length;
		this.sha1Hashes = sha1Hashes;
		this.uploadedFile = new File(fileName + ".stat");
		this.fileName = new File(fileName);
		this.completedPieces = new boolean[this.numberPieces];	
		this.torrent = torrent;
	}
	
	/**
	 * InitializeFile creates a random access file if the file does not exist.
	 * If the file exists, it calls methods to check what pieces the file has.
	 * If there is an uploaded file, get the stored stat information from the file and 
	 * set the Uploaded and Downloaded variables in torrent.
	 */
	public void initializeFile() {
		try {
			if(this.fileName.isFile() && this.uploadedFile.isFile()) {
				FileInputStream fstream = new FileInputStream(uploadedFile);
				DataInputStream in = new DataInputStream(fstream);
				BufferedReader br = new BufferedReader(new InputStreamReader(in));
				br.readLine(); //read "Uploaded: " string
				String value = br.readLine();
				int uploadedValue = Integer.parseInt(value);
				torrent.setUploaded(uploadedValue);
				br.readLine(); //read "Downloaded: " string
				String DLValue = br.readLine();
				int downloadedValue = Integer.parseInt(DLValue);
				torrent.setDownloaded(downloadedValue);
			}
		} catch (IOException e) {
			System.out.println("Problem accessing the upload data file. Program will close.");
			torrent.shutdown();
		} 
		
		try {
			if(this.fileName.isFile()){
				System.out.println("File already exists. Rehashing to see which pieces have been completed.");
				raFile = new RandomAccessFile(fileName, "rw");
					reHash();
			} else 
				raFile = new RandomAccessFile(fileName, "rw");
		} catch (FileNotFoundException e) {
			System.out.println("Problem accessing the file. Program will close.");
			torrent.shutdown();
		}
	}
	/**
	 * reHash checks the hashes of a file to see if a piece is complete
	 */
	private boolean[] reHash() {
		byte[] piece = new byte[pieceLength];
		byte[] lastPiece = new byte[fileLength - ((numberPieces-1)*pieceLength)];
		byte[] correctHash;
		boolean verified = false;
		try {
			this.raFile.seek(0); //make sure we're at start of file
			for(int i = 0; i < numberPieces; i++) {
				correctHash = sha1Hashes[i].array();
				if(i < (numberPieces - 1)){
					raFile.readFully(piece);
					verified = verifyPieceHash(piece,correctHash);
					if(verified){
						this.completedPieces[i] = true;
						System.out.println("Piece: " + i + " verified.");
						torrent.updateLeft(pieceLength);
					}
				} else { //If we're on the lastPiece
					raFile.readFully(lastPiece);
					verified = verifyPieceHash(lastPiece,correctHash);
					if(verified){
						this.completedPieces[i] = true;
						System.out.println("Piece: " + i + " verified.");
						torrent.updateLeft(fileLength - ((numberPieces-1)*pieceLength));
					}
				}		
			}
		} catch (NoSuchAlgorithmException e) {
				//BALLS
		} catch (IOException d) {
				//MORE BALLS
		}	
		return completedPieces;
	}
	/**
     * Verifies the piece data in the temporary buffer generating a SHA-1 hash,
     * and comparing it to the correct hash expected, which was passed when the Piece
     * object was first created.
     * @param piece a piece of data
     * @param correctHash the correct sha1 hash for the piece
     * @return True if piece is correct (matches SHA-1), False otherwise.
     */
    public boolean verifyPieceHash(byte[] piece, byte[] correctHash) throws NoSuchAlgorithmException {
        MessageDigest sha1 = MessageDigest.getInstance("SHA-1");
        sha1.update(piece);
        byte[] calculatedHash = sha1.digest();
        return Arrays.equals(calculatedHash, correctHash); 
    }
    
    /**
     * Writes the amount of data that has been uploaded to the statistics file
     */
    public void writeUploadedAndDownloaded() {
		try {
			BufferedWriter bw = new BufferedWriter(new FileWriter(uploadedFile, false));
			String upString = Integer.toString(torrent.getUploaded());
			bw.write("Uploaded: \n");
			bw.write(upString);
			bw.write("\n");
			String downString = Integer.toString(torrent.getDownloaded());
			bw.write("Downloaded: \n");
			bw.write(downString);
			bw.flush();
			bw.close();
		} catch (IOException e) {
			System.out.println("Problem writing to upload file. Guess thats not getting saved.");
		}	
	}
	
	/**
	 * Writes the given piece of data to the given index, zero based.
	 * @param index the index where to write the desired piece
	 * @param data the data block to write to the file
	 */
	public synchronized void writePiece(int index, byte[] data) {
		try {
			this.raFile.seek(index * pieceLength);
			this.raFile.write(data);
		} catch (IOException e) {
			//TURDS
		}
		this.completedPieces[index] = true;	
		System.out.println("Piece: " + index + "written to file.");
	}
	/**
	 * Returns the subpiece of data at a given index, offset and of a give length.
	 * @param index the index of the desired piece
	 * @param offset the offset within a piece
	 * @param length the length of the subpiece
	 * @return the desired piece
	 */
	public synchronized byte[] getSubPiece(int index, int offset, int length) {
		if(!completedPieces[index] || index < 0 || index >= this.numberPieces)
			return null; // MAYBE THROW ERROR HERE
		try {
			this.raFile.seek(index * pieceLength + offset);
			byte[] subPiece = new byte[length];
			this.raFile.readFully(subPiece);
			return subPiece;
			
		} catch (IOException e) {
		    System.out.println("Error reading subpiece from file");
		    e.printStackTrace();
		    System.exit(1);
		}
		return null;
	}
	
	/**
	 * Returns the boolean array of completed pieces
	 * @return the boolean array of completed pieces
	 */
	public boolean[] getBooleanArray() {
		return this.completedPieces;
	}

	/**
	 * Closes the random access File where the file has been saved too
	 */
	public void close() {
	    try {
            raFile.close();
        } catch (IOException e) {
            System.out.println("Could not close file properly: " + e.getMessage());
        }
    }
}
