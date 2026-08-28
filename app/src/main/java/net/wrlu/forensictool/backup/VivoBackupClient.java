package net.wrlu.forensictool.backup;

import android.content.Context;
import android.os.ParcelFileDescriptor;
import android.os.RemoteException;

import java.io.File;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import vivo.app.backup.IPackageBackupRestoreObserver;

public class VivoBackupClient extends VendorBackupClient {

    private static final String VIVO_BACKUP_MGR = "com.vivo.framework.backup.VivoBackupManager";

    private static final String EXCLUDE_DATA_DATA_BACKUP_DIR = "EXCLUDE_DATA_DATA_BACKUP_DIR";
    private static final String EXCLUDE_ANDROID_DATA_BACKUP_DIR = "EXCLUDE_ANDORID_DATA_BACKUP_DIR";

    public VivoBackupClient(Context context) {
        super(context);
    }

    private Class<?> backupManagerClass;
    private Object backupManagerInstance;

    @Override
    public boolean connect() {
        try {
            backupManagerClass = Class.forName(VIVO_BACKUP_MGR);
            backupManagerInstance = backupManagerClass.getDeclaredMethod("getInstance").invoke(null);
            return backupManagerInstance != null;
        } catch (Exception e) {
            return false;
        }
    }

    @Override
    public void disconnect() {
        backupManagerClass = null;
        backupManagerInstance = null;
    }

    @Override
    public void backupPackage(String pkgName, int userId) {
        if (callback != null) {
            callback.onStarted(pkgName);
        }
        new Thread(() -> doBackup(pkgName)).start();
    }

    private void doBackup(String pkgName) {
        File tempFile = new File(context.getCacheDir(), pkgName + ".ab");
        try {
            configureBackup(pkgName);

            ParcelFileDescriptor fd = ParcelFileDescriptor.open(tempFile,
                    ParcelFileDescriptor.MODE_WRITE_ONLY
                            | ParcelFileDescriptor.MODE_CREATE
                            | ParcelFileDescriptor.MODE_TRUNCATE);

            CountDownLatch latch = new CountDownLatch(1);
            boolean[] result = {false};
            String[] errMsg = {null};

            IPackageBackupRestoreObserver observer = new IPackageBackupRestoreObserver.Stub() {
                @Override
                public void onStart(String str, int i10) throws RemoteException {
                }

                @Override
                public void onEnd(String str, int i10) throws RemoteException {
                    result[0] = (i10 == 0);
                    if (!result[0]) errMsg[0] = "onEnd code=" + i10;
                    latch.countDown();
                }

                @Override
                public void onError(String str, int i10, int i11) throws RemoteException {
                    result[0] = false;
                    errMsg[0] = "onError: " + i10 + ", " + i11;
                    latch.countDown();
                }

                @Override
                public void onProgress(String str, int i10, long j10, long j11) throws RemoteException {
                }
            };

            Method backupMethod = findBackupMethod();
            if (backupMethod == null) {
                fd.close();
                if (callback != null) {
                    callback.onCompleted(pkgName, false, "method not found", null);
                }
                return;
            }

            boolean started = (boolean) backupMethod.invoke(backupManagerInstance, pkgName, fd, observer);

            if (!started) {
                fd.close();
                if (callback != null) {
                    callback.onCompleted(pkgName, false, "returned false", null);
                }
                return;
            }

            boolean finished = latch.await(120, TimeUnit.SECONDS);
            fd.close();

            if (!finished) {
                if (callback != null) {
                    callback.onCompleted(pkgName, false, "timeout", tempFile);
                }
                return;
            }

            if (callback != null) {
                callback.onCompleted(pkgName, result[0], result[0] ? "SUCCESS" : errMsg[0], tempFile);
            }
        } catch (Exception e) {
            if (callback != null) {
                callback.onCompleted(pkgName, false, e.getMessage(), null);
            }
        } finally {
            cleanupBackupConfig(pkgName);
        }
    }

    private Method findBackupMethod() {
        for (Method m : backupManagerClass.getDeclaredMethods()) {
            if ("backupPackage".equals(m.getName()) && m.getParameterCount() == 3) {
                Class<?>[] pts = m.getParameterTypes();
                if (pts[0] == String.class && pts[1] == ParcelFileDescriptor.class) {
                    m.setAccessible(true);
                    return m;
                }
            }
        }
        return null;
    }

    private boolean setDirFullBackupEnable(String pkgName, boolean enable) {
        try {
            backupManagerClass.getDeclaredMethod("setDirFullBackupEnableByPackage",
                    String.class, boolean.class).invoke(backupManagerInstance, pkgName, enable);
            return true;
        } catch (NoSuchMethodException e) {
            try {
                backupManagerClass.getDeclaredMethod("setDirFullBackupEnable", boolean.class)
                        .invoke(backupManagerInstance, enable);
                return true;
            } catch (Exception e3) {
                return false;
            }
        } catch (Exception e) {
            return false;
        }
    }

    private boolean setVivoBackupDir(String pkgName, Map<String, ArrayList<String>> dirMap) {
        try {
            backupManagerClass.getDeclaredMethod("setVivoBackupDirByPackage",
                    String.class, Map.class).invoke(backupManagerInstance, pkgName, dirMap);
            return true;
        } catch (NoSuchMethodException e) {
            try {
                backupManagerClass.getDeclaredMethod("setVivoBackupDir", Map.class)
                        .invoke(backupManagerInstance, dirMap);
                return true;
            } catch (Exception e3) {
                return false;
            }
        } catch (Exception e) {
            return false;
        }
    }

    private void configureBackup(String pkgName) {
        setDirFullBackupEnable(pkgName, true);

        HashMap<String, ArrayList<String>> dirMap = new HashMap<>();
        dirMap.put(EXCLUDE_DATA_DATA_BACKUP_DIR, new ArrayList<>());
        ArrayList<String> androidExclude = new ArrayList<>();
        androidExclude.add("cache");
        dirMap.put(EXCLUDE_ANDROID_DATA_BACKUP_DIR, androidExclude);
        setVivoBackupDir(pkgName, dirMap);
    }

    private void cleanupBackupConfig(String pkgName) {
        setDirFullBackupEnable(pkgName, false);
        setVivoBackupDir(pkgName, null);
    }
}
