package tftptest;

import static org.junit.Assert.*;

import java.net.DatagramPacket;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.*;
import java.io.*;

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
	//File does not exist
	//Expects exception thrown
	@Test (expected = FileNotFoundException.class)
	public void formDATAPacketsTest1() throws Exception {
		// Setup
		//exception.expect(FileNotFoundException.class);
		try {
			InetAddress addr = InetAddress.getLocalHost();
			int port = 69;
			String filename = "formDATAPacketsTest1.txt";

			TFTP.formDATAPackets(addr, port, filename);
		} catch(Exception e) {
			throw e;
		}
	}

	//public static Queue<DatagramPacket> formDATAPackets(InetAddress addr, int port, String filename)
	//Test empty file
	//Expects empty queue returned
	@Test
	public void formDATAPacketsTest2() {
		// Setup
		try {
			InetAddress addr = InetAddress.getLocalHost();
			int port = 69;

			String filename = "formDATAPacketsTest2.txt";
			File f= new File(filename);
			if (f.exists()) f.delete();
			f.createNewFile();

			Queue<DatagramPacket> dataPackets = TFTP.formDATAPackets(addr, port, filename);

			// Check that queue is empty
			assertTrue(dataPackets.size() == 1);
			// Check that the packet is empty
			DatagramPacket dataPacket = dataPackets.remove();
			assertTrue(TFTP.getData(dataPacket).length == 0);

			// Cleanup
			f.delete();
		} catch(Exception e) {
		}
	}

	//public static Queue<DatagramPacket> formDATAPackets(InetAddress addr, int port, String filename)
	//Test normal file of size 2*512 + 1 (not a multiple of 512)
	//Expects a datagram packet queue with 3 packets. The first two should have data of size 512,
	//and the last should have a size of 1
	@Test
	public void formDATAPacketsTest3() {
		try {
			// Setup
			InetAddress addr = InetAddress.getLocalHost();
			int port = 69;
			String filename = "formDATAPacketsTest3.txt";

			File f= new File(filename);
			if (f.exists()) f.delete();
			f.createNewFile();

			// Write 2*512 + 1 bytes to file
			BufferedOutputStream out = new BufferedOutputStream(new FileOutputStream(f));
			byte[] bytes = new byte[512*2+1];
			out.write(bytes);
			out.close();

			Queue<DatagramPacket> dataPackets = TFTP.formDATAPackets(addr, port, filename);

			// Check that queue is correct length
			assertTrue(dataPackets.size() == 3);
			// For each packek, check length and op code
			DatagramPacket packet1 = dataPackets.remove();
			assertTrue(TFTP.getData(packet1).length == 512);
			assertTrue(TFTP.getOpCode(packet1) == TFTP.DATA_OP_CODE);

			DatagramPacket packet2 = dataPackets.remove();
			assertTrue(TFTP.getData(packet2).length == 512);
			assertTrue(TFTP.getOpCode(packet2) == TFTP.DATA_OP_CODE);

			DatagramPacket packet3 = dataPackets.remove();
			assertTrue(TFTP.getData(packet3).length == 1);
			assertTrue(TFTP.getOpCode(packet3) == TFTP.DATA_OP_CODE);

			// Cleanup
			f.delete();
		} catch(Exception e) {
		}
	}

	//public static Queue<DatagramPacket> formDATAPackets(InetAddress addr, int port, String filename)
	//Test normal file of size 2*512 + 1 (not a multiple of 512)
	//Expects a datagram packet queue with 3 packets. The first two should have data of size 512,
	//and the last should have a size of 1
	@Test
	public void formDATAPacketsTest4() {
		try {
			// Setup
			InetAddress addr = InetAddress.getLocalHost();
			int port = 69;
			String filename = "formDATAPacketsTest4.txt";

			File f= new File(filename);
			if (f.exists()) f.delete();
			f.createNewFile();

			// Write 2*512 + 1 bytes to file
			BufferedOutputStream out = new BufferedOutputStream(new FileOutputStream(f));
			byte[] bytes = new byte[512*3];
			Arrays.fill(bytes, (byte)1);
			out.write(bytes);
			out.close();

			Queue<DatagramPacket> dataPackets = TFTP.formDATAPackets(addr, port, filename);

			// Check that queue is correct length
			// Length should be 4 because there needs to be an empty data packet with length 0
			assertTrue(dataPackets.size() == 4);
			// For each packek, check length and op code
			DatagramPacket packet1 = dataPackets.remove();
			assertTrue(TFTP.getData(packet1).length == 512);
			assertTrue(TFTP.getOpCode(packet1) == TFTP.DATA_OP_CODE);

			DatagramPacket packet2 = dataPackets.remove();
			assertTrue(TFTP.getData(packet2).length == 512);
			assertTrue(TFTP.getOpCode(packet2) == TFTP.DATA_OP_CODE);

			DatagramPacket packet3 = dataPackets.remove();
			assertTrue(TFTP.getData(packet3).length == 512);
			assertTrue(TFTP.getOpCode(packet3) == TFTP.DATA_OP_CODE);

			DatagramPacket packet4 = dataPackets.remove();
			assertTrue(TFTP.getData(packet4).length == 0);
			assertTrue(TFTP.getOpCode(packet4) == TFTP.DATA_OP_CODE);

			// Cleanup
			f.delete();
		} catch(Exception e) {
		}
	}

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
			assertTrue(parsedReadRequest.getFilePath().equals(readRequest.getFilePath()));
			assertTrue(parsedReadRequest.getMode().equals(readRequest.getMode()));
			assertTrue(parsedReadRequest.getType().equals(readRequest.getType()));

			Request writeRequest = new Request(Request.Type.WRITE, fileName, mode);
			DatagramPacket writeRequestPacket = TFTP.formRQPacket(
					InetAddress.getLocalHost(),
					69,
					writeRequest);
			Request parsedWriteRequest = TFTP.parseRQ(writeRequestPacket);
			assertTrue(parsedWriteRequest.getFilePath().equals(writeRequest.getFilePath()));
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

	//public static DatagramPacket formERRORPacket(InetAddress addr, int port, int errorCode, String errMsg) 
	@Test 
	public void formErrorPacketTest1() {
		try {
			InetAddress addr = InetAddress.getLocalHost();
		int port = 69;
		int errCode = 3;
		String errMsg = "hi";
		byte[] errMsgBytes = errMsg.getBytes();
		byte[] expectedBytes = new byte[TFTP.OP_CODE_SIZE + TFTP.ERROR_CODE_SIZE + errMsgBytes.length + 1];
		expectedBytes[0] = 0;
		expectedBytes[1] = (byte)TFTP.ERROR_OP_CODE;
		expectedBytes[2] = 0;
		expectedBytes[3] = (byte)errCode;
		System.arraycopy(errMsgBytes,0,expectedBytes,4,errMsgBytes.length);
		expectedBytes[expectedBytes.length-1] = 0;


		DatagramPacket formedPacket = TFTP.formERRORPacket(addr,port,errCode,errMsg);
		// Check address of packet
		assertTrue(formedPacket.getAddress().equals(addr));
		// Check port of packet
		assertTrue(formedPacket.getPort() == port);
		// Check data of packet
		byte[] formedBytes = formedPacket.getData();
		assertTrue(Arrays.equals(formedBytes,expectedBytes));

		} catch(Exception e) {
			e.printStackTrace();
		}
	}

	//public static boolean verifyRequestPacket(DatagramPacket packet, String[] errorMessage)
	@Test
	public void VerifyRequestPacketTest1()
	{
		try
		{
			String operation = "r";
			String fileName = "server.txt";
			String mode = "netascii";
			DatagramPacket packet = TFTP.formRQPacket(
					InetAddress.getLocalHost(),
					69,
					operation,
					fileName,
					mode);
			String[] errorMessage = new String[1];

			assertTrue(TFTP.verifyRequestPacket(packet, errorMessage));
		}
		catch (UnknownHostException e)
		{
			e.printStackTrace();
		}
	}

	@Test
	public void VerifyRequestPacketTest2()
	{
		try
		{
			String operation = "w";
			String fileName = "server.txt";
			String mode = "neTascii";
			DatagramPacket packet = TFTP.formRQPacket(
					InetAddress.getLocalHost(),
					69,
					operation,
					fileName,
					mode);
			String[] errorMessage = new String[1];

			assertTrue(TFTP.verifyRequestPacket(packet, errorMessage));
		}
		catch (UnknownHostException e)
		{
			e.printStackTrace();
		}
	}

	@Test
	public void VerifyRequestPacketTest3()
	{
		try
		{
			String operation = "r";
			String fileName = "server.txt";
			String mode = "octet";
			DatagramPacket packet = TFTP.formRQPacket(
					InetAddress.getLocalHost(),
					69,
					operation,
					fileName,
					mode);
			String[] errorMessage = new String[1];

			assertTrue(TFTP.verifyRequestPacket(packet, errorMessage));
		}
		catch (UnknownHostException e)
		{
			e.printStackTrace();
		}
	}

	@Test
	public void VerifyRequestPacketTest4()
	{
		try
		{
			String operation = "r";
			String fileName = "server.txt";
			String mode = "oCtEt";
			DatagramPacket packet = TFTP.formRQPacket(
					InetAddress.getLocalHost(),
					69,
					operation,
					fileName,
					mode);
			String[] errorMessage = new String[1];

			assertTrue(TFTP.verifyRequestPacket(packet, errorMessage));
		}
		catch (UnknownHostException e)
		{
			e.printStackTrace();
		}
	}

	@Test
	public void VerifyRequestPacketTest5()
	{
		try
		{
			String operation = "r";
			String fileName = "server.txt";
			String mode = "netas";
			DatagramPacket packet = TFTP.formRQPacket(
					InetAddress.getLocalHost(),
					69,
					operation,
					fileName,
					mode);
			String[] errorMessage = new String[1];

			assertFalse(TFTP.verifyRequestPacket(packet, errorMessage));
			assertTrue(errorMessage[0].equals("Invalid mode"));
		}
		catch (UnknownHostException e)
		{
			e.printStackTrace();
		}
	}

	@Test
	public void VerifyRequestPacketTest6()
	{
		try
		{
			String operation = "r";
			String fileName = "";
			String mode = "netascii";
			DatagramPacket packet = TFTP.formRQPacket(
					InetAddress.getLocalHost(),
					69,
					operation,
					fileName,
					mode);
			String[] errorMessage = new String[1];

			assertFalse(TFTP.verifyRequestPacket(packet, errorMessage));
			assertTrue(errorMessage[0].equals("Missing file name"));
		}
		catch (UnknownHostException e)
		{
			e.printStackTrace();
		}
	}

	@Test
	public void VerifyRequestPacketTest7()
	{
		try
		{
			byte[] dataBuf = {1, 0, 65, 0, 111, 99, 116, 101, 116, 0};
			DatagramPacket packet = TFTP.formPacket(
					InetAddress.getLocalHost(),
					69,
					dataBuf);
			String[] errorMessage = new String[1];

			assertFalse(TFTP.verifyRequestPacket(packet, errorMessage));
			assertTrue(errorMessage[0].equals("First byte is not 0"));
		}
		catch (UnknownHostException e)
		{
			e.printStackTrace();
		}
	}

	@Test
	public void VerifyRequestPacketTest8()
	{
		try
		{
			byte[] dataBuf = {0, 3, 65, 0, 111, 99, 116, 101, 116, 0};
			DatagramPacket packet = TFTP.formPacket(
					InetAddress.getLocalHost(),
					69,
					dataBuf);
			String[] errorMessage = new String[1];

			assertFalse(TFTP.verifyRequestPacket(packet, errorMessage));
			assertTrue(errorMessage[0].equals("Invalid op code"));
		}
		catch (UnknownHostException e)
		{
			e.printStackTrace();
		}
	}

	@Test
	public void VerifyRequestPacketTest9()
	{
		try
		{
			byte[] dataBuf = {0, 1, 65, 0, 111, 99, 116, 101, 116};
			DatagramPacket packet = TFTP.formPacket(
					InetAddress.getLocalHost(),
					69,
					dataBuf);
			String[] errorMessage = new String[1];

			assertFalse(TFTP.verifyRequestPacket(packet, errorMessage));
			assertTrue(errorMessage[0].equals("Missing final 0 byte"));
		}
		catch (UnknownHostException e)
		{
			e.printStackTrace();
		}
	}

	@Test
	public void VerifyRequestPacketTest10()
	{
		try
		{
			byte[] dataBuf = {0, 1, 65, 0, 0};
			DatagramPacket packet = TFTP.formPacket(
					InetAddress.getLocalHost(),
					69,
					dataBuf);
			String[] errorMessage = new String[1];

			assertFalse(TFTP.verifyRequestPacket(packet, errorMessage));
			assertTrue(errorMessage[0].equals("Invalid mode"));
		}
		catch (UnknownHostException e)
		{
			e.printStackTrace();
		}
	}

	@Test
	public void VerifyRequestPacketTest11()
	{
		try
		{
			byte[] dataBuf = {0, 1, 65, 0, 111, 99, 116, 101, 116, 0, 3};
			DatagramPacket packet = TFTP.formPacket(
					InetAddress.getLocalHost(),
					69,
					dataBuf);
			String[] errorMessage = new String[1];

			assertFalse(TFTP.verifyRequestPacket(packet, errorMessage));
			assertTrue(errorMessage[0].equals("No termination after final 0 byte"));
		}
		catch (UnknownHostException e)
		{
			e.printStackTrace();
		}
	}

	@Test
	public void VerifyRequestPacketTest12()
	{
		try
		{
			byte[] dataBuf = {0, 1, 65, 0, 111, 99, 116, 101, 116, 0, 0};
			DatagramPacket packet = TFTP.formPacket(
					InetAddress.getLocalHost(),
					69,
					dataBuf);
			String[] errorMessage = new String[1];

			assertFalse(TFTP.verifyRequestPacket(packet, errorMessage));
			assertTrue(errorMessage[0].equals("No termination after final 0 byte"));
		}
		catch (UnknownHostException e)
		{
			e.printStackTrace();
		}
	}

	//public static boolean verifyDataPacket(DatagramPacket packet, int blockNumber)
	@Test
	public void VerifyDataPacketTest1()
	{
		try
		{
			int blockNumber = 0;
			DatagramPacket packet = TFTP.formDATAPacket(
					InetAddress.getLocalHost(),
					69,
					blockNumber,
					new byte[0]);
			String[] errorMessage = new String[1];

			assertTrue(TFTP.verifyDataPacket(packet, blockNumber, errorMessage));
		}
		catch (UnknownHostException e)
		{
			e.printStackTrace();
		}
	}

	@Test
	public void VerifyDataPacketTest2()
	{
		try
		{
			int blockNumber = 65535;
			DatagramPacket packet = TFTP.formDATAPacket(
					InetAddress.getLocalHost(),
					69,
					blockNumber,
					new byte[0]);
			String[] errorMessage = new String[1];

			assertTrue(TFTP.verifyDataPacket(packet, blockNumber, errorMessage));
		}
		catch (UnknownHostException e)
		{
			e.printStackTrace();
		}
	}

	@Test
	public void VerifyDataPacketTest3()
	{
		try
		{
			byte[] data = {0, 3, 0, 0, 127};
			DatagramPacket packet = TFTP.formPacket(
					InetAddress.getLocalHost(),
					69,
					data);
			String[] errorMessage = new String[1];

			assertTrue(TFTP.verifyDataPacket(packet, 0, errorMessage));
		}
		catch (UnknownHostException e)
		{
			e.printStackTrace();
		}
	}

	@Test
	public void VerifyDataPacketTest4()
	{
		try
		{
			byte[] data = {1, 3, 0, 0, 127};
			DatagramPacket packet = TFTP.formPacket(
					InetAddress.getLocalHost(),
					69,
					data);
			String[] errorMessage = new String[1];

			assertFalse(TFTP.verifyDataPacket(packet, 0, errorMessage));
			assertTrue(errorMessage[0].equals("First byte is not 0"));
		}
		catch (UnknownHostException e)
		{
			e.printStackTrace();
		}
	}

	@Test
	public void VerifyDataPacketTest5()
	{
		try
		{
			byte[] data = {0, 4, 0, 0, 127};
			DatagramPacket packet = TFTP.formPacket(
					InetAddress.getLocalHost(),
					69,
					data);
			String[] errorMessage = new String[1];

			assertFalse(TFTP.verifyDataPacket(packet, 0, errorMessage));
			assertTrue(errorMessage[0].equals("Invalid op code"));
		}
		catch (UnknownHostException e)
		{
			e.printStackTrace();
		}
	}

	@Test
	public void VerifyDataPacketTest6()
	{
		try
		{
			byte[] data = {0, 3, 1, 0, 127};
			DatagramPacket packet = TFTP.formPacket(
					InetAddress.getLocalHost(),
					69,
					data);
			String[] errorMessage = new String[1];

			assertFalse(TFTP.verifyDataPacket(packet, 0, errorMessage));
			assertTrue(errorMessage[0].equals("Invalid block number"));
		}
		catch (UnknownHostException e)
		{
			e.printStackTrace();
		}
	}

	@Test
	public void VerifyDataPacketTest7()
	{
		try
		{
			byte[] data = {0, 3, 0, 1, 127};
			DatagramPacket packet = TFTP.formPacket(
					InetAddress.getLocalHost(),
					69,
					data);
			String[] errorMessage = new String[1];

			assertFalse(TFTP.verifyDataPacket(packet, 0, errorMessage));
			assertTrue(errorMessage[0].equals("Invalid block number"));
		}
		catch (UnknownHostException e)
		{
			e.printStackTrace();
		}
	}

	@Test
	public void VerifyDataPacketTest9()
	{
		try
		{
			byte[] data = new byte[TFTP.MAX_PACKET_SIZE];
			data[0] = 0;
			data[1] = TFTP.DATA_OP_CODE;
			data[2] = 0;
			data[3] = 0;
			for (int i=4; i<data.length; i++)
			{
				data[i] = 1;
			}
			DatagramPacket packet = TFTP.formPacket(
					InetAddress.getLocalHost(),
					69,
					data);
			String[] errorMessage = new String[1];

			assertTrue(TFTP.verifyDataPacket(packet, 0, errorMessage));
		}
		catch (UnknownHostException e)
		{
			e.printStackTrace();
		}
	}

	@Test
	public void VerifyDataPacketTest10()
	{
		try
		{
			byte[] data = new byte[TFTP.MAX_PACKET_SIZE + 1];
			data[0] = 0;
			data[1] = TFTP.DATA_OP_CODE;
			data[2] = 0;
			data[3] = 0;
			for (int i=4; i<data.length; i++)
			{
				data[i] = 1;
			}
			DatagramPacket packet = TFTP.formPacket(
					InetAddress.getLocalHost(),
					69,
					data);
			String[] errorMessage = new String[1];

			assertFalse(TFTP.verifyDataPacket(packet, 0, errorMessage));
			assertTrue(errorMessage[0].equals("Packet too large"));
		}
		catch (UnknownHostException e)
		{
			e.printStackTrace();
		}
	}

	// Returns true if acknowledgement packet matches TFTP specifications
	@Test
	public void VerifyAckPacketTest1()
	{
		try
		{
			int blockNumber = 0;
			DatagramPacket packet = TFTP.formACKPacket(
					InetAddress.getLocalHost(),
					69,
					blockNumber);
			String[] errorMessage = new String[1];

			assertTrue(TFTP.verifyAckPacket(packet, blockNumber, errorMessage));
		}
		catch (UnknownHostException e)
		{
			e.printStackTrace();
		}
	}

	@Test
	public void VerifyAckPacketTest2()
	{
		try
		{
			int blockNumber = 65535;
			DatagramPacket packet = TFTP.formACKPacket(
					InetAddress.getLocalHost(),
					69,
					blockNumber);
			String[] errorMessage = new String[1];

			assertTrue(TFTP.verifyAckPacket(packet, blockNumber, errorMessage));
		}
		catch (UnknownHostException e)
		{
			e.printStackTrace();
		}
	}

	@Test
	public void VerifyAckPacketTest3()
	{
		try
		{
			byte[] dataBuf = {1, 4, 0, 0};
			DatagramPacket packet = TFTP.formPacket(
					InetAddress.getLocalHost(),
					69,
					dataBuf);
			String[] errorMessage = new String[1];

			assertFalse(TFTP.verifyAckPacket(packet, 0, errorMessage));
			assertTrue(errorMessage[0].equals("First byte is not 0"));
		}
		catch (UnknownHostException e)
		{
			e.printStackTrace();
		}
	}

	@Test
	public void VerifyAckPacketTest4()
	{
		try
		{
			byte[] dataBuf = {0, 3, 0, 0};
			DatagramPacket packet = TFTP.formPacket(
					InetAddress.getLocalHost(),
					69,
					dataBuf);
			String[] errorMessage = new String[1];

			assertFalse(TFTP.verifyAckPacket(packet, 0, errorMessage));
			assertTrue(errorMessage[0].equals("Invalid op code"));
		}
		catch (UnknownHostException e)
		{
			e.printStackTrace();
		}
	}

	@Test
	public void VerifyAckPacketTest5()
	{
		try
		{
			byte[] dataBuf = {0, 4, 0};
			DatagramPacket packet = TFTP.formPacket(
					InetAddress.getLocalHost(),
					69,
					dataBuf);
			String[] errorMessage = new String[1];

			assertFalse(TFTP.verifyAckPacket(packet, 0, errorMessage));
			assertTrue(errorMessage[0].equals("Packet too small"));
		}
		catch (UnknownHostException e)
		{
			e.printStackTrace();
		}
	}

	@Test
	public void VerifyAckPacketTest6()
	{
		try
		{
			byte[] dataBuf = {0, 4, 0, 0, 0};
			DatagramPacket packet = TFTP.formPacket(
					InetAddress.getLocalHost(),
					69,
					dataBuf);
			String[] errorMessage = new String[1];

			assertFalse(TFTP.verifyAckPacket(packet, 0, errorMessage));
			assertTrue(errorMessage[0].equals("Packet too large"));
		}
		catch (UnknownHostException e)
		{
			e.printStackTrace();
		}
	}

	//public static boolean verifyErrorPacket(DatagramPacket packet)
	@Test
	public void VerifyErrorPacketTest1()
	{
		try
		{
			int errorCode = TFTP.ERROR_CODE_NOT_DEFINED;
			String message = "Undefined error";
			DatagramPacket packet = TFTP.formERRORPacket(
					InetAddress.getLocalHost(),
					69,
					errorCode,
					message);
			String[] errorMessage = new String[1];

			assertTrue(TFTP.verifyErrorPacket(packet, errorMessage));
		}
		catch (UnknownHostException e)
		{
			e.printStackTrace();
		}
	}

	@Test
	public void VerifyErrorPacketTest2()
	{
		try
		{
			int errorCode = TFTP.ERROR_CODE_NO_SUCH_USER;
			String message = "No such user error";
			DatagramPacket packet = TFTP.formERRORPacket(
					InetAddress.getLocalHost(),
					69,
					errorCode,
					message);
			String[] errorMessage = new String[1];

			assertTrue(TFTP.verifyErrorPacket(packet, errorMessage));
		}
		catch (UnknownHostException e)
		{
			e.printStackTrace();
		}
	}

	@Test
	public void VerifyErrorPacketTest3()
	{
		try
		{
			// Invalid error code
			int errorCode = TFTP.ERROR_CODE_NO_SUCH_USER + 1;
			String message = "Invalid error code";
			DatagramPacket packet = TFTP.formERRORPacket(
					InetAddress.getLocalHost(),
					69,
					errorCode,
					message);
			String[] errorMessage = new String[1];

			assertFalse(TFTP.verifyErrorPacket(packet, errorMessage));
			assertTrue(errorMessage[0].equals("Invalid error code"));
		}
		catch (UnknownHostException e)
		{
			e.printStackTrace();
		}
	}

	@Test
	public void VerifyErrorPacketTest4()
	{
		try
		{
			byte[] dataBuf = {1, 5, 0, 1, 0};
			DatagramPacket packet = TFTP.formPacket(
					InetAddress.getLocalHost(),
					69,
					dataBuf);
			String[] errorMessage = new String[1];

			assertFalse(TFTP.verifyErrorPacket(packet, errorMessage));
			assertTrue(errorMessage[0].equals("First byte is not 0"));
		}
		catch (UnknownHostException e)
		{
			e.printStackTrace();
		}
	}

	@Test
	public void VerifyErrorPacketTest5()
	{
		try
		{
			byte[] dataBuf = {0, 4, 0, 1, 0};
			DatagramPacket packet = TFTP.formPacket(
					InetAddress.getLocalHost(),
					69,
					dataBuf);
			String[] errorMessage = new String[1];

			assertFalse(TFTP.verifyErrorPacket(packet, errorMessage));
			assertTrue(errorMessage[0].equals("Invalid op code"));
		}
		catch (UnknownHostException e)
		{
			e.printStackTrace();
		}
	}

	@Test
	public void VerifyErrorPacketTest6()
	{
		try
		{
			byte[] dataBuf = {0, 5, 1, 1, 0};
			DatagramPacket packet = TFTP.formPacket(
					InetAddress.getLocalHost(),
					69,
					dataBuf);
			String[] errorMessage = new String[1];

			assertFalse(TFTP.verifyErrorPacket(packet, errorMessage));
			assertTrue(errorMessage[0].equals("Missing 0 byte separating op code and error code"));
		}
		catch (UnknownHostException e)
		{
			e.printStackTrace();
		}
	}

	@Test
	public void VerifyErrorPacketTest7()
	{
		try
		{
			byte[] dataBuf = {0, 5, 0, 1, 8, 8};
			DatagramPacket packet = TFTP.formPacket(
					InetAddress.getLocalHost(),
					69,
					dataBuf);
			String[] errorMessage = new String[1];

			assertFalse(TFTP.verifyErrorPacket(packet, errorMessage));
			assertTrue(errorMessage[0].equals("Missing final 0 byte"));
		}
		catch (UnknownHostException e)
		{
			e.printStackTrace();
		}
	}

	@Test
	public void VerifyErrorPacketTest8()
	{
		try
		{
			byte[] dataBuf = {0, 5, 0, 1, 8, 8, 0};
			DatagramPacket packet = TFTP.formPacket(
					InetAddress.getLocalHost(),
					69,
					dataBuf);
			String[] errorMessage = new String[1];

			assertTrue(TFTP.verifyErrorPacket(packet, errorMessage));
		}
		catch (UnknownHostException e)
		{
			e.printStackTrace();
		}
	}

	@Test
	public void VerifyErrorPacketTest9()
	{
		try
		{
			byte[] dataBuf = {0, 5, 0, 1, 8, 8, 0, 0};
			DatagramPacket packet = TFTP.formPacket(
					InetAddress.getLocalHost(),
					69,
					dataBuf);
			String[] errorMessage = new String[1];

			assertFalse(TFTP.verifyErrorPacket(packet, errorMessage));
			assertTrue(errorMessage[0].equals("No termination after final 0 byte"));
		}
		catch (UnknownHostException e)
		{
			e.printStackTrace();
		}
	}

	//public static String getErrorMessage(DatagramPacket packet)
	@Test
	public void getErrorMessageTest1() {
		String msg = "sumting wong";
		try {
			DatagramPacket packet = TFTP.formERRORPacket(InetAddress.getLocalHost(),69,3,msg);
			assertTrue(msg.equals(TFTP.getErrorMessage(packet)));
		} catch(Exception e) {
			e.printStackTrace();
		}
	}
	
	//public static int getErrorCode(DatagramPacket packet)
	@Test
	public void getErrorCodeTest1() {
		int code = 3;
		try {
			DatagramPacket packet = TFTP.formERRORPacket(InetAddress.getLocalHost(),69,code,"sumting wong");
			assertTrue(code == TFTP.getErrorCode(packet));
		} catch(Exception e) {
			e.printStackTrace();
		}
	}
}
