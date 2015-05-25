package tftp;

import java.io.IOException;
import java.net.*;
import java.util.Scanner;

public class ErrorSimulator {
	/**
	 * Fields
	 */
	private DatagramSocket receiveSocket;
	//private static int RECEIVE_PORT = 68;
	//private static int SEND_PORT = 69;
	private static int RECEIVE_PORT = 32001;
	private static int SEND_PORT = 32002;
	int choices[] = new int[4];
	int blockCount = 1;
	
	//private static final int CLIENT_SENT_ERROR = 1;
	//private static final int SERVER_SENT_ERROR = 2;
	
	/**
	 * Class constructor
	 */
	public ErrorSimulator() {
		try {
			receiveSocket = new DatagramSocket(RECEIVE_PORT);
		} catch(Exception e) {
			e.printStackTrace();
			System.exit(1);
		}
	}

	public void start() throws IOException {
		try {
			// Prompt for user inputs on error simulation mode
			@SuppressWarnings("resource")
			Scanner in = new Scanner(System.in);
			System.out.println("Please select the type of packet for the error to occur in:\n"
					+ "0 - Normal operation mode (no error will be simulated)\n"
					+ "1 - RRQ/WRQ Packet\n"
					+ "2 - ACK/DATA Packet");
			choices[0] = in.nextInt();
			switch (choices[0]) {
			case 0:
				System.out.println("0 has been chosen, error simulator will just pass on the packets.");
				break;
			case 1:
				System.out.println("Select type of error to simulate:\n"
						+ "0 - Normal operation mode (no error will be simulated)\n"
						+ "1 - Packet too large\n"
						+ "2 - Invalid TFTP Opcode\n"
						+ "3 - Invalid mode\n"
						+ "4 - Missing file name\n"
						+ "5 - Packet does not terminate after final 0\n"
						+ "6 - Final 0 is lost");
				choices[1] = in.nextInt();
				if (choices[1] == 0) {
					System.out.println("0 has been chosen, error simulator will just pass on the packets.");
					choices[0] = 0;
					break;
				}
				else if (choices[1] > 0 && choices[1] < 7) {
					System.out.println("Error simulator ready. Selected error will be simulated when it is applicable.\n");
					break;
				}
				else
					throw new IllegalArgumentException();
			case 2:
				System.out.println("Select type of error to simulate:\n"
						+ "0 - Normal operation mode (no error will be simulated)\n"
						+ "1 - Packet too large\n"
						+ "2 - Invalid TFTP Opcode\n"
						+ "3 - Invalid block number\n"
						+ "4 - Unknown transfer ID");
				choices[1] = in.nextInt();
				if (choices[1] == 0) {
					System.out.println("0 has been chosen, error simulator will just pass on the packets.");
					choices[0] = 0;
					break;
				}
				else if (choices[1] > 0 && choices[1] < 5) {
					System.out.println("Please select:\n"
							+ "0 - Normal operation mode (no error will be simulated)\n"
							+ "1 - Client send the packet with simulated error\n"
							+ "2 - Server send the packet with simulated error");
					choices[2] = in.nextInt();
					if (choices[2] == 0) {
						choices[0] = 0;
						choices[1] = 0;
						System.out.println("0 has been chosen, error simulator will just pass on the packets.");
						break;
					}
					else if (choices[2] == 1) {
						System.out.println("Please enter packet block number(greater than 0) within the transfer in which to simulate the chosen error:");
						choices[3] = in.nextInt();
						if (choices[3] > 0) {
							blockCount = 1;
							System.out.println("Error simulator ready. Selected error will be simulated when it is applicable.\n");
						}
						else
							throw new IllegalArgumentException();
						break;
					}
					else if (choices[2] == 2) {
						System.out.println("Please enter packet block number(with 0 stands for ACK 0 during a WRQ transfer) within the transfer in which "
								+ "to simulate the chosen error:");
						choices[3] = in.nextInt();
						if (choices[3] >= 0) {
							blockCount = 1;
							System.out.println("Error simulator ready. Selected error will be simulated when it is applicable.\n");
						}
						else
							throw new IllegalArgumentException();
						break;
					}
				}
			default:
				throw new IllegalArgumentException();
			}
			while (true) {
				// Creates a DatagramPacket to receive request from client
				DatagramPacket clientRequestPacket = TFTP.formPacket();

				// Receives response packet through socket
				receiveSocket.receive(clientRequestPacket);
				TFTP.shrinkData(clientRequestPacket);
				TFTP.printPacket(clientRequestPacket);

				// Creates a thread to handle client request
				Thread requestHandlerThread = new Thread(
						new RequestHandler(clientRequestPacket),
						"Request Handler Thread");

				// Start request handler thread
				requestHandlerThread.start();
			}
		} catch (IllegalArgumentException e) {
			System.out.println("Error simulator terminated. Please select a valid choice based on options given.");
		} finally {
			receiveSocket.close();
		}
	}

