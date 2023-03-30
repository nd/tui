package tui;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.*;
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
    String id = String.valueOf(myFileId.incrementAndGet());
    TuiFile result = new TuiFile(project, id, name, fileType);
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
