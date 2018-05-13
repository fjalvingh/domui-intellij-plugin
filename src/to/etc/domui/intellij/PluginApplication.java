package to.etc.domui.intellij;

import com.intellij.openapi.components.ApplicationComponent;
import to.etc.domui.intellij.runtime.DomUIDebugListener;

/**
 * @author <a href="mailto:jal@etc.to">Frits Jalvingh</a>
 * Created on 26-4-18.
 */
public class PluginApplication implements ApplicationComponent {
	@Override
	public void initComponent() {
		System.out.println("domui Initializing");
		DomUIDebugListener.INSTANCE.start();
	}

	@Override public void disposeComponent() {
		DomUIDebugListener.INSTANCE.stop();
	}
}
