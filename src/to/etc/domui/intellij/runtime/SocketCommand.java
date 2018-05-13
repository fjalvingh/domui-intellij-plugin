package to.etc.domui.intellij.runtime;

import com.intellij.openapi.editor.CaretModel;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.LogicalPosition;
import com.intellij.openapi.editor.ScrollType;
import com.intellij.openapi.fileEditor.FileEditor;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.fileEditor.TextEditor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.net.Socket;

/**
 * @author <a href="mailto:jal@etc.to">Frits Jalvingh</a>
 * Created on 13-5-18.
 */
final class SocketCommand implements Runnable {
	final private Socket m_socket;

	final private OutputStream m_os;

	private final String m_command;

	private final InputStream m_is;

	private PrintWriter m_pw;

	private int m_length;

	private int m_index;

	private StringBuilder m_copyBuffer = new StringBuilder();

	public SocketCommand(Socket socket, InputStream is, OutputStream os, String command) {
		m_socket = socket;
		m_is = is;
		m_os = os;
		m_command = command;
		m_length = command.length();
		m_index = 0;
	}

	public void execute() throws Exception {

		System.out.println("DOMUI RECEIVED: " + m_command);

		String command = scanWord();
		if("SELECT".equalsIgnoreCase(command)) {
			String what = scanWord();					// The webapp that sent the command- accept always

			if(null == what) {
				pw().println("SELECT-FAILED");
				return;
			}

			command = scanWord();
		}

		//-- Handle commands
		try {
			if("OPENFILE".equals(command)) {
				String file = scanWord();
				if(null == file)
					throw new RuntimeException("Missing file name after OpenFile");
				openFile(file);
			} else {
				//-- Unsupported, return an error
				pw().println("ERROR Unknown command '" + command + "'");
			}
		} catch(IOException iox) {
			iox.printStackTrace();
		} catch(CmdException x) {
			pw().println("ERROR " + x.getMessage());
		} catch(Exception x) {
			pw().println("ERROR " + x.toString());
		}
	}

	/**
	 * Format:
	 * <pre>
	 *     OPENFILE `to/etc/domui/hibernate/types/PersistentObservableList.java#72`
	 * </pre>
	 */
	private void openFile(String in) throws Exception {
		//-- Split into line# and file
		int pos = in.indexOf('#');
		int line;
		String file;
		if(pos == -1) {
			line = -1;
			file = in;
		} else {
			file = in.substring(0, pos);
			line = Integer.parseInt(in.substring(pos + 1));
		}

		//-- Work around a bug in DomUI where it reports an inner class or lambda; remove that class.
		pos = file.lastIndexOf('/');
		int dpos = file.indexOf('$', pos + 1);
		if(dpos > 0) {
			file = file.substring(0, dpos) + ".java";
		}

		PrjFile prjFile = findProjectAndFile(file);
		if(null == prjFile) {
			throw new CmdException("Can't locate project for " + in + " (" + file + ")");
		}

		/*
		 * Try to move the caret to the correct line number, if applicable.
		 */
		FileEditorManager editorManager = FileEditorManager.getInstance(prjFile.getProject());
		FileEditor[] fileEditors = editorManager.openFile(prjFile.getFile(), true);
		if(fileEditors != null && fileEditors.length >= 0 && line != -1) {
			FileEditor fileEditor = fileEditors[0];
			if(fileEditor instanceof TextEditor) {
				TextEditor tedit = (TextEditor) fileEditor;
				Editor editor = tedit.getEditor();
				Document document = editor.getDocument();
				if(line <= document.getLineCount()) {
					CaretModel caretModel = editor.getCaretModel();
					caretModel.moveToLogicalPosition(new LogicalPosition(line -1, 0));
					editor.getScrollingModel().scrollToCaret(ScrollType.CENTER);
				}
			}
		}


		pw().println("OK");
	}

	private Project findProjectFor(String file) {
		Project[] openProjects = ProjectManager.getInstance().getOpenProjects();
		if(null == openProjects)
			throw new IllegalStateException("No open project(s)");
		for(Project prj : openProjects) {
			String canonicalPath = prj.getBaseDir().getCanonicalPath();
			if(file.startsWith( canonicalPath + "/")) {
				return prj;
			}
		}
		return null;
	}

	private PrjFile findProjectAndFile(String file) {
		Project[] openProjects = ProjectManager.getInstance().getOpenProjects();
		if(null == openProjects)
			throw new IllegalStateException("No open project(s)");
		for(Project prj : openProjects) {
			for(VirtualFile srcRoot : ProjectRootManager.getInstance(prj).getContentSourceRoots()) {
				VirtualFile relativeFile = VfsUtilCore.findRelativeFile(file, srcRoot);
				if(null != relativeFile) {
					if(relativeFile.exists()) {
						return new PrjFile(prj, relativeFile);
					}
				}
			}
		}
		return null;
	}


	private class PrjFile {
		private final Project m_project;

		private final VirtualFile m_file;

		public PrjFile(Project project, VirtualFile file) {
			m_project = project;
			m_file = file;
		}

		public Project getProject() {
			return m_project;
		}

		public VirtualFile getFile() {
			return m_file;
		}
	}



	private int LA() {
		if(m_index >= m_length)
			return -1;
		return m_command.charAt(m_index) & 0xffff;
	}

	private void accept() {
		m_index++;
	}

	private void copy() {
		if(m_index >= m_length)
			return;
		m_copyBuffer.append(m_command.charAt(m_index++));
	}

	private String scanWord() {
		m_copyBuffer.setLength(0);
		while(Character.isWhitespace(LA()))
			accept();

		int c = LA();
		if(c == -1)
			return null;

		int qc = 0;
		if(c == '\'' || c == '"' || c == '`') {
			//-- Collect $
			qc = c;
			accept();
			for(; ; ) {
				c = LA();
				if(c == -1)
					break;
				else if(c == qc) {
					accept();
					break;
				} else {
					copy();
				}
			}
		} else {
			//-- Collect ws-separated word.
			for(; ; ) {
				c = LA();
				if(c == -1)
					break;
				else if(Character.isWhitespace(c)) {
					accept();
					break;
				} else {
					copy();
				}
			}
		}
		return m_copyBuffer.toString();
	}

	public void close() {
		if(m_pw != null) {
			try {
				m_pw.flush();
				m_pw.close();
			} catch(Exception x) {
				x.printStackTrace();
			}
			m_pw = null;
		}
		try {
			m_os.close();
		} catch(Exception x) {
			x.printStackTrace();
		}
		try {
			m_is.close();
		} catch(Exception x) {
			x.printStackTrace();
		}
		try {
			m_socket.close();
		} catch(Exception x) {
			x.printStackTrace();
		}
	}

	public PrintWriter pw() {
		return m_pw;
	}

	public void run() {
		try {
			m_pw = new PrintWriter(new OutputStreamWriter(m_os, "utf-8"));
			execute();
		} catch(Exception x) {
			x.printStackTrace();
		} finally {
			close();
		}
	}
}
