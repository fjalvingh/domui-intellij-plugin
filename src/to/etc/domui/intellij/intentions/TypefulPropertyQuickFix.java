package to.etc.domui.intellij.intentions;

import com.intellij.codeInsight.intention.impl.BaseIntentionAction;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiFile;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;

/**
 * @author <a href="mailto:jal@etc.to">Frits Jalvingh</a>
 * Created on 11-5-18.
 */
public class TypefulPropertyQuickFix extends BaseIntentionAction {
	private String m_key;

	public TypefulPropertyQuickFix(String key) {
		m_key = key;
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
	}
}
