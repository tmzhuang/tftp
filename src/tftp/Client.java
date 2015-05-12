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

	//public void sendWriteRequest(InetAddress addr, String filename)
	//{
	//int TID, blockCount = 1;
	//// Issue a Read request from client to server
	//send(addr, SEND_PORT, new Request(Request.Type.WRITE, filename, "netascii"));
	//// Form ACK packet to receive from server
	//byte[] data = new byte[4];
	//DatagramPacket packet = new DatagramPacket(data, data.length);
	//try {
	//// Receiving ACK 0
	//if (verbose) System.out.println("Waiting for server...");
	//sendReceiveSocket.receive(packet);
	//// If condition here determines if server respond with ACK 0 or not
	//// If yes, then start the file transfer from client to server
	//if(TFTP.getOpCode(packet) == TFTP.ACK_OP_CODE
	//&& TFTP.getBlockNumber(packet) == 0) {
	//TID = packet.getPort();
	//// Generate a queue of data packets waiting to be transferred
	//System.out.println("forming data queue");
	//Queue<DatagramPacket> packetQueue = TFTP.formDATAPackets(addr, TID, filename);
	//while(!packetQueue.isEmpty()) {
	//// Extract data packet from queue and sent it to server --> DATA 1...n
	//DatagramPacket dataPacket = packetQueue.remove();
	//sendReceiveSocket.send(dataPacket);
	//// Form an acknowledgement packet for receiving respond from server
	//byte[] ack = new byte[4];
	//DatagramPacket ackPacket = new DatagramPacket(ack, ack.length);
	//// Receive server response for acknowledgement --> ACK 1...n
	//sendReceiveSocket.receive(ackPacket);
	//int blockNumber = Byte.toUnsignedInt(ackPacket.getData()[0]);	// Or use Brandon's version of Byte-Int conversion
	//if(blockNumber == blockCount)
	//blockCount++;
	//else
	//throw new Exception();
	//}
	//System.out.println("Client write request complete.");
	//}
	//// If ACK 0 never arrives when WRQ is issued, then error must have occurred
	//else
	//throw new Exception();
	//} catch(Exception e) {
	//e.printStackTrace();
	//System.exit(1);
	//}
	//}


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
				System.out.println();
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
		//try {
			//client.read(InetAddress.getLocalHost(), "a.txt", "netascii");
		//} catch(Exception e) {
		//}
	}
}
