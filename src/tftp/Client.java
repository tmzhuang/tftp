package tftp;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.net.*;
import java.util.*;

/**
 * Implementation of the TFTP file transfer program on client side.
 * 
 * @author Team 4
 * @version Iteration 3
 */
public class Client implements Exitable, Runnable {
	private DatagramSocket sendReceiveSocket;
	private boolean verbose = true;
	private static int SERVER_PORT = 69;
	private static int ERRSIM_PORT = 68;
	private InetAddress replyAddr;
	private InetAddress sendAddr;
	private int TID;
	private int sendPort;
	private String directory;
	private String[] args;

	//Maximum number of times to try re-send packet without response: 3
	private static int RESEND_LIMIT = 3;

	/**
	 * Constructor for Client class, initialize a new socket upon called.
	 */
	public Client(String[] args) {
		// Default states
		verbose = false;
		sendPort = SERVER_PORT;

		// Change states based on args
		this.args = args;
		if (this.args.length >= 1) {
			for (String arg: args) {
				switch(arg) {
				case "-v":
					verbose = true;
					System.out.println("Verbose mode is on.");
					break;
				case "-t": 
					sendPort = ERRSIM_PORT; 
					System.out.println("The client is being routed through the error simulator."); 
					break;
				default:
					System.out.println("Invalid command line arugment received. Exiting client...");
					System.exit(1);
				}
			}
		}
		// Create data socket for communicating with server
		try {
			sendReceiveSocket = new DatagramSocket();
			
			//Maximum time to wait for response before timeout and re-send packet: 2 seconds (2000)
			sendReceiveSocket.setSoTimeout(2000);		
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
		try {
		// Make request packet and send
		if (verbose) System.out.println("Sending WRITE request\n");
		Request r = new Request(Request.Type.WRITE, filePath, mode);
		DatagramPacket requestPacket = TFTP.formRQPacket(addr, sendPort, r);
		
		sendReceiveSocket.send(requestPacket);

		boolean packetInOrder = false;
			
		// Wait for ACK0
		do{
			try {
				// Get a packet from server
				DatagramPacket receivePacket = TFTP.formPacket();
				if (verbose) System.out.println("Waiting for ACK 0...");

				for(int i = 0; i<RESEND_LIMIT; i++) {
					try {
						sendReceiveSocket.receive(receivePacket);
						i = RESEND_LIMIT+1;		//packet successfully received, leave loop
					} catch(SocketTimeoutException e) {
						//if re-send attempt limit reached, 'give up' and cancel transfer
						if(i == RESEND_LIMIT-1) {
							System.out.println("No response from server after " + RESEND_LIMIT + " attempts. Try again later.");
							return;
						}
						//otherwise re-send
						if(verbose) System.out.println("\nServer timed out. WRQ resent.\n");
						sendReceiveSocket.send(requestPacket);
					}
				}

				TFTP.shrinkData(receivePacket);
				this.replyAddr = receivePacket.getAddress();
				this.TID = receivePacket.getPort();

				// This block is entered if the packet received is not a valid ACK packet
				String[] errorMessage = new String[1];
				if (!TFTP.verifyAckPacket(receivePacket, 0, errorMessage)) {
					// If an ERROR packet is received instead of the expected ACK packet, abort the transfer
					if (TFTP.verifyErrorPacket(receivePacket, errorMessage)) {
						if(verbose) System.out.println("Received ERROR packet with ERROR code " + TFTP.getErrorCode(receivePacket) + ": " + TFTP.getErrorMessage(receivePacket) + ". Aborting transfer...\n");
						return;
					}
					// If the received packet is not an ACK or an ERROR packet, then send an illegal TFTP
					// operation ERROR packet and abort the transfer
					else {
						// Creates an "illegal TFTP operation" error packet
						DatagramPacket errorPacket = TFTP.formERRORPacket(
								replyAddr,
								TID,
								TFTP.ERROR_CODE_ILLEGAL_TFTP_OPERATION,
								r.getFileName() + " could not be transferred because of the following error: " + errorMessage[0] + " (client expected a ACK packet with block#: 0)");

						// Sends error packet
						//TFTP.printPacket(errorPacket);
						sendReceiveSocket.send(errorPacket);

						// Echo error message
						if(verbose) System.out.println("Sent ERROR packet with ERROR code " + TFTP.getErrorCode(errorPacket) + ": Illegal TFTP Operation. Aborting transfer...\n");

						return;
					}
				}

				packetInOrder = TFTP.checkPacketInOrder(receivePacket, 0);

				if (verbose && packetInOrder) System.out.println("ACK0 received.");
			} catch(Exception e) {
				System.out.println(e.getMessage());
				return;
			}

		} while(!packetInOrder);
			
		// Covert file into queue of datagram packets
		if (verbose) System.out.println("Forming packet queue from file...");
		Queue<DatagramPacket> dataPacketQueue = null;
		int currentBlockNumber = 1;
		
		try {
			dataPacketQueue = TFTP.formDATAPackets(replyAddr, TID, filePath);
		} catch (FileNotFoundException e1) {
			e1.printStackTrace();
		}
		if (verbose)System.out.println("Packets formed. Ready to send " + dataPacketQueue.size() + " blocks.");

		DatagramPacket nextPacket = dataPacketQueue.remove();
		boolean doneTransfer = false;
		
		// Send each packet and wait for an ACK until queue is empty
		while (!doneTransfer) {
			
			DatagramPacket currentPacket = nextPacket;
			
			//Only update current block number and send data block if the previous packet was the correct sequential packet (not duplicated/delayed)
			if(packetInOrder){
				// Send a packet
				currentBlockNumber = TFTP.getBlockNumber(currentPacket);
				if (verbose) System.out.println("DATA " + currentBlockNumber + " sent.");
				//if (verbose) System.out.println("Block size is" + TFTP.getData(currentPacket).length + ".");
				sendReceiveSocket.send(currentPacket);
			}

			// Wait for ACK
			try {
				// Get a packet from server
				DatagramPacket receivePacket;
				if (verbose) System.out.println("Waiting for ACK from server...");

				// Flag set to true if an unexpected packet is received
				boolean unexpectedPacket;

				do {
					unexpectedPacket = false;
					
					receivePacket = TFTP.formPacket();

					for(int i = 0; i<RESEND_LIMIT+1; i++) {
						try {
								sendReceiveSocket.receive(receivePacket);
								i = RESEND_LIMIT+1;		//packet successfully received, exit loop
						} catch(SocketTimeoutException e) {
							//if re-send attempt limit reached, 'give up' and cancel transfer
							if(i == RESEND_LIMIT) {
								System.out.println("No response from server after " + RESEND_LIMIT + " attempts. Try again later.");
								return;
							}
							//otherwise re-send
							if(verbose) System.out.println("\nServer tiemd out. DATA " + TFTP.getBlockNumber(currentPacket) + " resent.");
							sendReceiveSocket.send(currentPacket);
							//TFTP.printPacket(currentPacket);
						}
					}				
					
					TFTP.shrinkData(receivePacket);

					InetAddress packetAddress = receivePacket.getAddress();
					int packetPort = receivePacket.getPort();
					if (!(packetAddress.equals(replyAddr) && (packetPort == TID))) {
						// Creates an "unknown TID" error packet
						DatagramPacket errorPacket = TFTP.formERRORPacket(
								packetAddress,
								packetPort,
								TFTP.ERROR_CODE_UNKNOWN_TID,
								"The address and port of the packet does not match the TID of the ongoing transfer.");

						// Sends error packet
						sendReceiveSocket.send(errorPacket);

						// Echo error message
						if (verbose) System.out.println("Sent ERROR packet with ERROR code " + TFTP.getErrorCode(errorPacket) + ": Received packet from an unknown host. Discarding packet and continuing transfer...\n");

						unexpectedPacket = true;
						continue;
					}
				} while (unexpectedPacket);

				// This block is entered if the packet received is not a valid ACK packet
				String[] errorMessage = new String[1];
				if (!TFTP.verifyAckPacket(receivePacket, currentBlockNumber, errorMessage))
				{
					// If an ERROR packet is received instead of the expected ACK packet, abort the transfer
					if (TFTP.verifyErrorPacket(receivePacket, errorMessage))
					{
						System.out.println("Received ERROR packet with ERROR code " + TFTP.getErrorCode(receivePacket) + ": " + TFTP.getErrorMessage(receivePacket) + ". Aborting transfer...\n");
						return;
					}
					// If the received packet is not an ACK or an ERROR packet, then send an illegal TFTP
					// operation ERROR packet and abort the transfer
					else
					{
						// Creates an "illegal TFTP operation" error packet
						DatagramPacket errorPacket = TFTP.formERRORPacket(
								replyAddr,
								TID,
								TFTP.ERROR_CODE_ILLEGAL_TFTP_OPERATION,
								r.getFileName() + " could not be transferred because of the following error: " + errorMessage[0] + " (client expected a ACK packet with block#: " + currentBlockNumber + ")");

						// Sends error packet
						//TFTP.printPacket(errorPacket);
						sendReceiveSocket.send(errorPacket);

						// Echo error message
						if (verbose) System.out.println("Sent ERROR packet with ERROR code " + TFTP.getErrorCode(errorPacket) + ": Illegal TFTP Operation. Aborting transfer...\n");

						return;
					}
				}

				packetInOrder = TFTP.checkPacketInOrder(receivePacket, currentBlockNumber);
				
				doneTransfer = dataPacketQueue.isEmpty();
				
				if(packetInOrder && !doneTransfer){
					nextPacket = dataPacketQueue.remove();
				}
				
				if (verbose) System.out.println("ACK " + TFTP.getBlockNumber(receivePacket) + " received."); //addr = " + receivePacket.getAddress().toString() + ", port = " + receivePacket.getPort());
				// Newline
				if (verbose) System.out.println();
			} catch(Exception e) {
				System.out.println(e.getMessage());
			}
		}
		System.out.println("End of file transfer.\n");
		
		} catch(Exception e) {
			e.printStackTrace();
			System.exit(1);
		}
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
			DatagramPacket requestPacket = TFTP.formRQPacket(addr, sendPort, r);
			DatagramPacket dataPacket;
			// Send the request
			sendReceiveSocket.send(requestPacket);

			boolean transferComplete = false;

			boolean firstIteration = true;

			boolean packetInOrder;

			//To hold most recently sent packet for possible re-sending if needed.
			//DatagramPacket previousPacket = requestPacket;

			int currentBlockNumber = 1;
			byte[] fileBytes = new byte[0];
			do {
				// Make packet to receive DATA
				dataPacket = TFTP.formPacket();

				// Wait for DATA from server. If no response within set timeout limit, re-send packet (up to maximum re-send limit)
				if (verbose) System.out.println("Waiting for DATA from server...");

				for(int i = 0; i<RESEND_LIMIT; i = i+1) {
					try {
						//System.out.println("Waiting...  " + i);
						sendReceiveSocket.receive(dataPacket);
						i = RESEND_LIMIT+1;		//If packet successfully received, leave loop
					} catch(SocketTimeoutException e) {
						//if re-send attempt limit reached, 'give up' and cancel transfer
						if(i == RESEND_LIMIT-1)
						{
							System.out.println("No response from server after " + RESEND_LIMIT + " attempts. Try again later.");
							return;
						}
						//don't re-send ACK packets, but do re-send request
						if(firstIteration)
						{
							if(verbose) System.out.println("\nServer timed out. RRQ resent.\n");
							sendReceiveSocket.send(requestPacket);
						}
					}
				}

				TFTP.shrinkData(dataPacket);

				// If this is the first DATA packet received, record the address and port
				if (firstIteration) {
					this.replyAddr = dataPacket.getAddress();
					this.TID = dataPacket.getPort();
					firstIteration = false;
				} else {
					InetAddress packetAddress = dataPacket.getAddress();
					int packetPort = dataPacket.getPort();
					if (!(packetAddress.equals(replyAddr) && (packetPort == TID))) {
						// Creates an "unknown TID" error packet
						DatagramPacket errorPacket = TFTP.formERRORPacket(
								packetAddress,
								packetPort,
								TFTP.ERROR_CODE_UNKNOWN_TID,
								"The address and port of the packet does not match the TID of the ongoing transfer.");

						// Sends error packet
						sendReceiveSocket.send(errorPacket);

						// Echo error message
						if (verbose) System.out.println("Sent ERROR packet with ERROR code " + TFTP.getErrorCode(errorPacket) + ": Received packet from an unknown host. Discarding packet and continuing transfer...\n");
						continue;
					}
				}

				// This block is entered if the packet received is not a valid DATA packet
				String[] errorMessage = new String[1];
				if (!TFTP.verifyDataPacket(dataPacket, currentBlockNumber, errorMessage)) {
					// If an ERROR packet is received instead of the expected DATA packet, delete the file
					// and abort the transfer
					if (TFTP.verifyErrorPacket(dataPacket, errorMessage)) {
						if (verbose) System.out.println("Received ERROR packet with ERROR code " + TFTP.getErrorCode(dataPacket) + ": " + TFTP.getErrorMessage(dataPacket) + ". Aborting transfer...\n");
						return;
					}
					// If the received packet is not a DATA or an ERROR packet, then send an illegal TFTP
					// operation ERROR packet and abort the transfer
					else {
						// Creates an "illegal TFTP operation" error packet
						DatagramPacket errorPacket = TFTP.formERRORPacket(
								replyAddr,
								TID,
								TFTP.ERROR_CODE_ILLEGAL_TFTP_OPERATION,
								r.getFileName() + " could not be transferred because of the following error: " + errorMessage[0] + " (client expected a DATA packet with block#: " + currentBlockNumber + ")");

						// Sends error packet
						//TFTP.printPacket(errorPacket);
						sendReceiveSocket.send(errorPacket);

						// Echo error message
						if (verbose) System.out.println("Send ERROR packet with ERROR code " + TFTP.getErrorCode(errorPacket) + ": Illegal TFTP Operation. Aborting transfer...\n");

						return;
					}
				}

				if (verbose) System.out.println("DATA " + TFTP.getBlockNumber(dataPacket) + " received.");
				//if (verbose) System.out.println("The size of the data was " + TFTP.getData(dataPacket).length + ".");

				// Transfer is complete if data block is less than MAX_PACKET_SIZE
				if (dataPacket.getLength() < TFTP.MAX_PACKET_SIZE) {
					transferComplete = true;
				}

				packetInOrder = TFTP.checkPacketInOrder(dataPacket, currentBlockNumber);

				//If the packet was the expected sequential block number in the transfer (not duplicated or delayed), write the data to the file
				if(packetInOrder){
					// Write data to file
					//if (verbose) System.out.println("Appending current block to filebytes.");
					fileBytes = TFTP.appendData(dataPacket, fileBytes);
					if ((fileBytes.length*TFTP.MAX_DATA_SIZE) > TFTP.getFreeSpaceOnFileSystem(directory)) {
						// Creates a "disk full" error packet
						DatagramPacket errorPacket = TFTP.formERRORPacket(
								replyAddr,
								TID,
								TFTP.ERROR_CODE_DISK_FULL,
								"\"" + r.getFileName() + "\" could not be transferred because disk is full.");

						// Sends error packet
						try {
							sendReceiveSocket.send(errorPacket);
						} catch (Exception e) {
							// Do nothing
						}

						// Echo error message
						if (verbose) System.out.println("Sent ERROR packet with ERROR code " + TFTP.ERROR_CODE_DISK_FULL + ": Disk full. Aborting transfer...\n");

						// Closes socket and aborts thread
						return;
					}
				}

				// Form an ACK packet to respond with and send
				DatagramPacket ackPacket = TFTP.formACKPacket(replyAddr, TID, TFTP.getBlockNumber(dataPacket));
				if (verbose) System.out.println("ACK " + TFTP.getBlockNumber(ackPacket) + " sent.");
				sendReceiveSocket.send(ackPacket);


				//Update the current block number only if the packet was not a duplicate/delayed
				if(packetInOrder){
					currentBlockNumber = (currentBlockNumber + 1) % 65536;
				}

				// Newline
				if (verbose) System.out.println();
			} while (!transferComplete);
			//} while (TFTP.getData(dataPacket).length == TFTP.MAX_DATA_SIZE);
			if (verbose) System.out.println("Writing bytes to file...");
			TFTP.writeBytesToFile(filePath, fileBytes);
			System.out.println("Read complete.\n");
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
		
		// Get the IP address or host name from user input
		for (boolean validHost = false; validHost == false; ) {
			System.out.println("Please enter the IP address of the server:");
			String host = in.next();
			try {
				sendAddr = InetAddress.getByName(host);
			} catch(UnknownHostException e) {
				System.out.println("Invalid host name or IP address. Please try again.");
				continue;
			}
			validHost = true;
		}
		
		// Sets the directory for the client
		do {
			badDirectory = false;
			System.out.println("Please enter the directory that you want to use for the client files:");
			directory = in.next();
			char lastChar = directory.charAt(directory.length()-1);
			if (!TFTP.isDirectory(directory)) {
				System.out.println("Directory does not exist.");
				badDirectory = true;
			}
			else if (lastChar != '/' && lastChar != '\\')
			{
				System.out.println("Valid directory must end with either a '/' or a '\\'");
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
					// Newline
					System.out.println();
				}
			}
			
			// Make sure packet buffer is clear before processing transfer
			if (verbose) System.out.println("Clearing socket receive buffer...");
			DatagramPacket throwAwayPacket = TFTP.formPacket();
			// Receive packets until a timeout occurs
			do {
				try {
					sendReceiveSocket.receive(throwAwayPacket);
				} catch (SocketTimeoutException e) {
					break;
				} catch (IOException e) {
					e.printStackTrace();
				}
			} while (true);
			if (verbose) System.out.println("Socket cleared.\n");

			// Send the request
			try {
				switch (t) {
					case READ:
						System.out.println("Reading " + filePath + " from server...");
						this.read(sendAddr, filePath, "netascii");
						break;
					case WRITE:
						System.out.println("Writing " + filePath + " to server...");
						this.write(sendAddr, filePath, "netascii");
						break;
					default:
						System.out.println("Invalid request type. Quitting...");
						System.exit(1);
				}
			} catch(Exception e) {
				// Do nothing
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
		Client client = new Client(args);
		client.run();
	}
}
