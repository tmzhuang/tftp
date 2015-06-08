package tftp;

import java.io.IOException;
import java.net.*;
import java.util.Scanner;

import tftp.TFTP;

/**
 * Implementation of the TFTP file transfer program on error simulator side.
 * 
 * @author Team 4
 * @version Iteration 3
 */
public class ErrorSimulator implements Runnable
{
	/**
	 * Fields
	 */
	private DatagramSocket receiveSocket;
	private static int RECEIVE_PORT = 68;
	private static int SEND_PORT = 69;
	//private static int RECEIVE_PORT = 32001;
	//private static int SEND_PORT = 32002;

	// Mode types
	private static final int MODE_NORMAL		= 1;
	private static final int MODE_READ_WRITE	= 2;
	private static final int MODE_DATA_ACK		= 3;
	
	// Errors for MODE_READ_WRITE
	private static final int ERROR_PACKET_TOO_LARGE					= 1;
	private static final int ERROR_INVALID_OPCODE					= 2;
	private static final int ERROR_INVALID_MODE						= 3;
	private static final int ERROR_MISSING_FILE_NAME				= 4;
	private static final int ERROR_NO_TERMINATION_AFTER_FINAL_0		= 5;
	private static final int ERROR_NO_FINAL_0						= 6;
	private static final int ERROR_REQUEST_DELAY 					= 7;
	private static final int ERROR_REQUEST_DUPLICATE				= 8;
	private static final int ERROR_REQUEST_LOSS						= 9;
	
	// Errors for MODE_DATA and MODE_ACK
//	private static final int ERROR_PACKET_TOO_LARGE1			= 1;
//	private static final int ERROR_INVALID_OPCODE				= 2;
	private static final int ERROR_INVALID_BLOCK_NUMBER			= 3;
	private static final int ERROR_UNKNOWN_TID					= 4;
	private static final int ERROR_ACK_DATA_DELAY 				= 5;
	private static final int ERROR_ACK_DATA_DUPLICATE			= 6;
	private static final int ERROR_ACK_DATA_LOSS				= 7;
	
	// Cause of error for MODE_DATA_ACK
	private static final int CAUSE_CLIENT_SENT = 1;
	private static final int CAUSE_SERVER_SENT = 2;
	
	// Used for tracking where the previous packet received came from
	private static final int RECEIVED_FROM_CLIENT = 1;
	private static final int RECEIVED_FROM_SERVER = 2;
	
	private int modeSelected;
	private int errorSelected;
	private int causeSelected;
	private int blockNumberSelected;
	private int packetDelay;
	private boolean sendFromUnknownTID;
	
	private boolean errorSimulated = false;
	
	private InetAddress sendAddr;

	private class PacketDelayer implements Runnable {
		private DatagramSocket socket;
		private DatagramPacket packet;
		private int delay;
		private static final int DELAY_INTERVAL = 1000;

		public PacketDelayer(DatagramSocket socket, DatagramPacket packet, int delay) {
			this.socket = socket;
			this.packet = packet;
			this.delay = delay;
		}

		public void run() {
			System.out.println("Starting a new packet delayer thread.");
			try {
				// Delay 
				while(delay > DELAY_INTERVAL) {
					System.out.println("Waiting for " + delay + "ms before sending packet.");
					Thread.sleep(DELAY_INTERVAL);
					delay -= DELAY_INTERVAL;
				}
				System.out.println("Waiting for " + delay + "ms before sending packet.");
				Thread.sleep(delay);
				System.out.println("Delay complete. Sending packet now.");
				socket.send(packet);
				System.out.println("[DELAYED PACKET]");
				TFTP.printPacket(packet);
				//System.out.println("BLARGH");
			} catch(InterruptedException e) {
				System.out.println("Packet delay was interrupted.");
				return;
			} catch(Exception e) {
				e.printStackTrace();
				return;
			}
			return;
		}
	}
	
	/**
	 * Class constructor
	 */
	public ErrorSimulator()
	{
		try
		{
			receiveSocket = new DatagramSocket(RECEIVE_PORT);
		}
		catch(Exception e)
		{
			e.printStackTrace();
			System.exit(1);
		}
		modeSelected = -1;
		errorSelected = -1;
		causeSelected = -1;
		blockNumberSelected = -1;
		packetDelay = -1;
		sendFromUnknownTID = false;
	}

