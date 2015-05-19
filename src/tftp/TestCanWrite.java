package tftp;

import java.io.File;

public class TestCanWrite
{
	public static void main(String[] args)
	{
		File file = new File("C:\\temp\\server_files\\nonwritable.txt");
		System.out.println(file.canWrite());
		System.out.println(file.canRead());
	}
}
