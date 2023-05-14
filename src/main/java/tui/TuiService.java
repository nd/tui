package tui;

import com.intellij.ide.plugins.DynamicPluginListener;
import com.intellij.ide.plugins.IdeaPluginDescriptor;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.actionSystem.IdeActions;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.Service;
import com.intellij.openapi.editor.Caret;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.EditorFactory;
import com.intellij.openapi.editor.actionSystem.EditorActionHandler;
import com.intellij.openapi.editor.actionSystem.EditorActionManager;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.UserDataHolder;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.messages.MessageBusConnection;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.concurrent.atomic.AtomicBoolean;

@Service
public final class TuiService {

  private static final Key<Boolean> TUI_MARKER = Key.create("tui.tuiFileMark");

  private static final AtomicBoolean ourLoaded = new AtomicBoolean(false);

  private final long myId;

  public TuiService() {
    myId = System.currentTimeMillis();
    Disposable disposable = Disposer.newDisposable();
    MessageBusConnection bus = ApplicationManager.getApplication().getMessageBus().connect(disposable);
    bus.subscribe(DynamicPluginListener.TOPIC, new DynamicPluginListener() {
      @Override
      public void pluginLoaded(@NotNull IdeaPluginDescriptor pluginDescriptor) {
        DynamicPluginListener.super.pluginLoaded(pluginDescriptor);
      }

      public void beforePluginUnload(@NotNull IdeaPluginDescriptor pluginDescriptor, boolean isUpdate) {
        closeTuiFiles();
        ourLoaded.set(false);
        Disposer.dispose(disposable);
      }
    });
    TuiTypingHandler.init();
    EditorActionHandler orig = EditorActionManager.getInstance().getActionHandler(IdeActions.ACTION_EDITOR_ENTER);
    EditorActionManager.getInstance().setActionHandler(IdeActions.ACTION_EDITOR_ENTER, new TuiEditorActionHandler(orig));
    ourLoaded.set(true);
  }

  private void closeTuiFiles() {
    for (Editor editor : EditorFactory.getInstance().getAllEditors()) {
      Project project = editor.getProject();
      if (project == null) {
        continue;
      }
      VirtualFile file = editor.getVirtualFile();
      if (isTui(file)) {
        FileEditorManager.getInstance(project).closeFile(file);
      }
    }
  }

  @NotNull
  static TuiService getInstance() {
    return ApplicationManager.getApplication().getService(TuiService.class);
  }

  public boolean isTui(@Nullable UserDataHolder o) {
    return o != null && o.getUserData(TUI_MARKER) == Boolean.TRUE;
  }

  public void setTui(@NotNull UserDataHolder o, boolean isTui) {
    o.putUserData(TUI_MARKER, isTui);
  }

  static boolean isLoaded() {
    return ourLoaded.get();
  }

  public long getId() {
    return myId;
  }

  static class TuiEditorActionHandler extends EditorActionHandler {
    private final EditorActionHandler myOriginal;

    TuiEditorActionHandler(EditorActionHandler original) {
      myOriginal = original;
    }

    @Override
    protected boolean isEnabledForCaret(@NotNull Editor editor, @NotNull Caret caret, DataContext dataContext) {
      if (!isLoaded()) {
        return myOriginal.isEnabled(editor, caret, dataContext);
      }
      if (TuiService.getInstance().isTui(editor.getVirtualFile())) {
        return true;
      }
      return myOriginal.isEnabled(editor, caret, dataContext);
    }

    @Override
    protected void doExecute(@NotNull Editor editor, @Nullable Caret caret, DataContext dataContext) {
      if (isLoaded() && TuiService.getInstance().isTui(editor.getVirtualFile())) {
        TuiTypingHandler.getInstance().execute(editor, '\n', dataContext);
        return;
      }
      myOriginal.execute(editor, caret, dataContext);
    }

    @Override
    public boolean runForAllCarets() {
      return myOriginal.runForAllCarets();
    }
  }
}
