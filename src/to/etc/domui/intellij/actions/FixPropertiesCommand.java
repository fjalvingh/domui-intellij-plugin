package to.etc.domui.intellij.actions;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiElementFactory;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;

/**
 * @author <a href="mailto:jal@etc.to">Frits Jalvingh</a>
 * Created on 11-5-18.
 */
final class FixPropertiesCommand implements Runnable {
	private final Project m_project;

	private final PsiManager m_psiManager;

	private final PsiFile m_psiFile;

	private final PsiElementFactory m_psiElementFactory;

	private final JavaPsiFacade m_javaPsiFacade;

	public FixPropertiesCommand(Project project, PsiManager psiManager, PsiFile psiFile) {
		m_project = project;
		m_psiManager = psiManager;
		m_psiFile = psiFile;
		JavaPsiFacade javaPsiFacade = m_javaPsiFacade = JavaPsiFacade.getInstance(m_psiManager.getProject());
		m_psiElementFactory = javaPsiFacade.getElementFactory();
	}

	@Override public void run() {
		//final PsiJavaFile javaFile = (PsiJavaFile)psiClass.getContainingFile();

		ApplicationManager.getApplication().runWriteAction(() -> {
			m_psiFile.accept(new FixPropertyReferencesMethodVisitor(m_project, m_javaPsiFacade, m_psiElementFactory));
		});
	}
}
