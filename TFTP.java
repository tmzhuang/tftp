import java.net.*;
import java.util.*;
import java.io.*;

public class TFTP {
	public static final int BUF_SIZE = 100;
	public static final int TFPT_PADDING = 0;
	public static final int OP_CODE_SIZE = 2;
	public static final int BLOCK_NUMBER_SIZE = 2;
	public static final int MAX_DATA_SIZE = 512;

	// Forms a DatagramPacket using Request r with information about request type
	// (read, write, or test), filename, and mode (ascii, octet, etc.).
	public static DatagramPacket formRQPacket(InetAddress addr, int port, Request r) {
		int currentIndex;
		// Create byte array for packet
		byte[] buf = new byte[BUF_SIZE];
		// First element will always be 0
		buf[0] = TFPT_PADDING;
		switch (r.getType()) {
			case READ:
				buf[1] = 1;
				break;
			case WRITE:
				buf[1] = 2;
				break;
			default:
				buf[1] = TFPT_PADDING;
				break;
		}

		// Add filename to packet data
		byte[] fbytes = r.getFilename().getBytes();
		System.arraycopy(fbytes,0,buf,OP_CODE_SIZE,fbytes.length);

		// Add 0 byte padding
		currentIndex = fbytes.length + OP_CODE_SIZE;
		buf[currentIndex] = TFPT_PADDING;
		currentIndex++;

		// Add mode to packet data
		byte[] mbytes = r.getMode().getBytes();
		System.arraycopy(mbytes,0,buf,currentIndex,mbytes.length);

		// Add terminating 0 byte
		currentIndex = currentIndex + mbytes.length;
		buf[currentIndex] = TFPT_PADDING;

		// Truncate trailing zeros by copyings to a new array
		byte[] data = new byte[currentIndex + 1];
		System.arraycopy(buf,0,data,0,currentIndex+1);

		return new DatagramPacket(data,currentIndex+1, addr, port);
	}

	// Given a filename, returns a queue of datagram packets for that
	// file in 512 byte blocks.
	public static Queue<DatagramPacket> formDATAPackets(InetAddress addr, int port, String filename) {
		Queue<DatagramPacket> packetQueue = new ArrayDeque<DatagramPacket>();
		try {
			BufferedInputStream in = new BufferedInputStream(new FileInputStream(filename));
			byte[] data = new byte[MAX_DATA_SIZE];
			int blockNumber = 1;
			int n;

			//Read the file in 512 byte chunks and add to packet queue 
			while ((n = in.read(data)) != -1) {
				packetQueue.add(formDATAPacket(addr, port, blockNumber, data));
				blockNumber++;
			}
		} catch(Exception e) {
			e.printStackTrace();
			System.exit(1);
		}

		return packetQueue;
	}

	public void writeDATAToFile(DatagramPacket dataPacket, String filename) {

	}

	public int getBlockNumber(DatagramPacket packet) throws IllegalArgumentException {
		// Check that packet is either DATA or ACK
		byte[] data = packet.getData();
		boolean isDATA = (data[0]==0) && (data[1]==3);
		boolean isACK = (data[0]==0) && (data[1]==4);

		// If isn't DATA or ACK, throw an exception
		if (!(isDATA || isACK)) throw new IllegalArgumentException();

		// Get the block number as a byte array
		byte[] blockNumberBytes = new byte[BLOCK_NUMBER_SIZE];
		System.arraycopy(data,OP_CODE_SIZE,blockNumberBytes,0,BLOCK_NUMBER_SIZE);
		
		return bytesToBlockNumber(blockNumberBytes);
	}

	public byte[] getData(DatagramPacket packet) {
	}

	// Give a block number and a byte array of data, creates a datagram packet for the
	// given ip address and port.
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

	// Converts an integer to a 2-byte byte array
	public static byte[] blockNumberToBytes(int blockNumber) throws IllegalArgumentException {
		if (blockNumber<0 || blockNumber>66535) throw new IllegalArgumentException();

		byte[] blockNumberBytes = new byte[2];
		blockNumberBytes[0] = (byte) (blockNumber / 256);
		blockNumberBytes[1] = (byte) (blockNumber % 256);

		return blockNumberBytes;
	}

	// Converts a 2-byte byte array to an integer
	public static int bytesToBlockNumber(byte[] bytes) throws IllegalArgumentException {
		if (bytes.length != 2) throw new IllegalArgumentException();
		int msb = Byte.toUnsignedInt(bytes[0]);
		int lsb = Byte.toUnsignedInt(bytes[1]);
		return msb*256 + lsb;
	}

	// Forms a ACK packet for the given ip address, port and blocknumber
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

	public static DatagramPacket formERRORPacket(InetAddress addr, int port, int errorCode, String errMsg) {
		return null;	
	}

	public static void main (String[] args) {
	}
}
