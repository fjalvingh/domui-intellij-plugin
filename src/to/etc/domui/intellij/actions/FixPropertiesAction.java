package to.etc.domui.intellij.actions;

import com.intellij.codeInsight.AnnotationUtil;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.LangDataKeys;
import com.intellij.openapi.actionSystem.PlatformDataKeys;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.command.CommandProcessor;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.vfs.ReadonlyStatusHandler;
import com.intellij.openapi.vfs.ReadonlyStatusHandler.OperationStatus;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.JavaRecursiveElementVisitor;
import com.intellij.psi.PsiAnnotation;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiClassType;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiElementFactory;
import com.intellij.psi.PsiExpression;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiMethodCallExpression;
import com.intellij.psi.PsiPrimitiveType;
import com.intellij.psi.PsiType;
import com.intellij.psi.codeStyle.JavaCodeStyleManager;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.util.PsiTreeUtil;
import com.siyeh.ig.psiutils.ExpressionUtils;
import to.etc.domui.intellij.FixUtils;
import to.etc.domui.intellij.FixUtils.QFieldMethod;

/**
 *
 * Used examples:
 * <ul>
 *     <li>https://github.com/joewalnes/idea-community/blob/master/plugins/testng/src/com/theoryinpractice/testng/inspection/JUnitConvertTool.java</li>
 * </ul>
 *
 *
 * @author <a href="mailto:jal@etc.to">Frits Jalvingh</a>
 * Created on 26-4-18.
 */
public class FixPropertiesAction extends AnAction {

	static private final String GENPROP_FQN = "to.etc.annotations.GenerateProperties";


	@Override public void actionPerformed(AnActionEvent e) {
		//Messages.showErrorDialog("You're not doing it right", "Welcome");
		VirtualFile myFile = e.getData(PlatformDataKeys.VIRTUAL_FILE);
		Editor editor = e.getData(PlatformDataKeys.EDITOR);
		if(null == editor) {
			Messages.showErrorDialog("This action is only valid inside an editor", "Welcome");
			return;
		}
		//Document document = editor.getDocument();
		Project project = e.getData(PlatformDataKeys.PROJECT);
		if(null == project) {
			Messages.showErrorDialog("This action is only valid inside a project", "Welcome");
			return;
		}

		final PsiManager psiManager = PsiManager.getInstance(project);

		OperationStatus os = ReadonlyStatusHandler.getInstance(project).ensureFilesWritable(myFile);
		if(os.hasReadonlyFiles()) {
			Messages.showErrorDialog("The file(s) are readonly", "Welcome");
			return;
		}

		PsiFile psiFile = e.getData(LangDataKeys.PSI_FILE);
		if(null == psiFile) {
			Messages.showErrorDialog("Current file is not known", "Welcome");
			return;
		}

		//System.out.println("Psi: ===================");
		//System.out.println(psiFile.toString());

		FixPropertiesCommand fp = new FixPropertiesCommand(project, psiManager, psiFile);

		CommandProcessor.getInstance().executeCommand(project, fp, "Fix Properties", null);
	}

	@Override public void update(AnActionEvent e) {
		e.getPresentation().setEnabled(isEnabled(e));
	}

	/**
	 * Only enable the action when we're properly inside some file.
	 */
	private boolean isEnabled(AnActionEvent e) {
		Editor editor = e.getData(PlatformDataKeys.EDITOR);
		if(null == editor)
			return false;
		Project project = e.getData(PlatformDataKeys.PROJECT);
		if(null == project)
			return false;
		PsiFile psiFile = e.getData(LangDataKeys.PSI_FILE);
		if(null == psiFile)
			return false;
		return true;
	}

