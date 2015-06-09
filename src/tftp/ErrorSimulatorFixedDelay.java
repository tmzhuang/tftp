/**
 * TFTP Error Simulator
 *
 * @author	Group 4
 * @since	May 6, 2015
 */
package tftp;

import java.io.IOException;
import java.io.InterruptedIOException;
import java.net.*;
import java.util.HashMap;
import java.util.Scanner;

import tftp.TFTP;

public class ErrorSimulatorFixedDelay
{
	/**
	 * Fields
	 */
	//private static int RECEIVE_PORT = 68;
	//private static int SEND_PORT = 69;
	private static int RECEIVE_PORT = 32001;
	private static int SEND_PORT = 32002;
	private static final int RETRANSMITTION_TIMEOUT = 2000;
	private static final int ADDED_DELAY = 10;
	private static final int MAX_TIMEOUT_COUNT = 3;

	// Mode types
	private static final int MODE_NORMAL		= 1;
	private static final int MODE_READ_WRITE	= 2;
	private static final int MODE_DATA_ACK		= 3;
	
	// Errors for MODE_READ_WRITE
	private static final int ERROR_DELAYED_PACKET					= 1;
	private static final int ERROR_DUPLICATE_PACKET					= 2;
	private static final int ERROR_LOST_PACKET						= 3;
	private static final int ERROR_PACKET_TOO_LARGE					= 4;
	private static final int ERROR_INVALID_OPCODE					= 5;
	private static final int ERROR_INVALID_MODE						= 6;
	private static final int ERROR_MISSING_FILE_NAME				= 7;
	private static final int ERROR_NO_TERMINATION_AFTER_FINAL_0		= 8;
	private static final int ERROR_NO_FINAL_0						= 9;
	
	// Errors for MODE_DATA and MODE_ACK
	//private static final int ERROR_DELAYED_PACKET				= 1;
	//private static final int ERROR_DUPLICATE_PACKET			= 2;
	//private static final int ERROR_LOST_PACKET				= 3;
	//private static final int ERROR_PACKET_TOO_LARGE			= 4;
	//private static final int ERROR_INVALID_OPCODE				= 5;
	private static final int ERROR_INVALID_BLOCK_NUMBER			= 6;
	private static final int ERROR_UNKNOWN_TID					= 7;
	
	// Cause of error for MODE_DATA_ACK
	private static final int CAUSE_CLIENT_SENT = 1;
	private static final int CAUSE_SERVER_SENT = 2;
	
	// Used for tracking where the previous packet received came from
	private static final int RECEIVED_FROM_CLIENT = 1;
	private static final int RECEIVED_FROM_SERVER = 2;
	
	private int modeSelected = -1;
	private int errorSelected = -1;
	private int causeSelected = -1;
	private int blockNumberSelected = -1;

	private InetAddress sendAddr;

	/**
	 * Class constructor
	 */
	public ErrorSimulatorFixedDelay()
	{
		// No initialization required
	}

