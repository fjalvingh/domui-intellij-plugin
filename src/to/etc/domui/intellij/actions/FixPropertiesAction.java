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
import com.intellij.psi.PsiClassType;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiExpression;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiMethodCallExpression;
import com.intellij.psi.PsiParameter;
import com.intellij.psi.PsiParameterList;
import com.intellij.psi.PsiReference;
import com.intellij.psi.PsiReferenceExpression;
import com.intellij.psi.PsiType;
import com.intellij.psi.PsiTypeParameter;
import com.intellij.psi.impl.source.PsiClassReferenceType;
import com.intellij.psi.util.PsiTreeUtil;
import com.siyeh.ig.psiutils.ExpressionUtils;

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

			//System.out.println("Target class = " + targetClass.getQualifiedName() + " method=" + methodName);

			QFieldMethod replacementMethod = findQFieldMethod(targetClass, methodName, mc.getArgumentList().getExpressionTypes());
			if(null == replacementMethod) {
				System.out.println(" - no alt found");
				return;
			}
			System.out.println(" - replacement " + replacementMethod.getMethod().getText());

			//-- Find the type that needs to have properties generated,
			PsiType dataClass = findPropertySourceClass(mc, replacementMethod.getMethod(), replacementMethod.getqIndex());
			if(null == dataClass) {
				System.out.println("- cannot find type of object");
				return;
			}
			System.out.println("- property is on " + dataClass.getCanonicalText());

			//-- We can do this but we need the target to be annotated
			PsiExpression propertyExpr = mc.getArgumentList().getExpressions()[replacementMethod.getqIndex()];
			String path = calculatePropertyPath(propertyExpr);
			if(null == path) {
				System.out.println("- cannot resolve propertyExpr " + propertyExpr.getText());
				return;
			}
			System.out.println("- property expr resolves to " + path);


		}

		private String calculatePropertyPath(PsiExpression propertyExpr) {
			try {
				Object o = ExpressionUtils.computeConstantExpression(propertyExpr);
				if(o instanceof String) {
					return (String) o;
				}
			} catch(Exception x) {
			}
			return null;
		}

		private PsiType findPropertySourceClass(PsiMethodCallExpression mc, PsiMethod replacementMethod, int qFieldIndex) {
			PsiParameterList parameterList = replacementMethod.getParameterList();
			PsiParameter[] parameters = parameterList.getParameters();
			if(qFieldIndex == 1) {
				//-- We would expect the parameter before the qfield to be the type - it must be Object or generic
				PsiType formalType = parameters[0].getType();
				if(formalType instanceof PsiClassReferenceType) {
					PsiClassReferenceType rt = (PsiClassReferenceType) formalType;
					PsiClass resolvedType = rt.resolve();
					if(resolvedType instanceof PsiTypeParameter) {
						//-- It is -> get the actual's type
						PsiType actualType = mc.getArgumentList().getExpressionTypes()[0];
						return actualType;
					}
				}
				return null;
			}

			if(qFieldIndex == 0) {
				//-- We should look at a class type parameter of the actual class mc is method of
				PsiReference reference = mc.getMethodExpression().getReference();	// rr.column
				if(null == reference)
					return null;

				if(reference instanceof PsiReferenceExpression) {
					PsiReferenceExpression rx = (PsiReferenceExpression) reference; // rr.column still
					PsiExpression subref = rx.getQualifierExpression();
					//System.out.println("* " + subref);								// rr only, now
					PsiType type = subref.getType();
					//System.out.println("*  type " + type.getCanonicalText());		// to.etc.domui.component.tbl.RowRenderer<to.etc.domuidemo.pages.binding.xxxmodel.InvoiceLineModel>

					//-- We should have a generic instantiation as the type
					if(type instanceof PsiClassType) {
						PsiClassType ct = (PsiClassType) type;

						PsiType[] typeParams = ct.getParameters();
						if(typeParams != null && typeParams.length > 0) {
							PsiType typeParam = typeParams[0];
							//System.out.println("  * contained type is " + typeParam);
							return typeParam;
						}
					}
				}
			}
			return null;
		}


		/**
		 * Find a method similar in nam and arguments to the passed method, but with one of the
		 * String parameters changed to QField.
		 */
		private QFieldMethod findQFieldMethod(PsiClass targetClass, String methodName, PsiType[] matchingTypes) {
			PsiMethod[] methods = targetClass.getAllMethods();
			for(PsiMethod method : methods) {
				if(method.getName().equals(methodName)) {
					int index = methodHasSimilarParameters(method, matchingTypes);
					if(index >= 0) {
						return new QFieldMethod(method, index);
					}
				}
			}
			return null;
		}

		private int methodHasSimilarParameters(PsiMethod method, PsiType[] matchingTypes) {
			PsiParameterList parameterList = method.getParameterList();
			PsiParameter[] parameters = parameterList.getParameters();
			if(parameters.length != matchingTypes.length)
				return -1;

			int gotQField = -1;
			for(int i = 0; i < parameters.length; i++) {
				PsiParameter parameter = parameters[i];
				PsiType formalType = parameter.getType();
				PsiType actualType = matchingTypes[i];
				if(null == formalType || null == actualType)
					return -1;

				if(formalType.getCanonicalText().startsWith(QFIELD_FQN + "<")) {		// Formal is QField
					//-- Actual must be String for match
					if(actualType.getCanonicalText().startsWith(STRING_FQN)) {
						//-- Match && qfield found
						if(gotQField != -1)						// Second one?
							return -1;
						gotQField = i;
					} else {
						return -1;
					}
				} else {
					//-- Does the formal accept the actual?
					if(! formalType.isConvertibleFrom(actualType))
						return -1;
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


	private static class QFieldMethod {
		final private PsiMethod m_method;

		final private int m_qIndex;

		public QFieldMethod(PsiMethod method, int qIndex) {
			m_method = method;
			m_qIndex = qIndex;
		}

		public PsiMethod getMethod() {
			return m_method;
		}

		public int getqIndex() {
			return m_qIndex;
		}
	}

}