	public void run()
	{
		try
		{
			// Scanner used for user input
			Scanner scanner = new Scanner(System.in);

			boolean validInput;

			String modeSelectedString;

			// Get the IP address or host name from user input
			for (boolean validHost = false; validHost == false; ) {
				System.out.println("Please enter the IP address of the server:");
				String host = scanner.next();
				try {
					sendAddr = InetAddress.getByName(host);
				} catch(UnknownHostException e) {
					System.out.println("Invalid host name or IP address. Please try again.");
					continue;
				}
				validHost = true;
			}

			// Get mode from user input
			do
			{
				validInput = true;
				System.out.println("Select the type of packets that you want to simulate errors for:");
				System.out.println("	(" + MODE_NORMAL + ") - No error simulation");
				System.out.println("	(" + MODE_READ_WRITE + ") - RRQ/WRQ packets");
				System.out.println("	(" + MODE_DATA_ACK + ") - DATA/ACK packets");
				modeSelectedString = scanner.next();

				try
				{
					modeSelected = Integer.valueOf(modeSelectedString);
				}
				catch (NumberFormatException e)
				{
					System.out.println("Please enter a value from " + MODE_NORMAL + " to " + MODE_DATA_ACK);
					validInput = false;
					continue;
				}

				if ((modeSelected < MODE_NORMAL) || (modeSelected > MODE_DATA_ACK))
				{
					System.out.println("Please enter a value from " + MODE_NORMAL + " to " + MODE_DATA_ACK);
					validInput = false;
				}
			} while (!validInput);

			String errorSelectedString;	
			String causeSelectedString;
			String blockNumberSelectedString;
			// Get error for read/write mode 
			if (modeSelected == MODE_READ_WRITE)
			{
				do
				{
					validInput = true;
					System.out.println("Select the type of error you wish to simulate:");
					System.out.println("	(" + ERROR_PACKET_TOO_LARGE + ") - Packet too large");
					System.out.println("	(" + ERROR_INVALID_OPCODE + ") - Invalid opcode");
					System.out.println("	(" + ERROR_INVALID_MODE + ") - Invalid mode");
					System.out.println("	(" + ERROR_MISSING_FILE_NAME + ") - Missing file name");
					System.out.println("	(" + ERROR_NO_TERMINATION_AFTER_FINAL_0 + ") - No termination after final 0");
					System.out.println("	(" + ERROR_NO_FINAL_0 + ") - No final 0");
					System.out.println("	(" + ERROR_REQUEST_DELAY + ") - Request delay");
					System.out.println("	(" + ERROR_REQUEST_DUPLICATE + ") - Request duplicate");
					System.out.println("	(" + ERROR_REQUEST_LOSS + ") - Request loss");
					errorSelectedString = scanner.next();

					try
					{
						errorSelected = Integer.valueOf(errorSelectedString);
					}
					catch (NumberFormatException e)
					{
						System.out.println("Please enter a value from " + ERROR_PACKET_TOO_LARGE + " to " + ERROR_REQUEST_LOSS);
						validInput = false;
						continue;
					}

					if ((errorSelected < ERROR_PACKET_TOO_LARGE) || (errorSelected > ERROR_REQUEST_LOSS))
					{
						System.out.println("Please enter a value from " + ERROR_PACKET_TOO_LARGE + " to " + ERROR_REQUEST_LOSS);
						validInput = false;
					}
				} while (!validInput);

				// If error is a network error, get the delay
				if (isDelayableError(modeSelected, errorSelected)) {
					do {
						System.out.println("Please enter the amount of time in ms you would like to wait before sending"
								+ " the delayed/duplicated request:");
						String packetDelayString = scanner.next();
						try
						{
							packetDelay = Integer.valueOf(packetDelayString);
						}
						catch (NumberFormatException e)
						{
							System.out.println("Please enter a value from " + 0 + " to " + 999999 );
							validInput = false;
							continue;
						}

						if ((packetDelay < 0) || (packetDelay > 999999))
						{
							System.out.println("Please enter a value from " + 0 + " to " + 999999 );
							validInput = false;
						}
					} while (!validInput);
				}
			}
			// Get error for data/ack mode
			else if (modeSelected == MODE_DATA_ACK)
			{
				do
				{
					validInput = true;
					System.out.println("Select the type of error you wish to simulate:");
					System.out.println("	(" + ERROR_PACKET_TOO_LARGE + ") - Packet too large");
					System.out.println("	(" + ERROR_INVALID_OPCODE + ") - Invalid opcode");
					System.out.println("	(" + ERROR_INVALID_BLOCK_NUMBER + ") - Invalid block number");
					System.out.println("	(" + ERROR_UNKNOWN_TID + ") - Unknown TID");
					System.out.println("	(" + ERROR_ACK_DATA_DELAY + ") - ACK/DATA delay");
					System.out.println("	(" + ERROR_ACK_DATA_DUPLICATE + ") - ACK/DATA duplicate");
					System.out.println("	(" + ERROR_ACK_DATA_LOSS + ") - ACK/DATA loss");
					errorSelectedString = scanner.next();

					try
					{
						errorSelected = Integer.valueOf(errorSelectedString);
					}
					catch (NumberFormatException e)
					{
						System.out.println("Please enter a value from " + ERROR_PACKET_TOO_LARGE + " to " + ERROR_ACK_DATA_LOSS);
						validInput = false;
						continue;
					}

					if ((errorSelected < ERROR_PACKET_TOO_LARGE) || (errorSelected > ERROR_ACK_DATA_LOSS))
					{
						System.out.println("Please enter a value from " + ERROR_PACKET_TOO_LARGE + " to " + ERROR_ACK_DATA_LOSS);
						validInput = false;
					}
				} while (!validInput);

				do
				{
					validInput = true;
					System.out.println("Select which host will cause the simulated error:");
					System.out.println("	(" + CAUSE_CLIENT_SENT + ") - Client");
					System.out.println("	(" + CAUSE_SERVER_SENT + ") - Server");
					causeSelectedString = scanner.next();

					try
					{
						causeSelected = Integer.valueOf(causeSelectedString);
					}
					catch (NumberFormatException e)
					{
						System.out.println("Please enter a value from " + CAUSE_CLIENT_SENT + " to " + CAUSE_SERVER_SENT);
						validInput = false;
						continue;
					}

					if ((causeSelected < CAUSE_CLIENT_SENT) || (causeSelected > CAUSE_SERVER_SENT))
					{
						System.out.println("Please enter a value from " + CAUSE_CLIENT_SENT + " to " + CAUSE_SERVER_SENT);
						validInput = false;
					}
				} while (!validInput);

				// Get the block number to produce error on
				do
				{
					validInput = true;
					System.out.println("Enter the block number of the packet that the error will be simulated on:");
					blockNumberSelectedString = scanner.next();

					try
					{
						blockNumberSelected = Integer.valueOf(blockNumberSelectedString);
					}
					catch (NumberFormatException e)
					{
						System.out.println("Please enter a value from 0 to " + TFTP.MAX_BLOCK_NUMBER);
						validInput = false;
						continue;
					}

					if ((blockNumberSelected < 0) || (blockNumberSelected > TFTP.MAX_BLOCK_NUMBER))
					{
						System.out.println("Please enter a value from 0 to " + TFTP.MAX_BLOCK_NUMBER);
						validInput = false;
					}
				} while (!validInput);

				// If error is a network error, get the delay
				if (isDelayableError(modeSelected, errorSelected)) {
					do {
						System.out.println("Please enter the amount of time in ms you would like to wait before sending"
								+ " the delayed/duplicated packet:");
						String packetDelayString = scanner.next();
						try
						{
							packetDelay = Integer.valueOf(packetDelayString);
						}
						catch (NumberFormatException e)
						{
							System.out.println("Please enter a value from " + 0 + " to " + 999999 );
							validInput = false;
							continue;
						}

						if ((packetDelay < 0) || (packetDelay > 999999))
						{
							System.out.println("Please enter a value from " + 0 + " to " + 999999 );
							validInput = false;
						}
					} while (!validInput);
				}


			}

			scanner.close();

			System.out.println("Error simulator ready.\n");

			try
			{
				while (true)
				{
					// Creates a DatagramPacket to receive request from client
					DatagramPacket clientRequestPacket = TFTP.formPacket();

					// Receives response packet through socket
					receiveSocket.receive(clientRequestPacket);
					TFTP.shrinkData(clientRequestPacket);
					System.out.println("[CLIENT=>ERRSIM]");
					TFTP.printPacket(clientRequestPacket);

					// Creates a thread to handle client request
					Thread requestHandlerThread = new Thread(
							new RequestHandler(clientRequestPacket),
							"Request Handler Thread");

					// Start request handler thread
					requestHandlerThread.start();
				}
			}
			finally
			{
				receiveSocket.close();
			}
		}
		catch (IOException e)
		{
			e.printStackTrace();
		}
	}