	public void start() throws IOException
	{
		// Socket used for receiving packets from client
		DatagramSocket receiveSocket = new DatagramSocket(RECEIVE_PORT);

		// Scanner used for user input
		Scanner scanner = new Scanner(System.in);

		boolean validInput;

		// Get the IP address or host name from user input
		for (boolean validHost = false; validHost == false; )
		{
			System.out.println("Please enter the IP address of the server:");
			String host = scanner.next();
			try {
				sendAddr = InetAddress.getByName(host);
			} catch(UnknownHostException e) {
				System.out.println("Invalid host name or IP address. Please try again.\n");
				continue;
			}
			validHost = true;
			System.out.println("");
		}

		String modeSelectedString;
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
		if (modeSelected == MODE_READ_WRITE)
		{
			do
			{
				validInput = true;
				System.out.println("Select the type of error you wish to simulate:");
				System.out.println("	(" + ERROR_DELAYED_PACKET + ") - Delayed packet");
				System.out.println("	(" + ERROR_DUPLICATE_PACKET + ") - Duplicated packet");
				System.out.println("	(" + ERROR_LOST_PACKET + ") - Lost packet");
				System.out.println("	(" + ERROR_PACKET_TOO_LARGE + ") - Packet too large");
				System.out.println("	(" + ERROR_INVALID_OPCODE + ") - Invalid opcode");
				System.out.println("	(" + ERROR_INVALID_MODE + ") - Invalid mode");
				System.out.println("	(" + ERROR_MISSING_FILE_NAME + ") - Missing file name");
				System.out.println("	(" + ERROR_NO_TERMINATION_AFTER_FINAL_0 + ") - No termination after final 0");
				System.out.println("	(" + ERROR_NO_FINAL_0 + ") - No final 0");
				errorSelectedString = scanner.next();

				try
				{
					errorSelected = Integer.valueOf(errorSelectedString);
				}
				catch (NumberFormatException e)
				{
					System.out.println("Please enter a value from " + ERROR_DELAYED_PACKET + " to " + ERROR_NO_FINAL_0);
					validInput = false;
					continue;
				}

				if ((errorSelected < ERROR_DELAYED_PACKET) || (errorSelected > ERROR_NO_FINAL_0))
				{
					System.out.println("Please enter a value from " + ERROR_DELAYED_PACKET + " to " + ERROR_NO_FINAL_0);
					validInput = false;
				}
			} while (!validInput);
		}
		else if (modeSelected == MODE_DATA_ACK)
		{
			do
			{
				validInput = true;
				System.out.println("Select the type of error you wish to simulate:");
				System.out.println("	(" + ERROR_DELAYED_PACKET + ") - Delayed packet");
				System.out.println("	(" + ERROR_DUPLICATE_PACKET + ") - Duplicated packet");
				System.out.println("	(" + ERROR_LOST_PACKET + ") - Lost packet");
				System.out.println("	(" + ERROR_PACKET_TOO_LARGE + ") - Packet too large");
				System.out.println("	(" + ERROR_INVALID_OPCODE + ") - Invalid opcode");
				System.out.println("	(" + ERROR_INVALID_BLOCK_NUMBER + ") - Invalid block number");
				System.out.println("	(" + ERROR_UNKNOWN_TID + ") - Unknown TID");
				errorSelectedString = scanner.next();

				try
				{
					errorSelected = Integer.valueOf(errorSelectedString);
				}
				catch (NumberFormatException e)
				{
					System.out.println("Please enter a value from " + ERROR_DELAYED_PACKET + " to " + ERROR_UNKNOWN_TID);
					validInput = false;
					continue;
				}

				if ((errorSelected < ERROR_DELAYED_PACKET) || (errorSelected > ERROR_UNKNOWN_TID))
				{
					System.out.println("Please enter a value from " + ERROR_DELAYED_PACKET + " to " + ERROR_UNKNOWN_TID);
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
		}

		scanner.close();

		System.out.println("Error simulator ready.\n");
		
		boolean errorSimulation;

		try
		{
			while (true)
			{
				// Creates a DatagramPacket to receive request from client
				DatagramPacket clientRequestPacket = TFTP.formPacket();

				// Receives response packet through socket
				receiveSocket.receive(clientRequestPacket);
				System.out.println("[ Client -> ErrorSim ]");
				TFTP.shrinkData(clientRequestPacket);
				TFTP.printPacket(clientRequestPacket);
				
				errorSimulation = (java.lang.Thread.activeCount() == 1);

				// Creates a thread to handle client request
				Thread requestHandlerThread = new Thread(
						new RequestHandler(clientRequestPacket, errorSimulation),
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
	
	private void tamperPacket(DatagramPacket packet, int receivedFrom, HashMap<String, Boolean> simulateError)
	{
		int operation = TFTP.getOpCode(packet);
		if (((operation == TFTP.READ_OP_CODE) || (operation == TFTP.WRITE_OP_CODE))
				&& (modeSelected == MODE_READ_WRITE))
		{
			tamperReadWritePacket(packet, simulateError);
		}
		else if (((operation == TFTP.DATA_OP_CODE) || (operation == TFTP.ACK_OP_CODE))
				&& (modeSelected == MODE_DATA_ACK)
				&& (blockNumberSelected == TFTP.getBlockNumber(packet))
				&& (receivedFrom == causeSelected))
		{
			tamperDataAckPacket(packet, simulateError);
		}
	}
	
	private void tamperReadWritePacket(DatagramPacket packet, HashMap<String, Boolean> simulateError)
	{
		if (errorSelected == ERROR_DELAYED_PACKET)
		{
			System.out.println("Simulating delayed packet");
			simulateError.put("simulateDelayedPacket", true);
		}
		else if (errorSelected == ERROR_DUPLICATE_PACKET)
		{
			System.out.println("Simulating duplicate packet");
			simulateError.put("simulateDuplicatePacket", true);
		}
		else if (errorSelected == ERROR_LOST_PACKET)
		{
			System.out.println("Simulating lost packet");
			simulateError.put("simulateLostPacket", true);
		}
		else if (errorSelected == ERROR_PACKET_TOO_LARGE)
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
			String operation = TFTP.opCodeToString(TFTP.getOpCode(packet));
			String fileName = TFTP.parseRQ(packet).getFileName();
			String invalidMode = "invalidMode";
			DatagramPacket newRequestPacket = TFTP.formRQPacket(
					sendAddr,
					SEND_PORT,
					operation,
					fileName,
					invalidMode);
			packet.setData(newRequestPacket.getData());
		}
		else if (errorSelected == ERROR_MISSING_FILE_NAME)
		{
			// Delete the file name from the data buffer
			System.out.println("Simulating ERROR 4 (Illegal TFTP Operation) for missing file name");
			String operation = TFTP.opCodeToString(TFTP.getOpCode(packet));
			String missingFileName = "";
			String mode = TFTP.parseRQ(packet).getMode();
			DatagramPacket newRequestPacket = TFTP.formRQPacket(
					sendAddr,
					SEND_PORT,
					operation,
					missingFileName,
					mode);
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

	private void tamperDataAckPacket(DatagramPacket packet, HashMap<String, Boolean> simulateError)
	{
		if (errorSelected == ERROR_DELAYED_PACKET)
		{
			System.out.println("Simulating delayed packet");
			simulateError.put("simulateDelayedPacket", true);
		}
		else if (errorSelected == ERROR_DUPLICATE_PACKET)
		{
			System.out.println("Simulating duplicate packet");
			simulateError.put("simulateDuplicatePacket", true);
		}
		else if (errorSelected == ERROR_LOST_PACKET)
		{
			System.out.println("Simulating lost packet");
			simulateError.put("simulateLostPacket", true);
		}
		else if (errorSelected == ERROR_PACKET_TOO_LARGE)
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
		else if (errorSelected == ERROR_INVALID_BLOCK_NUMBER)
		{
			// Changes the block number
			System.out.println("Simulating ERROR 4 (Illegal TFTP Operation) for invalid block number");
			byte[] data = packet.getData();
			byte lsb_blockNumber = data[3];
			lsb_blockNumber += 1;
			data[3] = lsb_blockNumber;
			packet.setData(data);
		}
		else if (errorSelected == ERROR_UNKNOWN_TID)
		{
			// Sets this flag to let spawnUnknownTIDThread() know that it should spawn a thread
			System.out.println("Simulating ERROR 5 (Unknown TID)");
			simulateError.put("simulateUnknownTID", true);
		}
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
	
	public DatagramPacket receivePacket(DatagramSocket socket) throws IOException
	{
		int numTimeouts = 0;
		DatagramPacket packet = TFTP.formPacket();

		do
		{
			try
			{
				socket.receive(packet);
				return packet;
			}
			catch (InterruptedIOException e)
			{
				if (++numTimeouts == MAX_TIMEOUT_COUNT)
				{
					System.out.println("Connection lost... Aborting transfer.");
					return null;
				}
				else
				{
					continue;
				}
			}
		} while (true);
	}

	/**
	 * Creates a ErrorSimulator object and invoke it's start method
	 */
	public static void main(String[] args)
	{
		ErrorSimulatorFixedDelay errorSimulator = new ErrorSimulatorFixedDelay();
		try
		{
			errorSimulator.start();
		}
		catch (IOException e)
		{
			e.printStackTrace();
		}
	}

	/**
	 * Thread used to handle client requests
	 */
	class RequestHandler implements Runnable
	{
		private DatagramPacket requestPacket;
		private boolean errorSimulation;

		public RequestHandler(DatagramPacket requestPacket, boolean errorSimulation)
		{
			this.requestPacket = requestPacket;
			this.errorSimulation = errorSimulation;
		}

		public void run()
		{
			// Returns if request packet is malformed
			String[] errorMessage = new String[1];
			if (!TFTP.verifyRequestPacket(requestPacket, errorMessage))
			{
				System.out.println("ERROR CODE 4: " + errorMessage[0] + "... Aborting transfer...\n");
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
							new ReadTransferHandler(requestPacket, errorSimulation),
							"Read Transfer Handler Thread");
					break;
				case TFTP.WRITE_OP_CODE:
					transferHandlerThread = new Thread(
							new WriteTransferHandler(requestPacket, errorSimulation),
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
		private boolean errorSimulation;
		private HashMap<String, Boolean> simulateError = new HashMap<String, Boolean>();

		public ReadTransferHandler(DatagramPacket clientRequestPacket, boolean errorSimulation)
		{
			this.clientRequestPacket = clientRequestPacket;
			this.errorSimulation = errorSimulation;
			simulateError.put("simulateUnknownTID", false);
			simulateError.put("simulateDelayedPacket", false);
			simulateError.put("simulateDuplicatePacket", false);
			simulateError.put("simulateLostPacket", false);
		}

		public void run()
		{
			try
			{
				System.out.println("ReadTransferHandler thread started.\n");
				// Socket used for sending and receiving packets from server
				DatagramSocket sendReceiveServerSocket = new DatagramSocket();

				// Socket will time out after a couple of seconds
				sendReceiveServerSocket.setSoTimeout(RETRANSMITTION_TIMEOUT);

				// Socket used for sending and receiving packets from client
				DatagramSocket sendReceiveClientSocket = new DatagramSocket();

				// Socket will time out after a couple of seconds
				sendReceiveClientSocket.setSoTimeout(RETRANSMITTION_TIMEOUT);

				boolean continueDuplicatedRequestSimulation = false;

				// Saves the client TID
				InetAddress clientAddressTID = clientRequestPacket.getAddress();
				int clientPortTID = clientRequestPacket.getPort();

				// Creates a DatagramPacket to send request to server
				DatagramPacket serverRequestPacket = TFTP.formPacket(
						sendAddr,
						SEND_PORT,
						clientRequestPacket.getData());

				if (errorSimulation)
					tamperPacket(serverRequestPacket, RECEIVED_FROM_CLIENT, simulateError);

				if (simulateError.get("simulateDelayedPacket"))
				{
					System.out.println("Expecting client to resend RRQ packet...\n");
					try
					{
						Thread.sleep(RETRANSMITTION_TIMEOUT + ADDED_DELAY);
					}
					catch (InterruptedException e)
					{
						// Do nothing
					}
					System.out.println("[ ErrorSim -> Server ]");
					TFTP.printPacket(serverRequestPacket);
					sendReceiveServerSocket.send(serverRequestPacket);

					DatagramPacket firstDataPacket = receivePacket(sendReceiveServerSocket);
					if (firstDataPacket == null)
					{
						return;
					}
					System.out.println("[ Server -> ErrorSim ]");
					TFTP.shrinkData(firstDataPacket);
					TFTP.printPacket(firstDataPacket);

					DatagramSocket unknownTIDSocket = new DatagramSocket();
					
					DatagramPacket forwardedFirstDataPacket = TFTP.formPacket(
							clientAddressTID,
							clientPortTID,
							firstDataPacket.getData());
					System.out.println("[ ErrorSim -> Client ]");
					TFTP.printPacket(forwardedFirstDataPacket);
					unknownTIDSocket.send(forwardedFirstDataPacket);

					DatagramPacket unknownTIDErrorResponsePacket = receivePacket(unknownTIDSocket);
					if (unknownTIDErrorResponsePacket == null)
					{
						return;
					}
					System.out.println("[ Client -> ErrorSim ]");
					TFTP.shrinkData(unknownTIDErrorResponsePacket);
					TFTP.printPacket(unknownTIDErrorResponsePacket);
					
					DatagramPacket forwardedUnknownTIDErrorResponsePacket = TFTP.formPacket(
							firstDataPacket.getAddress(),
							firstDataPacket.getPort(),
							unknownTIDErrorResponsePacket.getData());
					System.out.println("[ ErrorSim -> Server ]");
					TFTP.printPacket(forwardedUnknownTIDErrorResponsePacket);
					sendReceiveServerSocket.send(forwardedUnknownTIDErrorResponsePacket);
					
					unknownTIDSocket.close();
					
					simulateError.put("simulateDelayedPacket", false);
					errorSimulation = false;
					return;
				}
				else if (simulateError.get("simulateDuplicatePacket"))
				{
					// Sends request packet through socket
					System.out.println("[ ErrorSim -> Server ]");
					TFTP.printPacket(serverRequestPacket);
					sendReceiveServerSocket.send(serverRequestPacket);
					try
					{
						Thread.sleep(RETRANSMITTION_TIMEOUT/4);
					}
					catch (InterruptedException e)
					{
						// Do nothing
					}
					System.out.println("Sending duplicated packet");
					System.out.println("[ ErrorSim -> Server ]");
					TFTP.printPacket(serverRequestPacket);
					sendReceiveServerSocket.send(serverRequestPacket);
					continueDuplicatedRequestSimulation = true;

					errorSimulation = false;
					simulateError.put("simulateDuplicatePacket", false);
				}
				else if (simulateError.get("simulateLostPacket"))
				{
					System.out.println("Losing previous packet");
					errorSimulation = false;
					simulateError.put("simulateLostPacket", false);
				}
				else
				{
					// Sends request packet through socket
					System.out.println("[ ErrorSim -> Server ]");
					TFTP.printPacket(serverRequestPacket);
					sendReceiveServerSocket.send(serverRequestPacket);
				}

				// Transfer ID of the server
				InetAddress serverAddressTID = null;
				int serverPortTID = -1;

				// Flag set when transfer is finished
				boolean transferComplete = false;

				// Flag set when error packet is received
				boolean errorPacketReceived = false;

				// Flag indicating first loop iteration (for setting up TID)
				boolean firstIteration = true;
				
				try
				{
					while (!transferComplete)
					{
						// Creates a DatagramPacket to receive data packet from server
						// Receives data packet from server
						DatagramPacket dataPacket = receivePacket(sendReceiveServerSocket);
						if (dataPacket == null)
						{
							return;
						}

						// Transfer is complete if server sends back an error packet
						if (TFTP.getOpCode(dataPacket) == TFTP.ERROR_OP_CODE)
						{
							errorPacketReceived = true;
						}

						// Transfer is complete if data block is less than MAX_DATA_SIZE
						if (dataPacket.getLength() < TFTP.MAX_PACKET_SIZE)
						{
							transferComplete = true;
						}
						System.out.println("[ Server -> ErrorSim ]");
						TFTP.shrinkData(dataPacket);
						TFTP.printPacket(dataPacket);

						// Saves server TID on first iteration
						if (firstIteration)
						{
							serverAddressTID = dataPacket.getAddress();
							serverPortTID = dataPacket.getPort();
						}

						// Sends data packet to client
						DatagramPacket forwardedDataPacket = TFTP.formPacket(
								clientAddressTID,
								clientPortTID,
								dataPacket.getData());
						if (errorSimulation)
							tamperPacket(forwardedDataPacket, RECEIVED_FROM_SERVER, simulateError);

						if (simulateError.get("simulateUnknownTID"))
						{
							spawnUnknownTIDThread(forwardedDataPacket, clientAddressTID, clientPortTID);
							System.out.println("[ ErrorSim -> Client ]");
							TFTP.printPacket(forwardedDataPacket);
							sendReceiveClientSocket.send(forwardedDataPacket);
							errorSimulation = false;
							simulateError.put("simulateUnknownTID", false);
						}
						else if (simulateError.get("simulateDelayedPacket"))
						{
							if (firstIteration)
							{
								System.out.println("Expecting client to resend RRQ packet...\n");
								try
								{
									Thread.sleep(RETRANSMITTION_TIMEOUT + ADDED_DELAY);
								}
								catch (InterruptedException e)
								{
									// Do nothing
								}
								
								DatagramSocket unknownTIDSocket = new DatagramSocket();

								System.out.println("[ ErrorSim -> Client ]");
								TFTP.printPacket(forwardedDataPacket);
								unknownTIDSocket.send(forwardedDataPacket);

								DatagramPacket unknownTIDErrorResponsePacket = receivePacket(unknownTIDSocket);
								if (unknownTIDErrorResponsePacket == null)
								{
									return;
								}
								System.out.println("[ Client -> ErrorSim ]");
								TFTP.shrinkData(unknownTIDErrorResponsePacket);
								TFTP.printPacket(unknownTIDErrorResponsePacket);

								DatagramPacket forwardedUnknownTIDErrorResponsePacket = TFTP.formPacket(
										serverAddressTID,
										serverPortTID,
										unknownTIDErrorResponsePacket.getData());
								System.out.println("[ ErrorSim -> Server ]");
								TFTP.printPacket(forwardedUnknownTIDErrorResponsePacket);
								sendReceiveServerSocket.send(forwardedUnknownTIDErrorResponsePacket);

								unknownTIDSocket.close();

								simulateError.put("simulateDelayedPacket", false);
								errorSimulation = false;
								return;
							}
							else
							{
								System.out.println("Expecting server to resend latest DATA packet...\n");
								try
								{
									Thread.sleep(RETRANSMITTION_TIMEOUT/2);
								}
								catch (InterruptedException e)
								{
									// Do nothing
								}

								DatagramPacket retransmittedDataPacket = receivePacket(sendReceiveServerSocket);
								if (retransmittedDataPacket == null)
								{
									return;
								}
								System.out.println("[ Server -> ErrorSim ]");
								TFTP.shrinkData(retransmittedDataPacket);
								TFTP.printPacket(retransmittedDataPacket);

								DatagramPacket forwardedRetransmittedDataPacket = TFTP.formPacket(
										clientAddressTID,
										clientPortTID,
										retransmittedDataPacket.getData());
								System.out.println("[ ErrorSim -> Client ]");
								TFTP.shrinkData(forwardedRetransmittedDataPacket);
								TFTP.printPacket(forwardedRetransmittedDataPacket);
								sendReceiveClientSocket.send(forwardedRetransmittedDataPacket);;

								DatagramPacket ackForFirstSentPacket = receivePacket(sendReceiveClientSocket);
								if (ackForFirstSentPacket == null)
								{
									return;
								}
								System.out.println("[ Client -> ErrorSim ]");
								TFTP.shrinkData(ackForFirstSentPacket);
								TFTP.printPacket(ackForFirstSentPacket);

								DatagramPacket forwardedAckForFirstSentPacket = TFTP.formPacket(
										serverAddressTID,
										serverPortTID,
										ackForFirstSentPacket.getData());
								System.out.println("[ ErrorSim -> Server ]");
								TFTP.printPacket(forwardedAckForFirstSentPacket);
								sendReceiveServerSocket.send(forwardedAckForFirstSentPacket);

								System.out.println("Sending delayed DATA packet");
								System.out.println("[ ErrorSim -> Client ]");
								TFTP.printPacket(forwardedDataPacket);
								sendReceiveClientSocket.send(forwardedDataPacket);

								errorSimulation = false;
								simulateError.put("simulateDelayedPacket", false);
							}
						}
						else if (simulateError.get("simulateDuplicatePacket"))
						{
							System.out.println("[ ErrorSim -> Client ]");
							TFTP.printPacket(forwardedDataPacket);
							sendReceiveClientSocket.send(forwardedDataPacket);
							try
							{
								Thread.sleep(RETRANSMITTION_TIMEOUT/2);
							}
							catch (InterruptedException e)
							{
								// Do nothing
							}

							DatagramPacket ackForFirstDuplicatePacket = receivePacket(sendReceiveClientSocket);
							if (ackForFirstDuplicatePacket == null)
							{
								return;
							}
							System.out.println("[ Client -> ErrorSim ]");
							TFTP.shrinkData(ackForFirstDuplicatePacket);
							TFTP.printPacket(ackForFirstDuplicatePacket);

							DatagramPacket forwardedAckForFirstDuplicatePacket = TFTP.formPacket(
									serverAddressTID,
									serverPortTID,
									ackForFirstDuplicatePacket.getData());
							System.out.println("[ ErrorSim -> Server ]");
							TFTP.printPacket(forwardedAckForFirstDuplicatePacket);
							sendReceiveServerSocket.send(forwardedAckForFirstDuplicatePacket);

							System.out.println("Sending duplicated DATA packet");
							System.out.println("[ ErrorSim -> Client ]");
							TFTP.printPacket(forwardedDataPacket);
							sendReceiveClientSocket.send(forwardedDataPacket);

							errorSimulation = false;
							simulateError.put("simulateDuplicatePacket", false);
						}
						else if (simulateError.get("simulateLostPacket"))
						{
							System.out.println("Losing DATA packet");
							System.out.println("Expecting server to resend latest DATA packet...\n");
							try
							{
								Thread.sleep(RETRANSMITTION_TIMEOUT/2);
							}
							catch (InterruptedException e)
							{
								// Do nothing
							}
							DatagramPacket retransmittedDataPacket = receivePacket(sendReceiveServerSocket);
							if (retransmittedDataPacket == null)
							{
								return;
							}
							System.out.println("[ Server -> ErrorSim ]");
							TFTP.shrinkData(retransmittedDataPacket);
							TFTP.printPacket(retransmittedDataPacket);

							DatagramPacket forwardedRetransmittedDataPacket = TFTP.formPacket(
									clientAddressTID,
									clientPortTID,
									retransmittedDataPacket.getData());
							System.out.println("[ ErrorSim -> Client ]");
							TFTP.printPacket(forwardedRetransmittedDataPacket);
							sendReceiveClientSocket.send(forwardedRetransmittedDataPacket);

							errorSimulation = false;
							simulateError.put("simulateLostPacket", false);
						}
						else
						{
							System.out.println("[ ErrorSim -> Client ]");
							TFTP.printPacket(forwardedDataPacket);
							sendReceiveClientSocket.send(forwardedDataPacket);
						}

						firstIteration = false;

						if (continueDuplicatedRequestSimulation)
						{
							// Creates a DatagramPacket to receive duplicate data packet from server
							DatagramPacket duplicatedFirstDataPacket = receivePacket(sendReceiveServerSocket);
							if (duplicatedFirstDataPacket == null)
							{
								return;
							}
							System.out.println("[ Server -> ErrorSim ]");
							TFTP.shrinkData(duplicatedFirstDataPacket);
							TFTP.printPacket(duplicatedFirstDataPacket);

							DatagramSocket unknownTIDSocket = new DatagramSocket();

							DatagramPacket forwardedDuplicatedFirstDataPacket = TFTP.formPacket(
									clientAddressTID,
									clientPortTID,
									duplicatedFirstDataPacket.getData());
							System.out.println("[ ErrorSim -> Client ]");
							TFTP.printPacket(forwardedDuplicatedFirstDataPacket);
							unknownTIDSocket.send(forwardedDuplicatedFirstDataPacket);

							DatagramPacket unknownTIDErrorResponsePacket = receivePacket(unknownTIDSocket);
							if (unknownTIDErrorResponsePacket == null)
							{
								return;
							}
							System.out.println("[ Client -> ErrorSim ]");
							TFTP.shrinkData(unknownTIDErrorResponsePacket);
							TFTP.printPacket(unknownTIDErrorResponsePacket);

							DatagramPacket forwardedUnknownTIDErrorResponsePacket = TFTP.formPacket(
									duplicatedFirstDataPacket.getAddress(),
									duplicatedFirstDataPacket.getPort(),
									unknownTIDErrorResponsePacket.getData());
							System.out.println("[ ErrorSim -> Server ]");
							TFTP.printPacket(forwardedUnknownTIDErrorResponsePacket);
							sendReceiveServerSocket.send(forwardedUnknownTIDErrorResponsePacket);

							unknownTIDSocket.close();

							continueDuplicatedRequestSimulation = false;
						}

						// End transfer if last packet received was an error packet
						if (errorPacketReceived) {
							transferComplete = true;
							break;
						}

						// Creates a DatagramPacket to receive acknowledgement packet from client
						DatagramPacket ackPacket = receivePacket(sendReceiveClientSocket);
						if (ackPacket == null)
						{
							return;
						}
						System.out.println("[ Client -> ErrorSim ]");
						TFTP.shrinkData(ackPacket);
						TFTP.printPacket(ackPacket);

						// Sends acknowledgement packet to server
						DatagramPacket forwardedAckPacket = TFTP.formPacket(
								serverAddressTID,
								serverPortTID,
								ackPacket.getData());
						if (errorSimulation)
							tamperPacket(forwardedAckPacket, RECEIVED_FROM_CLIENT, simulateError);

						if (simulateError.get("simulateUnknownTID"))
						{
							spawnUnknownTIDThread(forwardedAckPacket, serverAddressTID, serverPortTID);
							System.out.println("[ ErrorSim -> Server ]");
							TFTP.printPacket(forwardedAckPacket);
							sendReceiveServerSocket.send(forwardedAckPacket);

							errorSimulation = false;
							simulateError.put("simulateUnknownTID", false);
						}
						else if (simulateError.get("simulateDelayedPacket"))
						{
							System.out.println("Expecting server to resend latest DATA packet...\n");
							try
							{
								Thread.sleep(RETRANSMITTION_TIMEOUT/2);
							}
							catch (InterruptedException e)
							{
								// Do nothing
							}
							
							DatagramPacket retransmittedDataPacket = receivePacket(sendReceiveServerSocket);
							if (retransmittedDataPacket == null)
							{
								return;
							}
							System.out.println("[ Server -> ErrorSim ]");
							TFTP.shrinkData(retransmittedDataPacket);
							TFTP.printPacket(retransmittedDataPacket);
							
							DatagramPacket forwardedRetransmittedDataPacket = TFTP.formPacket(
									clientAddressTID,
									clientPortTID,
									retransmittedDataPacket.getData());
							System.out.println("[ ErrorSim -> Client ]");
							TFTP.printPacket(forwardedRetransmittedDataPacket);
							sendReceiveClientSocket.send(forwardedRetransmittedDataPacket);

							DatagramPacket retransmittedAckPacket = receivePacket(sendReceiveClientSocket);
							if (retransmittedAckPacket == null)
							{
								return;
							}
							System.out.println("[ Client -> ErrorSim ]");
							TFTP.shrinkData(retransmittedAckPacket);
							TFTP.printPacket(retransmittedAckPacket);

							DatagramPacket forwardedRetransmittedAckPacket = TFTP.formPacket(
									serverAddressTID,
									serverPortTID,
									retransmittedAckPacket.getData());
							System.out.println("[ ErrorSim -> Server ]");
							TFTP.printPacket(forwardedRetransmittedAckPacket);
							sendReceiveServerSocket.send(forwardedRetransmittedAckPacket);

							System.out.println("Sending delayed ACK packet");
							System.out.println("[ ErrorSim -> Server ]");
							TFTP.printPacket(forwardedAckPacket);
							sendReceiveServerSocket.send(forwardedAckPacket);

							errorSimulation = false;
							simulateError.put("simulateDelayedPacket", false);
						}
						else if (simulateError.get("simulateDuplicatePacket"))
						{
							System.out.println("[ ErrorSim -> Server ]");
							TFTP.printPacket(forwardedAckPacket);
							sendReceiveServerSocket.send(forwardedAckPacket);
							try
							{
								Thread.sleep(RETRANSMITTION_TIMEOUT/2);
							}
							catch (InterruptedException e)
							{
								// Do nothing
							}

							System.out.println("Sending duplicated ACK packet");
							System.out.println("[ ErrorSim -> Server ]");
							TFTP.printPacket(forwardedAckPacket);
							sendReceiveServerSocket.send(forwardedAckPacket);

							errorSimulation = false;
							simulateError.put("simulateDuplicatePacket", false);
						}
						else if (simulateError.get("simulateLostPacket"))
						{
							System.out.println("Losing ACK packet");
							System.out.println("Expecting server to resend latest DATA packet...\n");
							// Wait a little bit
							try
							{
								Thread.sleep(RETRANSMITTION_TIMEOUT/2);
							}
							catch (InterruptedException e)
							{
								// Do nothing
							}
							
							// Receive retransmitted data packet from server
							DatagramPacket retransmittedDataPacket = receivePacket(sendReceiveServerSocket);
							if (retransmittedDataPacket == null)
							{
								return;
							}
							System.out.println("[ Server -> ErrorSim ]");
							TFTP.shrinkData(retransmittedDataPacket);
							TFTP.printPacket(retransmittedDataPacket);
							
							// Send retransmitted data packet to client
							DatagramPacket forwardedRetransmittedDataPacket = TFTP.formPacket(
									clientAddressTID,
									clientPortTID,
									retransmittedDataPacket.getData());
							System.out.println("[ ErrorSim -> Client ]");
							TFTP.printPacket(forwardedRetransmittedDataPacket);
							sendReceiveClientSocket.send(forwardedRetransmittedDataPacket);

							// Receive retransmitted acknowledgement packet from client
							DatagramPacket retransmittedAckPacket = receivePacket(sendReceiveClientSocket);
							if (retransmittedAckPacket == null)
							{
								return;
							}
							System.out.println("[ Client -> ErrorSim ]");
							TFTP.shrinkData(retransmittedAckPacket);
							TFTP.printPacket(retransmittedAckPacket);
							
							// Send retransmitted acknowledgement packet to server
							DatagramPacket forwardedRetransmittedAckPacket = TFTP.formPacket(
									serverAddressTID,
									serverPortTID,
									retransmittedAckPacket.getData());
							System.out.println("[ ErrorSim -> Server ]");
							TFTP.printPacket(forwardedRetransmittedAckPacket);
							sendReceiveServerSocket.send(forwardedRetransmittedAckPacket);

							errorSimulation = false;
							simulateError.put("simulateLostPacket", false);
						}
						else
						{
							System.out.println("[ ErrorSim -> Server ]");
							TFTP.printPacket(forwardedAckPacket);
							sendReceiveServerSocket.send(forwardedAckPacket);
						}

						// Transfer is complete if client sends back an error packet
						if (TFTP.getOpCode(ackPacket) == TFTP.ERROR_OP_CODE)
						{
							errorPacketReceived = true;
							transferComplete = true;
							break;
						}
					}
					System.out.println("Connection terminated.\n");
				}
				finally
				{
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
				System.out.println("ReadTransferHandler thread terminated.\n");
			}
		}
	}

	/**
	 * Thread used to handle client write transfers
	 */
	class WriteTransferHandler implements Runnable
	{
		private DatagramPacket clientRequestPacket;
		private boolean errorSimulation;
		private HashMap<String, Boolean> simulateError = new HashMap<String, Boolean>();

		public WriteTransferHandler(DatagramPacket clientRequestPacket, boolean errorSimulation)
		{
			this.clientRequestPacket = clientRequestPacket;
			this.errorSimulation = errorSimulation;
			simulateError.put("simulateUnknownTID", false);
			simulateError.put("simulateDelayedPacket", false);
			simulateError.put("simulateDuplicatePacket", false);
			simulateError.put("simulateLostPacket", false);
		}

		public void run()
		{
			try
			{
				System.out.println("WriteTransferHandler thread started.\n");
				// Socket used for sending and receiving packets from server
				DatagramSocket sendReceiveServerSocket = new DatagramSocket();

				// Socket will time out after a couple of seconds
				sendReceiveServerSocket.setSoTimeout(RETRANSMITTION_TIMEOUT);

				// Socket used for sending and receiving packets from client
				DatagramSocket sendReceiveClientSocket = new DatagramSocket();

				// Socket will time out after a couple of seconds
				sendReceiveClientSocket.setSoTimeout(RETRANSMITTION_TIMEOUT);

				// Saves the client TID
				InetAddress clientAddressTID = clientRequestPacket.getAddress();
				int clientPortTID = clientRequestPacket.getPort();

				// Flag set when error packet is received
				boolean errorPacketReceived = false;

				boolean continueDuplicatedRequestSimulation = false;
				
				// Creates a DatagramPacket to send request to server
				DatagramPacket serverRequestPacket = TFTP.formPacket(
						sendAddr,
						SEND_PORT,
						clientRequestPacket.getData());
				if (errorSimulation)
					tamperPacket(serverRequestPacket, RECEIVED_FROM_CLIENT, simulateError);

				if (simulateError.get("simulateDelayedPacket"))
				{
					System.out.println("Expecting client to resend WRQ packet...\n");
					try
					{
						Thread.sleep(RETRANSMITTION_TIMEOUT + ADDED_DELAY);
					}
					catch (InterruptedException e)
					{
						// Do nothing
					}

					System.out.println("[ ErrorSim -> Server ]");
					TFTP.printPacket(serverRequestPacket);
					sendReceiveServerSocket.send(serverRequestPacket);

					DatagramPacket firstAckPacket = receivePacket(sendReceiveServerSocket);
					if (firstAckPacket == null)
					{
						return;
					}
					System.out.println("[ Server -> ErrorSim ]");
					TFTP.shrinkData(firstAckPacket);
					TFTP.printPacket(firstAckPacket);
					
					DatagramSocket unknownTIDSocket = new DatagramSocket();
					
					DatagramPacket forwardedFirstAckPacket = TFTP.formPacket(
							clientAddressTID,
							clientPortTID,
							firstAckPacket.getData());
					System.out.println("[ ErrorSim -> Client ]");
					TFTP.printPacket(forwardedFirstAckPacket);
					unknownTIDSocket.send(forwardedFirstAckPacket);
					
					DatagramPacket unknownTIDErrorResponsePacket = receivePacket(unknownTIDSocket);
					if (unknownTIDErrorResponsePacket == null)
					{
						return;
					}
					System.out.println("[ Client -> ErrorSim ]");
					TFTP.shrinkData(unknownTIDErrorResponsePacket);
					TFTP.printPacket(unknownTIDErrorResponsePacket);

					DatagramPacket forwardedUnknownTIDErrorResponsePacket = TFTP.formPacket(
							firstAckPacket.getAddress(),
							firstAckPacket.getPort(),
							unknownTIDErrorResponsePacket.getData());
					System.out.println("[ ErrorSim -> Server ]");
					TFTP.printPacket(forwardedUnknownTIDErrorResponsePacket);
					sendReceiveServerSocket.send(forwardedUnknownTIDErrorResponsePacket);
					
					unknownTIDSocket.close();
					
					errorPacketReceived = true;
					errorSimulation = false;
					simulateError.put("simulateDelayedPacket", false);
					return;
				}
				else if (simulateError.get("simulateDuplicatePacket"))
				{
					// Sends request packet through socket
					System.out.println("[ ErrorSim -> Server ]");
					TFTP.printPacket(serverRequestPacket);
					sendReceiveServerSocket.send(serverRequestPacket);
					
					try
					{
						Thread.sleep(RETRANSMITTION_TIMEOUT/4);
					}
					catch (InterruptedException e)
					{
						// Do nothing
					}

					System.out.println("Sending duplicated WRQ packet");
					System.out.println("[ ErrorSim -> Server ]");
					TFTP.printPacket(serverRequestPacket);
					sendReceiveServerSocket.send(serverRequestPacket);
					continueDuplicatedRequestSimulation = true;

					errorSimulation = false;
					simulateError.put("simulateDuplicatePacket", false);
				}
				else if (simulateError.get("simulateLostPacket"))
				{
					errorSimulation = false;
					simulateError.put("simulateLostPacket", false);
				}
				else
				{
					// Sends request packet through socket
					System.out.println("[ ErrorSim -> Server ]");
					TFTP.printPacket(serverRequestPacket);
					sendReceiveServerSocket.send(serverRequestPacket);
				}

				// Creates a DatagramPacket to receive acknowledgement packet from server
				DatagramPacket firstAckPacket = receivePacket(sendReceiveServerSocket);
				if (firstAckPacket == null)
				{
					return;
				}
				System.out.println("[ Server -> ErrorSim ]");
				TFTP.shrinkData(firstAckPacket);
				TFTP.printPacket(firstAckPacket);

				// Transfer if server sends back an error packet
				if (TFTP.getOpCode(firstAckPacket) == TFTP.ERROR_OP_CODE)
				{
					errorPacketReceived = true;
				}

				// Saves the server TID
				InetAddress serverAddressTID = firstAckPacket.getAddress();
				int serverPortTID = firstAckPacket.getPort();
				
				// Sends acknowledgement packet to client
				DatagramPacket forwardedFirstAckPacket = TFTP.formPacket(
						clientAddressTID,
						clientPortTID,
						firstAckPacket.getData());
				if (errorSimulation)
					tamperPacket(forwardedFirstAckPacket, RECEIVED_FROM_SERVER, simulateError);

				if (simulateError.get("simulateUnknownTID"))
				{
					spawnUnknownTIDThread(forwardedFirstAckPacket, clientAddressTID, clientPortTID);
					System.out.println("[ ErrorSim -> Client ]");
					TFTP.printPacket(forwardedFirstAckPacket);
					sendReceiveClientSocket.send(forwardedFirstAckPacket);

					errorSimulation = false;
					simulateError.put("simulateUnknownTID", false);
				}
				else if (simulateError.get("simulateDelayedPacket"))
				{
					System.out.println("Expecting client to resend WRQ packet...\n");
					try
					{
						Thread.sleep(RETRANSMITTION_TIMEOUT + ADDED_DELAY);
					}
					catch (InterruptedException e)
					{
						// Do nothing
					}
					
					DatagramSocket unknownTIDSocket = new DatagramSocket();
					
					System.out.println("[ ErrorSim -> Client ]");
					TFTP.printPacket(forwardedFirstAckPacket);
					unknownTIDSocket.send(forwardedFirstAckPacket);

					DatagramPacket unknownTIDErrorResponsePacket = receivePacket(unknownTIDSocket);
					if (unknownTIDErrorResponsePacket == null)
					{
						return;
					}
					System.out.println("[ Client -> ErrorSim ]");
					TFTP.shrinkData(unknownTIDErrorResponsePacket);
					TFTP.printPacket(unknownTIDErrorResponsePacket);
					
					DatagramPacket forwardedUnknownTIDErrorResponsePacket = TFTP.formPacket(
							firstAckPacket.getAddress(),
							firstAckPacket.getPort(),
							unknownTIDErrorResponsePacket.getData());
					System.out.println("[ ErrorSim -> Server ]");
					TFTP.printPacket(forwardedUnknownTIDErrorResponsePacket);
					sendReceiveServerSocket.send(forwardedUnknownTIDErrorResponsePacket);
					
					unknownTIDSocket.close();
					
					errorPacketReceived = true;
					errorSimulation = false;
					simulateError.put("simulateDelayedPacket", false);
					return;
				}
				else if (simulateError.get("simulateDuplicatePacket"))
				{
					System.out.println("[ ErrorSim -> Client ]");
					TFTP.printPacket(forwardedFirstAckPacket);
					sendReceiveClientSocket.send(forwardedFirstAckPacket);

					System.out.println("Sending duplicated ACK packet");
					System.out.println("[ ErrorSim -> Client ]");
					TFTP.printPacket(forwardedFirstAckPacket);
					sendReceiveClientSocket.send(forwardedFirstAckPacket);

					errorSimulation = false;
					simulateError.put("simulateDuplicatePacket", false);
				}
				else if (simulateError.get("simulateLostPacket"))
				{
					System.out.println("Losing ACK packet");
					errorSimulation = false;
					simulateError.put("simulateLostPacket", false);
				}
				else
				{
					System.out.println("[ ErrorSim -> Client ]");
					TFTP.printPacket(forwardedFirstAckPacket);
					sendReceiveClientSocket.send(forwardedFirstAckPacket);
				}

				if (continueDuplicatedRequestSimulation)
				{
					// Creates a DatagramPacket to receive duplicate acknowledgement packet from server
					DatagramPacket duplicatedFirstAckPacket = receivePacket(sendReceiveServerSocket);
					if (duplicatedFirstAckPacket == null)
					{
						return;
					}
					System.out.println("[ Server -> ErrorSim ]");
					TFTP.shrinkData(duplicatedFirstAckPacket);
					TFTP.printPacket(duplicatedFirstAckPacket);

					DatagramSocket unknownTIDSocket = new DatagramSocket();

					DatagramPacket forwardedDuplicatedFirstAckPacket = TFTP.formPacket(
							clientAddressTID,
							clientPortTID,
							duplicatedFirstAckPacket.getData());
					System.out.println("[ ErrorSim -> Client ]");
					TFTP.printPacket(forwardedDuplicatedFirstAckPacket);
					unknownTIDSocket.send(forwardedDuplicatedFirstAckPacket);

					DatagramPacket unknownTIDErrorResponsePacket = receivePacket(unknownTIDSocket);
					if (unknownTIDErrorResponsePacket == null)
					{
						return;
					}
					System.out.println("[ Client -> ErrorSim ]");
					TFTP.shrinkData(unknownTIDErrorResponsePacket);
					TFTP.printPacket(unknownTIDErrorResponsePacket);

					DatagramPacket forwardedUnknownTIDErrorResponsePacket = TFTP.formPacket(
							duplicatedFirstAckPacket.getAddress(),
							duplicatedFirstAckPacket.getPort(),
							unknownTIDErrorResponsePacket.getData());
					System.out.println("[ ErrorSim -> Server ]");
					TFTP.printPacket(forwardedUnknownTIDErrorResponsePacket);
					sendReceiveServerSocket.send(forwardedUnknownTIDErrorResponsePacket);
					
					unknownTIDSocket.close();

					continueDuplicatedRequestSimulation = false;
				}

				// Flag set when transfer is finished
				boolean transferComplete = false;

				// End transfer if last packet received was an error packet
				if (errorPacketReceived)
				{
					transferComplete = true;
				}

				try
				{
					while (!transferComplete)
					{
						// Creates a DatagramPacket to receive data packet from client
						DatagramPacket dataPacket = receivePacket(sendReceiveClientSocket);
						if (dataPacket == null)
						{
							return;
						}

						// Transfer if client sends back an error packet
						if (TFTP.getOpCode(dataPacket) == TFTP.ERROR_OP_CODE)
						{
							errorPacketReceived = true;
						}

						// Transfer is complete if data block is less than MAX_DATA_SIZE
						if (dataPacket.getLength() < TFTP.MAX_PACKET_SIZE)
						{
							transferComplete = true;
						}
						System.out.println("[ Client -> ErrorSim ]");
						TFTP.shrinkData(dataPacket);
						TFTP.printPacket(dataPacket);

						// Sends data packet to server
						DatagramPacket forwardedDataPacket = TFTP.formPacket(
								serverAddressTID,
								serverPortTID,
								dataPacket.getData());
						if (errorSimulation)
							tamperPacket(forwardedDataPacket, RECEIVED_FROM_CLIENT, simulateError);

						if (simulateError.get("simulateUnknownTID"))
						{
							spawnUnknownTIDThread(forwardedDataPacket, serverAddressTID, serverPortTID);
							System.out.println("[ ErrorSim -> Server ]");
							TFTP.printPacket(forwardedDataPacket);
							sendReceiveServerSocket.send(forwardedDataPacket);

							errorSimulation = false;
							simulateError.put("simulateUnknownTID", false);
						}
						else if (simulateError.get("simulateDelayedPacket"))
						{
							System.out.println("Expecting client to resend latest DATA packet...\n");
							try
							{
								Thread.sleep(RETRANSMITTION_TIMEOUT/2);
							}
							catch (InterruptedException e)
							{
								// Do nothing
							}

							DatagramPacket retransmittedDataPacket = receivePacket(sendReceiveClientSocket);
							if (retransmittedDataPacket == null)
							{
								return;
							}
							System.out.println("[ Client -> ErrorSim ]");
							TFTP.shrinkData(retransmittedDataPacket);
							TFTP.printPacket(retransmittedDataPacket);

							DatagramPacket forwardedRetransmittedDataPacket = TFTP.formPacket(
									serverAddressTID,
									serverPortTID,
									retransmittedDataPacket.getData());
							System.out.println("[ ErrorSim -> Server ]");
							TFTP.shrinkData(forwardedRetransmittedDataPacket);
							TFTP.printPacket(forwardedRetransmittedDataPacket);
							sendReceiveServerSocket.send(forwardedRetransmittedDataPacket);;

							DatagramPacket ackForFirstSentPacket = receivePacket(sendReceiveServerSocket);
							if (ackForFirstSentPacket == null)
							{
								return;
							}
							System.out.println("[ Server -> ErrorSim ]");
							TFTP.shrinkData(ackForFirstSentPacket);
							TFTP.printPacket(ackForFirstSentPacket);

							DatagramPacket forwardedAckForFirstSentPacket = TFTP.formPacket(
									clientAddressTID,
									clientPortTID,
									ackForFirstSentPacket.getData());
							System.out.println("[ ErrorSim -> Client ]");
							TFTP.printPacket(forwardedAckForFirstSentPacket);
							sendReceiveClientSocket.send(forwardedAckForFirstSentPacket);

							System.out.println("[ ErrorSim -> Server ]");
							TFTP.printPacket(forwardedDataPacket);
							sendReceiveServerSocket.send(forwardedDataPacket);

							errorSimulation = false;
							simulateError.put("simulateDelayedPacket", false);
						}
						else if (simulateError.get("simulateDuplicatePacket"))
						{
							System.out.println("[ ErrorSim -> Server ]");
							TFTP.printPacket(forwardedDataPacket);
							sendReceiveServerSocket.send(forwardedDataPacket);
							try
							{
								Thread.sleep(RETRANSMITTION_TIMEOUT/2);
							}
							catch (InterruptedException e)
							{
								// Do nothing
							}
							System.out.println("Sending duplicated DATA packet");
							System.out.println("[ ErrorSim -> Server ]");
							TFTP.printPacket(forwardedDataPacket);
							sendReceiveServerSocket.send(forwardedDataPacket);

							DatagramPacket ackForFirstDuplicatePacket = receivePacket(sendReceiveServerSocket);
							if (ackForFirstDuplicatePacket == null)
							{
								return;
							}
							System.out.println("[ Server -> ErrorSim ]");
							TFTP.shrinkData(ackForFirstDuplicatePacket);
							TFTP.printPacket(ackForFirstDuplicatePacket);

							DatagramPacket forwardedAckForFirstDuplicatePacket = TFTP.formPacket(
									clientAddressTID,
									clientPortTID,
									ackForFirstDuplicatePacket.getData());
							System.out.println("[ ErrorSim -> Client ]");
							TFTP.printPacket(forwardedAckForFirstDuplicatePacket);
							sendReceiveClientSocket.send(forwardedAckForFirstDuplicatePacket);

							errorSimulation = false;
							simulateError.put("simulateDuplicatePacket", false);
						}
						else if (simulateError.get("simulateLostPacket"))
						{
							System.out.println("Losing DATA packet");
							System.out.println("Expecting client to resend latest DATA packet...\n");
							try
							{
								Thread.sleep(RETRANSMITTION_TIMEOUT/2);
							}
							catch (InterruptedException e)
							{
								// Do nothing
							}
							DatagramPacket retransmittedDataPacket = receivePacket(sendReceiveClientSocket);
							if (retransmittedDataPacket == null)
							{
								return;
							}
							System.out.println("[ Client -> ErrorSim ]");
							TFTP.shrinkData(retransmittedDataPacket);
							TFTP.printPacket(retransmittedDataPacket);

							DatagramPacket forwardedRetransmittedDataPacket = TFTP.formPacket(
									serverAddressTID,
									serverPortTID,
									retransmittedDataPacket.getData());
							System.out.println("[ ErrorSim -> Server ]");
							TFTP.printPacket(forwardedRetransmittedDataPacket);
							sendReceiveServerSocket.send(forwardedRetransmittedDataPacket);

							errorSimulation = false;
							simulateError.put("simulateLostPacket", false);
						}
						else
						{
							System.out.println("[ ErrorSim -> Server ]");
							TFTP.printPacket(forwardedDataPacket);
							sendReceiveServerSocket.send(forwardedDataPacket);
						}
						
						// End transfer if last packet received was an error packet
						if (errorPacketReceived)
						{
							transferComplete = true;
							break;
						}

						// Creates a DatagramPacket to receive acknowledgement packet from server
						// Receives acknowledgement packet from server
						DatagramPacket ackPacket = receivePacket(sendReceiveServerSocket);
						if (ackPacket == null)
						{
							return;
						}
						System.out.println("[ Server -> ErrorSim ]");
						TFTP.shrinkData(ackPacket);
						TFTP.printPacket(ackPacket);

						// Sends acknowledgement packet to client
						DatagramPacket forwardedAckPacket = TFTP.formPacket(
								clientAddressTID,
								clientPortTID,
								ackPacket.getData());
						if (errorSimulation)
							tamperPacket(forwardedAckPacket, RECEIVED_FROM_SERVER, simulateError);

						if (simulateError.get("simulateUnknownTID"))
						{
							spawnUnknownTIDThread(forwardedAckPacket, clientAddressTID, clientPortTID);
							System.out.println("[ ErrorSim -> Client ]");
							TFTP.printPacket(forwardedAckPacket);
							sendReceiveClientSocket.send(forwardedAckPacket);

							errorSimulation = false;
							simulateError.put("simulateUnknownTID", false);
						}
						else if (simulateError.get("simulateDelayedPacket"))
						{
							System.out.println("Expecting client to resend latest DATA packet...\n");
							try
							{
								Thread.sleep(RETRANSMITTION_TIMEOUT/2);
							}
							catch (InterruptedException e)
							{
								// Do nothing
							}
							
							DatagramPacket retransmittedDataPacket = receivePacket(sendReceiveClientSocket);
							if (retransmittedDataPacket == null)
							{
								return;
							}
							System.out.println("[ Client -> ErrorSim ]");
							TFTP.shrinkData(retransmittedDataPacket);
							TFTP.printPacket(retransmittedDataPacket);
							
							DatagramPacket forwardedRetransmittedDataPacket = TFTP.formPacket(
									serverAddressTID,
									serverPortTID,
									retransmittedDataPacket.getData());
							System.out.println("[ ErrorSim -> Server ]");
							TFTP.printPacket(forwardedRetransmittedDataPacket);
							sendReceiveServerSocket.send(forwardedRetransmittedDataPacket);

							DatagramPacket retransmittedAckPacket = receivePacket(sendReceiveServerSocket);
							if (retransmittedAckPacket == null)
							{
								return;
							}
							System.out.println("[ Server -> ErrorSim ]");
							TFTP.shrinkData(retransmittedAckPacket);
							TFTP.printPacket(retransmittedAckPacket);

							DatagramPacket forwardedRetransmittedAckPacket = TFTP.formPacket(
									clientAddressTID,
									clientPortTID,
									retransmittedAckPacket.getData());
							System.out.println("[ ErrorSim -> Client ]");
							TFTP.printPacket(forwardedRetransmittedAckPacket);
							sendReceiveClientSocket.send(forwardedRetransmittedAckPacket);

							System.out.println("Sending delayed ACK packet");
							System.out.println("[ ErrorSim -> Client ]");
							TFTP.printPacket(forwardedAckPacket);
							sendReceiveClientSocket.send(forwardedAckPacket);

							errorSimulation = false;
							simulateError.put("simulateDelayedPacket", false);
						}
						else if (simulateError.get("simulateDuplicatePacket"))
						{
							System.out.println("[ ErrorSim -> Client ]");
							TFTP.printPacket(forwardedAckPacket);
							sendReceiveClientSocket.send(forwardedAckPacket);
							try
							{
								Thread.sleep(RETRANSMITTION_TIMEOUT/2);
							}
							catch (InterruptedException e)
							{
								// Do nothing
							}
							System.out.println("Sending duplicated ACK packet");
							System.out.println("[ ErrorSim -> Client ]");
							TFTP.printPacket(forwardedAckPacket);
							sendReceiveClientSocket.send(forwardedAckPacket);

							errorSimulation = false;
							simulateError.put("simulateDuplicatePacket", false);
						}
						else if (simulateError.get("simulateLostPacket"))
						{
							System.out.println("Losing ACK packet");
							System.out.println("Expecting client to resend latest DATA packet...\n");
							try
							{
								Thread.sleep(RETRANSMITTION_TIMEOUT/2);
							}
							catch (InterruptedException e)
							{
								// Do nothing
							}
							// Receive retransmitted data packet from client
							DatagramPacket retransmittedDataPacket = receivePacket(sendReceiveClientSocket);
							if (retransmittedDataPacket == null)
							{
								return;
							}
							System.out.println("[ Client -> ErrorSim ]");
							TFTP.shrinkData(retransmittedDataPacket);
							TFTP.printPacket(retransmittedDataPacket);
							
							// Send retransmitted data packet to server
							DatagramPacket forwardedRetransmittedDataPacket = TFTP.formPacket(
									serverAddressTID,
									serverPortTID,
									retransmittedDataPacket.getData());
							System.out.println("[ ErrorSim -> Server ]");
							TFTP.printPacket(forwardedRetransmittedDataPacket);
							sendReceiveServerSocket.send(forwardedRetransmittedDataPacket);

							// Receive retransmitted acknowledgement packet from server
							DatagramPacket retransmittedAckPacket = receivePacket(sendReceiveServerSocket);
							if (retransmittedAckPacket == null)
							{
								return;
							}
							System.out.println("[ Server -> ErrorSim ]");
							TFTP.shrinkData(retransmittedAckPacket);
							TFTP.printPacket(retransmittedAckPacket);
							
							// Send retransmitted acknowledgement packet to client
							DatagramPacket forwardedRetransmittedAckPacket = TFTP.formPacket(
									clientAddressTID,
									clientPortTID,
									retransmittedAckPacket.getData());
							System.out.println("[ ErrorSim -> Client ]");
							TFTP.printPacket(forwardedRetransmittedAckPacket);
							sendReceiveClientSocket.send(forwardedRetransmittedAckPacket);

							errorSimulation = false;
							simulateError.put("simulateLostPacket", false);
						}
						else
						{
							System.out.println("[ ErrorSim -> Client ]");
							TFTP.printPacket(forwardedAckPacket);
							sendReceiveClientSocket.send(forwardedAckPacket);
						}
						
						// Transfer is complete if server sends back an error packet
						if (TFTP.getOpCode(ackPacket) == TFTP.ERROR_OP_CODE)
						{
							transferComplete = true;
							errorPacketReceived = true;
							break;
						}
					}
					System.out.println("Connection terminated.\n");
				}
				finally
				{
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
				System.out.println("WriteTransferHandler thread terminated.\n");
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
				System.out.println("UnknownTIDTransferHandler thread started.\n");
				// New socket with a different TID than the currently ongoing transfer
				DatagramSocket socket = new DatagramSocket();

				// Sends the packet to the host using this new TID
				socket.send(packet);
				
				// Error packet that is expected from the host
				DatagramPacket errorPacket = TFTP.formPacket();
				
				boolean unexpectedPacket;
				
				// Continue to receive packets until the correct packet is received
				do
				{
					unexpectedPacket = false;

					// Receives invalid TID error packet
					socket.receive(errorPacket);
					TFTP.shrinkData(errorPacket);
					TFTP.printPacket(errorPacket);
					
					// Check if the address and port of the received packet match the TID
					InetAddress packetAddress = errorPacket.getAddress();
					int packetPort = errorPacket.getPort();
					String[] errorMessage = new String[1];
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
				System.out.println("UnknownTIDTransferHandler thread terminated.\n");
			}
		}
	}
}