	private class FixPropertiesCommand implements Runnable {
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
				m_psiFile.accept(new MethodWithPropertyVisitor(m_project, m_psiManager, m_javaPsiFacade, m_psiElementFactory));
			});
		}
	}

	private class MethodWithPropertyVisitor extends JavaRecursiveElementVisitor {

		private final Project m_project;

		private final PsiManager m_psiManager;

		private final JavaPsiFacade m_javaPsiFacade;

		private final PsiElementFactory m_psiElementFactory;

		public MethodWithPropertyVisitor(Project project, PsiManager psiManager, JavaPsiFacade javaPsiFacade, PsiElementFactory psiElementFactory) {
			m_project = project;
			m_psiManager = psiManager;
			m_javaPsiFacade = javaPsiFacade;
			m_psiElementFactory = psiElementFactory;
		}

		/**
		 * If the method call in the resolved class has an alternative that has
		 * a QField as an alternate to a String then replace the string expression
		 * with the equivalent QField expression.
		 */
		@Override public void visitMethodCallExpression(PsiMethodCallExpression mc) {
			super.visitMethodCallExpression(mc);
			if(!FixUtils.hasStringParameters(mc))
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

			QFieldMethod replacementMethod = FixUtils.findQFieldMethod(targetClass, methodName, mc.getArgumentList().getExpressionTypes());
			if(null == replacementMethod) {
				System.out.println(" - no alt found");
				return;
			}
			System.out.println(" - replacement " + replacementMethod.getMethod().getText());

			//-- Find the type that needs to have properties generated,
			PsiType dataClassTypeRef = FixUtils.findPropertySourceClass(mc, replacementMethod.getMethod(), replacementMethod.getqIndex());
			if(null == dataClassTypeRef) {
				System.out.println("- cannot find type of object");
				return;
			}
			System.out.println("- property is on " + dataClassTypeRef.getCanonicalText());
			if(! (dataClassTypeRef instanceof PsiClassType)) {
				System.out.println("- data class is not of type Class");
				return;
			}
			PsiClassType dataClassType = (PsiClassType) dataClassTypeRef;

			PsiClass dataClass = dataClassType.resolve();
			if(null == dataClass)
				return;

			//-- We can do this but we need the target to be annotated
			PsiExpression propertyExpr = mc.getArgumentList().getExpressions()[replacementMethod.getqIndex()];
			String path = calculatePropertyPath(propertyExpr);
			if(null == path) {
				System.out.println("- cannot resolve propertyExpr " + propertyExpr.getText());
				return;
			}
			System.out.println("- property expr resolves to " + path);

			//-- We need a reference to the data class with a _ appended
			String typedClassName = dataClass.getQualifiedName() + "_";
			PsiClass aClass = m_javaPsiFacade.findClass(typedClassName, GlobalSearchScope.allScope(m_project));

			//-- Annotate the target class
			String callPath = resolvePropertyPath(dataClassType, path);
			if(null == callPath)
				return;

			PsiExpression ref = m_psiElementFactory.createExpressionFromText(typedClassName + "." + callPath, mc);
			propertyExpr.replace(ref);
			JavaCodeStyleManager.getInstance(m_project).shortenClassReferences(mc);
		}

		private String resolvePropertyPath(PsiClassType rootType, String path) {
			StringBuilder sb = new StringBuilder();
			String[] segments = path.split("\\.");
			PsiClassType ct = rootType;
			for(String segment : segments) {
				if(sb.length() > 0)
					sb.append('.');
				sb.append(segment).append("()");

				//-- First: Annotate whatever class
				addAnnotationToDataClass(ct);					// Make sure the class the property comes from is annotated

				//-- Find the next step.
				PsiMethod getter = findProperty(ct, segment);
				if(null == getter) {
					return sb.toString();
				}

				PsiType returnType = getter.getReturnType();
				if(returnType instanceof PsiPrimitiveType) {
					System.out.println(">> at " + segment + " is primitive");
					return sb.toString();
				}

				if(returnType instanceof PsiClassType) {
					ct = (PsiClassType) returnType;


				} else {
					System.out.println(">> at " + segment + " no class");
					return null;
				}
			}
			return sb.toString();
		}

		private PsiMethod findProperty(PsiClassType rt, String name) {
			PsiClass psiClass = rt.resolve();
			if(null == psiClass)
				return null;

			String javaCrapName = crapName(name);
			PsiMethod[] allMethods = psiClass.getAllMethods();
			for(PsiMethod method : allMethods) {
				String s = method.getName();
				if(s.startsWith("is")) {
					if(s.substring(2).equals(javaCrapName)) {
						return method;
					}
				} else if(s.startsWith("get")) {
					if(s.substring(3).equals(javaCrapName)) {
						return method;
					}
				}
			}
			return null;
		}

		/**
		 * Convert a property name to the crappy java method name, without the is or get.
		 */
		private String crapName(String name) {
			if(name.length() > 1) {
				if(Character.isUpperCase(name.charAt(0)))
					return name;
			}
			return Character.toUpperCase(name.charAt(0)) + name.substring(1);
		}


		/**
		 * Is the class already annotated?
		 */
		private void addAnnotationToDataClass(PsiClassType rt ) {
			PsiClass psiClass = rt.resolve();
			if(null == psiClass)
				return;
			if(hasAnnotation(psiClass, GENPROP_FQN))
				return;

			PsiAnnotation annotation = m_psiElementFactory.createAnnotationFromText("@" + GENPROP_FQN, psiClass);
			JavaCodeStyleManager.getInstance(annotation.getProject()).shortenClassReferences(psiClass.getModifierList().addAfter(annotation, null));
		}

		private boolean hasAnnotation(PsiClass psiClass, String name) {
			return null != AnnotationUtil.findAnnotation(psiClass, name);
		}

		/**
		 * Resolve the full property path.
		 */
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





	}

}
