package net.wrlu.forensictool.backup;

import java.io.File;

public interface BackupCallback {

    void onStarted(String pkgName);

    void onProgress(String pkgName, int percent);

    void onCompleted(String pkgName, boolean success, String errorDesc, File tempFile);

    void onAcquireRequired(String vendor, String serverPackage);
}
