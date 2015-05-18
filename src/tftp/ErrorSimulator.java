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
				int clientTID = clientRequestPacket.getPort();

				// Transfer ID of the server
				int serverTID = -1;

				// Flag set when transfer is finished
				boolean transferComplete = false;

				// Flag indicating first loop iteration (for setting up TID)
				boolean firstIteration = true;

				try {
					while (!transferComplete) {
						// Creates a DatagramPacket to receive data packet from server
						DatagramPacket dataPacket = TFTP.formPacket();

						// Receives data packet from server
						sendReceiveServerSocket.receive(dataPacket);

						// TODO (Brandon): Transfer is complete if server sends back an error packet
						// Transfer is complete if data block is less than MAX_DATA_SIZE
						if (dataPacket.getLength() < TFTP.MAX_PACKET_SIZE) {
							transferComplete = true;
							TFTP.shrinkData(dataPacket);
						}
						TFTP.printPacket(dataPacket);

						// Saves server TID on first iteration
						if (firstIteration) {
							serverTID = dataPacket.getPort();
							firstIteration = false;
						}

						// Sends data packet to client
						DatagramPacket forwardedDataPacket = TFTP.formPacket(
								InetAddress.getLocalHost(),
								clientTID,
								dataPacket.getData());
						TFTP.printPacket(forwardedDataPacket);
						sendReceiveClientSocket.send(forwardedDataPacket);

						// Creates a DatagramPacket to receive acknowledgement packet from client
						DatagramPacket ackPacket = TFTP.formPacket();

						// Receives acknowledgement packet from client
						sendReceiveClientSocket.receive(ackPacket);
						TFTP.shrinkData(ackPacket);
						TFTP.printPacket(ackPacket);

						// Sends acknowledgement packet to server
						DatagramPacket forwardedAckPacket = TFTP.formPacket(
								InetAddress.getLocalHost(),
								serverTID,
								ackPacket.getData());
						TFTP.printPacket(forwardedAckPacket);
						sendReceiveServerSocket.send(forwardedAckPacket);
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

				// Saves the client TID
				int clientTID = clientRequestPacket.getPort();

				// Saves the server TID
				int serverTID = firstAckPacket.getPort();

				// Sends acknowledgement packet to client
				DatagramPacket forwardedFirstAckPacket = TFTP.formPacket(
						InetAddress.getLocalHost(),
						clientTID,
						firstAckPacket.getData());
				TFTP.printPacket(forwardedFirstAckPacket);
				sendReceiveClientSocket.send(forwardedFirstAckPacket);

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
								InetAddress.getLocalHost(),
								serverTID,
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
								InetAddress.getLocalHost(),
								clientTID,
								ackPacket.getData());
						TFTP.printPacket(forwardedAckPacket);
						sendReceiveClientSocket.send(forwardedAckPacket);
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

/*import java.net.*;
import java.util.*;

public class ErrorSimulator {
	private DatagramSocket receiveSocket, sendReceiveSocket;
	//private static int RECEIVE_PORT = 4;
	//private static int SEND_PORT = 69;
	private static int RECEIVE_PORT = 32001;
	private static int SEND_PORT = 32002;
	private static int BUF_SIZE = 100;
	private boolean verbose = true;

	public ErrorSimulator() {
		try {
			receiveSocket = new DatagramSocket(RECEIVE_PORT);
			sendReceiveSocket = new DatagramSocket();
		} catch(Exception e) {
			e.printStackTrace();
			System.exit(1);
		}
	}

	// Listens to client until a packet is received and relays the packet
	// to server. Then waits for response from server and relays the response
	// back to the client.
	public void listen() {
		InetAddress clientAddr = null;
		int clientPort = -1;

		// Receive packet from client
		if (verbose) System.out.println("Waiting for client...");
		byte[] buf = new byte[BUF_SIZE];
		DatagramPacket receivePacket = new DatagramPacket(buf, buf.length);

		try {
			receiveSocket.receive(receivePacket);
			if (verbose) {
				System.out.println("Packet from client received.");
				System.out.print("Request: ");
				System.out.println(new String(receivePacket.getData()));
				System.out.println("Length of packet received is: " + receivePacket.getLength());
			}
			clientAddr = receivePacket.getAddress();
			clientPort = receivePacket.getPort();
			
		} catch(Exception e) {
			e.printStackTrace();
			System.exit(1);
		}

		// Create packet to send to server
		try {
			// Truncate received packet to received length
			byte[] data = new byte[receivePacket.getLength()];
			System.arraycopy(buf,0,data,0,receivePacket.getLength());
			System.out.println(Arrays.toString(data));
			DatagramPacket sendPacket = new DatagramPacket(data, data.length, InetAddress.getLocalHost(), SEND_PORT);
		// Send packet to server
			if (verbose) System.out.println("Sending packet to server");
			sendReceiveSocket.send(sendPacket);
			
		} catch(Exception e) {
			e.printStackTrace();
			System.exit(1);
		}

		// Wait for response from server
		try {
			buf = new byte[BUF_SIZE];
			receivePacket = new DatagramPacket(buf, buf.length);
			if (verbose) System.out.println("Waiting for server...");
			sendReceiveSocket.receive(receivePacket);
			if (verbose) {
				System.out.println("Packet from server received.");
				System.out.print("Request: ");
				System.out.println(new String(receivePacket.getData()));
			}
		} catch(Exception e) {
			e.printStackTrace();
			System.exit(1);
		}

		// Create packet to send back to back to client
		try {
			DatagramPacket sendPacket = new DatagramPacket(buf, buf.length, clientAddr, clientPort);
			DatagramSocket tmpSocket = new DatagramSocket();
			if (verbose) {
				System.out.println("Sending packet to client");
				System.out.print("Request: ");
				System.out.println(new String(sendPacket.getData()));
				System.out.println();
			}
			tmpSocket.send(sendPacket);
			tmpSocket.close();
		} catch(Exception e) {
		}
	}

	public static void main (String[] args) {
		ErrorSimulator ihost = new ErrorSimulator();
		while (true) {
			ihost.listen();
		}
	}
}*/