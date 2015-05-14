package tftp;

import java.net.*;
import java.util.*;

/**
 * Implementation of the TFTP file transfer program on client side.
 * 
 * @author Team 4
 * @version Iteration 1
 */
public class Client implements Exitable {
	private DatagramSocket sendReceiveSocket;
	private boolean verbose = true;
	private static int SEND_PORT = 68;
	//private static int SEND_PORT = 69;
	private InetAddress replyAddr;
	private int TID;

	/**
	 * Constructor for Client class, initialize a new socket upon called.
	 */
	public Client() {
		//// Start repl for quitting client
		//Thread repl = new Thread(new Repl(this));
		//repl.start();

		// Create data socket for communicating with server
		try {
			sendReceiveSocket = new DatagramSocket();
		} catch(Exception se) {
			se.printStackTrace();
			System.exit(1);
		}
	}

	/**
	 * This method implement the writing process on the client side during the TFTP
	 * file transfer. A valid write process will take place given a valid IP address,
	 * a file location, and a transfer mode.
	 * 
	 * @param addr IP address of the packet being sent
	 * @param filename String representation of the file and directory that is related
	 * to the write request
	 * @param mode Mode of the transfer i.e Netascii, octet, etc.
	 */
	public void write(InetAddress addr, String filename, String mode) {
		// Make request packet and send
		if (verbose) System.out.println("Sending WRITE request");
		Request r = new Request(Request.Type.WRITE, filename, mode);
		DatagramPacket requestPacket = TFTP.formRQPacket(addr, SEND_PORT, r);
		try {
			sendReceiveSocket.send(requestPacket);
		} catch(Exception e) {
			e.printStackTrace();
			System.exit(1);
		}

		// Wait for ACK0
		try {
			// ACK should be set size
			int bufferSize = TFTP.OP_CODE_SIZE + TFTP.BLOCK_NUMBER_SIZE;
			byte[] buf = new byte[bufferSize];
			// Get a packet from server
			DatagramPacket receivePacket = new DatagramPacket(buf,buf.length);
			if (verbose) System.out.println("Waiting for ACK0...");
			sendReceiveSocket.receive(receivePacket);

			// Throw exception if wrong OP code
			if (TFTP.getOpCode(receivePacket) != TFTP.ACK_OP_CODE)
				throw new Exception("Expected ACK packet but a non-ACK packet was received.");

			// Throw exception if DATA and ACK block numbers don't match
			if (TFTP.getBlockNumber(receivePacket) != 0)
				throw new Exception("ACK packet received does not match block number of DATA sent.");

			if (verbose) System.out.println("ACK0 received.");
			this.replyAddr = receivePacket.getAddress();
			this.TID = receivePacket.getPort();
		} catch(Exception e) {
			System.out.println(e.getMessage());
		}

		// Covert file into queue of datagram packets
		if (verbose) System.out.println("Forming packet queue from file...");
		Queue<DatagramPacket> dataPacketQueue = TFTP.formDATAPackets(replyAddr, TID, filename);
		if (verbose) System.out.println("Packets formed. Ready to send " + dataPacketQueue.size() + " blocks.");

		// Send each packet and wait for an ACK until queue is empty
		while (!dataPacketQueue.isEmpty()) {
			// Send a packet
			DatagramPacket currentPacket = dataPacketQueue.remove();
			int currentBlockNumber = TFTP.getBlockNumber(currentPacket);
			try {
				if (verbose) System.out.println("Sending DATA" + currentBlockNumber + ".");
				//if (verbose) System.out.println("Block size is" + TFTP.getData(currentPacket).length + ".");
				sendReceiveSocket.send(currentPacket);
			} catch(Exception e) {
			}

			// Wait for ACK
			try {
				// ACK should be set size
				int bufferSize = TFTP.OP_CODE_SIZE + TFTP.BLOCK_NUMBER_SIZE;
				byte[] buf = new byte[bufferSize];
				// Get a packet from server
				DatagramPacket receivePacket = new DatagramPacket(buf,buf.length);
				if (verbose) System.out.println("Waiting for ACK" + currentBlockNumber + "...");
				sendReceiveSocket.receive(receivePacket);

				// Throw exception if sender is invalid
				if (!receivePacket.getAddress().equals(replyAddr) || receivePacket.getPort() != TID) 
					throw new Exception("Packet recevied from invalid sender.");

				// Throw exception if wrong OP code
				if (TFTP.getOpCode(receivePacket) != TFTP.ACK_OP_CODE)
					throw new Exception("Expected ACK packet but a non-ACK packet was received.");

				// Throw exception if DATA and ACK block numbers don't match
				if (TFTP.getBlockNumber(receivePacket) != currentBlockNumber)
					throw new Exception("ACK packet received does not match block number of DATA sent.");

				if (verbose) System.out.println("ACK" + currentBlockNumber + " received.");
			} catch(Exception e) {
				System.out.println(e.getMessage());
			}
		}
		System.out.println("End of file transfer.\n");
	}
	
