package tui;

import com.intellij.codeInsight.editorActions.TypedHandlerDelegate;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.actionSystem.TypedAction;
import com.intellij.openapi.editor.actionSystem.TypedActionHandler;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFile;
import org.jetbrains.annotations.NotNull;

public class TuiTypingHandler extends TypedHandlerDelegate implements TypedActionHandler {
  static TuiTypingHandler INSTANCE;

  private final TypedActionHandler myDelegate;

  public TuiTypingHandler() {
    INSTANCE = this;
    TypedAction typingAction = TypedAction.getInstance();
    myDelegate = typingAction.getRawHandler();
    typingAction.setupRawHandler(INSTANCE);
  }

  @Override
  public void execute(@NotNull Editor editor, char charTyped, @NotNull DataContext dataContext) {
    VirtualFile file = editor.getVirtualFile();
    if (!Tui.isTui(file)) {
      myDelegate.execute(editor, charTyped, dataContext);
      return;
    }

    TypedActionHandler handler = Tui.getTypingHandler(file);
    if (handler != null) {
      handler.execute(editor, charTyped, dataContext);
    }
  }


  @Override
  public @NotNull Result beforeCharTyped(char c, @NotNull Project project, @NotNull Editor editor, @NotNull PsiFile file, @NotNull FileType fileType) {
    VirtualFile vfile = editor.getVirtualFile();
    if (!Tui.isTui(vfile)) {
      return super.beforeCharTyped(c, project, editor, file, fileType);
    }
    return Result.STOP;
  }
}
