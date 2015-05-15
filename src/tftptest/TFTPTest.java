package tftptest;

import static org.junit.Assert.*;

import java.net.DatagramPacket;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Arrays;

import org.junit.Test;

import tftp.*;

public class TFTPTest {

	//public static DatagramPacket formRQPacket(InetAddress addr, int port, Request r)
	@Test
	public void formRQPacketTest1() {
		try {
			String fileName = "hello.txt";
			String mode = "netascii";
			Request request = new Request(Request.Type.READ, fileName, mode);
			DatagramPacket packet = TFTP.formRQPacket(
					InetAddress.getLocalHost(),
					69,
					request);

			byte[] data = packet.getData();
			
			assertTrue(data[0] == 0);
			assertTrue(data[1] == 1);
			
			byte[] fileNameBytes = new byte[9];
			System.arraycopy(data, 2, fileNameBytes, 0, 9);

			assertTrue(Arrays.equals(fileNameBytes, fileName.getBytes()));
			
			assertTrue(data[11] == 0);
			
			byte[] modeBytes = new byte[8];
			System.arraycopy(data, 12, modeBytes, 0, 8);
			
			assertTrue(Arrays.equals(modeBytes, mode.getBytes()));

			assertTrue(data[20] == 0);
		} catch (UnknownHostException e) {
			e.printStackTrace();
		}
	}

	@Test
	public void formRQPacketTest2() {
		try {
			String fileName = "hello.txt";
			String mode = "octet";
			Request request = new Request(Request.Type.WRITE, fileName, mode);
			DatagramPacket packet = TFTP.formRQPacket(
					InetAddress.getLocalHost(),
					69,
					request);

			byte[] data = packet.getData();
			
			assertTrue(data[0] == 0);
			assertTrue(data[1] == 2);
			
			byte[] fileNameBytes = new byte[9];
			System.arraycopy(data, 2, fileNameBytes, 0, 9);

			assertTrue(Arrays.equals(fileNameBytes, fileName.getBytes()));
			
			assertTrue(data[11] == 0);
			
			byte[] modeBytes = new byte[5];
			System.arraycopy(data, 12, modeBytes, 0, 5);
			
			assertTrue(Arrays.equals(modeBytes, mode.getBytes()));
			
			assertTrue(data[17] == 0);
		} catch (UnknownHostException e) {
			e.printStackTrace();
		}
	}

	//public static Queue<DatagramPacket> formDATAPackets(InetAddress addr, int port, String filename)

	//public static void writeDATAToFile(DatagramPacket dataPacket, String filename)

	//public static int getOpCode(DatagramPacket packet)
	@Test
	public void getOpCodeTest1() {
		byte[] readOpCode = {0, 1};
		byte[] writeOpCode = {0, 2};
		byte[] dataOpCode = {0, 3};
		byte[] ackOpCode = {0, 4};
		byte[] errorOpCode = {0, 5};

		DatagramPacket readPacket = new DatagramPacket(readOpCode, 2);
		assertTrue(TFTP.getOpCode(readPacket) == 1);

		DatagramPacket writePacket = new DatagramPacket(writeOpCode, 2);
		assertTrue(TFTP.getOpCode(writePacket) == 2);

		DatagramPacket dataPacket = new DatagramPacket(dataOpCode, 2);
		assertTrue(TFTP.getOpCode(dataPacket) == 3);

		DatagramPacket ackPacket = new DatagramPacket(ackOpCode, 2);
		assertTrue(TFTP.getOpCode(ackPacket) == 4);

		DatagramPacket errorPacket = new DatagramPacket(errorOpCode, 2);
		assertTrue(TFTP.getOpCode(errorPacket) == 5);
	}

	//public static int getBlockNumber(DatagramPacket packet) throws IllegalArgumentException
	@Test (expected = IllegalArgumentException.class)
	public void getBlockNumberTest1() {
		byte[] zeroBlockNumBuf = {0, 3, 0, 0};
		byte[] maxBlockNumBuf = {0, 4, -1, -1};
		byte[] errorBuf = {0, 5, 0, 0};

		DatagramPacket zeroBlockNumPacket = new DatagramPacket(zeroBlockNumBuf, 4);
		assertTrue(TFTP.getBlockNumber(zeroBlockNumPacket) == 0);

		DatagramPacket maxBlockNumPacket = new DatagramPacket(maxBlockNumBuf, 4);
		assertTrue(TFTP.getBlockNumber(maxBlockNumPacket) == 65535);

		// Should throw IllegalArgumentException
		DatagramPacket errorPacket = new DatagramPacket(errorBuf, 4);
		TFTP.getBlockNumber(errorPacket);
	}

	//public static byte[] getData(DatagramPacket packet) throws IllegalArgumentException {
	@Test (expected = IllegalArgumentException.class)
	public void getDataTest1() {
		byte[] dataBuf = {0, 3, 0, 0, 65, 65, 65};
		byte[] errorBuf = {0, 5, 0, 0, 65, 65, 65};

		DatagramPacket dataPacket = new DatagramPacket(dataBuf, 7);
		byte[] dataExpected = {65, 65, 65};
		assertTrue(Arrays.equals(TFTP.getData(dataPacket), dataExpected));

		// Should throw IllegalArgumentException
		DatagramPacket errorPacket = new DatagramPacket(errorBuf, 7);
		TFTP.getData(errorPacket);
	}

