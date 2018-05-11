package to.etc.domui.intellij.intentions;

import com.intellij.lang.annotation.AnnotationHolder;
import com.intellij.lang.annotation.Annotator;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiClassType;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiExpression;
import com.intellij.psi.PsiMethodCallExpression;
import com.intellij.psi.PsiType;
import com.intellij.psi.util.PsiTreeUtil;
import org.jetbrains.annotations.NotNull;
import to.etc.domui.intellij.FixUtils;
import to.etc.domui.intellij.FixUtils.QFieldMethod;

/**
 * This tries to recognize occurrences of property strings in calls that
 * could be replaced by QField typed properties.
 *
 * @author <a href="mailto:jal@etc.to">Frits Jalvingh</a>
 * Created on 11-5-18.
 */
final public class TypedPropertiesAnnotator implements Annotator {
	@Override public void annotate(@NotNull PsiElement element, @NotNull AnnotationHolder holder) {
		if(! (element instanceof PsiMethodCallExpression))
			return;
		PsiMethodCallExpression mc = (PsiMethodCallExpression) element;

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
			//System.out.println(" - no alt found");
			return;
		}
		//System.out.println(" - replacement " + replacementMethod.getMethod().getText());

		//-- Find the type that needs to have properties generated,
		PsiType dataClassTypeRef = FixUtils.findPropertySourceClass(mc, replacementMethod.getMethod(), replacementMethod.getqIndex());
		if(null == dataClassTypeRef) {
			//System.out.println("- cannot find type of object");
			return;
		}
		//System.out.println("- property is on " + dataClassTypeRef.getCanonicalText());
		if(! (dataClassTypeRef instanceof PsiClassType)) {
			//System.out.println("- data class is not of type Class");
			return;
		}
		PsiClassType dataClassType = (PsiClassType) dataClassTypeRef;

		PsiClass dataClass = dataClassType.resolve();
		if(null == dataClass)
			return;

		//-- We can do a quick fix.
		PsiExpression propertyArgumentExpression = mc.getArgumentList().getExpressions()[replacementMethod.getqIndex()];
		holder.createWeakWarningAnnotation(propertyArgumentExpression, "Property string can be replaced with DomUI typed property")
			.registerFix(new TypefulPropertyQuickFix())
		;
	}
}
