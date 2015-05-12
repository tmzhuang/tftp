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

	// Given a request type, a filename, and a mode,
	// forms a DatagramPacket and sends it to socket bound
	// to port SEND_PORT
	public void send(InetAddress addr, int port, Request r) {
		// Form packet
		DatagramPacket packet = TFTP.formRQPacket(addr, port, r);

		// Send the packet 
		try {
			if (verbose) {
				System.out.print("Sending packet to ");
				System.out.println(addr.getHostAddress() + ":" + port);
				System.out.print("Packet string: ");
				System.out.println(new String(packet.getData()));
				System.out.print("Packet bytes: ");
				System.out.println(Arrays.toString(packet.getData()));
				System.out.println();
			}
			sendReceiveSocket.send(packet);
		} catch(Exception e) {
			e.printStackTrace();
			System.exit(1);
		}

		// Wait for response from server
		this.listen();
	}

	// Sends Request r to LOCAL_HOST:SEND_PORT.
	public void send(Request r) {
		try {
			this.send(InetAddress.getLocalHost(), SEND_PORT, r);
		} catch(Exception e) {
			System.exit(1);
		}
	}
	
	public void sendWriteRequest(InetAddress addr, String filename)
	{
		int TID, blockCount = 1;
		// Issue a Read request from client to server
		send(addr, SEND_PORT, new Request(Request.Type.WRITE, filename, "netascii"));
		// Form ACK packet to receive from server
		byte[] data = new byte[4];
		DatagramPacket packet = new DatagramPacket(data, data.length);
		try {
			// Receiving ACK 0
			if (verbose) System.out.println("Waiting for server...");
			sendReceiveSocket.receive(packet);
			// If condition here determines if server respond with ACK 0 or not
			// If yes, then start the file transfer from client to server
			if(TFTP.getOpCode(packet) == TFTP.ACK_OP_CODE
					&& TFTP.getBlockNumber(packet) == 0) {
				TID = packet.getPort();
				// Generate a queue of data packets waiting to be transferred
				Queue<DatagramPacket> packetQueue = TFTP.formDATAPackets(addr, TID, filename);
				while(!packetQueue.isEmpty()) {
					// Extract data packet from queue and sent it to server --> DATA 1...n
					DatagramPacket dataPacket = packetQueue.remove();
					sendReceiveSocket.send(dataPacket);
					// Form an acknowledgement packet for receiving respond from server
					byte[] ack = new byte[4];
					DatagramPacket ackPacket = new DatagramPacket(ack, ack.length);
					// Receive server response for acknowledgement --> ACK 1...n
					sendReceiveSocket.receive(ackPacket);
					int blockNumber = Byte.toUnsignedInt(ackPacket.getData()[0]);	// Or use Brandon's version of Byte-Int conversion
					if(blockNumber == blockCount)
						blockCount++;
					else
						throw new Exception();
				}
				System.out.println("Client write request complete.");
			}
			// If ACK 0 never arrives when WRQ is issued, then error must have occurred
			else
				throw new Exception();
		} catch(Exception e) {
			e.printStackTrace();
			System.exit(1);
		}
	}

	
	public void sendReadRequest(InetAddress addr, String src, String des)
	{
		int TID, blockSize = 0, blockCount = 1;
		send(addr, SEND_PORT, new Request(Request.Type.READ, src, "netascii"));
		do {
			// Form packet to receive server packet
			int bufferSize = TFTP.OP_CODE_SIZE + TFTP.BLOCK_NUMBER_SIZE + TFTP.MAX_DATA_SIZE;
			byte[] data = new byte[bufferSize];
			DatagramPacket packet = new DatagramPacket(data, data.length);
			try {
				if (verbose) System.out.println("Waiting for server...");
				sendReceiveSocket.receive(packet);
				TID = packet.getPort();
				// If receive DATA packet and the block number is correct, proceed writing to file
				if(TFTP.getOpCode(packet) == TFTP.DATA_OP_CODE
						&& TFTP.getBlockNumber(packet) == blockCount) {
					TFTP.writeDATAToFile(packet, des);
					DatagramPacket ack = TFTP.formACKPacket(addr, TID, blockCount);
					sendReceiveSocket.send(ack);
					blockSize = TFTP.getData(packet).length;
					if(blockSize == 512)
						blockCount++;
				}
				else
					throw new Exception();
			}
			catch(Exception e) {
				e.printStackTrace();
				System.exit(1);
			}
		} while(blockSize == 512);
		System.out.println("Client read request complete.");
	}

	// Waits for response from server
	public void listen() {
		// Form packet to receive
		byte[] data = new byte[BUF_SIZE];
		DatagramPacket packet = new DatagramPacket(data, data.length);

		// Receive packet
		try {
			if (verbose) System.out.println("Waiting for server...");
			sendReceiveSocket.receive(packet);
			// Print info about packet received
			if (verbose) {
				System.out.println("Packet received from ");
				System.out.println(packet.getAddress().getHostAddress() + ":" + packet.getPort());
				System.out.print("Packet string: ");
				System.out.println(packet.getData().toString());
				System.out.print("Packet bytes: ");
				System.out.println(Arrays.toString(packet.getData()));
				System.out.println();
			}
		} catch(Exception e) {
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

		// Create requests for testing
		Request[] testRequests = new Request[11];
		for (int i=0; i<5; i++) {
			int even = 2*i;
			int odd = 2*i + 1;
			testRequests[even] = new Request(Request.Type.READ, even +".txt", "ascii");
			testRequests[odd] = new Request(Request.Type.WRITE, odd +".txt", "ocTEt");
		}
		testRequests[10] = new Request(Request.Type.TEST, "test.txt", "netascii");

		// Send test requests
		for (Request r : testRequests) {
			client.send(r);
		}

		// Close client sockets
		client.exit();
	}
}
