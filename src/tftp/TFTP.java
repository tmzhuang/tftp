package tftp;

import java.net.*;
import java.nio.file.DirectoryNotEmptyException;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.io.*;

/**
 * Implements the necessary tools related to TFTP for the system. It includes
 * forming different types of TFTP packets (RRQ/WRQ, DATA, ACK, and ERROR), writing to system
 * files, and some block manipulation algorithm.
 * 
 * @author Team 4
 * @version Iteration 1
 */
public class TFTP {
	public static final int BUF_SIZE = 100;
	public static final int TFTP_PADDING = 0;
	public static final int OP_CODE_SIZE = 2;
	public static final int BLOCK_NUMBER_SIZE = 2;
	public static final int MAX_DATA_SIZE = 512;
	public static final int MAX_PACKET_SIZE = 516;
	public static final int READ_OP_CODE = 1;
	public static final int WRITE_OP_CODE = 2;
	public static final int DATA_OP_CODE = 3;
	public static final int ACK_OP_CODE = 4;
	public static final int ERROR_OP_CODE = 5;
	public static final int ERROR_CODE_SIZE = 2;
	public static final int ERROR_CODE_FILE_NOT_FOUND = 1;
	public static final int ERROR_CODE_ACCESS_VIOLATION = 2;
	public static final int ERROR_CODE_DISK_FULL = 3;
	public static final int MIN_PORT = 1;
	public static final int MAX_PORT = 65535;
	public static final int MAX_ERROR_CODE = 7;
	public static final int MAX_OP_CODE = 5;
	public static final int MAX_BLOCK_NUMBER = 65535;

	/**
	 * Forms a DatagramPacket with an empty data buffer large enough to hold the maximum
	 * packet size
	 *
	 * @return DatagramPacket with an empty data buffer of size MAX_PACKET_SIZE
	 */
	public static DatagramPacket formPacket() {
		byte[] data = new byte[MAX_PACKET_SIZE];
		return new DatagramPacket(data, data.length);
	}

	/**
	 * Forms a DatagramPacket with the byte[] data passed in
	 *
	 * @param addr InetAddress of packet destination
	 * @param port Port number of packet destination
	 * @param data byte[] to use as the packet payload
	 * 
	 * @return Datagram packet for specified address and port with given request
	 */
	public static DatagramPacket formPacket(InetAddress addr, int port, byte[] data) {
		return new DatagramPacket(data, data.length, addr, port);
	}
	
	/**
	 * Forms a DatagramPacket using Request r with information about request type
	 * (read, write, or test), filename, and mode (ascii, octet, etc.).
	 *
	 * @param addr InetAddress of packet destination
	 * @param port Port number of packet destination
	 * @param r Request contains request type (READ or WRITE), filename, and mode
	 *
	 * @return Datagram packet for specified address and port with given request
	 */
	public static DatagramPacket formRQPacket(InetAddress addr, int port, Request r) {
		if (!isValidPort(port)) throw new IllegalArgumentException();
		int currentIndex;
		// Create byte array for packet
		byte[] buf = new byte[BUF_SIZE];
		// First element will always be 0
		buf[0] = TFTP_PADDING;
		switch (r.getType()) {
			case READ:
				buf[1] = 1;
				break;
			case WRITE:
				buf[1] = 2;
				break;
			default:
				buf[1] = TFTP_PADDING;
				break;
		}

		// Add filename to packet data
		byte[] fbytes = r.getFileName().getBytes();
		System.arraycopy(fbytes,0,buf,OP_CODE_SIZE,fbytes.length);

		// Add 0 byte padding
		currentIndex = fbytes.length + OP_CODE_SIZE;
		buf[currentIndex] = TFTP_PADDING;
		currentIndex++;

		// Add mode to packet data
		byte[] mbytes = r.getMode().getBytes();
		System.arraycopy(mbytes,0,buf,currentIndex,mbytes.length);

		// Add terminating 0 byte
		currentIndex = currentIndex + mbytes.length;
		buf[currentIndex] = TFTP_PADDING;

		// Truncate trailing zeros by copyings to a new array
		byte[] data = new byte[currentIndex + 1];
		System.arraycopy(buf,0,data,0,currentIndex+1);

		return new DatagramPacket(data,currentIndex+1, addr, port);
	}

