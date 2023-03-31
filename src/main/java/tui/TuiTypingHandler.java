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

import java.util.concurrent.atomic.AtomicReference;

public class TuiTypingHandler extends TypedHandlerDelegate implements TypedActionHandler {
  private static final AtomicReference<TuiTypingHandler> INSTANCE = new AtomicReference<>();

  private final TypedActionHandler myDelegate;

  public TuiTypingHandler(@NotNull TypedActionHandler original) {
    myDelegate = original;
  }

  static void init() {
    if (INSTANCE.get() == null) {
      TypedAction typingAction = TypedAction.getInstance();
      TuiTypingHandler instance = new TuiTypingHandler(typingAction.getRawHandler());
      if (INSTANCE.compareAndSet(null, instance)) {
        typingAction.setupRawHandler(INSTANCE.get());
      }
    }
  }

  static @NotNull TuiTypingHandler getInstance() {
    return INSTANCE.get();
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
