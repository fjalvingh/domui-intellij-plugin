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
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiMethodCallExpression;
import com.intellij.psi.PsiParameter;
import com.intellij.psi.PsiParameterList;
import com.intellij.psi.PsiType;
import com.intellij.psi.util.PsiTreeUtil;

/**
 * @author <a href="mailto:jal@etc.to">Frits Jalvingh</a>
 * Created on 26-4-18.
 */
public class FixPropertiesAction extends AnAction {
	static private final String QFIELD_FQN = "to.etc.webapp.query.QField";


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

		public static final String STRING_FQN = "java.lang.String";

		/**
		 * If the method call in the resolved class has an alternative that has
		 * a QField as an alternate to a String then replace the string expression
		 * with the equivalent QField expression.
		 */
		@Override public void visitMethodCallExpression(PsiMethodCallExpression mc) {
			super.visitMethodCallExpression(mc);
			if(! hasStringParameters(mc))
				return;

			String methodName = mc.getMethodExpression().getReferenceName();
			PsiElement targetMethod = mc.getMethodExpression().getReference().resolve();
			if(null == targetMethod || null == methodName)
				return;

			//-- Does the class that is the target have a method that has the same name and signature,
			//-- but with one String replaced by a QField?
			PsiClass targetClass = PsiTreeUtil.getParentOfType(targetMethod, PsiClass.class);
			if(null == targetClass)
				return;

			System.out.println("Target class = " + targetClass.getQualifiedName() + " method=" + methodName);

			PsiMethod replacementMethod = findQFieldMethod(targetClass, methodName, mc.getArgumentList().getExpressionTypes());
			if(null == replacementMethod) {
				System.out.println(" - no alt found");
				return;
			}
			System.out.println(" - replacement " + replacementMethod.getText());

			////-- One of the types must be string
			//for(int i = 0; i < mc.getArgumentList().getExpressionTypes().length; i++) {
			//	PsiType psiType = mc.getArgumentList().getExpressionTypes()[i];
			//
			//	String canonicalText = psiType.getCanonicalText();
			//	System.out.println(">> " + mc.getMethodExpression().getReferenceName() + " type " + i + " = " + canonicalText);
			//
			//
			//	System.out.println(">> resolve = " + resolve.getText());
			//
			//}
			//

		}

		private PsiMethod findQFieldMethod(PsiClass targetClass, String methodName, PsiType[] matchingTypes) {
			PsiMethod[] methods = targetClass.getAllMethods();
			for(PsiMethod method : methods) {
				if(method.getName().equals(methodName)) {
					if(methodHasSimilarParameters(method, matchingTypes)) {
						return method;
					}
				}
			}
			return null;
		}

		private boolean methodHasSimilarParameters(PsiMethod method, PsiType[] matchingTypes) {
			PsiParameterList parameterList = method.getParameterList();
			PsiParameter[] parameters = parameterList.getParameters();
			if(parameters.length != matchingTypes.length)
				return false;

			boolean gotQField = false;
			for(int i = 0; i < parameters.length; i++) {
				PsiParameter parameter = parameters[i];
				PsiType formalType = parameter.getType();
				PsiType actualType = matchingTypes[i];
				if(null == formalType || null == actualType)
					return false;

				if(formalType.getCanonicalText().startsWith(QFIELD_FQN + "<")) {		// Formal is QField
					//-- Actual must be String for match
					if(actualType.getCanonicalText().startsWith(STRING_FQN)) {
						//-- Match && qfield found
						gotQField = true;
					} else {
						return false;
					}
				} else {
					//-- Does the formal accept the actual?
					if(! formalType.isConvertibleFrom(actualType))
						return false;
				}
			}
			return gotQField;
		}


		private boolean hasStringParameters(PsiMethodCallExpression mc) {
			if(mc.getArgumentList().isEmpty()) {
				return false;
			}
			for(int i = 0; i < mc.getArgumentList().getExpressionTypes().length; i++) {
				PsiType psiType = mc.getArgumentList().getExpressionTypes()[i];
				if(psiType != null && psiType.getCanonicalText().equals(STRING_FQN))
					return true;
			}
			return false;
		}

	}




}