	/**
	 * Given a filename, returns a queue of datagram packets for that
	 * file in 512 byte blocks.
	 *
	 * @param addr InetAddress of packet destination
	 * @param port Port number of packet destination
	 * @param filePath path of file to read
	 *
	 * @return A queue of DATA packets formed from the file specified in 512-byte chunks
	 * @throws FileNotFoundException 
	 */
	public static Queue<DatagramPacket> formDATAPackets(InetAddress addr, int port, String filePath) throws FileNotFoundException {
		if (!isValidPort(port)) throw new IllegalArgumentException();
		if (filePath.isEmpty()) throw new IllegalArgumentException();
		Queue<DatagramPacket> packetQueue = new ArrayDeque<DatagramPacket>();
		try {
			BufferedInputStream in = new BufferedInputStream(new FileInputStream(filePath));
			byte[] data = new byte[MAX_DATA_SIZE];
			int blockNumber = 1;
			int n, lastn;
			lastn = -1;

			//Read the file in 512 byte chunks and add to packet queue 
			while ((n = in.read(data)) != -1) {
				byte[] buf = new byte[n];
				System.arraycopy(data,0,buf,0,n);
				packetQueue.add(formDATAPacket(addr, port, blockNumber, buf));
				blockNumber = (blockNumber + 1) % (MAX_BLOCK_NUMBER + 1);
				lastn = n;
			}
			// Close stream
			in.close();
			// If the file is a multiple of 512, add a 0-byte data packet
			if (lastn == MAX_DATA_SIZE) {
				packetQueue.add(formDATAPacket(addr, port, blockNumber, new byte[0]));
			} else if (packetQueue.isEmpty()) {
				packetQueue.add(formDATAPacket(addr, port, blockNumber, new byte[0]));
			}
		} catch (FileNotFoundException e) {
			throw e;
		} catch(Exception e) {
			e.printStackTrace();
			System.exit(1);
		}
		return packetQueue;
	}

	/**
	 * Append a TFTP DATA packet's data to the given array byte.
	 *
	 * @param dataPacket A TFTP DATA packet
	 * @param fileBytes Byte array to append to
	 *
	 * @return New byte array with data appended
	 */
	public static byte[] appendData(DatagramPacket dataPacket, byte[] fileBytes) {
		byte[] data = getData(dataPacket);
		byte[] newFileBytes = new byte[fileBytes.length + data.length];
		// Copy old file array into new array
		System.arraycopy(fileBytes,0,newFileBytes,0,fileBytes.length);
		// Append data to array
		System.arraycopy(data,0,newFileBytes,fileBytes.length,data.length);
		return newFileBytes;
	}

	/**
	 * Write an array of bytes to a file
	 *
	 * @param filePath Name of file (including directory) to write to
	 * @param fileBytes Array of bytes to write
	 * 
	 * @return true if write successful, false if disk is full
	 */
	public static boolean writeBytesToFile(String filePath, byte[] fileBytes) {
		if (filePath.isEmpty()) throw new IllegalArgumentException();
		BufferedOutputStream out;
		try {
			out = new BufferedOutputStream(new FileOutputStream(filePath));
			out.write(fileBytes);
			out.close();
		} catch(IOException e) {
			if (e.getMessage().equals("No space left on device")) {
				return false;
			} else {
				e.printStackTrace();
				System.exit(1);
			}
		}
		return true;
	}

