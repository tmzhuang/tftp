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
			System.out.println(r.getType() + " request for file \"" + r.getFilename() + "\".");

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
			if (verbose) System.out.println("Forming packet queue from file...");
			Queue<DatagramPacket> dataPacketQueue = TFTP.formDATAPackets(replyAddr, TID, filename);
			if (verbose) System.out.println("Packets formed. Ready to send " + dataPacketQueue.size() + " blocks.");

			// Send each packet and wait for an ACK until queue is empty
			while (!dataPacketQueue.isEmpty()) {
				// Send a packet
				DatagramPacket currentPacket = dataPacketQueue.remove();
				int currentBlockNumber = TFTP.getBlockNumber(currentPacket);
				try {
					if (verbose) System.out.println("Sending DATA block number " + currentBlockNumber + ".");
					if (verbose) System.out.println("Block size is" + TFTP.getData(currentPacket).length + ".");
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
					if (verbose) System.out.println("Waiting for ACK" + currentBlockNumber + "...");
					socket.receive(receivePacket);

					// Throw exception if sender is invalid
					System.out.println("Expected reply address is " + replyAddr.getHostAddress() + ":" + TID + ".");
					System.out.println("Received from " + receivePacket.getAddress() + ":" + receivePacket.getPort() + ".");
					if (!receivePacket.getAddress().equals(replyAddr)) System.out.println("Wrong address.");
					if (receivePacket.getPort() != TID) System.out.println("Wrong port.");
					if (!receivePacket.getAddress().equals(replyAddr) || receivePacket.getPort() != TID) 
						throw new Exception("Packet recevied from invalid sender.");

					// Throw exception if wrong OP code
					if (TFTP.getOpCode(receivePacket) != TFTP.ACK_OP_CODE)
						throw new Exception("Expected ACK packet but a non-ACK packet was received.");

					// Throw exception if DATA and ACK block numbers don't match
					if (TFTP.getBlockNumber(receivePacket) != currentBlockNumber)
						throw new Exception("ACK packet received does not match block number of DATA sent.");

					if (verbose) System.out.println("ACK" + currentBlockNumber + " received.");
				} catch(Exception e) {
					System.out.println(e.getMessage());
				}
			}
			System.out.println("End of file transfer.\n");
		}

		/**
		 * Handle write requests
		 *
		 * @param r Request type, filename, and mode
		 */
		private void handleWrite(Request r) {
			try {
				int maxPacketLen = TFTP.OP_CODE_SIZE + TFTP.BLOCK_NUMBER_SIZE + TFTP.MAX_DATA_SIZE;
				int currentBlockNumber = 1;
				DatagramPacket receivePacket;
				byte[] fileBytes = new byte[0];

				// Form and send ACK0
				if (verbose) System.out.println("Sending ACK0.");
				DatagramPacket ackPacket = TFTP.formACKPacket(replyAddr, TID, 0);
				socket.send(ackPacket);

				do {
					// Wait for a DATA packet
					if (verbose) System.out.println("Waiting for DATA" + currentBlockNumber + "...");
					byte[] buf = new byte[maxPacketLen];
					if (verbose) System.out.println("DATA" + currentBlockNumber + "received.");
					receivePacket = new DatagramPacket(buf,buf.length);
					socket.receive(receivePacket);

					// Throw exception if wrong OP code
					if (TFTP.getOpCode(receivePacket) != TFTP.DATA_OP_CODE)
						throw new Exception("Expected DATA packet but a non-DATA packet was received.");

					// Throw exception if unexpected block number
					if (TFTP.getBlockNumber(receivePacket) != currentBlockNumber)
						throw new Exception("DATA packet received has an unexpected block number.");

					// Write the data packet to file
					fileBytes = TFTP.appendData(receivePacket, fileBytes);

					// Form a ACK packet to respond with
					if (verbose) System.out.println("Sending ACK" + currentBlockNumber + ".");
					ackPacket = TFTP.formACKPacket(replyAddr, TID, currentBlockNumber);
					socket.send(ackPacket);
					currentBlockNumber++;
				} while (TFTP.getData(receivePacket).length == TFTP.MAX_DATA_SIZE);
				// Write data to file
				TFTP.writeBytesToFile("tmp/" + r.getFilename(), fileBytes);
				if (verbose) System.out.println("Write complete.");
			} catch(Exception e) {
				System.out.println(e.getMessage());
			}
		}
	}

	private class Listener implements Runnable {
		private DatagramSocket receiveSocket;

		public Listener() {
			System.out.println("Creating new listener.");
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
					if (verbose) System.out.println("Waiting for request from client...");
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
		server.run();
	}
}
