package to.etc.domui.intellij.runtime;

import com.intellij.execution.ExecutionException;
import com.intellij.execution.RunConfigurationExtension;
import com.intellij.execution.configurations.JavaParameters;
import com.intellij.execution.configurations.RunConfigurationBase;
import com.intellij.execution.configurations.RunnerSettings;
import org.jetbrains.annotations.NotNull;

/**
 * This adds the DomUI command port as a command line parameter to the server startup line, when
 * the Tomcat server is started from IntelliJ.
 *
 * @author <a href="mailto:jal@etc.to">Frits Jalvingh</a>
 * Created on 11-5-18.
 */
public class DomUIDebugPortConfigurationExtension extends RunConfigurationExtension {
	@Override public <T extends RunConfigurationBase> void updateJavaParameters(T configuration, JavaParameters params, RunnerSettings runnerSettings) throws ExecutionException {
		System.out.println("FIXING: ");

		int port = DomUIDebugListener.INSTANCE.getPort();
		if(port >= 1024) {
			params.getVMParametersList().add("-DDOMUIDBGPORT="+port);
		}
	}

	@Override protected boolean isApplicableFor(@NotNull RunConfigurationBase configuration) {
		return true;
	}
}
