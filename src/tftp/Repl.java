package tftp;

import java.util.*;

public class Repl implements Runnable {
	private Exitable prog;
	private Scanner in;

	public Repl(Exitable prog, Scanner in) {
		this.prog = prog;
		this.in = in;
	}

	public void run() {
		//Scanner in = new Scanner(System.in);
		String s;
		while (true) {
			// Get input
			s = in.next();

			// Quit server if exit command given
			if (s.equalsIgnoreCase("exit")) {
				System.out.println("Shutting down...");
				in.close();
				prog.exit();
				return;
			} else {
				System.out.println("Invalid command. Please type \"exit\" to quit.");
			}
		}

	}
}
