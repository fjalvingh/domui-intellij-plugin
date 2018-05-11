package to.etc.domui.intellij.intentions;

import com.intellij.codeInsight.intention.impl.BaseIntentionAction;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiElementFactory;
import com.intellij.psi.PsiFile;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import to.etc.domui.intellij.actions.FixPropertyReferencesMethodVisitor;

/**
 * @author <a href="mailto:jal@etc.to">Frits Jalvingh</a>
 * Created on 11-5-18.
 */
public class TypefulPropertyQuickFix extends BaseIntentionAction {

	public TypefulPropertyQuickFix() {
	}

	@NotNull @Override public String getText() {
		return "Replace with typed property";
	}

	@Nls @NotNull @Override public String getFamilyName() {
		return "DomUI Typed Properties";
	}

	@Override public boolean isAvailable(@NotNull Project project, Editor editor, PsiFile file) {
		return true;
	}

	@Override public void invoke(@NotNull Project project, Editor editor, PsiFile file) throws IncorrectOperationException {
		JavaPsiFacade javaPsiFacade = JavaPsiFacade.getInstance(project);
		PsiElementFactory psiElementFactory = javaPsiFacade.getElementFactory();

		ApplicationManager.getApplication().runWriteAction(() -> {
			file.accept(new FixPropertyReferencesMethodVisitor(project, javaPsiFacade, psiElementFactory));
		});
	}
}