	/**
	 * Returns the OP code of a datagram packet as an integer.
	 *
	 * @param packet A TFTP DatagramPacket
	 *
	 * @return The OP code of the TFTP packet
	 */
	public static int getOpCode(DatagramPacket packet) {
		byte[] opCodeBytes = new byte[OP_CODE_SIZE];
		byte[] data = packet.getData();
		System.arraycopy(data,0,opCodeBytes,0,OP_CODE_SIZE);
		int opCode = toUnsignedInt(opCodeBytes[1]);
		if (!isValidOpCode(opCode))  throw new IllegalArgumentException("DatagramPacket is not a valid TFTP packet.");
		return opCode;
	}

	/**
	 * Checks the validity of the op code opCode supplied
	 *
	 * @param opCode int representing the op code to be tested
	 *
	 * @return boolean telling if the op code is valid
	 */
	public static boolean isValidOpCode(int opCode) {
		return opCode >= 1 && opCode <= 5;
	}

	/**
	 * Given a DATA or ACK datagram packet, returns the block number as an int.
	 *
	 * @param packet A TFTP DATA or ACK packet
	 *
	 * @return The block number of the ACK or DATA packet
	 */
	public static int getBlockNumber(DatagramPacket packet) {
		// Check that packet is either DATA or ACK
		int opCode = getOpCode(packet);
		boolean isDATA = opCode == DATA_OP_CODE;
		boolean isACK = opCode == ACK_OP_CODE;

		// If isn't DATA or ACK, throw an exception
		if (!(isDATA || isACK)) throw new IllegalArgumentException("Cannot get block number of packet that is not DATA or ACK.");

		// Get the block number as a byte array
		byte[] blockNumberBytes = new byte[BLOCK_NUMBER_SIZE];
		System.arraycopy(packet.getData(),OP_CODE_SIZE,blockNumberBytes,0,BLOCK_NUMBER_SIZE);

		// Check that the block number is valid
		int blockNumber = bytesToBlockNumber(blockNumberBytes);
		if (!isValidBlockNumber(blockNumber)) throw new IllegalArgumentException("Block number out of range.");

		return blockNumber;
	}

	/**
	 * Checks the validity of the block number blockNumber supplied
	 *
	 * @param opCode int representing the block number to be tested
	 *
	 * @return boolean telling if the block number is valid
	 */
	public static boolean isValidBlockNumber(int blockNumber) {
		return blockNumber>=0 && blockNumber<=MAX_BLOCK_NUMBER;
	}

	/**
	 * Given a TFTP DATA packet, returns the data portion of the TFTP packet 
	 * as a byte array.
	 *
	 * @param packet A TFTP DATA packet
	 *
	 * @return The data portion of a DATA packet as a byte array
	 */
	public static byte[] getData(DatagramPacket packet) {
		// Check that packet is DATA
		int opCode = getOpCode(packet);
		boolean isDATA = opCode == DATA_OP_CODE;

		// If packet isn't DATA, throw exception
		if (!isDATA) throw new IllegalArgumentException();

		// Check that the block number is valid
		if (!isValidBlockNumber(getBlockNumber(packet))) throw new IllegalArgumentException();

		int dataLen = packet.getLength() - OP_CODE_SIZE - BLOCK_NUMBER_SIZE;
		int dataStart = OP_CODE_SIZE + BLOCK_NUMBER_SIZE;
		byte[] data = new byte[dataLen];
		System.arraycopy(packet.getData(),dataStart,data,0,dataLen);

		return data;
	}

	/**
	 * Gets TODO(Brandon)
	 *
	 * @param packet A TFTP DATA packet
	 *
	 * @return The data portion of a DATA packet as a byte array
	 */
	public static String getErrorMessage(DatagramPacket packet) {
		//If packet isn't an error, throw exception
		if(getOpCode(packet) != ERROR_OP_CODE) throw new IllegalArgumentException();

		int msgLen = packet.getLength() - OP_CODE_SIZE - BLOCK_NUMBER_SIZE - 1;
		byte[] errorMsgBytes =  new byte [msgLen];
		System.arraycopy(packet.getData(),OP_CODE_SIZE+ERROR_CODE_SIZE,errorMsgBytes,0,msgLen);

		String errorMsg = new String(errorMsgBytes);

		return errorMsg;
	}

