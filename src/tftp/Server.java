package tftp;

import java.io.*;
import java.net.*;
import java.util.*;

/**
 * Implementation of the TFTP file transfer program on server side.
 * 
 * @author Team 4
 * @version Iteration 1
 */
public class Server implements Exitable, Runnable {
	//private static int RECEIVE_PORT = 69;
	private static int RECEIVE_PORT = 32002;
	private boolean verbose = true;
	private boolean running = true;
	private String directory;
	private static int TIMEOUT = 2000; 	//Maximum time to wait for response before timeout and re-send packet: 2 seconds (2000ms)
	private static int RESEND_LIMIT = 3; //Maximum number of times to try re-send packet without response: 3

	/**
	 * Constructor of the Server class
	 */
	public Server() {
		// No initialization needed
	}

	/**
	 * An exit method for client, start repl for quitting client
	 */
	public void run() {
		Scanner in = new Scanner(System.in);
		boolean badDirectory;

		// Sets the directory for the server
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

		(new Thread(new Repl(this, in))).start();
		(new Thread(new Listener())).start();
	}

	/**
	 * Inner Class ClientHandler here handles read and write requests
	 * from client side and responds with corresponding packet.
	 * 
	 * @author Team 4
	 * @version Iteration 1
	 */
	private class ClientHandler implements Runnable {
		private InetAddress replyAddr;
		private int TID;
		private DatagramPacket initialPacket;
		private DatagramSocket socket;
		
		/**
		 * Constructor of the class ClientHandler, initialize relevant
		 * fields given the received packet and open up a new socket for
		 * the transfer process.
		 * 
		 * @param packet Packet received from the client
		 */
		public ClientHandler(DatagramPacket packet) {
			this.replyAddr = packet.getAddress();
			this.TID = packet.getPort();
			this.initialPacket = packet;
			try {
				this.socket = new DatagramSocket();

				socket.setSoTimeout(TIMEOUT);	
			} catch(Exception e) {
				e.printStackTrace();
				System.exit(1);
			}
		}

		/**
		 * Parse the packet received and invoke either read or write handling
		 * method based on the OP code of the packet.
		 */
		public void run() {
			String[] errorMessage = new String[1];
			if (!TFTP.verifyRequestPacket(initialPacket, errorMessage))
			{
				DatagramPacket errorPacket = TFTP.formERRORPacket(
						replyAddr,
						TID,
						TFTP.ERROR_CODE_ILLEGAL_TFTP_OPERATION,
						errorMessage[0]);
						
				try {
					socket.send(errorPacket);
				} catch (IOException e) {
					e.printStackTrace();
				}
				
				if (verbose) {
					System.out.println("ERROR CODE " + TFTP.ERROR_CODE_ILLEGAL_TFTP_OPERATION + ": Request packet malformed. Aborting transfer...\n");
				}
				
				socket.close();
				return;
			}
			Request r = TFTP.parseRQ(initialPacket);
			System.out.println(r.getType() + " request for file \"" + directory + r.getFileName() + "\".\n");

			switch (r.getType()) {
				case READ:
					handleRead(r);
					break;
				case WRITE:
					handleWrite(r);
					break;
				default: break;

			}
		}

