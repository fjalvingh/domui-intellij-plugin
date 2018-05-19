package to.etc.domui.intellij;

import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiClassType;
import com.intellij.psi.PsiExpression;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiMethodCallExpression;
import com.intellij.psi.PsiParameter;
import com.intellij.psi.PsiParameterList;
import com.intellij.psi.PsiReference;
import com.intellij.psi.PsiReferenceExpression;
import com.intellij.psi.PsiType;
import com.intellij.psi.PsiTypeParameter;
import com.intellij.psi.impl.source.PsiClassReferenceType;

/**
 * @author <a href="mailto:jal@etc.to">Frits Jalvingh</a>
 * Created on 11-5-18.
 */
final public class FixUtils {
	public static final String STRING_FQN = "java.lang.String";

	static private final String QFIELD_FQN = "to.etc.webapp.query.QField";

	public static final String GENPROP_FQN = "to.etc.annotations.GenerateProperties";

	private FixUtils() {
	}

	static public boolean hasStringParameters(PsiMethodCallExpression mc) {
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

	static public PsiType findPropertySourceClass(PsiMethodCallExpression mc, PsiMethod replacementMethod, int qFieldIndex) {
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
			PsiReference reference = mc.getMethodExpression().getReference();    // rr.column
			if(null == reference)
				return null;

			if(reference instanceof PsiReferenceExpression) {
				PsiReferenceExpression rx = (PsiReferenceExpression) reference; // rr.column still
				PsiExpression subref = rx.getQualifierExpression();
				//System.out.println("* " + subref);								// rr only, now
				if(null != subref) {
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
		}
		return null;
	}


	/**
	 * Find a method similar in nam and arguments to the passed method, but with one of the
	 * String parameters changed to QField.
	 */
	static public QFieldMethod findQFieldMethod(PsiClass targetClass, String methodName, PsiType[] matchingTypes) {
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

	static private int methodHasSimilarParameters(PsiMethod method, PsiType[] matchingTypes) {
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

			if(formalType.getCanonicalText().startsWith(QFIELD_FQN + "<")) {        // Formal is QField
				//-- Actual must be String for match
				if(actualType.getCanonicalText().startsWith(FixUtils.STRING_FQN)) {
					//-- Match && qfield found
					if(gotQField != -1)                        // Second one?
						return -1;
					gotQField = i;
				} else {
					return -1;
				}
			} else {
				//-- Does the formal accept the actual?
				if(!formalType.isConvertibleFrom(actualType))
					return -1;
			}
		}
		return gotQField;
	}


	public static class QFieldMethod {
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
