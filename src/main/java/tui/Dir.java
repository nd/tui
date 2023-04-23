package tui;

import com.github.markusbernhardt.proxy.util.PlatformUtil;
import com.intellij.CommonBundle;
import com.intellij.ide.DataManager;
import com.intellij.ide.FileSelectInContext;
import com.intellij.ide.SelectInContext;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.ScrollType;
import com.intellij.openapi.editor.actionSystem.EditorActionHandler;
import com.intellij.openapi.editor.actionSystem.EditorActionManager;
import com.intellij.openapi.editor.actionSystem.TypedActionHandler;
import com.intellij.openapi.fileChooser.FileChooserDescriptor;
import com.intellij.openapi.fileChooser.FileChooserFactory;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.fileEditor.TextEditor;
import com.intellij.openapi.fileEditor.impl.text.TextEditorCustomizer;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.ide.CopyPasteManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogPanel;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.NlsContexts;
import com.intellij.openapi.util.NlsSafe;
import com.intellij.openapi.util.UserDataHolder;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileManager;
import com.intellij.ui.TextFieldWithHistoryWithBrowseButton;
import com.intellij.util.ObjectUtils;
import com.intellij.util.PlatformIcons;
import com.intellij.util.ui.FormBuilder;
import com.intellij.util.ui.JBUI;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.awt.datatransfer.StringSelection;
import java.nio.file.Path;
import java.util.List;
import java.util.*;

public class Dir implements TypedActionHandler {
  private static final Key<VirtualFile> DIR = Key.create("tui.dir.currentDir");
  private static final Key<List<VirtualFile>> FILES = Key.create("tui.dir.files");
  private static final Map<String, TypedActionHandler> DEFAULT_KEY_MAP = new HashMap<>() {{
    put("\n", Dir::openFileUnderCaret);
    put("j", Dir::down);
    put("k", Dir::up);
    put("g", Dir::refresh);
    put("p", Dir::copyPathUnderCaret);
    put("u", Dir::gotoParentDir);
    put("q", Dir::closeDir);
  }};

  public static void openAsText(@NotNull Project project, @NotNull VirtualFile dir, @Nullable VirtualFile focus) {
    TuiFile file = TuiFS.getInstance().createFile(project, "", DirFileType.INSTANCE);
    file.setWritable(false);
    TuiService.getInstance().setTui(file, true);
    Tui.setTypingHandler(file, new Dir());
    Tui.open(file, project, tui -> printDir(tui, dir, focus));
  }

  @Override
  public void execute(@NotNull Editor editor, char charTyped, @NotNull DataContext dataContext) {
    TuiFile file = ObjectUtils.tryCast(editor.getVirtualFile(), TuiFile.class);
    if (file == null) {
      return;
    }
    Map<String, TypedActionHandler> keymap = Tui.TUI_KEYMAP.get(file, DEFAULT_KEY_MAP);
    String charStr = String.valueOf(charTyped);
    TypedActionHandler handler = keymap != null ? keymap.get(charStr) : null;
    if (handler != null) {
      handler.execute(editor, charTyped, dataContext);
    }
  }

  private static @Nullable VirtualFile getFileUnderCaret(@NotNull Editor editor) {
    VirtualFile file = editor.getVirtualFile();
    if (file == null) {
      return null;
    }
    int fileIdx = editor.getDocument().getLineNumber(editor.getCaretModel().getCurrentCaret().getOffset()) - 1;
    List<VirtualFile> files = Tui.getTuiData(file).getUserData(FILES);
    if (files == null) {
      files = Collections.emptyList();
    }
    if (0 <= fileIdx && fileIdx < files.size()) {
      return files.get(fileIdx);
    }
    return null;
  }

  private static @Nullable VirtualFile getDir(@Nullable VirtualFile file) {
    UserDataHolder data = Tui.getTuiData(file);
    return data.getUserData(DIR);
  }

  private static void runAction(@NotNull Editor editor, @NotNull DataContext dataContext, @NotNull String actionId) {
    EditorActionHandler up = EditorActionManager.getInstance().getActionHandler(actionId);
    up.execute(editor, editor.getCaretModel().getCurrentCaret(), dataContext);
  }

