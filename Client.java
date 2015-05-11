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

	// Waits for response from server
	public void listen() {
		// Form packet to receive
		byte[] data = new byte[4];
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
