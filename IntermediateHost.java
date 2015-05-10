import java.io.*;
import java.net.*;
import java.util.*;

public class IntermediateHost {
	private DatagramSocket receiveSocket, sendReceiveSocket;
	private static int RECEIVE_PORT = 4;
	private static int SEND_PORT = 69;
	private static int BUF_SIZE = 100;
	private boolean verbose = true;

	public IntermediateHost() {
		try {
			receiveSocket = new DatagramSocket(RECEIVE_PORT);
			sendReceiveSocket = new DatagramSocket();
		} catch(Exception e) {
			e.printStackTrace();
			System.exit(1);
		}
	}

	// Listens to client until a packet is received and relays the packet
	// to server. Then waits for reponse from server and relays the response
	// back to the client.
	public void listen() {
		InetAddress clientAddr = null;
		int clientPort = -1;

		// Receive packet from client
		if (verbose) System.out.println("Waiting for client...");
		byte[] buf = new byte[BUF_SIZE];
		DatagramPacket receivePacket = new DatagramPacket(buf, buf.length);

		try {
			receiveSocket.receive(receivePacket);
			if (verbose) {
				System.out.println("Packet from client received.");
				System.out.print("Request: ");
				System.out.println(new String(receivePacket.getData()));
				System.out.println("Length of packet received is: " + receivePacket.getLength());
			}
			clientAddr = receivePacket.getAddress();
			clientPort = receivePacket.getPort();
			
		} catch(Exception e) {
			e.printStackTrace();
			System.exit(1);
		}

		// Create packet to send to server
		try {
			// Truncate received packet to received length
			byte[] data = new byte[receivePacket.getLength()];
			System.arraycopy(buf,0,data,0,receivePacket.getLength());
			System.out.println(Arrays.toString(data));
			DatagramPacket sendPacket = new DatagramPacket(data, data.length, InetAddress.getLocalHost(), SEND_PORT);
		// Send packet to server
			if (verbose) System.out.println("Sending packet to server");
			sendReceiveSocket.send(sendPacket);
			
		} catch(Exception e) {
			e.printStackTrace();
			System.exit(1);
		}

		// Wait for response from server
		try {
			buf = new byte[BUF_SIZE];
			receivePacket = new DatagramPacket(buf, buf.length);
			if (verbose) System.out.println("Waiting for server...");
			sendReceiveSocket.receive(receivePacket);
			if (verbose) {
				System.out.println("Packet from server received.");
				System.out.print("Request: ");
				System.out.println(new String(receivePacket.getData()));
			}
		} catch(Exception e) {
			e.printStackTrace();
			System.exit(1);
		}

		// Create packet to send back to back to client
		try {
			DatagramPacket sendPacket = new DatagramPacket(buf, buf.length, clientAddr, clientPort);
			DatagramSocket tmpSocket = new DatagramSocket();
			if (verbose) {
				System.out.println("Sending packet to client");
				System.out.print("Request: ");
				System.out.println(new String(sendPacket.getData()));
				System.out.println();
			}
			tmpSocket.send(sendPacket);
		} catch(Exception e) {
		}
	}

	public static void main (String[] args) {
		IntermediateHost ihost = new IntermediateHost();
		while (true) {
			ihost.listen();
		}
	}
}