	private boolean isDelayableError(int mode, int error) {
		//System.out.println("Mode is " + modeSelected);
		//System.out.println("Error is " + errorSelected);
		if (mode == MODE_READ_WRITE) {
			return (error >= ERROR_REQUEST_DELAY && error <= ERROR_REQUEST_DUPLICATE);
		} else if (mode == MODE_DATA_ACK) {
			return (error >= ERROR_ACK_DATA_DELAY && error <= ERROR_ACK_DATA_DUPLICATE);
		} else {
			return false;
		}
	}
	
	private void tamperPacket(DatagramPacket packet, int receivedFrom)
	{
		int operation = TFTP.getOpCode(packet);
		if (((operation == TFTP.READ_OP_CODE) || (operation == TFTP.WRITE_OP_CODE))
				&& (modeSelected == MODE_READ_WRITE))
		{
			tamperReadWritePacket(packet);
		}
		else if (((operation == TFTP.DATA_OP_CODE) || (operation == TFTP.ACK_OP_CODE))
				&& (modeSelected == MODE_DATA_ACK)
				&& (blockNumberSelected == TFTP.getBlockNumber(packet))
				&& (receivedFrom == causeSelected))
		{
			tamperDataAckPacket(packet);
		}
	}
	
	private void tamperReadWritePacket(DatagramPacket packet)
	{
		if (errorSelected == ERROR_PACKET_TOO_LARGE)
		{
			// Creates a buffer large than the largest expected packet size, copy over the packet data
			// over to it, then set it as the new packet data buffer
			System.out.println("Simulating ERROR 4 (Illegal TFTP Operation) for packet too large");
			byte[] data = packet.getData();
			byte[] dataLargeBuffer = new byte[TFTP.MAX_PACKET_SIZE + 1];
			System.arraycopy(data, 0, dataLargeBuffer, 0, data.length);
			packet.setData(dataLargeBuffer);
		}
		else if (errorSelected == ERROR_INVALID_OPCODE)
		{
			// Change the opcode to an invalid opcode
			System.out.println("Simulating ERROR 4 (Illegal TFTP Operation) for invalid opcode");
			byte[] data = packet.getData();
			data[1] = 100;
			packet.setData(data);
		}
		else if (errorSelected == ERROR_INVALID_MODE)
		{
			// Change the mode to an invalid mode
			System.out.println("Simulating ERROR 4 (Illegal TFTP Operation) for invalid mode");
			Request oldRequest = TFTP.parseRQ(packet);
			String invalidMode = "invalidMode";
			Request newRequest = new Request(oldRequest.getType(), oldRequest.getFileName(), invalidMode);
			DatagramPacket newRequestPacket = TFTP.formRQPacket(
					sendAddr,
					SEND_PORT,
					newRequest);
			packet.setData(newRequestPacket.getData());
		}
		else if (errorSelected == ERROR_MISSING_FILE_NAME)
		{
			// Delete the file name from the data buffer
			System.out.println("Simulating ERROR 4 (Illegal TFTP Operation) for missing file name");
			Request oldRequest = TFTP.parseRQ(packet);
			String missingFileName = "";
			Request newRequest = new Request(oldRequest.getType(), missingFileName, oldRequest.getMode());
			DatagramPacket newRequestPacket = TFTP.formRQPacket(
					sendAddr,
					SEND_PORT,
					newRequest);
			packet.setData(newRequestPacket.getData());
		}
		else if (errorSelected == ERROR_NO_TERMINATION_AFTER_FINAL_0)
		{
			// Creates a buffer with a byte added to the end of the packet data
			System.out.println("Simulating ERROR 4 (Illegal TFTP Operation) for no termination after final 0");
			byte[] data = packet.getData();
			byte[] dataPlusOne = new byte[data.length + 1];
			System.arraycopy(data, 0, dataPlusOne, 0, data.length);
			packet.setData(dataPlusOne);
		}
		else if (errorSelected == ERROR_NO_FINAL_0)
		{
			// Creates a buffer with a byte deleted from the end of the packet data
			System.out.println("Simulating ERROR 4 (Illegal TFTP Operation) for no final 0");
			byte[] data = packet.getData();
			byte[] dataMinusOne = new byte[data.length - 1];
			System.arraycopy(data, 0, dataMinusOne, 0, data.length - 1);
			packet.setData(dataMinusOne);
		}
	}