	// Modify the packet based on choices on error that needed to simulate
	public DatagramPacket tamperPacket(DatagramPacket receivePacket, int opCode, InetAddress forwardAddr, int TID)
	{
		byte[] buf;
		byte invalidOpcode = 9;
		String invalidMode = "Invalid Mode";
		byte[] tooLargePacket = new byte[TFTP.MAX_PACKET_SIZE];
		for (int i = 0; i < tooLargePacket.length; i++) {
			tooLargePacket[i] = 100;		// Random chosen number --> char 'd'
		}
		
		try {
			switch (choices[0]) {
			// RRQ or WRQ Packet
			case 1:
				switch (choices[1]) {
				case 1:
					// Packet too large
					buf = new byte[receivePacket.getLength() + tooLargePacket.length];
					System.arraycopy(receivePacket.getData(), 0, buf, 0, TFTP.getModeIndex(receivePacket) - 1);
					System.arraycopy(tooLargePacket, 0, buf, TFTP.getModeIndex(receivePacket) - 1, tooLargePacket.length);
					System.arraycopy(receivePacket.getData(), TFTP.getModeIndex(receivePacket), 
							buf, TFTP.getModeIndex(receivePacket) + tooLargePacket.length, 
							buf.length - TFTP.getModeIndex(receivePacket) - tooLargePacket.length);
					return new DatagramPacket(buf, buf.length, receivePacket.getAddress(), receivePacket.getPort());
				case 2:
					// Invalid TFTP Opcode
					receivePacket.getData()[1] = invalidOpcode;
					return receivePacket;
				case 3:
					// Invalid mode
					buf = new byte[TFTP.getModeIndex(receivePacket) + invalidMode.getBytes().length + 1];
					System.arraycopy(receivePacket.getData(), 0, buf, 0, TFTP.getModeIndex(receivePacket));
					System.arraycopy(invalidMode.getBytes(), 0, buf, TFTP.getModeIndex(receivePacket), invalidMode.getBytes().length);
					buf[buf.length - 1] = 0;
					return new DatagramPacket(buf, buf.length, receivePacket.getAddress(), receivePacket.getPort());
				case 4:
					// Missing file name
					buf = new byte[receivePacket.getLength() - TFTP.getModeIndex(receivePacket) + 3];
					System.arraycopy(receivePacket.getData(), 0, buf, 0, 2);
					System.arraycopy(receivePacket.getData(), TFTP.getModeIndex(receivePacket), buf, 3,
							receivePacket.getLength() - TFTP.getModeIndex(receivePacket));
					buf[2] = 0;
					return new DatagramPacket(buf, buf.length, receivePacket.getAddress(), receivePacket.getPort());
				case 5:
					// Packet does not terminate after final 0
					buf = new byte[receivePacket.getLength() + invalidMode.getBytes().length];
					System.arraycopy(receivePacket.getData(), 0, buf, 0, receivePacket.getLength());
					System.arraycopy(invalidMode.getBytes(), 0, buf, receivePacket.getLength(), invalidMode.getBytes().length);
					return new DatagramPacket(buf, buf.length, receivePacket.getAddress(), receivePacket.getPort());
				case 6:
					// Final 0 is lost
					buf = new byte[receivePacket.getLength() - 1];
					System.arraycopy(receivePacket.getData(), 0, buf, 0, buf.length);
					return new DatagramPacket(buf, buf.length, receivePacket.getAddress(), receivePacket.getPort());
				default:
					throw new IllegalArgumentException();
				}
			// ACK or DATA Packet
			case 2:
				//	 client send error & read				server send error & write
				if ((choices[2] == 1 && opCode == 1) || (choices[2] == 2 && opCode == 2)) {
					// ACK Packet
					switch (choices[1]) {
					case 1:
						// Packet too large
						buf = new byte[receivePacket.getLength()*2];
						System.arraycopy(receivePacket.getData(), 0, buf, 0, receivePacket.getLength());
						System.arraycopy(receivePacket.getData(), 0, buf, receivePacket.getLength(), receivePacket.getLength());
						return new DatagramPacket(buf, buf.length, receivePacket.getAddress(), receivePacket.getPort());
					case 2:
						// Invalid TFTP Opcode
						receivePacket.getData()[1] = invalidOpcode;
						return receivePacket;
					case 3:
						// Invalid block number
						if (choices[3] == 0)					//cases when ACK 0 is requested
							receivePacket.getData()[3] = 1;
						else {
							receivePacket.getData()[2] = 0;
							receivePacket.getData()[3] = 0;
						}
						return receivePacket;
					case 4:
						// Unknown transfer ID
						System.out.println("I'm here.");
						spawnUnknownTIDThread(receivePacket, forwardAddr, TID);
					}
				}
				//          client send error & write			server send error & read
				else if ((choices[2] == 1 && opCode == 2) || (choices[2] == 2 && opCode == 1)){
					// DATA Packet
					switch (choices[1]) {
					case 1:
						// Packet too large
						buf = new byte[receivePacket.getLength() + tooLargePacket.length];
						System.arraycopy(receivePacket.getData(), 0, buf, 0, receivePacket.getLength());
						System.arraycopy(tooLargePacket, 0, buf, receivePacket.getLength(), tooLargePacket.length);
						return new DatagramPacket(buf, buf.length, receivePacket.getAddress(), receivePacket.getPort());
					case 2:
						// Invalid TFTP Opcode
						receivePacket.getData()[1] = invalidOpcode;
						return receivePacket;
					case 3:
						// Invalid block number
						receivePacket.getData()[2] = 0;
						receivePacket.getData()[3] = 0;
						return receivePacket;
					case 4:
						// Unknown transfer ID
						spawnUnknownTIDThread(receivePacket, forwardAddr, TID);
					}
				}
			default:
				throw new IllegalArgumentException();
			}
		} catch (IllegalArgumentException e) {
			System.out.println("Error simulation mode error, please restart the error simulator.");
		}
		return receivePacket;
	}