	public static int getErrorCode(DatagramPacket packet) {

		//If packet isn't an error, throw exception
		if(getOpCode(packet) != ERROR_OP_CODE) throw new IllegalArgumentException();

		byte[] errorCodeBytes = new byte[ERROR_CODE_SIZE];
		System.arraycopy(packet.getData(),OP_CODE_SIZE,errorCodeBytes,0,ERROR_CODE_SIZE);

		// Check that packet has a valid error code
		int errorCode = bytesToBlockNumber(errorCodeBytes);
		if (!isValidErrorCode(errorCode)) throw new IllegalArgumentException();

		return errorCode;
	}

	/**
	 * Checks the validity of the error code errorCode supplied
	 *
	 * @param opCode int representing the error code to be tested
	 *
	 * @return boolean telling if the error code is valid
	 */
	public static boolean isValidErrorCode(int errorCode) {
		return errorCode>=0 && errorCode<=MAX_ERROR_CODE;
	}

	/**
	 * Give a block number and a byte array of data, creates a datagram packet for the
	 * given IP address and port.
	 *
	 * @param addr InetAddress of DATA packet destination
	 * @param port Port number of DATA packet destination
	 * @param blockNumber The block number of the DATA packet
	 * @param data The byte array holding the data
	 *
	 * @return The respective DATA packet formed with given inputs.
	 */
	private static DatagramPacket formDATAPacket(InetAddress addr, int port, int blockNumber, byte[] data) {
		// 4+data.length because 2 bytes for op code and 2 bytes for blockNumber
		byte[] buf = new byte[OP_CODE_SIZE + BLOCK_NUMBER_SIZE +data.length];

		// Op code
		buf[0] = 0;
		buf[1] = 3;

		// Block number
		try {
			byte[] blockNumberBytes = blockNumberToBytes(blockNumber);
			System.arraycopy(blockNumberBytes,0,buf,OP_CODE_SIZE,BLOCK_NUMBER_SIZE);
		} catch(Exception e) {
			e.printStackTrace();
			System.exit(1);
		}

		// Data
		int startIndex = OP_CODE_SIZE + BLOCK_NUMBER_SIZE;
		System.arraycopy(data,0,buf,startIndex,data.length);

		return new DatagramPacket(buf,buf.length,addr,port);
	}


	/**
	 * Converts an integer to a 2-byte byte array.
	 *
	 * @param blockNumber Integer to be converted to a 2-byte byte array
	 *
	 * @return 2-byte representation of given block number
	 */
	public static byte[] blockNumberToBytes(int blockNumber) {
		if (blockNumber<0 || blockNumber>66535) throw new IllegalArgumentException();

		byte[] blockNumberBytes = new byte[2];
		blockNumberBytes[0] = (byte) (blockNumber / 256);
		blockNumberBytes[1] = (byte) (blockNumber % 256);

		return blockNumberBytes;
	}

	/**
	 * Converts a 2-byte byte array to an integer.
	 *
	 * @param bytes 2-byte byte array holding the block number
	 *
	 * @return Integer representation of given byte array
	 */
	public static int bytesToBlockNumber(byte[] bytes) {
		if (bytes.length != 2) throw new IllegalArgumentException();
		int msb = toUnsignedInt(bytes[0]);
		int lsb = toUnsignedInt(bytes[1]);
		return msb*256 + lsb;
	}

	/**
	 * Forms a ACK packet for the given IP address, port and block number
	 *
	 * @param addr InetAddress of ACK packet destination
	 * @param port Port number of ACK packet destination
	 * @param blockNumber Block number of the ACK packet
	 *
	 * @return ACK packet formed with given inputs
	 */
	public static DatagramPacket formACKPacket(InetAddress addr, int port, int blockNumber) {
		byte[] buf = new byte[OP_CODE_SIZE + BLOCK_NUMBER_SIZE];

		// Op code
		buf[0] = 0;
		buf[1] = 4;

		// Block number
		try {
			byte[] blockNumberBytes = blockNumberToBytes(blockNumber);
			System.arraycopy(blockNumberBytes,0,buf,OP_CODE_SIZE,BLOCK_NUMBER_SIZE);
		} catch(Exception e) {
			e.printStackTrace();
			System.exit(1);
		}

		return new DatagramPacket(buf,buf.length,addr,port);
	}

