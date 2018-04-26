package to.etc.domui.intellij.actions;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.LangDataKeys;
import com.intellij.openapi.actionSystem.PlatformDataKeys;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.vfs.ReadonlyStatusHandler;
import com.intellij.openapi.vfs.ReadonlyStatusHandler.OperationStatus;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.JavaRecursiveElementVisitor;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiMethodCallExpression;
import com.intellij.psi.PsiType;

/**
 * @author <a href="mailto:jal@etc.to">Frits Jalvingh</a>
 * Created on 26-4-18.
 */
public class FixPropertiesAction extends AnAction {
	@Override public void actionPerformed(AnActionEvent e) {
		//Messages.showErrorDialog("You're not doing it right", "Welcome");
		VirtualFile myFile = e.getData(PlatformDataKeys.VIRTUAL_FILE);
		Document document = e.getData(PlatformDataKeys.EDITOR).getDocument();
		Project project = e.getData(PlatformDataKeys.PROJECT);

		OperationStatus os = ReadonlyStatusHandler.getInstance(project).ensureFilesWritable(myFile);
		if(os.hasReadonlyFiles()) {
			Messages.showErrorDialog("The file(s) are readonly", "Welcome");
			return;
		}

		PsiFile psiFile = e.getData(LangDataKeys.PSI_FILE);
		System.out.println("Psi: ===================");
		System.out.println(psiFile.toString());

		FixPropertiesCommand fp = new FixPropertiesCommand(psiFile);

		com.intellij.openapi.command.CommandProcessor.getInstance().executeCommand(project, fp, "Fix Properties", null);




	}

	private class FixPropertiesCommand implements Runnable {
		private final PsiFile m_psiFile;

		public FixPropertiesCommand(PsiFile psiFile) {
			m_psiFile = psiFile;
		}

		@Override public void run() {
			ApplicationManager.getApplication().runWriteAction(() -> {
				m_psiFile.accept(new MethodWithPropertyVisitor());
			});
		}
	}

	private class MethodWithPropertyVisitor extends JavaRecursiveElementVisitor {

		/**
		 * If the method call in the resolved class has an alternative that has
		 * a QField as an alternate to a String then replace the string expression
		 * with the equivalent QField expression.
		 */
		@Override public void visitMethodCallExpression(PsiMethodCallExpression mc) {
			super.visitMethodCallExpression(mc);
			if(mc.getArgumentList().isEmpty()) {
				return;
			}

			//-- One of the types must be string
			for(int i = 0; i < mc.getArgumentList().getExpressionTypes().length; i++) {
				PsiType psiType = mc.getArgumentList().getExpressionTypes()[i];

				String canonicalText = psiType.getCanonicalText();
				System.out.println(">> " + mc.getMethodExpression().getReferenceName() + " type " + i + " = " + canonicalText);
			}


		}
	}



}
