package tftp;

// Class to encapsulate data about a request
public class Request {
	public enum Type {
		READ, WRITE, TEST;
	}

	private Type type;
	private String filename;
	private String mode;

	public Request(Type t, String f, String m) {
		type = t;
		filename = f;
		mode = m;
	}

	public Type getType() {
		return type;
	}

	public String getFilename() {
		return filename;
	}

	public String getMode() {
		return mode;
	}
}
