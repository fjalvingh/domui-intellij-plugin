package to.etc.domui.intellij;

/**
 * Singleton which listens on the DomUI debug port for commands
 * sent by the DomUI application.
 *
 * @author <a href="mailto:jal@etc.to">Frits Jalvingh</a>
 * Created on 11-5-18.
 */
final public class DomUIDebugListener {
	static public final DomUIDebugListener INSTANCE = new DomUIDebugListener();

	private int m_port = 5051;

	public int getPort() {
		return m_port;
	}
}
