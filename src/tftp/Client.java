package tftp;

import java.io.*;
import java.net.*;
import java.util.*;

public class Client implements Exitable {
	private DatagramSocket sendReceiveSocket;
	private boolean verbose = true;
	//private static int SEND_PORT = 4;
	private static int SEND_PORT = 69;
	private static int BUF_SIZE = 100;
	private static byte TFPT_PADDING = 0;
	private InetAddress replyAddr;
	private int TID;

	public Client() {
		// Start repl for quitting client
		Thread repl = new Thread(new Repl(this));
		repl.start();

		// Create data socket for communicating with server
		try {
			sendReceiveSocket = new DatagramSocket();
		} catch(Exception se) {
			se.printStackTrace();
			System.exit(1);
		}
	}

	public void write(InetAddress addr, String filename, String mode) {
		// Make request packet and send
		if (verbose) System.out.println("Sending WRITE request");
		Request r = new Request(Request.Type.WRITE, filename, mode);
		DatagramPacket requestPacket = TFTP.formRQPacket(addr, SEND_PORT, r);
		try {
			sendReceiveSocket.send(requestPacket);
		} catch(Exception e) {
			e.printStackTrace();
			System.exit(1);
		}

		// Wait for ACK0
		try {
			// ACK should be set size
			int bufferSize = TFTP.OP_CODE_SIZE + TFTP.BLOCK_NUMBER_SIZE;
			byte[] buf = new byte[bufferSize];
			// Get a packet from server
			DatagramPacket receivePacket = new DatagramPacket(buf,buf.length);
			if (verbose) System.out.println("Waiting for ACK0...");
			sendReceiveSocket.receive(receivePacket);

			// Throw exception if wrong OP code
			if (TFTP.getOpCode(receivePacket) != TFTP.ACK_OP_CODE)
				throw new Exception("Expected ACK packet but a non-ACK packet was received.");

			// Throw exception if DATA and ACK block numbers don't match
			if (TFTP.getBlockNumber(receivePacket) != 0)
				throw new Exception("ACK packet received does not match block number of DATA sent.");

			if (verbose) System.out.println("ACK0 received.");
			this.replyAddr = receivePacket.getAddress();
			this.TID = receivePacket.getPort();
		} catch(Exception e) {
			System.out.println(e.getMessage());
		}

		// Covert file into queue of datagram packets
		if (verbose) System.out.println("Forming packet queue from file...");
		Queue<DatagramPacket> dataPacketQueue = TFTP.formDATAPackets(replyAddr, TID, filename);
		if (verbose) System.out.println("Packets formed. Ready to send " + dataPacketQueue.size() + " blocks.");

		// Send each packet and wait for an ACK until queue is empty
		while (!dataPacketQueue.isEmpty()) {
			// Send a packet
			DatagramPacket currentPacket = dataPacketQueue.remove();
			int currentBlockNumber = TFTP.getBlockNumber(currentPacket);
			try {
				if (verbose) System.out.println("Sending DATA" + currentBlockNumber + ".");
				//if (verbose) System.out.println("Block size is" + TFTP.getData(currentPacket).length + ".");
				sendReceiveSocket.send(currentPacket);
			} catch(Exception e) {
			}

			// Wait for ACK
			try {
				// ACK should be set size
				int bufferSize = TFTP.OP_CODE_SIZE + TFTP.BLOCK_NUMBER_SIZE;
				byte[] buf = new byte[bufferSize];
				// Get a packet from server
				DatagramPacket receivePacket = new DatagramPacket(buf,buf.length);
				if (verbose) System.out.println("Waiting for ACK" + currentBlockNumber + "...");
				sendReceiveSocket.receive(receivePacket);

				// Throw exception if sender is invalid
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
	public void read(InetAddress addr, String filename, String mode) {
		try {
			// Form request and send to server
			if (verbose) System.out.println("Sending a READ request to server for file \"" + filename + "\".");
			Request r = new Request(Request.Type.READ,filename,mode);
			DatagramPacket requestPacket = TFTP.formRQPacket(addr, SEND_PORT, r);
			DatagramPacket dataPacket;
			// Send the request
			sendReceiveSocket.send(requestPacket);

			int currentBlockNumber = 1;
			byte[] fileBytes = new byte[0];
			do {
				// Make packet to receive DATA
				int bufferSize = TFTP.OP_CODE_SIZE + TFTP.BLOCK_NUMBER_SIZE + TFTP.MAX_DATA_SIZE;
				byte[] buf = new byte[bufferSize];
				dataPacket = new DatagramPacket(buf, buf.length);

				// Wait for DATA from server
				if (verbose) System.out.println("Waiting for DATA packet from server...");
				sendReceiveSocket.receive(dataPacket);

				// Throw exception if wrong OP code
				if (TFTP.getOpCode(dataPacket) != TFTP.DATA_OP_CODE)
					throw new Exception("Expected DATA packet but a non-DATA packet was received.");

				// Throw exception if unexpected block number
				if (TFTP.getBlockNumber(dataPacket) != currentBlockNumber)
					throw new Exception("DATA packet received has an unexpected block number.");

				// If this is the first DATA packet received, record the address and port
				if (TFTP.getBlockNumber(dataPacket) == 1) {
					this.replyAddr = dataPacket.getAddress();
					this.TID = dataPacket.getPort();
				}

				if (verbose) System.out.println("DATA" + TFTP.getBlockNumber(dataPacket) + " received.");
				if (verbose) System.out.println("The size of the data was " + TFTP.getData(dataPacket).length + ".");

				// Write data to file
				if (verbose) System.out.println("Appending current block to filebytes.");
				fileBytes = TFTP.appendData(dataPacket, fileBytes);

				// Form a ACK packet to respond with
				DatagramPacket ackPacket = TFTP.formACKPacket(replyAddr, TID, currentBlockNumber);
				if (verbose) System.out.println("ACK " + currentBlockNumber + " sent.");
				sendReceiveSocket.send(ackPacket);
				currentBlockNumber++;

				// Newline
				if (verbose) System.out.println();
			} while (TFTP.getData(dataPacket).length == TFTP.MAX_DATA_SIZE);
			if (verbose) System.out.println("Writing bytes to file...");
			TFTP.writeBytesToFile("tmp/" + filename, fileBytes);
			if (verbose) System.out.println("Read complete.");
		}
		catch(Exception e) {
			e.printStackTrace();
			System.exit(1);
		}
	}

	// Makes a safe exit for client
	public void exit() {
		sendReceiveSocket.close();
	}

	public static void main (String[] args) {
		Client client = new Client();
		try {
			//client.read(InetAddress.getLocalHost(), "a.txt", "netascii");
			client.write(InetAddress.getLocalHost(), "a.txt", "netascii");
		} catch(Exception e) {
		}
	}
}