  private static void printDir(@NotNull Tui tui, @NotNull VirtualFile dir, @Nullable VirtualFile focus) {
    tui.name = dir.getPath();

    VirtualFile[] children = dir.getChildren();
    List<VirtualFile> dirs = new ArrayList<>();
    List<VirtualFile> files = new ArrayList<>();
    if (children != null) {
      for (VirtualFile child : children) {
        if (child.isDirectory()) {
          dirs.add(child);
        } else {
          files.add(child);
        }
      }
    }


    dirs.sort(Comparator.comparing(VirtualFile::getName, String.CASE_INSENSITIVE_ORDER));
    files.sort(Comparator.comparing(VirtualFile::getName, String.CASE_INSENSITIVE_ORDER));

    tui.text.append(dir.getPath()).append(":\n");

    tui.text.append(".\n");
    VirtualFile parentDir = dir.getParent();
    if (parentDir != null) {
      tui.text.append("..\n");
    }

    Integer firstItemOffset = null;
    Integer caretOffset = null;
    for (VirtualFile child : dirs) {
      if (firstItemOffset == null) {
        firstItemOffset = tui.text.length();
      }
      if (Objects.equals(child, focus)) {
        caretOffset = tui.text.length();
      }
      tui.text.append("[").append(child.getName()).append("]\n");
    }
    for (VirtualFile child : files) {
      if (firstItemOffset == null) {
        firstItemOffset = tui.text.length();
      }
      if (Objects.equals(child, focus)) {
        caretOffset = tui.text.length();
      }
      tui.text.append(child.getName()).append("\n");
    }

    if (caretOffset == null && !Objects.equals(tui.data.getUserData(DIR), dir)) {
      caretOffset = firstItemOffset;
    }
    if (caretOffset != null) {
      tui.caretOffset = caretOffset;
      tui.scrollToCaretType = ScrollType.CENTER;
    }

    List<VirtualFile> shownFiles = new ArrayList<>(children != null ? children.length : 0);
    shownFiles.add(dir);
    if (parentDir != null) {
      shownFiles.add(parentDir);
    }
    shownFiles.addAll(dirs);
    shownFiles.addAll(files);
    tui.newData.putUserData(DIR, dir);
    tui.newData.putUserData(FILES, shownFiles);
  }


  public static class OpenAction extends AnAction {
    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
      Project project = e.getProject();
      if (project == null) {
        return;
      }
      VirtualFile dir = getDirToOpen(e);
      if (dir == null) {
        return;
      }
      OpenDirDialog dialog = new OpenDirDialog(project, dir);
      if (dialog.showAndGet()) {
        String specifiedPath = dialog.myDir.getText();
        VirtualFile selectedDir;
        if (specifiedPath.equals("~") || specifiedPath.equals("~/")) {
          selectedDir = VirtualFileManager.getInstance().findFileByNioPath(Path.of(PlatformUtil.getUserHomeDir()));
        } else {
          selectedDir = VirtualFileManager.getInstance().findFileByNioPath(Path.of(specifiedPath));
        }
        if (selectedDir != null) {
          Dir.openAsText(project, selectedDir, e.getData(CommonDataKeys.VIRTUAL_FILE));
        } else {
          Messages.showErrorDialog(project, "Directory not found", CommonBundle.getErrorTitle());
        }
      }
    }