	private void tamperDataAckPacket(DatagramPacket packet)
	{
		if (errorSelected == ERROR_PACKET_TOO_LARGE)
		{
			// Creates a buffer large than the largest expected packet size, copy over the packet data
			// over to it, then set it as the new packet data buffer
			System.out.println("Simulating ERROR 4 (Illegal TFTP Operation) for packet too large");
			byte[] data = packet.getData();
			byte[] dataLargeBuffer = new byte[TFTP.MAX_PACKET_SIZE + 1];
			System.arraycopy(data, 0, dataLargeBuffer, 0, data.length);
			packet.setData(dataLargeBuffer);
			errorSimulated = true;
		}
		else if (errorSelected == ERROR_INVALID_OPCODE)
		{
			// Change the opcode to an invalid opcode
			System.out.println("Simulating ERROR 4 (Illegal TFTP Operation) for invalid opcode");
			byte[] data = packet.getData();
			data[1] = 100;
			packet.setData(data);
			errorSimulated = true;
		}
		else if (errorSelected == ERROR_INVALID_BLOCK_NUMBER)
		{
			// Changes the block number
			System.out.println("Simulating ERROR 4 (Illegal TFTP Operation) for invalid block number");
			byte[] data = packet.getData();
			byte lsb_blockNumber = data[3];
			lsb_blockNumber += 1;
			data[3] = lsb_blockNumber;
			packet.setData(data);
			errorSimulated = true;
		}
		else if (errorSelected == ERROR_UNKNOWN_TID)
		{
			// Sets this flag to let spawnUnknownTIDThread() know that it should spawn a thread
			System.out.println("Simulating ERROR 5 (Unknown TID)");
			sendFromUnknownTID = true;
			errorSimulated = true;
		}
	}
	
	private void spawnUnknownTIDThread(DatagramPacket packet, InetAddress addressTID, int portTID)
	{
		if (sendFromUnknownTID)
		{
			// Thread to handle client request
			Thread unknownTIDThread;

			unknownTIDThread = new Thread(
					new UnknownTIDTransferHandler(packet, addressTID, portTID),
					"Unknown TID Trasfer Handler Thread");

			// Start unknown TID handler thread
			unknownTIDThread.start();
			
			sendFromUnknownTID = false;
		}
	}

	/**
	 * Creates a ErrorSimulator object and invoke it's start method
	 */
	public static void main(String[] args)
	{
		ErrorSimulator errorSimulator = new ErrorSimulator();
		errorSimulator.run();

	}

	/**
	 * Thread used to handle client requests
	 */
	class RequestHandler implements Runnable
	{
		private DatagramPacket requestPacket;

		public RequestHandler(DatagramPacket requestPacket)
		{
			this.requestPacket = requestPacket;
		}

