package tftp;

import java.util.*;

public class Repl implements Runnable {
	private Exitable prog;

	public Repl(Exitable prog) {
		this.prog = prog;
	}

	public void run() {
		Scanner in = new Scanner(System.in);
		String s;
		while (true) {
			// Get input
			s = in.next();

			// Quit server if exit command given
			if (s.equalsIgnoreCase("exit")) {
				System.out.println("Server shutting down...");
				prog.exit();
				System.exit(1);
			} else {
				System.out.println("Invalid command. Please type \"exit\" to quit.");
			}
		}

	}
}