		/**
		 * Handles a read request.
		 *
		 * @param r Request type, filePath and mode
		 */
		private void handleRead(Request r) {
			String fileName = r.getFileName();
			String filePath = directory + fileName;
			
			// Send an error packet if file does not exist or is a directory
			if (!TFTP.fileExists(filePath) || TFTP.isDirectory(filePath)) {
				// Creates a "file not found" error packet
				DatagramPacket errorPacket = TFTP.formERRORPacket(
						replyAddr,
						TID,
						TFTP.ERROR_CODE_FILE_NOT_FOUND,
						"\"" + fileName + "\" does not exist on server.");

				// Sends error packet
				try {
					socket.send(errorPacket);
				} catch (Exception e) {
				}

				// Echo error message
				if (verbose) System.out.println("ERROR code " + TFTP.ERROR_CODE_FILE_NOT_FOUND + ": File does not exist. Aborting transfer...\n");

				// Closes socket and aborts thread
				socket.close();
				return;
			}

			// Send an error packet if file exists but is not readable
			if (TFTP.fileExists(filePath) && !TFTP.isReadable(filePath))
			{
				// Creates a "access violation" error packet
				DatagramPacket errorPacket = TFTP.formERRORPacket(
						replyAddr,
						TID,
						TFTP.ERROR_CODE_ACCESS_VIOLATION,
						"You do not have read access to the file \"" + fileName + "\".");

				// Sends error packet
				try {
					socket.send(errorPacket);
				} catch (IOException e) {
					e.printStackTrace();
				}

				// Echo error message
				if (verbose) System.out.println("ERROR code " + TFTP.ERROR_CODE_ACCESS_VIOLATION + ": File access violation. Aborting transfer...\n");

				// Closes socket and aborts thread
				socket.close();
				return;
			}
			
			if (verbose) System.out.println("Forming packet queue from file...");
			Queue<DatagramPacket> dataPacketQueue = null;
			try {
				dataPacketQueue = TFTP.formDATAPackets(replyAddr, TID, filePath);
			} catch (FileNotFoundException e1) {
				// TODO Auto-generated catch block
				e1.printStackTrace();
			}
			if (verbose) System.out.println("Packets formed. Ready to send " + dataPacketQueue.size() + " blocks.");

			boolean transferComplete = false;
			boolean packetInOrder = true;
			int currentBlockNumber = 1;
			
			DatagramPacket nextPacket = dataPacketQueue.remove();
			
			// Send each packet and wait for an ACK until queue is empty
			while (!transferComplete) {
				DatagramPacket currentPacket = nextPacket;
				
				//Only update current block number and send data block if the previous packet was the correct sequential packet (not duplicated/delayed)
				if(packetInOrder){
					// Send a packet
					currentBlockNumber = TFTP.getBlockNumber(currentPacket);
				}

				// Send a packet
				try {
					if (verbose) System.out.println("Sending DATA block number " + currentBlockNumber + ".");
					//if (verbose) System.out.println("Block size is " + TFTP.getData(currentPacket).length + ".");
					socket.send(currentPacket);
				} catch(Exception e) {}

				// Wait for ACK
				try {
					// Flag set to true if an unexpected packet is received
					boolean unexpectedPacket;
					
					
					DatagramPacket receivePacket;
					if (verbose) System.out.println("Waiting for ACK" + currentBlockNumber + "...");

						// Continue to receive packets until a packet from the correct client is received
						//http://www.coderanch.com/t/206099/sockets/java/DatagramPacket-getLength-refresh
						do {
							unexpectedPacket = false;
							receivePacket = TFTP.formPacket();

							// Wait for acknowledgement packet from client
							for(int i = 0; i<RESEND_LIMIT+1; i++) {
								try {
										socket.receive(receivePacket);
										i = RESEND_LIMIT+1;		//If packet successfully received, leave loop
								} catch(SocketTimeoutException e) {
									//if re-send attempt limit reached, 'give up' and cancel transfer
									if(i == RESEND_LIMIT) {
										System.out.println("No response from client after " + RESEND_LIMIT + " attempts. Try again later.");
										socket.close();
										return;
									}
									//otherwise re-send
										System.out.println("\nTIMED OUT, RESENDING DATA" + TFTP.getBlockNumber(currentPacket));
										socket.send(currentPacket);
								}
							}
							
							TFTP.shrinkData(receivePacket);
							
							InetAddress packetAddress = receivePacket.getAddress();
							int packetPort = receivePacket.getPort();
							
							// Check if the address and port of the received packet match the TID
							if (!(packetAddress.equals(replyAddr) && (packetPort == TID))) {
								// Creates an "unknown TID" error packet
								DatagramPacket errorPacket = TFTP.formERRORPacket(
										packetAddress,
										packetPort,
										TFTP.ERROR_CODE_UNKNOWN_TID,
										"The address and port of the packet does not match the TID of the ongoing transfer.");

								// Sends error packet
								socket.send(errorPacket);

								// Echo error message
								System.out.println("ERROR CODE " + TFTP.getErrorCode(errorPacket) + ": Received packet from an unknown host. Discarding packet and continuing transfer...\n");

								unexpectedPacket = true;
								continue;
							}
							
						} while (unexpectedPacket);

						// This block is entered if the packet received is not a valid ACK packet
						String[] errorMessage = new String[1];
						if (!TFTP.verifyAckPacket(receivePacket, currentBlockNumber, errorMessage)) {
							// If an ERROR packet is received instead of the expected ACK packet, abort the transfer
							if (TFTP.verifyErrorPacket(receivePacket, errorMessage)) {
								System.out.println("ERROR CODE " + TFTP.getErrorCode(receivePacket) + ": " + TFTP.getErrorMessage(receivePacket) + ". Aborting transfer...\n");

								// Closes socket and aborts thread
								socket.close();
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
										fileName + " could not be transferred because of the following error: " + errorMessage[0] + " (server expected a ACK packet with block#: " + currentBlockNumber + ")");

								// Sends error packet
								socket.send(errorPacket);

								// Echo error message
								System.out.println("ERROR CODE " + TFTP.getErrorCode(errorPacket) + ": Illegal TFTP Operation. Aborting transfer...\n");
								
								// Closes socket and aborts thread
								socket.close();
								return;
							}
						}

					if (verbose) System.out.println("ACK" + TFTP.getBlockNumber(receivePacket) + " received.");
					// Newline
					if (verbose) System.out.println();
					transferComplete = dataPacketQueue.isEmpty();
					packetInOrder = TFTP.checkPacketInOrder(receivePacket, currentBlockNumber);
				
					//if packet received is not the expected next ACK in the transfer (ie. a delayed or duplicate packet), ignore it 
					//otherwise increment current block number to wait for the next ack
					if(packetInOrder && !transferComplete)
					{
						currentBlockNumber = (currentBlockNumber + 1) % 65536;
						nextPacket = dataPacketQueue.remove();
					}
					
				} catch(Exception e) {
					System.out.println(e.getMessage());
				}
			}
			System.out.println("\nEnd of file transfer.\n");
		}