	/**
	 * Forms a ERROR packet given the IP address, port, and details of the error.
	 * 
	 * @param addr IP address of destination of the packet to be sent
	 * @param port Port number of destination of the packet to be sent
	 * @param errorCode Code representation of the error
	 * @param errMsg Detailed message of the error
	 * 
	 * @return ERROR packet formed with given inputs
	 */
	public static DatagramPacket formERRORPacket(InetAddress addr, int port, int errorCode, String errMsg) {
		///////////////////////////////
		// 4+data.length because 2 bytes for op code and 2 bytes for blockNumber
		byte[] sbytes = errMsg.getBytes();
		byte[] buf = new byte[OP_CODE_SIZE + BLOCK_NUMBER_SIZE + sbytes.length + 1];

		// Op code
		buf[0] = 0;
		buf[1] = ERROR_OP_CODE;

		// Block number
		// Block number
		try {
			byte[] blockNumberBytes = blockNumberToBytes(errorCode);
			System.arraycopy(blockNumberBytes,0,buf,OP_CODE_SIZE,BLOCK_NUMBER_SIZE);
		} catch(Exception e) {
			e.printStackTrace();
			System.exit(1);
		}

		// Data
		int startIndex = OP_CODE_SIZE + BLOCK_NUMBER_SIZE;
		System.arraycopy(sbytes, 0, buf, startIndex, sbytes.length);

		buf[OP_CODE_SIZE + BLOCK_NUMBER_SIZE + sbytes.length] = 0;

		return new DatagramPacket(buf, buf.length, addr, port);	
	}

	/**
	* Parse a given DatagramPacket p to see if it is valid. A valid packet must begin
	* with [0,1] or [0,2], followed by an arbitrary number of bytes representing the 
	* filename, followed by a 0 byte, followed by an arbitrary number of bytes representing
	* the mode, followed by a terminating 0 byte.
	* If the packet is valid, a request with the respective request type, filename, and mode
	* is created. Otherwise, an exception is thrown and the server quits.
	*
	* @param p Datagram packet to be parsed. Must either be a RRQ or WRQ packet.
	*
	* @return Request of the packet.
	*/
	public static Request parseRQ(DatagramPacket p) {
		Request.Type t;
		String f, m;
		int currentIndex = 0;
		int opCode = getOpCode(p);

		// Get number of bytes used by packet data
		int len = p.getData().length;
		// Make copy of data bytes to parse
		byte[] buf = new byte[len];
		System.arraycopy(p.getData(),0,buf,0,len);

		// Check second byte for read or write
		switch (opCode) {
			case 1:
				t = Request.Type.READ;
				break;
			case 2:
				t = Request.Type.WRITE;
				break;
			default:
				throw new IllegalArgumentException();
		}

		// Get filename
		currentIndex = 2;
		if (currentIndex >= len) throw new IllegalArgumentException();
		// Create an array of bytes to hold filename byte data
		byte[] fbytes = new byte[len];
		// Loop through array until 0 byte is found or out of bound occurs
		while (buf[currentIndex] != TFTP_PADDING) {
			currentIndex++;
			if (currentIndex >= len) throw new IllegalArgumentException();
		}
		int filenameLength = currentIndex - 2;
		System.arraycopy(buf,2,fbytes,0,filenameLength);
		f = new String(fbytes).trim();

		// Check for 0 byte padding between filename and mode
		if (buf[currentIndex] != TFTP_PADDING) throw new IllegalArgumentException();

		// Get mode
		currentIndex++;
		if (currentIndex >= len) throw new IllegalArgumentException();
		int modeStartIndex = currentIndex;
		byte[] mbytes = new byte[len];
		// Loop through array until 0 byte is found or out of bound occurs
		while (buf[currentIndex] != TFTP_PADDING) {
			currentIndex++;
			if (currentIndex >= len) throw new IllegalArgumentException();
		}
		int modeLength = currentIndex - modeStartIndex;
		System.arraycopy(buf,modeStartIndex,mbytes,0,modeLength);
		m = new String(mbytes).trim();

		return new Request(t, f, m);
	}

