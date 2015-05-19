package tftp;

import java.io.FileNotFoundException;
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
	//private static int SEND_PORT = 32001;
	//private static int SEND_PORT = 32002;
	private InetAddress replyAddr;
	private int TID;
	private String directory;

	/**
	 * Constructor for Client class, initialize a new socket upon called.
	 */
	public Client() {
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
	 * @param filePath String representation of the file and directory that is related
	 * to the write request
	 * @param mode Mode of the transfer i.e Netascii, octet, etc.
	 */
	public void write(InetAddress addr, String filePath, String mode) {
		// Make request packet and send
		if (verbose) System.out.println("Sending WRITE request\n");
		Request r = new Request(Request.Type.WRITE, filePath, mode);
		DatagramPacket requestPacket = TFTP.formRQPacket(addr, SEND_PORT, r);
		try {
			sendReceiveSocket.send(requestPacket);
		} catch(Exception e) {
			e.printStackTrace();
			System.exit(1);
		}

		// Wait for ACK0
		try {
			// Get a packet from server
			DatagramPacket receivePacket = TFTP.formPacket();
			if (verbose) System.out.println("Waiting for ACK0...");
			sendReceiveSocket.receive(receivePacket);

			switch (TFTP.getOpCode(receivePacket)) {
			case TFTP.ACK_OP_CODE:
				// Throw exception if unexpected block number
				if (TFTP.getBlockNumber(receivePacket) != 0)
					throw new Exception("ACK packet received does not match block number of DATA sent.");
				break;
			case TFTP.ERROR_OP_CODE:
				System.out.println("ERROR packet received: " + TFTP.getErrorMessage(receivePacket) + "\n");
				return;
			default:
				throw new Exception("Expected ACK packet but a non-ACK packet was received.");
			}

			if (verbose) System.out.println("ACK0 received.");
			this.replyAddr = receivePacket.getAddress();
			this.TID = receivePacket.getPort();
		} catch(Exception e) {
			System.out.println(e.getMessage());
			return;
		}

		// Covert file into queue of datagram packets
		if (verbose) System.out.println("Forming packet queue from file...");
		Queue<DatagramPacket> dataPacketQueue = null;
		try {
			dataPacketQueue = TFTP.formDATAPackets(replyAddr, TID, filePath);
		} catch (FileNotFoundException e1) {
			e1.printStackTrace();
		}
		if (verbose)System.out.println("Packets formed. Ready to send " + dataPacketQueue.size() + " blocks.");

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
				// Get a packet from server
				DatagramPacket receivePacket = TFTP.formPacket();
				if (verbose) System.out.println("Waiting for ACK" + currentBlockNumber + "...");
				sendReceiveSocket.receive(receivePacket);

				// Throw exception if sender is invalid
				if (!receivePacket.getAddress().equals(replyAddr) || receivePacket.getPort() != TID) 
					throw new Exception("Packet recevied from invalid sender.");

				switch (TFTP.getOpCode(receivePacket)) {
					case TFTP.ACK_OP_CODE:
						// Throw exception if DATA and ACK block numbers don't match
						if (TFTP.getBlockNumber(receivePacket) != currentBlockNumber)
							throw new Exception("ACK packet received does not match block number of DATA sent.");
						break;
					case TFTP.ERROR_OP_CODE:
						System.out.println("ERROR packet received: " + TFTP.getErrorMessage(receivePacket) + "\n");
						return;
					default:
						// Throw exception if wrong OP code
						throw new Exception("Expected ACK packet but a non-ACK packet was received.");
				}

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
	 * @param filePath String representation of the file and directory that is related
	 * to the write request
	 * @param mode Mode of the transfer i.e Netascii, octet, etc.
	 */
	public void read(InetAddress addr, String filePath, String mode) {
		try {
			// Form request and send to server
			Request r = new Request(Request.Type.READ,filePath,mode);
			if (verbose) System.out.println("Sending a READ request to server for file \"" + r.getFileName() + "\".\n");
			DatagramPacket requestPacket = TFTP.formRQPacket(addr, SEND_PORT, r);
			DatagramPacket dataPacket;
			// Send the request
			sendReceiveSocket.send(requestPacket);

			int currentBlockNumber = 1;
			byte[] fileBytes = new byte[0];
			do {
				// Make packet to receive DATA
				dataPacket = TFTP.formPacket();

				// Wait for DATA from server
				if (verbose) System.out.println("Waiting for DATA packet from server...");
				sendReceiveSocket.receive(dataPacket);
				
				switch (TFTP.getOpCode(dataPacket)) {
					case TFTP.DATA_OP_CODE:
						// Throw exception if unexpected block number
						if (TFTP.getBlockNumber(dataPacket) != currentBlockNumber)
							throw new Exception("DATA packet received has an unexpected block number.");
						break;
					case TFTP.ERROR_OP_CODE:
						System.out.println("ERROR packet received: " + TFTP.getErrorMessage(dataPacket) + "\n");
						return;
					default:
						throw new Exception("Expected DATA packet but a non-DATA packet was received.");
				}

				// If this is the first DATA packet received, record the address and port
				if (TFTP.getBlockNumber(dataPacket) == 1) {
					this.replyAddr = dataPacket.getAddress();
					this.TID = dataPacket.getPort();
				}

				if (verbose) System.out.println("DATA" + TFTP.getBlockNumber(dataPacket) + " received.");
				if (verbose) System.out.println("The size of the data was " + TFTP.getData(dataPacket).length + ".");
				
				// Test output... DELETE later
				System.out.println("free space = " + TFTP.getFreeSpaceOnFileSystem(directory));

				// Write data to file
				if (verbose) System.out.println("Appending current block to filebytes.");
				fileBytes = TFTP.appendData(dataPacket, fileBytes);
				if ((fileBytes.length*TFTP.MAX_DATA_SIZE) > TFTP.getFreeSpaceOnFileSystem(directory)) {
					// Creates a "file not found" error packet
					DatagramPacket errorPacket = TFTP.formERRORPacket(
							replyAddr,
							TID,
							TFTP.ERROR_CODE_DISK_FULL,
							r.getFileName() + " could not be transferred because disk is full.");

					// Sends error packet
					try {
						sendReceiveSocket.send(errorPacket);
					} catch (Exception e) {
					}

					// Echo error message
					if (verbose) System.out.println("Disk full. Aborting transfer...\n");

					// Closes socket and aborts thread
					return;
				}

				// Form a ACK packet to respond with
				DatagramPacket ackPacket = TFTP.formACKPacket(replyAddr, TID, currentBlockNumber);
				if (verbose) System.out.println("ACK " + currentBlockNumber + " sent.");
				sendReceiveSocket.send(ackPacket);
				currentBlockNumber = (currentBlockNumber + 1) % 65536;

				// Newline
				if (verbose) System.out.println();
			} while (TFTP.getData(dataPacket).length == TFTP.MAX_DATA_SIZE);
			if (verbose) System.out.println("Writing bytes to file...");
			TFTP.writeBytesToFile(filePath, fileBytes);
			if (verbose) System.out.println("Read complete.\n");
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
		String fileName;
		String filePath;
		boolean badDirectory;
		
		// Sets the directory for the client
		do {
			badDirectory = false;
			System.out.println("Please enter the directory that you want to use for the server files:");
			System.out.println("Must end with either a '/' or a '\\' to work");
			directory = in.next();
			char lastChar = directory.charAt(directory.length()-1);
			if (!TFTP.isDirectory(directory)) {
				System.out.println("Directory does not exist.");
				badDirectory = true;
			}
			else if (lastChar != '/' && lastChar != '\\')
			{
				System.out.println("Directory must end with either a '/' or a '\\'");
				badDirectory = true;
			}
		} while (badDirectory);
		System.out.println("The directory you entered is: " + directory + "\n");

		while (true) {
			boolean validCmd = false;
			while(!validCmd) {
				// Get get command
				System.out.println("Please enter a command (read/write/exit):");
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

			// Get file name
			do {
				System.out.println("Please enter the name of the file to transfer:");
				fileName = in.next();
				if (!TFTP.isPathless(fileName))
				{
					System.out.println("File names must not contain a path. The directory that you designated for transfer is: " + directory);
				}
			} while (!TFTP.isPathless(fileName));
			filePath = directory + fileName;
			
			// Check if the file exists and file readable on client if WRITE request, otherwise continue loop
			if (t == Request.Type.WRITE) {
				if (TFTP.fileExists(filePath) && !TFTP.isDirectory(filePath)) {
					if (!TFTP.isReadable(filePath)) {
						// Echo error message for access violation
						System.out.println("File access violation.\n");
						continue;
					} else {
						// Echo successful file found
						System.out.println("File found.\n");
					}
				} else {
					// Echo error message for file not found
					System.out.println("File not found.\n");
					continue;
				}
			// For read requests, check if file already exists on the client
			} else if (t == Request.Type.READ) {
				if (TFTP.fileExists(filePath) && !TFTP.isDirectory(filePath)) {
					// Echo error message
					System.out.println("File already exists.\n");
					continue;
				} else {
					// Prints empty line
					System.out.println("");
				}
			}

			// Send the request
			try {
				switch (t) {
					case READ:
						this.read(InetAddress.getLocalHost(), filePath, "netascii");
						break;
					case WRITE:
						this.write(InetAddress.getLocalHost(), filePath, "netascii");
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
	}
}