	//public static byte[] blockNumberToBytes(int blockNumber) throws IllegalArgumentException {
	@Test
	public void blockNumberToBytesTest1() {
		byte[] zeroBlock = TFTP.blockNumberToBytes(0);
		assertTrue(zeroBlock[0] == 0);
		assertTrue(zeroBlock[1] == 0);

		byte[] maxBlock = TFTP.blockNumberToBytes(65535);
		assertTrue(maxBlock[0] == -1);
		assertTrue(maxBlock[1] == -1);

		byte[] beforeSignFlip = TFTP.blockNumberToBytes(127);
		assertTrue(beforeSignFlip[0] == 0);
		assertTrue(beforeSignFlip[1] == 127);

		byte[] afterSignFlip = TFTP.blockNumberToBytes(128);
		assertTrue(afterSignFlip[0] == 0);
		assertTrue(afterSignFlip[1] == -128);
	}

	//public static int bytesToBlockNumber(byte[] bytes) throws IllegalArgumentException
	@Test
	public void bytesToBlockNumberTest1() {
		try {
			byte[] minCase = {0, 0};
			int blockNumber = TFTP.bytesToBlockNumber(minCase);
			assert(blockNumber == 0);

			byte[] lsbCase = {0, 127};
			blockNumber = TFTP.bytesToBlockNumber(lsbCase);
			assert(blockNumber == 127);

			byte[] lsbCaseUnsigned = {0, (byte)128};
			blockNumber = TFTP.bytesToBlockNumber(lsbCaseUnsigned);
			assert(blockNumber == 128);

			byte[] msbCase = {127, 0};
			blockNumber = TFTP.bytesToBlockNumber(msbCase);
			assert(blockNumber == 127*256);

			byte[] msbCaseUnsigned = {(byte)128, 0};
			blockNumber = TFTP.bytesToBlockNumber(msbCaseUnsigned);
			assert(blockNumber == 128*256);

			byte[] mixedCase = {1, 1};
			blockNumber = TFTP.bytesToBlockNumber(mixedCase);
			assert(blockNumber == 257);

			byte[] maxCase = {(byte)255, (byte)255};
			blockNumber = TFTP.bytesToBlockNumber(maxCase);
			assert(blockNumber == 65535);
		}
		catch (IllegalArgumentException e)
		{
			e.printStackTrace();
		}
	}

	//public static DatagramPacket formACKPacket(InetAddress addr, int port, int blockNumber)
	@Test
	public void formACKPacketTest1() {
		try {
			int zeroBlockNum = 0;
			DatagramPacket zeroBlockNumPacket = TFTP.formACKPacket(
					InetAddress.getLocalHost(),
					69,
					zeroBlockNum);
			
			byte[] zeroBlockNumBytes = zeroBlockNumPacket.getData();
			assertTrue(zeroBlockNumBytes[0] == 0);
			assertTrue(zeroBlockNumBytes[1] == 4);
			assertTrue(zeroBlockNumBytes[2] == 0);
			assertTrue(zeroBlockNumBytes[3] == 0);

			int maxBlockNum = 65535;
			DatagramPacket maxBlockNumPacket = TFTP.formACKPacket(
					InetAddress.getLocalHost(),
					69,
					maxBlockNum);
			
			byte[] maxBlockNumBytes = maxBlockNumPacket.getData();
			assertTrue(maxBlockNumBytes[0] == 0);
			assertTrue(maxBlockNumBytes[1] == 4);
			assertTrue(maxBlockNumBytes[2] == -1);
			assertTrue(maxBlockNumBytes[3] == -1);
		} catch (UnknownHostException e) {
			e.printStackTrace();
		}
	}

	//public static DatagramPacket formERRORPacket(InetAddress addr, int port, int errorCode, String errMsg) {

	//public static Request parseRQ(DatagramPacket p) throws IllegalArgumentException
	@Test
	public void parseRQTest1() {
		try {
			String fileName = "hello.txt";
			String mode = "netascii";
			Request readRequest = new Request(Request.Type.READ, fileName, mode);
			DatagramPacket readRequestPacket = TFTP.formRQPacket(
					InetAddress.getLocalHost(),
					69,
					readRequest);
			Request parsedReadRequest = TFTP.parseRQ(readRequestPacket);
			assertTrue(parsedReadRequest.getFilename().equals(readRequest.getFilename()));
			assertTrue(parsedReadRequest.getMode().equals(readRequest.getMode()));
			assertTrue(parsedReadRequest.getType().equals(readRequest.getType()));

			Request writeRequest = new Request(Request.Type.WRITE, fileName, mode);
			DatagramPacket writeRequestPacket = TFTP.formRQPacket(
					InetAddress.getLocalHost(),
					69,
					writeRequest);
			Request parsedWriteRequest = TFTP.parseRQ(writeRequestPacket);
			assertTrue(parsedWriteRequest.getFilename().equals(writeRequest.getFilename()));
			assertTrue(parsedWriteRequest.getMode().equals(writeRequest.getMode()));
			assertTrue(parsedWriteRequest.getType().equals(writeRequest.getType()));
		} catch (UnknownHostException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}

	//public static int toUnsignedInt(byte myByte)
	@Test
	public void toUnsignedIntTest1() {
		byte zero = 0;
		assertTrue(TFTP.toUnsignedInt(zero) == 0);

		byte max = -1;
		assertTrue(TFTP.toUnsignedInt(max) == 255);

		byte beforeSignFlip = 127;
		assertTrue(TFTP.toUnsignedInt(beforeSignFlip) == 127);

		byte afterSignFlip = -128;
		assertTrue(TFTP.toUnsignedInt(afterSignFlip) == 128);
	}


	//public static DatagramPacket formERRORPacket(InetAddress addr, int port, int errorCode, String errMsg) {

	//public static String getErrorMessage(DatagramPacket packet)
	
	//public static int getErrorCode(DatagramPacket packet)
}
