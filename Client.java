import java.io.*;
import java.net.*;
import java.util.*;

public class Client implements Exitable {
	private DatagramSocket sendReceiveSocket;
	private boolean verbose = true;
	private static int SEND_PORT = 4;
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
		DatagramPacket packet = formPacket(addr, port, r);

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

	// Forms a DatagramPacket using Request r with information about request type
	// (read, write, or test), filename, and mode (ascii, octet, etc.).
	public DatagramPacket formPacket(InetAddress addr, int port, Request r) {
		if (verbose) System.out.println("Forming packet...");

		int currentIndex;
		// Create byte array for packet
		byte[] buf = new byte[BUF_SIZE];
		// First element will always be 0
		buf[0] = TFPT_PADDING;
		switch (r.getType()) {
			case READ:
				if (verbose) System.out.println("Read request.");
				buf[1] = 1;
				break;
			case WRITE:
				if (verbose) System.out.println("Write request.");
				buf[1] = 2;
				break;
			default:
				if (verbose) System.out.println("Invalid request.");
				buf[1] = TFPT_PADDING;
				break;
		}

		// Add filename to packet data
		byte[] fbytes = r.getFilename().getBytes();
		System.arraycopy(fbytes,0,buf,2,fbytes.length);

		// Add 0 byte padding
		currentIndex = fbytes.length + 2;
		buf[currentIndex] = TFPT_PADDING;
		currentIndex++;

		// Add mode to packet data
		byte[] mbytes = r.getMode().getBytes();
		System.arraycopy(mbytes,0,buf,currentIndex,mbytes.length);

		// Add terminating 0 byte
		currentIndex = currentIndex + mbytes.length;
		buf[currentIndex] = TFPT_PADDING;

		// Truncate trailing zeros by copyings to a new array
		byte[] data = new byte[currentIndex + 1];
		System.arraycopy(buf,0,data,0,currentIndex+1);

		DatagramPacket packet = new DatagramPacket(data,currentIndex+1, addr, port);
		//DatagramPacket packet = new DatagramPacket(buf, currentIndex + 1, addr, port);
		//System.out.println("Formed bytes: " + Arrays.toString(packet.getData()));
		if (verbose) System.out.println("Packet formed.");

		return packet;
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
