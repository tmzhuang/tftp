package tftp;

import java.io.*;
import java.net.*;
import java.util.*;

public class Server implements Exitable {
	private static int RECEIVE_PORT = 69;
	private static int BUF_SIZE = 100; // Default buffer size for packet data
	private static byte TFTP_PADDING = 0; // Padding used in TFTP protocol
	private boolean verbose = true;
	private boolean running = true;

	// Constructor
	public Server() {
	}

	public void run() {
		// Start repl for quitting client
		(new Thread(new Repl(this))).start();
		(new Thread(new Listener())).start();
	}

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
				try {
					socket.receive(receivePacket);

				// Throw exception if wrong OP code
				if (TFTP.getOpCode(receivePacket) != TFTP.DATA_OP_CODE)
					throw new Exception("Expected DATA packet but a non-DATA packet was received.");

				// Throw exception if unexpected block number
				if (TFTP.getBlockNumber(receivePacket) != currentBlockNumber)
					throw new Exception("DATA packet received has an unexpected block number.");
				} catch(Exception e) {
					e.printStackTrace();
					System.exit(1);
				}


				// Write the data packet to file
				TFTP.writeDATAToFile(receivePacket, r.getFilename());

			} while (TFTP.getData(receivePacket).length == TFTP.MAX_DATA_SIZE);
		}
	}

	private class Listener implements Runnable {
		private DatagramSocket receiveSocket;

		public Listener() {
			try {
				receiveSocket = new DatagramSocket(RECEIVE_PORT);
			} catch(Exception se) {
				se.printStackTrace();
				System.exit(1);
			}
		}

		/**
		* Wait for packet from server. when received, a response packet
		* is sent back to the sender if the received packet is valid. otherwise
		* an exception is thrown and the server quits.
		*
		*/
		public void run() {
			while (running) {
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

			receiveSocket.close();
		}
	}

	public void exit() {
		this.running = false;
	}

	public static void main (String[] args) {
		// Listen on TFPT known port (69)
		Server server = new Server();
	}
}