	/**
	 * Overwrites the method toUnsignedInt in Class Byte since toUnsignedInt is
	 * only supported in JavaSE v1.8.
	 * 
	 * @param myByte Byte representation of the number.
	 * 
	 * @return Integer representation of the number given.
	 */
	public static int toUnsignedInt(byte myByte) {
		return (int)(myByte & 0xFF);
	}

	public static boolean fileExists(String filePath) {
		File file = new File(filePath);
		return file.exists();
	}
	
	public static boolean isDirectory(String filePath) {
		File file = new File(filePath);
		return file.isDirectory();
	}
	
	public static boolean isPathless(String filePath)
	{
		Path path = Paths.get(filePath);
		// The file is pathless is the file name is the same as the file path
		return path.getFileName().toString().equals(filePath);
	}
	
	public static long getFreeSpaceOnFileSystem(String filePath) {
		File file = new File(filePath);
		return file.getFreeSpace();
	}
	
	public static boolean isReadable(String filePath) {
		File file = new File(filePath);

		if (!file.canRead()) {
			return false;
		}

		try {
			FileReader fileReader = new FileReader(file.getAbsolutePath());
			fileReader.read();
			fileReader.close();
		} catch (Exception e) {
			return false;
		}
		
		return true;
	}
	
	public static boolean isWritable(String filePath) {
		File file = new File(filePath);
		return file.canWrite();
	}

	public static boolean delete(String filePath) {
		File file = new File(filePath);
		Path path = file.toPath();
		
		try {
			Files.delete(path);
		} catch (NoSuchFileException e) {
			System.out.println("No such file or directory");
			return false;
		} catch (DirectoryNotEmptyException e) {
			System.out.println("Directory not empty");
			return false;
		} catch (IOException e) {
			System.out.println("Something went wrong here");
			return false;
		}
		
		return true;
	}

	public static boolean isValidPort(int port) { 
		return port >= MIN_PORT && port <= MAX_PORT;
	}

	// Truncates data buffer to fit data length of received packet
	public static void shrinkData(DatagramPacket packet) {
		int dataLength = packet.getLength();
		byte data[] = new byte[dataLength];
		System.arraycopy(packet.getData(), 0, data, 0, dataLength);
		packet.setData(data);
	}

	public static void printPacket(DatagramPacket packet) {
		int operation = getOpCode(packet);
		System.out.println("===== Packet Info =====");
		System.out.println("Port = " + packet.getPort());
		System.out.println("Type = " + opCodeToString(operation));
		switch(operation) {
			case READ_OP_CODE:
			case WRITE_OP_CODE:
				System.out.println("File name = " + parseRQ(packet).getFileName());
				System.out.println("Mode = " + parseRQ(packet).getMode());
				break;
			case DATA_OP_CODE:
			case ACK_OP_CODE:
				System.out.println("Block # = " + getBlockNumber(packet));
				break;
			case ERROR_OP_CODE:
				System.out.println("Error message = " + getErrorMessage(packet));
				break;
			default:
				throw new UnsupportedOperationException();
		}
		System.out.println("Data = " + Arrays.toString(packet.getData()) + "\n");
	}

	// Converts the OPCODE to it's string representation
	private static String opCodeToString(int operation) {
		switch(operation) {
			case READ_OP_CODE:
				return "Read";
			case WRITE_OP_CODE:
				return "Write";
			case DATA_OP_CODE:
				return "Data";
			case ACK_OP_CODE:
				return "ACK";
			case ERROR_OP_CODE:
				return "ERROR";
			default:
				throw new UnsupportedOperationException();
		}
	}
}
