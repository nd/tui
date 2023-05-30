package tui;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.ScrollType;
import com.intellij.openapi.editor.actionSystem.TypedActionHandler;
import com.intellij.openapi.editor.markup.HighlighterLayer;
import com.intellij.openapi.editor.markup.HighlighterTargetArea;
import com.intellij.openapi.editor.markup.TextAttributes;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.fileEditor.FileEditor;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.fileEditor.TextEditor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.UserDataHolder;
import com.intellij.openapi.util.UserDataHolderBase;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.ui.JBColor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.Font;
import java.util.ArrayList;
import java.util.Map;
import java.util.function.Consumer;

public class Tui {
  public static final Key<Map<String, TypedActionHandler>> TUI_KEYMAP = Key.create("tui.keymap");
  private static final Key<TypedActionHandler> TUI_TYPING_HANDLER = Key.create("tui.typingHandler");
  private static final Key<UserDataHolder> TUI_DATA = Key.create("tui.data");

  public final StringBuilder text = new StringBuilder();
  public final UserDataHolder data;
  public final UserDataHolderBase newData = new UserDataHolderBase();
  public Integer caretOffset;
  public ScrollType scrollToCaretType;
  public String name;
  public java.util.List<Highlighter> highlighters = new ArrayList<>();

  public Tui(@NotNull UserDataHolder data) {
    this.data = data;
  }

  static @Nullable Tui open(@NotNull TuiFile file, @NotNull Project project, @NotNull Consumer<Tui> task) {
    Tui tui = Tui.update(file, null, task);
    FileEditor[] editors = FileEditorManager.getInstance(project).openFile(file, true);
    if (tui != null && tui.caretOffset != null) {
      for (FileEditor editor : editors) {
        if (editor instanceof TextEditor) {
          ((TextEditor) editor).getEditor().getCaretModel().moveToOffset(tui.caretOffset);
          ScrollType scrollType = tui.scrollToCaretType;
          if (scrollType != null) {
            ((TextEditor) editor).getEditor().getScrollingModel().scrollToCaret(scrollType);
          }
        }
      }
    }
    return tui;
  }

  static @Nullable Tui update(@NotNull TuiFile file, @Nullable Editor editor, @NotNull Consumer<Tui> task) {
    if (!TuiService.getInstance().isTui(file)) {
      return null;
    }
    Document doc = FileDocumentManager.getInstance().getDocument(file);
    if (doc == null) {
      return null;
    }

    boolean origFileWritable = file.isWritable();
    boolean origDocWritable = doc.isWritable();
    file.setWritable(true);
    try {
      doc.setReadOnly(false);
      UserDataHolder data = Tui.getTuiData(file);
      Tui tui = new Tui(data);

      task.accept(tui);

      ApplicationManager.getApplication().runWriteAction(() -> {
        doc.setText(tui.text);
        if (tui.name != null) {
          file.setPresentableName(tui.name);
        }
        if (editor != null) {
          editor.getMarkupModel().removeAllHighlighters();
          for (Highlighter h : tui.highlighters) {
            if (h.attributes != null) {
              editor.getMarkupModel().addRangeHighlighter(
                      h.startOffset, h.endOffset, h.layer, h.attributes, HighlighterTargetArea.EXACT_RANGE);
            }
          }
          if (tui.caretOffset != null) {
            editor.getCaretModel().moveToOffset(tui.caretOffset);
          }
          ScrollType scrollType = tui.scrollToCaretType;
          if (scrollType != null) {
            editor.getScrollingModel().scrollToCaret(scrollType);
          }
        }
      });

      Tui.setTuiData(file, tui.newData);
      return tui;
    } finally {
      doc.setReadOnly(!origDocWritable);
      file.setWritable(origFileWritable);
    }
  }

  public static @Nullable TypedActionHandler getTypingHandler(@Nullable UserDataHolder o) {
    return o != null ? o.getUserData(TUI_TYPING_HANDLER) : null;
  }

  public static void setTypingHandler(@NotNull UserDataHolder o, @NotNull TypedActionHandler handler) {
    o.putUserData(TUI_TYPING_HANDLER, handler);
  }

  public static @NotNull UserDataHolder getTuiData(@Nullable VirtualFile file) {
    UserDataHolder data = file != null ? file.getUserData(TUI_DATA) : null;
    return data != null ? data : new UserDataHolderBase();
  }

  public static void setTuiData(@NotNull VirtualFile file, @NotNull UserDataHolder data) {
    file.putUserData(TUI_DATA, data);
  }

  public static class Highlighter {
    int startOffset;
    int endOffset;
    int layer = HighlighterLayer.SYNTAX; // HighlighterLayer
    TextAttributes attributes;
  }
}
