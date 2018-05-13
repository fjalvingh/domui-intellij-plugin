package to.etc.domui.intellij.runtime;

import com.intellij.openapi.application.ApplicationManager;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
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
	private volatile boolean m_terminate;

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

	public void stop() {
		m_terminate = true;
		try {
			m_serverSocket.close();
		} catch(Exception x) {}
	}

	/**
	 * The actual server command loop: accept connections, read data, then execute command ad infinitum.
	 */
	void serverThread() {
		while(! m_terminate) {
			try {
				Socket accept = m_serverSocket.accept();
				if(m_terminate) {
					try {
						accept.close();
					} catch(Exception x) {
					}
					return;
				}
				handleSocket(accept);
			} catch(Exception x) {
				if(! m_terminate)
					x.printStackTrace();
			}
		}
	}

	/**
	 * Read an utf8 encoded command string, then execute it.
	 */
	private void handleSocket(Socket socket) {
		InputStream is = null;
		try {
			//-- Read byte array
			ByteArrayOutputStream baos = new ByteArrayOutputStream();
			is = socket.getInputStream();
			byte[] data = new byte[128];
			int szrd;
			while(0 < (szrd = is.read(data))) {
				if(data[szrd - 1] == 0) {
					baos.write(data, 0, szrd - 1);
					break;
				}

				baos.write(data, 0, szrd);
			}
			baos.close();

			//-- Convert to utf-8 string
			String commandString = new String(baos.toByteArray(), "UTF-8");
			//System.out.println("DomUI: received cmd=" + commandString);

			OutputStream outputStream = socket.getOutputStream();
			SocketCommand command = new SocketCommand(socket, is, outputStream, commandString);
			ApplicationManager.getApplication().invokeLater(command);
			socket = null;
			is = null;
		} catch(Exception x) {
			System.out.println("Socket command failed: " + x);
		} finally {
			try {
				if(is != null)
					is.close();
			} catch(Exception x) {}
			try {
				if(socket != null)
					socket.close();
			} catch(Exception x) {}
		}
	}

}