	private void spawnUnknownTIDThread(DatagramPacket packet, InetAddress addressTID, int portTID)
	{

		// Thread to handle client request
		Thread unknownTIDThread;

		unknownTIDThread = new Thread(
				new UnknownTIDTransferHandler(packet, addressTID, portTID),
				"Unknown TID Trasfer Handler Thread");

		// Start unknown TID handler thread
		unknownTIDThread.start();

	}

	/**
	 * Creates a ErrorSimulator object and invoke it's start method
	 */
	public static void main(String[] args) {
		ErrorSimulator errorSimulator = new ErrorSimulator();
		try {
			errorSimulator.start();
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	/**
	 * Thread used to handle client requests
	 */
	class RequestHandler implements Runnable {
		DatagramPacket requestPacket;

		public RequestHandler(DatagramPacket requestPacket) {
			this.requestPacket = requestPacket;
		}

		public void run() {
			// Extract operation from packet
			int operation = TFTP.getOpCode(requestPacket);

			// Thread to handle client request
			Thread transferHandlerThread = new Thread();

			// Creates thread corresponding to the operation received
			switch (operation) {
			case TFTP.READ_OP_CODE:
				transferHandlerThread = new Thread(
						new ReadTransferHandler(requestPacket),
						"Read Transfer Handler Thread");
				break;
			case TFTP.WRITE_OP_CODE:
				transferHandlerThread = new Thread(
						new WriteTransferHandler(requestPacket),
						"Write Transfer Handler Thread");
				break;
			default:
				//throw new UnsupportedOperationException();
				break;
			}
			// Start request handler thread
			transferHandlerThread.start();
		}
	}

	/**
	 * Thread used to handle client read transfers
	 */
	class ReadTransferHandler implements Runnable {
		DatagramPacket clientRequestPacket;

		public ReadTransferHandler(DatagramPacket clientRequestPacket) {
			this.clientRequestPacket = clientRequestPacket;
		}

		public void run() {
			try {
				// Socket used for sending and receiving packets from server
				DatagramSocket sendReceiveServerSocket = new DatagramSocket();

				// Socket used for sending and receiving packets from client
				DatagramSocket sendReceiveClientSocket = new DatagramSocket();

				// Creates a DatagramPacket to send request to server
				DatagramPacket serverRequestPacket = TFTP.formPacket(
						InetAddress.getLocalHost(),
						SEND_PORT,
						clientRequestPacket.getData());
				
				// Modify the RRQ if corresponding mode is selected
				if (choices[0] == 1)
					serverRequestPacket = tamperPacket(clientRequestPacket, TFTP.READ_OP_CODE, 
							serverRequestPacket.getAddress(), SEND_PORT);

				// Sends request packet through socket
				TFTP.printPacket(serverRequestPacket);
				sendReceiveServerSocket.send(serverRequestPacket);

				// Saves the client TID
				InetAddress clientAddressTID = clientRequestPacket.getAddress();
				int clientPortTID = clientRequestPacket.getPort();

				// Transfer ID of the server
				InetAddress serverAddressTID = null;
				int serverPortTID = -1;

				// Flag set when transfer is finished
				boolean transferComplete = false;

				// Flag set when error packet is received
				boolean errorPacketReceived = false;

				// Flag indicating first loop iteration (for setting up TID)
				boolean firstIteration = true;

				try {
					while (!transferComplete) {
						// Creates a DatagramPacket to receive data packet from server
						DatagramPacket dataPacket = TFTP.formPacket();

						// Receives data packet from server
						sendReceiveServerSocket.receive(dataPacket);

						// Transfer is complete if server sends back an error packet
						if (TFTP.getOpCode(dataPacket) == TFTP.ERROR_OP_CODE) {
							errorPacketReceived = true;
						}

						// Transfer is complete if data block is less than MAX_DATA_SIZE
						if (dataPacket.getLength() < TFTP.MAX_PACKET_SIZE) {
							transferComplete = true;
							TFTP.shrinkData(dataPacket);
						}
						TFTP.printPacket(dataPacket);

						// Saves server TID on first iteration
						if (firstIteration) {
							serverAddressTID = dataPacket.getAddress();
							serverPortTID = dataPacket.getPort();
							firstIteration = false;
						}

						// Generates data packet to client
						DatagramPacket forwardedDataPacket = TFTP.formPacket(
								clientAddressTID,
								clientPortTID,
								dataPacket.getData());
						
						// Modify the DATA Packet based on mode and block number selected
						if (choices[0] == 2 && choices[2] == 2 && blockCount == choices[3])
							forwardedDataPacket = tamperPacket(forwardedDataPacket, TFTP.READ_OP_CODE,
									clientAddressTID, clientPortTID);
						
						// Sends data packet to client
						TFTP.printPacket(forwardedDataPacket);
						sendReceiveClientSocket.send(forwardedDataPacket);

						// End transfer if last packet received was an error packet
						if (errorPacketReceived) {
							transferComplete = true;
							break;
						}

						// Creates a DatagramPacket to receive acknowledgement packet from client
						DatagramPacket ackPacket = TFTP.formPacket();

						// Receives acknowledgement packet from client
						sendReceiveClientSocket.receive(ackPacket);
						TFTP.shrinkData(ackPacket);
						TFTP.printPacket(ackPacket);

						// Generate acknowledgement packet to server
						DatagramPacket forwardedAckPacket = TFTP.formPacket(
								serverAddressTID,
								serverPortTID,
								ackPacket.getData());
						
						// Modify the ACK Packet based on mode and block number selected
						if (choices[0] == 2 && choices[2] == 1 && blockCount == choices[3])
							forwardedAckPacket = tamperPacket(forwardedAckPacket, TFTP.READ_OP_CODE,
									serverAddressTID, serverPortTID);

						// Sends acknowledgement packet to server
						TFTP.printPacket(forwardedAckPacket);
						sendReceiveServerSocket.send(forwardedAckPacket);
						
						blockCount++;

						// Transfer is complete if server sends back an error packet
						if (TFTP.getOpCode(ackPacket) == TFTP.ERROR_OP_CODE) {
							errorPacketReceived = true;
							transferComplete = true;
							break;
						}
					}
					System.out.println("Connection terminated.\n");
				} finally {
					sendReceiveClientSocket.close();
					sendReceiveServerSocket.close();
				}
			} catch (IOException e) {
				e.printStackTrace();
			}
		}
	}

	/**
	 * Thread used to handle client write transfers
	 */
	class WriteTransferHandler implements Runnable {
		DatagramPacket clientRequestPacket;

		public WriteTransferHandler(DatagramPacket clientRequestPacket) {
			this.clientRequestPacket = clientRequestPacket;
		}

		public void run() {
			try {
				// Socket used for sending and receiving packets from server
				DatagramSocket sendReceiveServerSocket = new DatagramSocket();

				// Socket used for sending and receiving packets from client
				DatagramSocket sendReceiveClientSocket = new DatagramSocket();

				// Creates a DatagramPacket to send request to server
				DatagramPacket serverRequestPacket = TFTP.formPacket(
						InetAddress.getLocalHost(),
						SEND_PORT,
						clientRequestPacket.getData());
				
				// Modify the WRQ if corresponding mode is selected
				if (choices[0] == 1)
					serverRequestPacket = tamperPacket(clientRequestPacket, TFTP.WRITE_OP_CODE,
							serverRequestPacket.getAddress(), SEND_PORT);

				// Sends request packet through socket
				TFTP.printPacket(serverRequestPacket);
				sendReceiveServerSocket.send(serverRequestPacket);

				// Creates a DatagramPacket to receive acknowledgement packet from server
				DatagramPacket firstAckPacket = TFTP.formPacket();

				// Receives acknowledgement packet from server
				sendReceiveServerSocket.receive(firstAckPacket);
				TFTP.shrinkData(firstAckPacket);
				TFTP.printPacket(firstAckPacket);

				// End transfer if last packet received was an error packet
				if (TFTP.getOpCode(firstAckPacket) == TFTP.ERROR_OP_CODE) {
					System.out.println("Connection terminated.\n");
				}

				// Saves the client TID
				InetAddress clientAddressTID = clientRequestPacket.getAddress();
				int clientPortTID = clientRequestPacket.getPort();

				// Saves the server TID
				InetAddress serverAddressTID = firstAckPacket.getAddress();
				int serverPortTID = firstAckPacket.getPort();

				// Sends acknowledgement packet to client
				DatagramPacket forwardedFirstAckPacket = TFTP.formPacket(
						clientAddressTID,
						clientPortTID,
						firstAckPacket.getData());
				
				// Modify the first ACK Packet if specific modes are selected --> choices[] == {2, 1-4, 2, 0}
				if (choices[0] == 2 && choices[1] != 0 && choices[2] == 2 && choices[3] == 0)
					forwardedFirstAckPacket = tamperPacket(forwardedFirstAckPacket, TFTP.WRITE_OP_CODE,
							clientAddressTID, clientPortTID);
				
				TFTP.printPacket(forwardedFirstAckPacket);
				sendReceiveClientSocket.send(forwardedFirstAckPacket);
				
				// Flag set when transfer is finished
				boolean transferComplete = false;
				
				// Flag set when error packet is received
				boolean errorPacketReceived = false;
				
				// Transfer is complete if server sends back an error packet
				if (TFTP.getOpCode(firstAckPacket) == TFTP.ERROR_OP_CODE) {
					//System.out.println("Connection terminated.\n");
					transferComplete = true;
				}

				try {
					while (!transferComplete) {
						// Creates a DatagramPacket to receive data packet from client
						DatagramPacket dataPacket = TFTP.formPacket();

						// Receives data packet from client
						sendReceiveClientSocket.receive(dataPacket);
						
						if (TFTP.getOpCode(dataPacket) == TFTP.ERROR_OP_CODE) {
							//System.out.println("Connection terminated.\n");
							errorPacketReceived = true;
						}
						
						// Transfer is complete if data block is less than MAX_DATA_SIZE
						if (dataPacket.getLength() < TFTP.MAX_PACKET_SIZE) {
							transferComplete = true;
							TFTP.shrinkData(dataPacket);
						}
						TFTP.printPacket(dataPacket);

						// Generates data packet to server
						DatagramPacket forwardedDataPacket = TFTP.formPacket(
								serverAddressTID,
								serverPortTID,
								dataPacket.getData());
						
						// Modify the DATA Packet based on mode and block number selected
						if (choices[0] == 2 && choices[2] == 2 && blockCount == choices[3])
							forwardedDataPacket = tamperPacket(forwardedDataPacket, TFTP.WRITE_OP_CODE,
									serverAddressTID, serverPortTID);
						
						// Sends data packet to server
						TFTP.printPacket(forwardedDataPacket);
						sendReceiveServerSocket.send(forwardedDataPacket);
						
						if (errorPacketReceived) {
							transferComplete = true;
							break;
						}

						// Creates a DatagramPacket to receive acknowledgement packet from server
						DatagramPacket ackPacket = TFTP.formPacket();

						// Receives acknowledgement packet from server
						sendReceiveServerSocket.receive(ackPacket);
						TFTP.shrinkData(ackPacket);
						TFTP.printPacket(ackPacket);

						// Generates acknowledgement packet to client
						DatagramPacket forwardedAckPacket = TFTP.formPacket(
								clientAddressTID,
								clientPortTID,
								ackPacket.getData());
						
						// Modify the ACK Packet based on mode and block number selected
						if (choices[0] == 2 && choices[2] == 1 && blockCount == choices[3])
							forwardedAckPacket = tamperPacket(forwardedAckPacket, TFTP.WRITE_OP_CODE,
									clientAddressTID, clientPortTID);
						
						// Sends acknowledgement packet to client
						TFTP.printPacket(forwardedAckPacket);
						sendReceiveClientSocket.send(forwardedAckPacket);
						
						blockCount++;

						// Transfer is complete if server sends back an error packet
						if (TFTP.getOpCode(ackPacket) == TFTP.ERROR_OP_CODE) {
							transferComplete = true;
							break;
						}
					}
					System.out.println("Connection terminated.\n");
				} finally {
					sendReceiveClientSocket.close();
					sendReceiveServerSocket.close();
				}
			} catch (IOException e) {
				e.printStackTrace();
			}
		}
	}

	/**
	 * Thread used to handle unknown TID transfers
	 */
	class UnknownTIDTransferHandler implements Runnable {
		private DatagramPacket packet;
		private InetAddress addressTID;
		private int portTID;

		public UnknownTIDTransferHandler(DatagramPacket packet, InetAddress addressTID, int portTID) {
			this.packet = packet;
			this.addressTID = addressTID;
			this.portTID = portTID;
		}

		public void run() {
			try {
				// New socket with a different TID than the currently ongoing transfer
				DatagramSocket socket = new DatagramSocket();

				// Sends the packet to the host using this new TID
				socket.send(packet);
				
				// Error packet that is expected from the host
				DatagramPacket errorPacket = TFTP.formPacket();
				
				boolean unexpectedPacket;
				
				// Continue to receive packets until the correct packet is received
				do {
					unexpectedPacket = false;

					// Receives invalid TID error packet
					socket.receive(errorPacket);
					TFTP.shrinkData(errorPacket);
					TFTP.printPacket(errorPacket);
					
					// Check if the address and port of the received packet match the TID
					InetAddress packetAddress = errorPacket.getAddress();
					int packetPort = errorPacket.getPort();
					if (!(packetAddress.equals(addressTID) && (packetPort == portTID))) {
						unexpectedPacket = true;
					} else if (!TFTP.verifyErrorPacket(errorPacket)) {
						unexpectedPacket = true;
					} else if (TFTP.getErrorCode(errorPacket) != TFTP.ERROR_CODE_UNKNOWN_TID) {
						unexpectedPacket = true;
					}
				} while (unexpectedPacket);

				socket.close();
			} catch (IOException e) {
				e.printStackTrace();
				return;
			}
		}
	}
}
