package tui;

import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.NlsSafe;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFilePathWrapper;
import com.intellij.openapi.vfs.VirtualFileSystem;
import com.intellij.testFramework.LightVirtualFile;
import org.jetbrains.annotations.NotNull;

class TuiFile extends LightVirtualFile implements VirtualFilePathWrapper /*just to show presentable name in switcher*/ {
  static final Key<VirtualFile> PREV_FILE = Key.create("tui.prev.file");

  private final Project myProject;
  private final String myId;
  private String myPresentableName;

  public TuiFile(@NotNull Project project, @NotNull String id, @NotNull String name, @NotNull FileType fileType) {
    super(name, fileType, "");
    myProject = project;
    myId = id;
    myPresentableName = name;
  }

  @Override
  public @NotNull @NlsSafe String getPresentableName() {
    return myPresentableName;
  }

  public void setPresentableName(@NotNull String presentableName) {
    myPresentableName = presentableName;
  }

  @Override
  public @NotNull String getPath() {
    return myId;
  }

  @Override
  public @NotNull VirtualFileSystem getFileSystem() {
    return TuiFS.getInstance();
  }

  @Override
  public @NotNull String getUrl() {
    // when file is closed platform asks for an url which requires a file system;
    // if plugin is unloaded the file system might already be unloaded and
    // attempt to get it will fail with NPE.
    if (TuiService.isLoaded()) {
      return super.getUrl();
    } else {
      return "";
    }
  }

  public @NotNull Project getProject() {
    return myProject;
  }

  @Override
  public @NotNull String getPresentablePath() {
    return myPresentableName;
  }

  @Override
  public boolean enforcePresentableName() {
    return true;
  }
}
