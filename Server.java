import java.io.*;
import java.net.*;
import java.util.*;

public class Server implements Exitable {
	private DatagramSocket receiveSocket;
	private static int RECEIVE_PORT = 69;
	private static int BUF_SIZE = 100; // Default buffer size for packet data
	private static byte TFTP_PADDING = 0; // Padding used in TFTP protocol
	private boolean verbose = true;

	// Thrown when server receives a packet of invalid format
	public class InvalidPacketReceivedException extends RuntimeException{
		public InvalidPacketReceivedException() {
			super();
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
			System.out.println("Length of packet received is: " + packet.getLength());
			System.arraycopy(buf,0,buf,0,packet.getLength());
			if (verbose) System.out.println("Packet received.");
		} catch(Exception e) {
			e.printStackTrace();
			System.exit(1);
		}

		// Parse packet
		if (verbose) System.out.println("Parsing packet...");
		Request r = this.parse(packet);
		if (verbose) {
			System.out.println("Byte data: " + Arrays.toString(packet.getData()));
			System.out.println("Request type: " + r.getType());
			System.out.println("Filename: " + r.getFilename());
			System.out.println("Mode: " + r.getMode());
			System.out.println();
		}

		// Respond to packet
		this.respond(packet.getAddress(), packet.getPort(), r);
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

	// Parse a given DatagramPacket p to see if it is valid. A valid packet must begin
	// with [0,1] or [0,2], followed by an arbitrary number of bytes representing the 
	// filename, followed by a 0 byte, followed by an arbitrary number of bytes representing
	// the mode, followed by a terminating 0 byte.
	// If the packet is valid, a request with the respective request type, filename, and mode
	// is created. Otherwise, an exception is thrown and the server quits.
	public Request parse(DatagramPacket p) throws InvalidPacketReceivedException {
		Request.Type t;
		String f, m;
		int currentIndex = 0;

		// Get number of bytes used by packet data
		int len = p.getData().length; 
		// Make copy of data bytes to parse
		byte[] buf = new byte[len];
		System.arraycopy(p.getData(),0,buf,0,len);

		// If first byte isn't 0, packet is invalid
		if (buf[0] != TFTP_PADDING) throw new InvalidPacketReceivedException();

		// Check second byte for read or write
		switch (buf[1]) {
			case 1:
				t = Request.Type.READ;
				break;
			case 2:
				t = Request.Type.WRITE;
				break;
			default:
				throw new InvalidPacketReceivedException();
		}

		// Get filename
		currentIndex = 2;
		if (currentIndex >= len) throw new InvalidPacketReceivedException();
		// Create an array of bytes to hold filename byte data
		byte[] fbytes = new byte[len];
		// Loop through array until 0 byte is found or out of bound occurs
		while (buf[currentIndex] != TFTP_PADDING) {
			currentIndex++;
			if (currentIndex >= len) throw new InvalidPacketReceivedException();
		}
		int filenameLength = currentIndex - 2;
		System.arraycopy(buf,2,fbytes,0,filenameLength);
		f = new String(fbytes);

		// Check for 0 byte padding between filename and mode
		if (buf[currentIndex] != TFTP_PADDING) throw new InvalidPacketReceivedException();

		// Get mode
		currentIndex++;
		if (currentIndex >= len) throw new InvalidPacketReceivedException();
		int modeStartIndex = currentIndex;
		byte[] mbytes = new byte[len];
		// Loop through array until 0 byte is found or out of bound occurs
		while (buf[currentIndex] != TFTP_PADDING) {
			currentIndex++;
			if (currentIndex >= len) throw new InvalidPacketReceivedException();
		}
		int modeLength = currentIndex - modeStartIndex;
		System.arraycopy(buf,modeStartIndex,mbytes,0,modeLength);
		m = new String(mbytes);

		return new Request(t, f, m);
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
