/****************************************************************************************
 * Internet Technology Project Assignment #2                                            *
 * Spring Semester 2012                                                                 *
 * Group 12: Rohit Kumar (rsk120), Akhilesh Maddali (amaddali), Justin Rokisky (jrokisk)*
 ***************************************************************************************/

import java.util.Random;

/**
 * The RUBTUtil class contains assorted utility functions
 */
public class RUBTUtil {
    private static char[] alphaNumArr = new char[62];                   //Alphanumeric array of a-zA-z0-9 for use in generating random peer id
    static {
        for (int x = 0; x < 10; x++) {
            alphaNumArr[x] = (char)('0' + x);
        }
        for (int x = 0; x < 26; x++) {
            alphaNumArr[x + 10] = (char)('a' + x);
            alphaNumArr[x + 36] = (char)('A' + x);
        }
    }
    /**
     * Generates a random PeerId from alphanumerics
     * 
     *@return a randomly generated PeerId
     */
    public static String generatePeerId() {                           
        Random generator = new Random();                                
        StringBuilder sb = new StringBuilder();
        sb.append("Group12");
        for (int x = 0; x < 13; x++) {
            int i = generator.nextInt(alphaNumArr.length);
            sb.append(alphaNumArr[i]);
        }
        return sb.toString();
    }
    /**
     * Escapes the values in a given byte array so it is HTTP safe
     * @param buf a byte array that needs to be escaped
     * @return the string format of the escaped byte array
     */
    public static String escapeBytes(byte[] buf) {
        StringBuilder sb = new StringBuilder();
        for (int x = 0; x < buf.length; x++) {                              //Escapes everything
            sb.append("%" + String.format("%02X", buf[x]));
        }
        return sb.toString();
    }

    /**
     * Checks whether a character is HTML safe
     * @param x the character that is to be checked
     * @return true if the character is HTTP safe
     * 			false if the character is not HTTP safe
     */
    private static boolean isSafe(char x) {                               //Only alphanumeric ascii characters, and 
        if((x >= 'A' && x <= 'Z') || (x >= 'a' && x <= 'z'))                //$-_.+!*'()," allowed. All others need to be 
            return true;                                                    //Escaped with % and hex code.
        if(x >= '0' && x <= '9')
            return true;
        String otherAllowedChars = "$-_.+!*'(),";
        if(otherAllowedChars.indexOf(x) != -1) 
            return true;
        return false;
    }
    /** Source: Hacking the Art of Exploitation by Jon Erickson <br>
     * Original snippet published in C, "translated" to Java.   <br>
     * Prints out data in a byte array in a readable format
     * @param data_buffer the byte array to be printed
     */
    public static void dump(byte[] data_buffer) {
        byte b;
        int i, j;
        for (i = 0; i < data_buffer.length; i++) {
            b = data_buffer[i];
            System.out.format("%02X ", data_buffer[i]);
            if(((i % 16) == 15) || (i == data_buffer.length - 1)) {
                for (j = 0; j < 15 - (i % 16); j++)
                    System.out.print("   ");
                System.out.print("| ");
                for (j = (i - (i % 16)); j <= i; j++) {
                    b = data_buffer[j];
                    if ((b > 31) && (b < 127))
                        System.out.format("%c", b);
                    else
                        System.out.print(".");
                }
                System.out.print("\n");
            }
        }
    }
}