	/**
	 * This method implement the reading process on the client side during the
	 * TFTP file transfer. A valid read process will take place given a valid IP address,
	 * a file location, and a transfer mode.
	 * 
	 * @param addr IP address of the packet being sent
	 * @param filename String representation of the file and directory that is related
	 * to the write request
	 * @param mode Mode of the transfer i.e Netascii, octet, etc.
	 */
	public void read(InetAddress addr, String filename, String mode) {
		try {
			// Form request and send to server
			if (verbose) System.out.println("Sending a READ request to server for file \"" + filename + "\".");
			Request r = new Request(Request.Type.READ,filename,mode);
			DatagramPacket requestPacket = TFTP.formRQPacket(addr, SEND_PORT, r);
			DatagramPacket dataPacket;
			// Send the request
			sendReceiveSocket.send(requestPacket);

			int currentBlockNumber = 1;
			byte[] fileBytes = new byte[0];
			do {
				// Make packet to receive DATA
				int bufferSize = TFTP.OP_CODE_SIZE + TFTP.BLOCK_NUMBER_SIZE + TFTP.MAX_DATA_SIZE;
				byte[] buf = new byte[bufferSize];
				dataPacket = new DatagramPacket(buf, buf.length);

				// Wait for DATA from server
				if (verbose) System.out.println("Waiting for DATA packet from server...");
				sendReceiveSocket.receive(dataPacket);

				// Throw exception if wrong OP code
				if (TFTP.getOpCode(dataPacket) != TFTP.DATA_OP_CODE)
					throw new Exception("Expected DATA packet but a non-DATA packet was received.");

				// Throw exception if unexpected block number
				if (TFTP.getBlockNumber(dataPacket) != currentBlockNumber)
					throw new Exception("DATA packet received has an unexpected block number.");

				// If this is the first DATA packet received, record the address and port
				if (TFTP.getBlockNumber(dataPacket) == 1) {
					this.replyAddr = dataPacket.getAddress();
					this.TID = dataPacket.getPort();
				}

				if (verbose) System.out.println("DATA" + TFTP.getBlockNumber(dataPacket) + " received.");
				if (verbose) System.out.println("The size of the data was " + TFTP.getData(dataPacket).length + ".");

				// Write data to file
				if (verbose) System.out.println("Appending current block to filebytes.");
				fileBytes = TFTP.appendData(dataPacket, fileBytes);

				// Form a ACK packet to respond with
				DatagramPacket ackPacket = TFTP.formACKPacket(replyAddr, TID, currentBlockNumber);
				if (verbose) System.out.println("ACK " + currentBlockNumber + " sent.");
				sendReceiveSocket.send(ackPacket);
				currentBlockNumber = (currentBlockNumber + 1) % 65535;

				// Newline
				if (verbose) System.out.println();
			} while (TFTP.getData(dataPacket).length == TFTP.MAX_DATA_SIZE);
			if (verbose) System.out.println("Writing bytes to file...");
			TFTP.writeBytesToFile("tmp/" + filename, fileBytes);
			if (verbose) System.out.println("Read complete.");
		}
		catch(Exception e) {
			e.printStackTrace();
			System.exit(1);
		}
	}

	/**
	 * This method will be invoked once a client program is executed. It will prompt
	 * the user for inputs for file transfer process and handle the request based on
	 * the arguments given. 
	 */
	public void run() {
		Scanner in = new Scanner(System.in);
		String cmd;
		Request.Type t = null;
		String filename;
		while (true) {
			boolean validCmd = false;
			while(!validCmd) {
				// Get get command
				System.out.println("Please enter a command (read, write, or exit):");
				cmd = in.next();
				// Quit server if exit command given
				if (cmd.equalsIgnoreCase("exit")) {
					in.close();
					System.out.println("Shutting down...");
					System.exit(1);
				} else if (cmd.equalsIgnoreCase("read")) {
					validCmd = true;
					t = Request.Type.READ;
				} else if (cmd.equalsIgnoreCase("write")) {
					validCmd = true;
					t = Request.Type.WRITE;
				} else {
					validCmd = false;
					System.out.println("Invalid command. Valid commands are read, write, and exit.");
				}
			}

			// Get filename
			System.out.println("Please enter a filename:");
			filename = in.next();

			// Send the request
			try {
				switch (t) {
					case READ:
						this.read(InetAddress.getLocalHost(), filename, "netascii");
						break;
					case WRITE:
						this.write(InetAddress.getLocalHost(), filename, "netascii");
						break;
					default:
						System.out.println("Invalid request type. Quitting...");
						System.exit(1);
				}
			} catch(Exception e) {
			}
		}
	}

	/**
	 * A safe exit approach from the client side.
	 */
	public void exit() {
		sendReceiveSocket.close();
	}

	/**
	 * Main method of client class.
	 * 
	 * @param args input arguments when execute the server
	 */
	public static void main (String[] args) {
		Client client = new Client();
		client.run();
		//try {
			//client.read(InetAddress.getLocalHost(), "a.txt", "netascii");
			//client.write(InetAddress.getLocalHost(), "a.txt", "netascii");
		//} catch(Exception e) {
		//}
	}
}
