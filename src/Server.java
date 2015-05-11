package tftp;

import java.io.*;
import java.net.*;
import java.util.*;

public class Server implements Exitable {
	private DatagramSocket receiveSocket;
	private static int RECEIVE_PORT = 69;
	private static int BUF_SIZE = 100; // Default buffer size for packet data
	private static byte TFTP_PADDING = 0; // Padding used in TFTP protocol
	private boolean verbose = true;

	private class ClientHandler implements Runnable {
		private InetAddress replyAddr;
		private int TID;
		private DatagramPacket initialPacket;
		private DatagramSocket socket;

		public ClientHandler(DatagramPacket packet) {
			this.replyAddr = packet.getAddress();
			this.TID = packet.getPort();
			this.initialPacket = packet;
			try {
				this.socket = new DatagramSocket();
			} catch(Exception e) {
				e.printStackTrace();
				System.exit(1);
			}
		}

		public void run() {
			Request r = TFTP.parseRQ(initialPacket);

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
		 * Handle a read request.
		 *
		 * @param r Request type, filename and mode
		 */
		private void handleRead(Request r) {
			String filename = r.getFilename();
			Queue<DatagramPacket> dataPacketQueue = TFTP.formDATAPackets(replyAddr, TID, filename);

			// Send each packet and wait for an ACK until queue is empty
			while (!dataPacketQueue.isEmpty()) {
				// Send a packet
				DatagramPacket currentPacket = dataPacketQueue.remove();
				int currentBlockNumber = TFTP.getBlockNumber(currentPacket);
				try {
					if (verbose) System.out.println("Sending DATA block number " + currentBlockNumber);
					socket.send(currentPacket);
				} catch(Exception e) {
				}

				// Wait for ACK
				try {
					// ACK should be set size
					int bufferSize = TFTP.OP_CODE_SIZE + TFTP.BLOCK_NUMBER_SIZE;
					byte[] buf = new byte[bufferSize];
					// Get a packet from client
					DatagramPacket receivePacket = new DatagramPacket(buf,buf.length);
					socket.receive(receivePacket);

					// Throw exception if sender is invalid
					if (receivePacket.getAddress() != replyAddr ||
							receivePacket.getPort() != TID) 
						throw new Exception("Packet recevied from invalid sender.");

					// Throw exception if wrong OP code
					if (TFTP.getOpCode(receivePacket) != TFTP.ACK_OP_CODE)
						throw new Exception("Expected ACK packet but a non-ACK packet was received.");

					// Throw exception if DATA and ACK block numbers don't match
					if (TFTP.getBlockNumber(receivePacket) != currentBlockNumber)
						throw new Exception("ACK packet received does not match block number of DATA sent.");

					if (verbose) System.out.println("ACK packet received for block number " + currentBlockNumber);
				} catch(Exception e) {
				}
			}
		}

		/**
		 * Handle write requests
		 *
		 * @param r Request type, filename, and mode
		 */
		private void handleWrite(Request r) {
			int maxPacketLen = TFTP.OP_CODE_SIZE + TFTP.BLOCK_NUMBER_SIZE + TFTP.MAX_DATA_SIZE;
			int currentBlockNumber = 0;
			DatagramPacket receivePacket;

			do {
				// Form a ACK packet to respond with
				DatagramPacket sendPacket = TFTP.formACKPacket(replyAddr, TID, currentBlockNumber);
				currentBlockNumber++;

				// Wait for a DATA packet
				byte[] buf = new byte[maxPacketLen];
				receivePacket = new DatagramPacket(buf,buf.length);
				socket.receive(receivePacket);

				// Throw exception if wrong OP code
				if (TFTP.getOpCode(receivePacket) != TFTP.DATA_OP_CODE)
					throw new Exception("Expected DATA packet but a non-DATA packet was received.");

				// Throw exception if unexpected block number
				if (TFTP.getBlockNumber(receivePacket) != currentBlockNumber)
					throw new Exception("DATA packet received has an unexpected block number.");

				// Write the data packet to file
				TFTP.writeDATAToFile(receivePacket, r.getFilename());

			} while (TFTP.getData(receivePacket).length == TFTP.MAX_DATA_SIZE);
		}
	}

	// Constructor
	public Server() {
		// Start repl for quitting client
		Thread repl = new Thread(new Repl(this));
		repl.start();

		try {
			receiveSocket = new DatagramSocket(RECEIVE_PORT);
		} catch(Exception se) {
			se.printStackTrace();
			System.exit(1);
		}
	}

	// Wait for packet from server. When received, a response packet
	// is sent back to the sender if the received packet is valid. Otherwise
	// an exception is thrown and the server quits.
	public void listen() {
		// Form packet for reception
		byte[] buf = new byte[BUF_SIZE];
		DatagramPacket packet = new DatagramPacket(buf, buf.length);

		// Receive packet
		try {
			if (verbose) System.out.println("Waiting for client...");
			receiveSocket.receive(packet);
			System.arraycopy(buf,0,buf,0,packet.getLength());
			// Truncate data to the length received
			byte[] data = new byte[packet.getLength()];
			System.arraycopy(buf,0,data,0,packet.getLength());
			packet.setData(data);
			if (verbose) System.out.println("Packet received.");
		} catch(Exception e) {
			e.printStackTrace();
			System.exit(1);
		}

		// Start a handler to connect with client
		(new Thread(new ClientHandler(packet))).start();
	}

	// Send a response packet to addr:port with Request r.
	// [0,3,0,1] to acknowledge a READ request, and [0,4,0,0] to
	// acknowledge a WRITE request.
	public void respond(InetAddress addr, int port, Request r) {
		// Form the response packet
		byte[] data;
		switch (r.getType()) {
			case READ:
				data = new byte[] {0,3,0,1};
				break;
			case WRITE:
				data = new byte[] {0,4,0,0};
				break;
			default:
				data = new byte[] {-1,-1,-1,-1};
				break;
		}
		DatagramPacket packet = new DatagramPacket(data,data.length,addr,port);

		// Send the formed packet
		try {
			DatagramSocket socket = new DatagramSocket();
			if (verbose) System.out.println("Sending response to client...");
			socket.send(packet);
			if (verbose) System.out.println("Byte data sent: " + Arrays.toString(packet.getData()));
			if (verbose) System.out.println();
				
			// Done with socket, close it
			socket.close();
		} catch(Exception e) {
			e.printStackTrace();
			System.exit(1);
		}

	}

	public void exit() {
		receiveSocket.close();
	}

	public static void main (String[] args) {
		// Listen on TFPT known port (69)
		Server server = new Server();
		while (true) {
			server.listen();
		}
		//try {
		//Thread.sleep(5000);
		//} catch (InterruptedException e ) {
		//e.printStackTrace();
		//System.exit(1);
		//}
	}
}
