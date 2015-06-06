package autoTest;

import static org.junit.Assert.*;

import java.io.*;

import org.junit.*;

import tftp.*;

public class IterationTest1_1 {
	private final String hostAddr = "127.0.0.1\n";
	private final String serverDir = "M:\\Test\\Server\\\n";
	private final String clientDir = "M:\\Test\\Client\\\n";
	private final String[] esCommand = {"-t"};
	private final String[] normalCommand = {"-n"};
	private final ByteArrayOutputStream outContent = new ByteArrayOutputStream();
	//private final ByteArrayOutputStream errContent = new ByteArrayOutputStream();

	@Before
	public void setUpStreams()
	{
		//System.setOut(new PrintStream(outContent));
		//System.setErr(new PrintStream(errContent));
	}

	@Test
	public void testEmpty() throws InterruptedException
	{
		Thread s = new Thread(new Server());
		Thread es = new Thread(new ErrorSimulator());
		Thread c = new Thread(new Client(normalCommand));
		try {
			try {
				String command = "cmd.exe fc";
				Process child = Runtime.getRuntime().exec(command);

				// Get output stream to write from it
				OutputStream out = child.getOutputStream();

				out.write("dir".getBytes());
				out.flush();
				out.write("dir /r/n".getBytes());
				out.close();
			} catch (IOException e) {
				e.printStackTrace();
			}
			s.start();
			System.setIn(new ByteArrayInputStream(serverDir.getBytes()));
			Thread.sleep(1000);
			/*c.start();
			System.setIn(new ByteArrayInputStream((hostAddr + clientDir + 
					"read\nanothertest.txt").getBytes()));
			Thread.sleep(1000);
			try {
				Runtime.getRuntime().exec("fc " + serverDir + "");
			} catch (IOException e) {
				e.printStackTrace();
			}*/
		} catch (InterruptedException e) {
			e.printStackTrace();
		}
	}
}