    private @Nullable VirtualFile getDirToOpen(@NotNull AnActionEvent e) {
      VirtualFile contextFile = e.getData(CommonDataKeys.VIRTUAL_FILE);
      if (contextFile == null) {
        return VirtualFileManager.getInstance().findFileByNioPath(Path.of(PlatformUtil.getUserHomeDir()));
      }
      if (contextFile.isDirectory()) {
        return contextFile;
      }
      VirtualFile it = contextFile;
      while (it != null && !it.isDirectory()) {
        it = it.getParent();
      }
      return it != null ? it : VirtualFileManager.getInstance().findFileByNioPath(Path.of(PlatformUtil.getUserHomeDir()));
    }
  }


  private static class OpenDirDialog extends DialogWrapper {
    private final TextFieldWithHistoryWithBrowseButton myDir;

    public OpenDirDialog(@Nullable Project project, @Nullable VirtualFile dir) {
      super(project, false);
      myDir = new TextFieldWithHistoryWithBrowseButton();
      FileChooserDescriptor config = new FileChooserDescriptor(false, true, false, false, false, false);
      FileChooserFactory.getInstance().installFileCompletion(myDir.getChildComponent().getTextEditor(), config, true, getDisposable());
      String defaultPath = dir != null ? dir.getCanonicalPath() : null;
      if (defaultPath == null) {
        defaultPath = "~/";
      }
      myDir.setText(defaultPath);
      init();
    }

    @Override
    protected @Nullable JComponent createCenterPanel() {
      DialogPanel result = new DialogPanel(new BorderLayout());
      result.setPreferredSize(JBUI.size(500, 30));
      result.add(FormBuilder.createFormBuilder().addComponent(myDir).getPanel(), BorderLayout.NORTH);
      result.setPreferredFocusedComponent(myDir);
      return result;
    }
  }


  public static class DirFileType implements FileType {
    public static DirFileType INSTANCE = new DirFileType();

    @Override
    public @NonNls @NotNull String getName() {
      return "Dir";
    }

    @Override
    public @NlsContexts.Label @NotNull String getDescription() {
      return "Directory";
    }

    @Override
    public @NlsSafe @NotNull String getDefaultExtension() {
      return "";
    }

    @Override
    public Icon getIcon() {
      return PlatformIcons.FOLDER_ICON;
    }

    @Override
    public boolean isBinary() {
      return false;
    }
  }

  public static class DirEditorCustomizer implements TextEditorCustomizer {
    @Override
    public void customize(@NotNull TextEditor textEditor) {
      Editor editor = textEditor.getEditor();
      VirtualFile file = editor.getVirtualFile();
      if (file instanceof TuiFile) {
        VirtualFile dir = getDir(file);
        if (dir != null) {
          editor.getComponent();
          DataManager.registerDataProvider(editor.getComponent(), new DataProvider() {
            @Override
            public @Nullable Object getData(@NotNull @NonNls String dataId) {
              if (SelectInContext.DATA_KEY.is(dataId)) {
                Project project = editor.getProject();
                if (project == null) {
                  return null;
                }
                VirtualFile f = getFileUnderCaret(editor);
                return new FileSelectInContext(project, f != null ? f : dir);
              }
              return null;
            }
          });
        }
      }
    }
  }

  public static void up(@NotNull Editor editor, char charTyped, @NotNull DataContext dataContext) {
    runAction(editor, dataContext, IdeActions.ACTION_EDITOR_MOVE_CARET_UP);
  }

  public static void down(@NotNull Editor editor, char charTyped, @NotNull DataContext dataContext) {
    runAction(editor, dataContext, IdeActions.ACTION_EDITOR_MOVE_CARET_DOWN);
  }

  public static void openFileUnderCaret(@NotNull Editor editor, char charTyped, @NotNull DataContext dataContext) {
    TuiFile file = ObjectUtils.tryCast(editor.getVirtualFile(), TuiFile.class);
    if (file == null) {
      return;
    }
    Project project = editor.getProject();
    if (charTyped == '\n') {
      VirtualFile f = getFileUnderCaret(editor);
      if (f == null) {
        return;
      }
      if (f.isDirectory()) {
        if (project != null && FileEditorManager.getInstance(project).getAllEditors(file).length > 1) {
          Dir.openAsText(project, f, null);
        } else {
          Tui.update(file, editor, tui -> printDir(tui, f, null));
        }
      } else {
        if (project != null) {
          FileEditorManager.getInstance(project).openFile(f, true);
        }
      }
    }
  }

  public static void copyPathUnderCaret(@NotNull Editor editor, char charTyped, @NotNull DataContext dataContext) {
    VirtualFile f = getFileUnderCaret(editor);
    if (f != null) {
      String path = f.getPath();
      CopyPasteManager.getInstance().setContents(new StringSelection(path));
    }
  }

  public static void gotoParentDir(@NotNull Editor editor, char charTyped, @NotNull DataContext dataContext) {
    TuiFile file = ObjectUtils.tryCast(editor.getVirtualFile(), TuiFile.class);
    if (file == null) {
      return;
    }
    Project project = editor.getProject();
    VirtualFile dir = getDir(file);
    if (dir != null) {
      VirtualFile parent = dir.getParent();
      if (parent != null) {
        if (project != null && FileEditorManager.getInstance(project).getAllEditors(file).length > 1) {
          Dir.openAsText(project, parent, dir);
        } else {
          Tui.update(file, editor, tui -> printDir(tui, parent, dir));
        }
      }
    }
  }

  public static void closeDir(@NotNull Editor editor, char charTyped, @NotNull DataContext dataContext) {
    TuiFile file = ObjectUtils.tryCast(editor.getVirtualFile(), TuiFile.class);
    if (file == null) {
      return;
    }
    Project project = editor.getProject();
    if (project != null) {
      VirtualFile prev = TuiFile.PREV_FILE.get(file);
      if (prev != null) {
        FileEditorManager.getInstance(project).openFile(prev, true);
      } else {
        FileEditorManager.getInstance(project).closeFile(file);
      }
    }
  }

  public static void refresh(@NotNull Editor editor, char charTyped, @NotNull DataContext dataContext) {
    TuiFile file = ObjectUtils.tryCast(editor.getVirtualFile(), TuiFile.class);
    if (file == null) {
      return;
    }
    VirtualFile dir = getDir(file);
    if (dir != null) {
      Tui.update(file, editor, tui -> printDir(tui, dir, null));
    }
  }
}
