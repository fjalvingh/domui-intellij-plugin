package to.etc.domui.intellij.actions;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.LangDataKeys;
import com.intellij.openapi.actionSystem.PlatformDataKeys;
import com.intellij.openapi.command.CommandProcessor;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.vfs.ReadonlyStatusHandler;
import com.intellij.openapi.vfs.ReadonlyStatusHandler.OperationStatus;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;

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
final public class FixPropertiesAction extends AnAction {

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

}
