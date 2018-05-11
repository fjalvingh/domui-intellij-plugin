package to.etc.domui.intellij;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.net.ServerSocket;
import java.net.Socket;

/**
 * Singleton which listens on the DomUI debug port for commands
 * sent by the DomUI application.
 *
 * @author <a href="mailto:jal@etc.to">Frits Jalvingh</a>
 * Created on 11-5-18.
 */
final public class DomUIDebugListener {
	static public final DomUIDebugListener INSTANCE = new DomUIDebugListener();

	private static final int PORT_START = 5051;

	private static final int PORT_END = 5060;

	private int m_port = -1;

	private ServerSocket m_serverSocket;

	public int getPort() {
		return m_port;
	}

	/**
	 * Start the socket listener thread, and
	 */
	public void start() {
		for(int port = PORT_START; port < PORT_END; port++) {
			try {
				m_serverSocket = new ServerSocket(port);
				m_port = port;
				System.out.println("Running DomUI command server on port " + port);
				Thread t = new Thread(new Runnable() {
					public void run() {
						serverThread();
					}
				});
				t.setDaemon(true);
				t.setName("DomUICmdSrvr");
				t.start();
				return;
			} catch(Exception x) {
				System.out.println("DomUI: port " + port + " occupied");
			}
		}

		//-- All ports exhausted...
		System.out.println("DomUI: All ports between " + PORT_START + " and " + PORT_END + " are occupied. Not running the DomUI command server.");
	}

	/**
	 * The actual server command loop: accept connections, read data, then execute command ad infinitum.
	 */
	void serverThread() {
		try {
			while(true) {
				Socket accept = m_serverSocket.accept();
				handleSocket(accept);
			}
		} catch(IOException e) {
			e.printStackTrace();
		}
	}

	private void handleSocket(Socket accept) {
		//-- Read an utf8 encoded command string, then execute it
		InputStream is = null;
		SocketCommand command = null;
		try {
			//-- Read byte array
			is = accept.getInputStream();
			int i;
			ByteArrayOutputStream baos = new ByteArrayOutputStream();
			byte[]	data = new byte[128];
			int szrd;
			while(0 < (szrd = is.read(data))) {
				baos.write(data, 0, szrd);
				if(data[szrd - 1] == 0)
					break;
			}
			baos.close();

			//-- Convert to utf-8 string
			String commandString = new String(baos.toByteArray(), "UTF-8");
			System.out.println("DomUI: received cmd=" + commandString);

			command = decodeSocketCommand(accept, is, commandString);
			if(null != command) {
				accept = null;								// Ownership passed to job
				is = null;
			}

		} catch(Exception x) {
			System.out.println("Socket command failed: " + x);
			return;
		} finally {
			try {
				if(is != null)
					is.close();
			} catch(Exception x) {}
			try {
				if(accept != null)
					accept.close();
			} catch(Exception x) {}
		}
		//if(null != command)
		//	new TrivialJob(command).run(new SearchProgressMonitor());
	}

	private SocketCommand decodeSocketCommand(Socket socket, InputStream is, final String command) throws Exception {
		return new SocketCommand(socket, is, socket.getOutputStream()) {
			@Override
			public void execute() throws Exception {
				DomUIDebugListener.this.runCommand(pw(), command);
			}
		};
	}

	private void runCommand(PrintWriter pw, String command) {
		System.out.println("DOMUI RECEIVED: " + command);
	}

	private static abstract class SocketCommand implements Runnable {
		final private Socket m_socket;

		final private OutputStream m_os;

		private PrintWriter m_pw;

		final private InputStream m_is;

		abstract public void execute() throws Exception;

		public SocketCommand(Socket socket, InputStream is, OutputStream os) {
			m_socket = socket;
			m_os = os;
			m_is = is;
		}

		public void close() {
			if(m_pw != null) {
				try {
					m_pw.flush();
					m_pw.close();
				} catch(Exception x) {
					x.printStackTrace();
				}
				m_pw = null;
			}
			try {
				m_os.close();
			} catch(Exception x) {
				x.printStackTrace();
			}
			try {
				m_is.close();
			} catch(Exception x) {
				x.printStackTrace();
			}
			try {
				m_socket.close();
			} catch(Exception x) {
				x.printStackTrace();
			}
		}

		public PrintWriter pw() {
			return m_pw;
		}

		public void run() {
			try {
				m_pw = new PrintWriter(new OutputStreamWriter(m_os, "utf-8"));
				execute();
			} catch(Exception x) {
				x.printStackTrace();
			} finally {
				close();
			}
		}
	}


}
