package tui;

import com.github.markusbernhardt.proxy.util.PlatformUtil;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.ScrollType;
import com.intellij.openapi.editor.actionSystem.EditorActionHandler;
import com.intellij.openapi.editor.actionSystem.EditorActionManager;
import com.intellij.openapi.editor.actionSystem.TypedActionHandler;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.ide.CopyPasteManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.NlsContexts;
import com.intellij.openapi.util.NlsSafe;
import com.intellij.openapi.util.UserDataHolder;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileManager;
import com.intellij.util.ObjectUtils;
import com.intellij.util.PlatformIcons;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.datatransfer.StringSelection;
import java.nio.file.Path;
import java.util.*;

public class Dir implements TypedActionHandler {
  private static final Key<VirtualFile> DIR = Key.create("tui.dir.currentDir");
  private static final Key<List<VirtualFile>> FILES = Key.create("tui.dir.files");

  public static void openAsText(@NotNull Project project, @NotNull VirtualFile dir, @Nullable VirtualFile focus) {
    Tui.init();
    TuiFile file = TuiFS.getInstance().createFile(project, "", DirFileType.INSTANCE);
    file.setWritable(false);
    Tui.setTui(file, true);
    Tui.setTypingHandler(file, new Dir());
    Tui.open(file, project, tui -> printDir(tui, dir, focus));
  }

  @Override
  public void execute(@NotNull Editor editor, char charTyped, @NotNull DataContext dataContext) {
    TuiFile file = ObjectUtils.tryCast(editor.getVirtualFile(), TuiFile.class);
    if (file == null) {
      return;
    }
    if (charTyped == '\n') {
      VirtualFile f = getFileUnderCaret(editor);
      if (f == null) {
        return;
      }
      if (f.isDirectory()) {
        Project project = editor.getProject();
        if (project != null && FileEditorManager.getInstance(project).getAllEditors(file).length > 1) {
          Dir.openAsText(project, f, null);
        } else {
          Tui.update(file, editor, tui -> printDir(tui, f, null));
        }
      } else {
        Project project = editor.getProject();
        if (project != null) {
          FileEditorManager.getInstance(project).openFile(f, true);
        }
      }
    }

    if (charTyped == 'j') {
      runAction(editor, dataContext, IdeActions.ACTION_EDITOR_MOVE_CARET_DOWN);
    }
    if (charTyped == 'k') {
      runAction(editor, dataContext, IdeActions.ACTION_EDITOR_MOVE_CARET_UP);
    }
    if (charTyped == 'g') {
      UserDataHolder data = Tui.getTuiData(file);
      VirtualFile dir = data.getUserData(DIR);
      if (dir != null) {
        Tui.update(file, editor, tui -> printDir(tui, dir, null));
      }
    }
    if (charTyped == 'p') {
      VirtualFile f = getFileUnderCaret(editor);
      if (f != null) {
        String path = f.getPath();
        CopyPasteManager.getInstance().setContents(new StringSelection(path));
      }
    }
    if (charTyped == 'u') {
      UserDataHolder data = Tui.getTuiData(file);
      VirtualFile dir = data.getUserData(DIR);
      if (dir != null) {
        VirtualFile parent = dir.getParent();
        Project project = editor.getProject();
        if (parent != null) {
          if (project != null && FileEditorManager.getInstance(project).getAllEditors(file).length > 1) {
            Dir.openAsText(project, parent, dir);
          } else {
            Tui.update(file, editor, tui -> printDir(tui, parent, dir));
          }
        }
      }
    }
  }

  private @Nullable VirtualFile getFileUnderCaret(@NotNull Editor editor) {
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
      Dir.openAsText(project, dir, e.getData(CommonDataKeys.VIRTUAL_FILE));
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
}
