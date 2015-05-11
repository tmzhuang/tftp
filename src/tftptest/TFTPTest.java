package tftptest;

import static org.junit.Assert.*;

import java.net.DatagramPacket;
import java.net.InetAddress;
import java.net.UnknownHostException;

import org.junit.Test;

import tftp.*;

public class TFTPTest {

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
			
			// [0] [1] [] [] [] [] [] [] [] [] [] [] [] [] [] []
			byte[] fileNameBytes = new byte[9];
			System.arraycopy(data, 2, fileNameBytes, 0, 9);

			assertTrue(fileNameBytes.equals(fileName.getBytes()));
			
			assertTrue(data[11] == 0);
			
			byte[] modeBytes = new byte[8];
			System.arraycopy(data, 2, modeBytes, 0, 8);
			
			assertTrue(modeBytes.equals(mode.getBytes()));
		} catch (UnknownHostException e) {
			e.printStackTrace();
		}
	}

	@Test
	public void bytesToBlockNumberTest() {
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

}