		/**
		 * Handle write requests
		 *
		 * @param r Request type, filename, and mode
		 */
		private void handleWrite(Request r) {
			try {
				String fileName = r.getFileName();
				String filePath = directory + fileName;
				int currentBlockNumber = 1;
				DatagramPacket receivePacket;
				byte[] fileBytes = new byte[0];

				boolean packetInOrder;
				
				// There is an error if the file exists and it not writable
				if (TFTP.fileExists(filePath) && !TFTP.isWritable(filePath)) {
					// Creates a "access violation" error packet
					DatagramPacket errorPacket = TFTP.formERRORPacket(
							replyAddr,
							TID,
							TFTP.ERROR_CODE_ACCESS_VIOLATION,
							"You do not have write access to the file \"" + fileName + "\".");
					
					// Sends error packet
					socket.send(errorPacket);

					// Echo error message
					if (verbose) System.out.println("ERROR code " + TFTP.ERROR_CODE_ACCESS_VIOLATION + ": File access violation. Aborting transfer...\n");
					
					// Closes socket and aborts thread
					socket.close();
					return;
				}

				// Form and send ACK0
				DatagramPacket ackPacket = TFTP.formACKPacket(replyAddr, TID, 0);
				if (verbose) System.out.println("Sending ACK0.");
				socket.send(ackPacket);

				// Flag set when transfer is finished
				boolean transferComplete = false;

				do {
					// Wait for a DATA packet
					if (verbose) System.out.println("Waiting for DATA" + currentBlockNumber + "...");
					receivePacket = TFTP.formPacket();
					
					for(int i = 0; i<RESEND_LIMIT+1; i++) {
						try {
								socket.receive(receivePacket);
								i = RESEND_LIMIT+1;		//If packet successfully received, leave loop
						} catch(SocketTimeoutException e) {
							//if re-send attempt limit reached, 'give up' and cancel transfer
							if(i == RESEND_LIMIT) {
								System.out.println("No response from client after " + RESEND_LIMIT + " attempts. Try again later.");
								socket.close();
								return;
							}
							//otherwise re-send
						//		System.out.println("Timed out, resending ACK" + + TFTP.getBlockNumber(ackPacket));
						//		socket.send(ackPacket);
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
						socket.send(errorPacket);

						// Echo error message
						System.out.println("ERROR CODE " + TFTP.getErrorCode(errorPacket) + ": Received packet from an unknown host. Discarding packet and continuing transfer...\n");
						continue;
					}

					// This block is entered if the packet received is not a valid DATA packet
					String[] errorMessage = new String[1];
					if (!TFTP.verifyDataPacket(receivePacket, currentBlockNumber, errorMessage)) {
						// If an ERROR packet is received instead of the expected DATA packet, delete the file
						// and abort the transfer
						if (TFTP.verifyErrorPacket(receivePacket, errorMessage)) {
							System.out.println("ERROR CODE " + TFTP.getErrorCode(receivePacket) + ": " + TFTP.getErrorMessage(receivePacket) + ". Aborting transfer...\n");
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
									fileName + " could not be transferred because of the following error: " + errorMessage[0] + " (server expected a DATA packet with block#: " + currentBlockNumber + ")");

							// Sends error packet
							socket.send(errorPacket);

							// Echo error message
							System.out.println("ERROR CODE " + TFTP.getErrorCode(errorPacket) + ": Illegal TFTP Operation. Aborting transfer...\n");
							return;
						}
					}

					// Transfer is complete if data block is less than MAX_DATA_SIZE
					if (receivePacket.getLength() < TFTP.MAX_PACKET_SIZE) {
						transferComplete = true;
					}

					// Echo successful data receive
					if (verbose) System.out.println("DATA" + TFTP.getBlockNumber(receivePacket) + " received.");
					// Newline
					if (verbose) System.out.println();
					
					packetInOrder = TFTP.checkPacketInOrder(receivePacket, currentBlockNumber);
					
					//If the packet was the correct next sequential packet in the transfer (not delayed/duplicated)
					if(packetInOrder){

						// Write the data packet to file
						fileBytes = TFTP.appendData(receivePacket, fileBytes);
						if ((fileBytes.length*TFTP.MAX_DATA_SIZE) > TFTP.getFreeSpaceOnFileSystem(directory)) {
							// Creates a "file not found" error packet
							DatagramPacket errorPacket = TFTP.formERRORPacket(
								replyAddr,
								TID,
								TFTP.ERROR_CODE_DISK_FULL,
								"\"" + r.getFileName() + "\" could not be transferred because disk is full.");

							// Sends error packet
							try {
								socket.send(errorPacket);
							} catch (Exception e) {
							}

							// Echo error message
							if (verbose) System.out.println("ERROR code " + TFTP.ERROR_CODE_DISK_FULL + ": Disk full. Aborting transfer...\n");

							// Closes socket and aborts thread
							socket.close();
							return;
						}
					}
					
					// Form a ACK packet to respond with
					ackPacket = TFTP.formACKPacket(replyAddr, TID, TFTP.getBlockNumber(receivePacket));
					if (verbose) System.out.println("Sending ACK" + TFTP.getBlockNumber(ackPacket) + ".");
					socket.send(ackPacket);
					
					//Incrament next block number expected only if the last packet received was the correct sequentially expected one 
					if(packetInOrder){
						currentBlockNumber = (currentBlockNumber + 1) % (TFTP.MAX_BLOCK_NUMBER + 1);
					}
					
				} while (!transferComplete);

				// Write data to file
				TFTP.writeBytesToFile(directory + r.getFileName(), fileBytes);
				if (verbose) System.out.println("\nWrite complete.\n");
			} catch(Exception e) {
				System.out.println(e.getMessage());
			}
		}
	}

	/**
	 * For each client, this Listener class creates a socket for the client,
	 * listens constantly, and responds with relevant packet when necessary.
	 * 
	 * @author Team 4
	 * @version Iteration 1
	 */
	private class Listener implements Runnable {
		private DatagramSocket receiveSocket;

		/**
		 * Constructor of the Listener class, initialize a new socket for each
		 * client connection.
		 */
		public Listener() {
			System.out.println("Creating new listener.");
			try {
				receiveSocket = new DatagramSocket(RECEIVE_PORT);
				receiveSocket.setSoTimeout(5000);
			} catch(Exception se) {
				se.printStackTrace();
				System.exit(1);
			}
		}

		/**
		* Wait for packet from client. When received, a response packet
		* is sent back to the sender if the received packet is valid. otherwise
		* an exception is thrown and the server quits.
		*/
		public void run() {

			while (running) {
				// Form packet for reception
				DatagramPacket packet = TFTP.formPacket();

				// Receive packet
				try {
					//if (verbose) System.out.println("Waiting for request from client...");
					receiveSocket.receive(packet);
					TFTP.shrinkData(packet);
					if (verbose) System.out.println("Packet received.");
				} catch(Exception e) {
					if (e instanceof InterruptedIOException) {
						//System.out.println("Socket timeout.");
						continue;
					} else {
						e.printStackTrace();
						System.exit(1);
					}
				}

				// Start a handler to connect with client
				(new Thread(new ClientHandler(packet))).start();
			}
			if (verbose) System.out.println("Listener closing...");
			receiveSocket.close();
		}
	}

	/**
	 * Exit the server upon called.
	 */
	public void exit() {
		this.running = false;
	}

	/**
	 * Main method of server class.
	 * 
	 * @param args input arguments when execute the server
	 */
	public static void main (String[] args) {
		// Listen on TFPT known port (69)
		Server server = new Server();
		server.run();
	}
}