		public void run()
		{
			// Returns if request packet is malformed
			String[] errorMessage = new String[1];
			if (!TFTP.verifyRequestPacket(requestPacket, errorMessage))
			{
				System.out.println("ERROR CODE 4: " + errorMessage[0]);
				return;
			}

			// Extract operation from packet
			int operation = TFTP.getOpCode(requestPacket);

			// Thread to handle client request
			Thread transferHandlerThread;

			// Creates thread corresponding to the operation received
			switch (operation)
			{
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
	class ReadTransferHandler implements Runnable
	{
		private DatagramPacket clientRequestPacket;
		
		public ReadTransferHandler(DatagramPacket clientRequestPacket)
		{
			System.out.println("Creating new ReadTransferHander");
			this.clientRequestPacket = clientRequestPacket;
		}

		public void run()
		{
			try
			{
				// Socket used for sending and receiving packets from server
				DatagramSocket sendReceiveServerSocket = new DatagramSocket();

				// Socket used for sending and receiving packets from client
				DatagramSocket sendReceiveClientSocket = new DatagramSocket();

				// Creates a DatagramPacket to send request to server
				DatagramPacket serverRequestPacket = TFTP.formPacket(
						sendAddr,
						SEND_PORT,
						clientRequestPacket.getData());

				Thread packetDelayerThread = null;

				// Sends request packet through socket
				tamperPacket(serverRequestPacket, RECEIVED_FROM_CLIENT);
				if (isDelayableError(modeSelected, errorSelected) && modeSelected == MODE_READ_WRITE && !errorSimulated) {
					// Send current request now if duplicating
					if (modeSelected == MODE_READ_WRITE && errorSelected == ERROR_REQUEST_DUPLICATE) {
						System.out.println("[ERRSIM=>SERVER]");
						TFTP.printPacket(serverRequestPacket);
						sendReceiveServerSocket.send(serverRequestPacket);
					}
					// Send a delayed packet
					errorSimulated = true;
					PacketDelayer packetDelayer = new PacketDelayer(sendReceiveServerSocket, serverRequestPacket, packetDelay);
					packetDelayerThread = new Thread(packetDelayer, "Packet Delayer Thread");
					packetDelayerThread.start();
				} else if (modeSelected == MODE_READ_WRITE && errorSelected == ERROR_REQUEST_LOSS && !errorSimulated) {
					// If request loss error, we don't send a packet at all
					System.out.println("SIMULATING LOST PACKET: RRQ.\n");
					errorSimulated = true;
				} else {
					// Proceed as normal otherwise
					System.out.println("[ERRSIM=>SERVER]");
					TFTP.printPacket(serverRequestPacket);
					sendReceiveServerSocket.send(serverRequestPacket);
				}

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

				String[] errorMessage = new String[1];

				try
				{
					boolean packetLossTriggered = false;
					//while (true)
					while (!transferComplete)
					{
						// Creates a DatagramPacket to receive data packet from server
						DatagramPacket dataPacket = TFTP.formPacket();

						// Receives data packet from server
						System.out.println("Waiting on DATA packet from server.");
						sendReceiveServerSocket.receive(dataPacket);
						
						// Transfer is complete if server sends back an error packet (other than error 5)
						if (TFTP.getOpCode(dataPacket) == TFTP.ERROR_OP_CODE)
						{
							if(!(TFTP.getErrorCode(dataPacket)==TFTP.ERROR_CODE_UNKNOWN_TID))
								errorPacketReceived = true;
						}

						// Transfer is complete if data block is less than MAX_DATA_SIZE
						if (dataPacket.getLength() < TFTP.MAX_DATA_SIZE)
						{
							transferComplete = true;
						}
						TFTP.shrinkData(dataPacket);
						System.out.println("[SERVER=>ERRSIM]");
						TFTP.printPacket(dataPacket);

						// Saves server TID on first iteration
						if (firstIteration)
						{
							serverAddressTID = dataPacket.getAddress();
							serverPortTID = dataPacket.getPort();
							firstIteration = false;
						}

						DatagramPacket forwardedDataPacket = TFTP.formPacket(
								clientAddressTID,
								clientPortTID,
								dataPacket.getData());

						// Simulate network error if all of the following apply
						// 1. Cause is server
						// 2. Block number matches the one selected by user
						// 3. Error simulator is simulating a network error
						tamperPacket(forwardedDataPacket, RECEIVED_FROM_SERVER);
						if (!errorSimulated && causeSelected == RECEIVED_FROM_SERVER &&
									!TFTP.verifyErrorPacket(forwardedDataPacket, errorMessage) &&
									!packetLossTriggered &&
									blockNumberSelected == TFTP.getBlockNumber(forwardedDataPacket)) { 
							if (isDelayableError(modeSelected, errorSelected) && modeSelected == MODE_DATA_ACK) {
								// Send current request now if duplicating
								if (modeSelected == MODE_DATA_ACK && errorSelected == ERROR_ACK_DATA_DUPLICATE) {
									System.out.println("[ERRSIM=>CLIENT]");
									TFTP.printPacket(forwardedDataPacket);
									sendReceiveClientSocket.send(forwardedDataPacket);
								}
								// Send a delayed packet
								errorSimulated = true;
								PacketDelayer packetDelayer = new PacketDelayer(sendReceiveClientSocket, forwardedDataPacket, packetDelay);
								packetDelayerThread = new Thread(packetDelayer, "Packet Delayer Thread");
								packetDelayerThread.start();
							} else if (modeSelected == MODE_DATA_ACK && errorSelected == ERROR_ACK_DATA_LOSS) {
								// If request loss error, we don't send a packet at all
								System.out.println("SIMULATING LOST PACKET: DATA" + blockNumberSelected + ".\n");
								// Break current loop and wait for next data packet
								packetLossTriggered = true;
								continue;
							}
						} else {
							// Proceed as normal otherwise
							// Sends data packet to client
							spawnUnknownTIDThread(forwardedDataPacket, clientAddressTID, clientPortTID);
							System.out.println("[ERRSIM=>CLIENT]");
							TFTP.printPacket(forwardedDataPacket);
							sendReceiveClientSocket.send(forwardedDataPacket);
						}
						
						// End transfer if last packet received was an error packet
						if (errorPacketReceived) {
							transferComplete = true;
							break;
						}

						// Creates a DatagramPacket to receive acknowledgment packet from client
						DatagramPacket ackPacket = TFTP.formPacket();

						// Receives acknowledgment packet from client
						System.out.println("Waiting on ACK from client.");
						sendReceiveClientSocket.receive(ackPacket);
						TFTP.shrinkData(ackPacket);
						System.out.println("[CLIENT=>ERRSIM]");
						TFTP.printPacket(ackPacket);

						// Sends acknowledgment packet to server
						DatagramPacket forwardedAckPacket = TFTP.formPacket(
								serverAddressTID,
								serverPortTID,
								ackPacket.getData());

						tamperPacket(forwardedAckPacket, RECEIVED_FROM_CLIENT);
						
						// Simulate network error if all of the following apply
						// 1. Cause is client
						// 2. Block number matches the one selected by user
						// 3. Error simulator is simulating a network error
						//System.out.println("causeSelected = client:" + (causeSelected == RECEIVED_FROM_SERVER));
						//System.out.println("blockNumberSelected = 1:" + (blockNumberSelected == TFTP.getBlockNumber(forwardedAckPacket)));
						//System.out.println("isDelayble" + isDelayableError(modeSelected, errorSelected));
						if (!errorSimulated && causeSelected == RECEIVED_FROM_CLIENT &&
									!packetLossTriggered &&
									!TFTP.verifyErrorPacket(forwardedAckPacket, errorMessage) &&
									blockNumberSelected == TFTP.getBlockNumber(forwardedAckPacket)) { 
							System.out.println("Simulating Network Error.");
							if (isDelayableError(modeSelected, errorSelected) && modeSelected == MODE_DATA_ACK) {
								// Send current request now if duplicating
								if (modeSelected == MODE_DATA_ACK && errorSelected == ERROR_ACK_DATA_DUPLICATE) {
									System.out.println("[ERRSIM=>SERVER]");
									TFTP.printPacket(forwardedAckPacket);
									sendReceiveServerSocket.send(forwardedAckPacket);
								}
								// Send a delayed packet
								errorSimulated = true;
								PacketDelayer packetDelayer = new PacketDelayer(sendReceiveServerSocket, forwardedAckPacket, packetDelay);
								packetDelayerThread = new Thread(packetDelayer, "Packet Delayer Thread");
								packetDelayerThread.start();
							} else if (modeSelected == MODE_DATA_ACK && errorSelected == ERROR_ACK_DATA_LOSS) {
								// If request loss error, we don't send a packet at all
								System.out.println("SIMULATING LOST PACKET: ACK" + blockNumberSelected + ".\n");
								packetLossTriggered = true;
								continue;
							}
						} else {
							// Proceed as normal otherwise
							spawnUnknownTIDThread(forwardedAckPacket, serverAddressTID, serverPortTID);
							System.out.println("[ERRSIM=>SERVER]");
							TFTP.printPacket(forwardedAckPacket);
							sendReceiveServerSocket.send(forwardedAckPacket);
						}

						// Transfer is complete if client sends back an error packet other than error 5
						if (TFTP.getOpCode(ackPacket) == TFTP.ERROR_OP_CODE)
						{
							if(TFTP.getErrorCode(ackPacket)!=TFTP.ERROR_CODE_UNKNOWN_TID)
									{
										errorPacketReceived = true;
										transferComplete = true;
										break;
									}
						}

						if (isDelayableError(modeSelected, errorSelected)
							&& packetDelayerThread != null) {
							try {
								// Wait for packet delayer to finish before closing sockets
								packetDelayerThread.join();
							} catch(InterruptedException e) {
							}
						}
					}
					//System.out.println("Connection terminated.\n");
				}
				finally
				{
					System.out.println("Handler done. Closing.");
					sendReceiveClientSocket.close();
					sendReceiveServerSocket.close();
				}
			}
			catch (IOException e)
			{
				e.printStackTrace();
			}
			finally
			{
				System.out.println("ReadTransferHandler thread finished");
			}
		}
	}

	/**
	 * Thread used to handle client write transfers
	 */
	class WriteTransferHandler implements Runnable
	{
		private DatagramPacket clientRequestPacket;
		

		public WriteTransferHandler(DatagramPacket clientRequestPacket)
		{
			this.clientRequestPacket = clientRequestPacket;
		}

		public void run()
		{
			try
			{
				// Socket used for sending and receiving packets from server
				DatagramSocket sendReceiveServerSocket = new DatagramSocket();

				// Socket used for sending and receiving packets from client
				DatagramSocket sendReceiveClientSocket = new DatagramSocket();

				// Flag set when error packet is received
				boolean errorPacketReceived = false;

				String[] errorMessage = new String[1];
				
				// Creates a DatagramPacket to send request to server
				DatagramPacket serverRequestPacket = TFTP.formPacket(
						sendAddr,
						SEND_PORT,
						clientRequestPacket.getData());

				Thread packetDelayerThread = null;
				// Sends request packet through socket
				tamperPacket(serverRequestPacket, RECEIVED_FROM_CLIENT);
				if (isDelayableError(modeSelected, errorSelected) && modeSelected == MODE_READ_WRITE && !errorSimulated) {
					// Send current request now if duplicating
					if (modeSelected == MODE_READ_WRITE && errorSelected == ERROR_REQUEST_DUPLICATE) {
						System.out.println("[ERRSIM=>SERVER]");
						TFTP.printPacket(serverRequestPacket);
						sendReceiveServerSocket.send(serverRequestPacket);
					}
					// Send a delayed packet
					errorSimulated = true;
					PacketDelayer packetDelayer = new PacketDelayer(sendReceiveServerSocket, serverRequestPacket, packetDelay);
					packetDelayerThread = new Thread(packetDelayer, "Packet Delayer Thread");
					packetDelayerThread.start();
				} else if (modeSelected == MODE_READ_WRITE && errorSelected == ERROR_REQUEST_LOSS && !errorSimulated) {
					// If request loss error, we don't send a packet at all
					System.out.println("SIMULATING LOST PACKET: " + blockNumberSelected + ".");
					errorSimulated = true;
				} else {
					// Proceed as normal otherwise
					System.out.println("[ERRSIM=>SERVER]");
					TFTP.printPacket(serverRequestPacket);
					sendReceiveServerSocket.send(serverRequestPacket);
				}

				// Creates a DatagramPacket to receive acknowledgment packet from server
				DatagramPacket firstAckPacket = TFTP.formPacket();

				// Receives acknowledgment packet from server
				sendReceiveServerSocket.receive(firstAckPacket);
				TFTP.shrinkData(firstAckPacket);
				System.out.println("[SERVER=>ERRSIM]");
				TFTP.printPacket(firstAckPacket);

				// Transfer if server sends back an error packet
				if (TFTP.getOpCode(firstAckPacket) == TFTP.ERROR_OP_CODE)
				{
					errorPacketReceived = true;
				}

				// Saves the client TID
				InetAddress clientAddressTID = clientRequestPacket.getAddress();
				int clientPortTID = clientRequestPacket.getPort();

				// Saves the server TID
				InetAddress serverAddressTID = firstAckPacket.getAddress();
				int serverPortTID = firstAckPacket.getPort();

				boolean packetLossTriggered = false;

				// Sends acknowledgment packet to client
				DatagramPacket forwardedFirstAckPacket = TFTP.formPacket(
						clientAddressTID,
						clientPortTID,
						firstAckPacket.getData());
				tamperPacket(forwardedFirstAckPacket, RECEIVED_FROM_SERVER);
				
				tamperPacket(forwardedFirstAckPacket, RECEIVED_FROM_SERVER);
				if (!errorSimulated && causeSelected == RECEIVED_FROM_SERVER &&
						!TFTP.verifyErrorPacket(forwardedFirstAckPacket, errorMessage) &&
						!packetLossTriggered &&
						blockNumberSelected == TFTP.getBlockNumber(forwardedFirstAckPacket)) {
					if (isDelayableError(modeSelected, errorSelected)) {
						// Send current request now if duplicating
						if (modeSelected == MODE_DATA_ACK && errorSelected == ERROR_ACK_DATA_DUPLICATE) {
							System.out.println("[ERRSIM=>CLIENT]");
							TFTP.printPacket(forwardedFirstAckPacket);
							sendReceiveClientSocket.send(forwardedFirstAckPacket);
						}
						// Send a delayed packet
						errorSimulated = true;
						PacketDelayer packetDelayer = new PacketDelayer(sendReceiveClientSocket, forwardedFirstAckPacket, packetDelay);
						packetDelayerThread = new Thread(packetDelayer, "Packet Delayer Thread");
						packetDelayerThread.start();
					} else if (modeSelected == MODE_DATA_ACK && errorSelected == ERROR_ACK_DATA_LOSS && !errorSimulated) {
						// If request loss error, we don't send a packet at all
						System.out.println("SIMULATING LOST PACKET: ACK" + blockNumberSelected + ".\n");
						// Break current loop and wait for next data packet
						packetLossTriggered = true;
					}
				} else {
					packetLossTriggered = false;
					// Proceed as normal otherwise
					// Sends data packet to client
					//tamperPacket(forwardedFirstAckPacket, RECEIVED_FROM_SERVER);
					spawnUnknownTIDThread(forwardedFirstAckPacket, clientAddressTID, clientPortTID);
					System.out.println("[ERRSIM=>CLIENT]");
					TFTP.printPacket(forwardedFirstAckPacket);
					sendReceiveClientSocket.send(forwardedFirstAckPacket);
				}

				// Flag set when transfer is finished
				//boolean transferComplete = false;

				// End transfer if last packet received was an error packet other than error 5
				if (errorPacketReceived)
				{
				//	transferComplete = true;
				}

				try
				{
					//while (!transferComplete)
					while (true) 
					{
						// Creates a DatagramPacket to receive data packet from client
						DatagramPacket dataPacket = TFTP.formPacket();

						// Receives data packet from client
						System.out.println("Waiting for data from client");
						sendReceiveClientSocket.receive(dataPacket);

						// Transfer if client sends back an error packet other than error code 5
						if (TFTP.getOpCode(dataPacket) == TFTP.ERROR_OP_CODE)
						{
							if(TFTP.getErrorCode(dataPacket)!=TFTP.ERROR_CODE_UNKNOWN_TID){
								errorPacketReceived = true;
							}
						}

						// Transfer is complete if data block is less than MAX_DATA_SIZE
						if (dataPacket.getLength() < TFTP.MAX_DATA_SIZE)
						{
						//	transferComplete = true;
						}
						TFTP.shrinkData(dataPacket);
						System.out.println("[CLIENT=>ERRSIM]");
						TFTP.printPacket(dataPacket);

						// Sends data packet to server
						DatagramPacket forwardedDataPacket = TFTP.formPacket(
								serverAddressTID,
								serverPortTID,
								dataPacket.getData());

						packetDelayerThread = null;
						tamperPacket(forwardedDataPacket, RECEIVED_FROM_CLIENT);
						// Simulate network error if all of the following apply
						// 1. Cause is client
						// 2. Block number matches the one selected by user
						// 3. Error simulator is simulating a network error
						//System.out.println("causeSelected = client:" + (causeSelected == RECEIVED_FROM_SERVER));
						//System.out.println("blockNumberSelected = 1:" + (blockNumberSelected == TFTP.getBlockNumber(forwardedAckPacket)));
						//System.out.println("isDelayble" + isDelayableError(modeSelected, errorSelected));
						if (!errorSimulated && causeSelected == RECEIVED_FROM_CLIENT &&
									!TFTP.verifyErrorPacket(forwardedDataPacket, errorMessage) &&
									!packetLossTriggered &&
									blockNumberSelected == TFTP.getBlockNumber(forwardedDataPacket)) { 
							if (isDelayableError(modeSelected, errorSelected) && modeSelected == MODE_DATA_ACK) {
								// Send current request now if duplicating
								if (modeSelected == MODE_DATA_ACK && errorSelected == ERROR_ACK_DATA_DUPLICATE) {
									System.out.println("[ERRSIM=>SERVER]");
									TFTP.printPacket(forwardedDataPacket);
									sendReceiveServerSocket.send(forwardedDataPacket);
								}
								// Send a delayed packet
								errorSimulated = true;
								PacketDelayer packetDelayer = new PacketDelayer(sendReceiveServerSocket, forwardedDataPacket, packetDelay);
								packetDelayerThread = new Thread(packetDelayer, "Packet Delayer Thread");
								packetDelayerThread.start();
								System.out.println("Delaying Packet");
							} else if (modeSelected == MODE_DATA_ACK && errorSelected == ERROR_ACK_DATA_LOSS) {
								// If request loss error, we dont send a packet at all
								System.out.println("SIMULATING LOST PACKET: DATA" + blockNumberSelected + ".\n");
								errorSimulated = true;
								packetLossTriggered = true;
								continue;
							}
						} else {
							// Proceed as normal otherwise
							//tamperPacket(forwardedDataPacket, RECEIVED_FROM_CLIENT);
							spawnUnknownTIDThread(forwardedDataPacket, serverAddressTID, serverPortTID);
							System.out.println("[ERRSIM=>SERVER]");
							TFTP.printPacket(forwardedDataPacket);
							sendReceiveServerSocket.send(forwardedDataPacket);
						}

						// End transfer if last packet received was an error packet
						if (errorPacketReceived)
						{
							//transferComplete = true;
							break;
						}

						// Creates a DatagramPacket to receive acknowledgment packet from server
						DatagramPacket ackPacket = TFTP.formPacket();

						// Receives acknowledgment packet from server
						System.out.println("Waiting for ack from server");
						sendReceiveServerSocket.receive(ackPacket);
						TFTP.shrinkData(ackPacket);
						System.out.println("[SERVER=>ERRSIM]");
						TFTP.printPacket(ackPacket);

						// Sends acknowledgment packet to client
						DatagramPacket forwardedAckPacket = TFTP.formPacket(
								clientAddressTID,
								clientPortTID,
								ackPacket.getData());

						// Simulate network error if all of the following apply
						// 1. Cause is server
						// 2. Block number matches the one selected by user
						// 3. Error simulator is simulating a network error
						tamperPacket(forwardedAckPacket, RECEIVED_FROM_SERVER);
						if (!errorSimulated && causeSelected == RECEIVED_FROM_SERVER &&
									!TFTP.verifyErrorPacket(forwardedAckPacket, errorMessage) &&
									!packetLossTriggered &&
									blockNumberSelected == TFTP.getBlockNumber(forwardedAckPacket)) {
							if (isDelayableError(modeSelected, errorSelected)) {
								// Send current request now if duplicating
								if (modeSelected == MODE_DATA_ACK && errorSelected == ERROR_ACK_DATA_DUPLICATE) {
									System.out.println("[ERRSIM=>CLIENT]");
									TFTP.printPacket(forwardedAckPacket);
									sendReceiveClientSocket.send(forwardedAckPacket);
								}
								// Send a delayed packet
								errorSimulated = true;
								PacketDelayer packetDelayer = new PacketDelayer(sendReceiveClientSocket, forwardedAckPacket, packetDelay);
								packetDelayerThread = new Thread(packetDelayer, "Packet Delayer Thread");
								packetDelayerThread.start();
							} else if (modeSelected == MODE_DATA_ACK && errorSelected == ERROR_ACK_DATA_LOSS && !errorSimulated) {
								// If request loss error, we don't send a packet at all
								System.out.println("SIMULATING LOST PACKET: ACK" + blockNumberSelected + ".\n");
								// Break current loop and wait for next data packet
								packetLossTriggered = true;
								continue;
							}
						} else {
							packetLossTriggered = false;
							// Proceed as normal otherwise
							// Sends data packet to client
							//tamperPacket(forwardedAckPacket, RECEIVED_FROM_SERVER);
							spawnUnknownTIDThread(forwardedAckPacket, clientAddressTID, clientPortTID);
							System.out.println("[ERRSIM=>CLIENT]");
							TFTP.printPacket(forwardedAckPacket);
							sendReceiveClientSocket.send(forwardedAckPacket);
						}
						

						// Transfer is complete if server sends back an error packet
						if (TFTP.getOpCode(ackPacket) == TFTP.ERROR_OP_CODE)
						{
							if(TFTP.getErrorCode(ackPacket)!=TFTP.ERROR_CODE_UNKNOWN_TID)
							{
								//	transferComplete = true;
								break;
							}
						}

						if (isDelayableError(modeSelected, errorSelected)
								&& packetDelayerThread != null) {
							try {
								// Wait for packet delayer to finish before closing sockets
								packetDelayerThread.join();
							} catch(InterruptedException e) {
							}
						}
					}

					System.out.println("Connection terminated.\n");
				}
				finally
				{
					System.out.println("Handler done. Closing.");
					sendReceiveClientSocket.close();
					sendReceiveServerSocket.close();
				}
			}
			catch (IOException e)
			{
				e.printStackTrace();
			}
		}
	}

	/**
	 * Thread used to handle unknown TID transfers
	 */
	class UnknownTIDTransferHandler implements Runnable
	{
		private DatagramPacket packet;
		private InetAddress addressTID;
		private int portTID;

		public UnknownTIDTransferHandler(DatagramPacket packet, InetAddress addressTID, int portTID)
		{
			this.packet = packet;
			this.addressTID = addressTID;
			this.portTID = portTID;
		}

		public void run()
		{
			try
			{
				// New socket with a different TID than the currently ongoing transfer
				DatagramSocket socket = new DatagramSocket();

				// Sends the packet to the host using this new TID
				System.out.println("[ERRSIM=>UNKNOWN]");
				socket.send(packet);
				
				// Error packet that is expected from the host
				DatagramPacket errorPacket = TFTP.formPacket();
				
				boolean unexpectedPacket;
				
				String[] errorMessage = new String[1];
				
				// Continue to receive packets until the correct packet is received
				do
				{
					unexpectedPacket = false;

					// Receives invalid TID error packet
					socket.receive(errorPacket);
					TFTP.shrinkData(errorPacket);
					System.out.println("[UNKNOWN=>ERRSIM]");
					TFTP.printPacket(errorPacket);
					
					// Check if the address and port of the received packet match the TID
					InetAddress packetAddress = errorPacket.getAddress();
					int packetPort = errorPacket.getPort();
					if (!(	packetAddress.equals(addressTID) &&
							(packetPort == portTID)	))
					{
						unexpectedPacket = true;
					}
					else if (!TFTP.verifyErrorPacket(errorPacket, errorMessage))
					{
						unexpectedPacket = true;
					}
					else if (TFTP.getErrorCode(errorPacket) != TFTP.ERROR_CODE_UNKNOWN_TID)
					{
						unexpectedPacket = true;
					}
				} while (unexpectedPacket);

				socket.close();
			}
			catch (IOException e)
			{
				e.printStackTrace();
				return;
			}
			finally
			{
				System.out.println("WriteTransferHandler thread finished");
			}
		}
	}
}
