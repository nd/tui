package tui;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.DeprecatedVirtualFileSystem;
import com.intellij.openapi.vfs.NonPhysicalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileManager;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicLong;

public class TuiFS extends DeprecatedVirtualFileSystem implements NonPhysicalFileSystem {

  private static final String PROTOCOL = "tui";
  private final ConcurrentMap<String, VirtualFile> myFiles = new ConcurrentHashMap<String, VirtualFile>();
  private final AtomicLong myFileId = new AtomicLong(0);

  public static @NotNull TuiFS getInstance() {
    VirtualFileManager virtualFileManager = ApplicationManager.getApplication().getService(VirtualFileManager.class);
    return (TuiFS) virtualFileManager.getFileSystem(PROTOCOL);
  }

  public @NotNull TuiFile createFile(@NotNull Project project, @NotNull String name, @NotNull FileType fileType) {
    // Workaround stupid switcher behavior: it saves urls in config and tries to load
    // them after restart. But we don't have files after restart. Switcher doesn't care.
    // If we then create a new file with the same id, stupid switcher won't show them.
    // Removing these files on project close doesn't help since switcher is fucking stupid.
    // Workaround is to use some kind of run id in TuiService which is not service
    // creation timestamp.
    String id = TuiService.getInstance().getId() + "-" + myFileId.incrementAndGet();
    TuiFile result = new TuiFile(this, project, id, name, fileType);
    myFiles.put(id, result);
    return result;
  }

  @Override
  public @NonNls @NotNull String getProtocol() {
    return PROTOCOL;
  }

  @Override
  public @Nullable VirtualFile findFileByPath(@NotNull @NonNls String path) {
    return myFiles.get(path);
  }

  @Override
  public @Nullable VirtualFile refreshAndFindFileByPath(@NotNull String path) {
    return myFiles.get(path);
  }

  @Override
  public void refresh(boolean asynchronous) {
  }

  public void closeFile(@NotNull VirtualFile file) {
    myFiles.remove(file.getPath());
  }
}
