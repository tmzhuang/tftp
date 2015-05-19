package tftp;

import java.io.IOException;
import java.net.*;

public class ErrorSimulator {
	/**
	 * Fields
	 */
	private DatagramSocket receiveSocket;
	//private static int RECEIVE_PORT = 4;
	//private static int SEND_PORT = 69;
	private static int RECEIVE_PORT = 32001;
	private static int SEND_PORT = 32002;

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
		} finally {
			receiveSocket.close();
		}
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
			Thread transferHandlerThread;

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
					throw new UnsupportedOperationException();
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

						// Sends data packet to client
						DatagramPacket forwardedDataPacket = TFTP.formPacket(
								clientAddressTID,
								clientPortTID,
								dataPacket.getData());
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

						// Sends acknowledgement packet to server
						DatagramPacket forwardedAckPacket = TFTP.formPacket(
								serverAddressTID,
								serverPortTID,
								ackPacket.getData());
						TFTP.printPacket(forwardedAckPacket);
						sendReceiveServerSocket.send(forwardedAckPacket);

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
				TFTP.printPacket(forwardedFirstAckPacket);
				sendReceiveClientSocket.send(forwardedFirstAckPacket);

				// Transfer is complete if server sends back an error packet
				if (TFTP.getOpCode(firstAckPacket) == TFTP.ERROR_OP_CODE) {
					System.out.println("Connection terminated.\n");
				}

				// Flag set when transfer is finished
				boolean transferComplete = false;

				try {
					while (!transferComplete) {
						// Creates a DatagramPacket to receive data packet from client
						DatagramPacket dataPacket = TFTP.formPacket();

						// Receives data packet from client
						sendReceiveClientSocket.receive(dataPacket);

						// Transfer is complete if data block is less than MAX_DATA_SIZE
						if (dataPacket.getLength() < TFTP.MAX_PACKET_SIZE) {
							transferComplete = true;
							TFTP.shrinkData(dataPacket);
						}
						TFTP.printPacket(dataPacket);

						// Sends data packet to server
						DatagramPacket forwardedDataPacket = TFTP.formPacket(
								serverAddressTID,
								serverPortTID,
								dataPacket.getData());
						TFTP.printPacket(forwardedDataPacket);
						sendReceiveServerSocket.send(forwardedDataPacket);

						// Creates a DatagramPacket to receive acknowledgement packet from server
						DatagramPacket ackPacket = TFTP.formPacket();

						// Receives acknowledgement packet from server
						sendReceiveServerSocket.receive(ackPacket);
						TFTP.shrinkData(ackPacket);
						TFTP.printPacket(ackPacket);

						// Sends acknowledgement packet to client
						DatagramPacket forwardedAckPacket = TFTP.formPacket(
								clientAddressTID,
								clientPortTID,
								ackPacket.getData());
						TFTP.printPacket(forwardedAckPacket);
						sendReceiveClientSocket.send(forwardedAckPacket);

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
}
