package net.wrlu.forensictool.backup;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.IBinder;
import android.os.Message;
import android.os.Messenger;
import android.os.ParcelFileDescriptor;
import android.os.RemoteException;
import android.util.Log;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

public abstract class TransferSdkBackupClient extends VendorBackupClient {

    private static final String TAG = "TransferSdkClient";

    private static final int MSG_BACKUP_DATA = 101;
    private static final int MSG_CANCEL_BACKUP = 102;
    private static final int MSG_BACKUP_RESULT = 103;
    private static final int MSG_GET_DATA_SIZE = 104;
    private static final int MSG_KEEP_ALIVE = 105;

    private static final String KEY_PARAM_TYPE = "param_type";
    private static final String KEY_PARAM_TASK_ID = "param_task_id";
    private static final String KEY_PARAM_PKG = "param_pkg";
    private static final String KEY_PARAM_USER = "param_user";
    private static final String KEY_PARAM_MSG_TOKEN = "param_msg_token";

    private static final String KEY_RETURN_DATA_FD = "return_data_fd";
    private static final String KEY_RETURN_ERR_MSG = "return_err_msg";
    private static final String KEY_RETURN_BACKUP_RESULT = "return_backup_result";
    private static final String KEY_RETURN_BACKUP_ERR_DESC = "return_backup_err_desc";
    private static final String KEY_RETURN_DATA_SIZE = "return_data_size";

    private final AtomicBoolean connected = new AtomicBoolean(false);
    private final ConcurrentHashMap<String, PendingRequest> pendingRequests = new ConcurrentHashMap<>();
    private final AtomicLong tokenSeq = new AtomicLong(0);
    private final Object connectLock = new Object();

    private Messenger serverMessenger;
    private Messenger clientMessenger;
    private HandlerThread handlerThread;
    private Handler responseHandler;
    private final Runnable keepAliveRunnable = new Runnable() {
        @Override
        public void run() {
            if (connected.get() && serverMessenger != null) {
                try {
                    Message msg = Message.obtain(null, MSG_KEEP_ALIVE, 0, 0, new Bundle());
                    msg.replyTo = clientMessenger;
                    serverMessenger.send(msg);
                } catch (RemoteException e) {
                    Log.d(TAG, "keep-alive failed: " + e.getMessage());
                }
            }
            if (responseHandler != null) {
                responseHandler.postDelayed(this, 7000L);
            }
        }
    };

    protected TransferSdkBackupClient(Context context) {
        super(context);
    }

    protected abstract String getAction();

    protected abstract String getTargetPackage();

    @Override
    public boolean connect() {
        synchronized (this) {
            if (responseHandler == null) {
                initHandler();
            }
            if (!connected.get()) {
                bindService();
            }
            return connected.get();
        }
    }

    @Override
    public void disconnect() {
        Log.d(TAG, "[" + getVendorName() + "] disconnect connected=" + connected.get());
        if (connected.get()) {
            try {
                context.unbindService(serviceConnection);
            } catch (Exception e) {
                Log.e(TAG, "disconnect " + e.getMessage());
            }
            serverMessenger = null;
            connected.set(false);
        }
        releaseHandler();
    }

    @Override
    public void backupPackage(String pkgName, int userId) {
        if (acquireEnabled && !acquired) {
            if (callback != null) {
                callback.onAcquireRequired(getVendorName(), getTargetPackage());
            }
            return;
        }
        if (callback != null) {
            callback.onStarted(pkgName);
        }
        File tempFile = new File(context.getCacheDir(), pkgName + ".ab");
        String taskId = System.currentTimeMillis() + "_0";
        Bundle args = new Bundle();
        args.putInt(KEY_PARAM_TYPE, 0);
        args.putString(KEY_PARAM_TASK_ID, taskId);
        args.putString(KEY_PARAM_PKG, pkgName);
        args.putInt(KEY_PARAM_USER, userId);
        try {
            if (!ensureConnected()) {
                notifyResult(pkgName, false, "init engine failure", null);
                return;
            }

            long totalSize = -1;
            try {
                Bundle sizeArgs = new Bundle();
                sizeArgs.putInt(KEY_PARAM_TYPE, 0);
                sizeArgs.putString(KEY_PARAM_PKG, pkgName);
                sizeArgs.putInt(KEY_PARAM_USER, userId);
                Bundle sizeResult = sendAndWait(MSG_GET_DATA_SIZE, sizeArgs);
                if (sizeResult != null) {
                    totalSize = sizeResult.getLong(KEY_RETURN_DATA_SIZE, -1);
                }
            } catch (Exception ignored) {
            }

            Bundle result = sendAndWait(MSG_BACKUP_DATA, args);
            if (result != null && result.containsKey(KEY_RETURN_ERR_MSG)) {
                String errMsg = result.getString(KEY_RETURN_ERR_MSG);
                notifyResult(pkgName, false, errMsg != null ? errMsg : "unknown error", null);
                return;
            }
            if (result != null) {
                ParcelFileDescriptor fd = result.getParcelable(KEY_RETURN_DATA_FD);
                if (fd != null) {
                    boolean written = writeFile(fd, tempFile, pkgName, totalSize);
                    if (!written) {
                        notifyResult(pkgName, false, "failed to write backup file", null);
                        return;
                    }
                }
            }
            Bundle queryArgs = new Bundle();
            queryArgs.putString(KEY_PARAM_TASK_ID, taskId);
            Bundle resultBundle = sendAndWait(MSG_BACKUP_RESULT, queryArgs);
            boolean success = true;
            String errorDesc = "SUCCESS";
            if (resultBundle != null) {
                success = resultBundle.getBoolean(KEY_RETURN_BACKUP_RESULT, false);
                errorDesc = resultBundle.getString(KEY_RETURN_BACKUP_ERR_DESC, "");
            }
            notifyResult(pkgName, success, errorDesc, tempFile);
        } catch (Exception e) {
            Log.e(TAG, "backupPackage exception " + e.getMessage());
            notifyResult(pkgName, false, e.getMessage() != null ? e.getMessage() : "unknown", null);
        }
    }

    private boolean writeFile(ParcelFileDescriptor pfd, File outputFile, String pkgName, long totalSize) {
        ParcelFileDescriptor dupFd = null;
        try {
            dupFd = pfd.dup();
            try (FileInputStream fis = new FileInputStream(dupFd.getFileDescriptor());
                 FileOutputStream fos = new FileOutputStream(outputFile)) {
                byte[] buf = new byte[8192];
                int n;
                long written = 0;
                int lastPercent = -1;
                while ((n = fis.read(buf)) != -1) {
                    fos.write(buf, 0, n);
                    written += n;
                    if (totalSize > 0 && callback != null) {
                        int percent = (int) (written * 100 / totalSize);
                        if (percent > 100) percent = 100;
                        if (percent != lastPercent) {
                            lastPercent = percent;
                            callback.onProgress(pkgName, percent);
                        }
                    }
                }
                fos.getFD().sync();
                if (callback != null) {
                    callback.onProgress(pkgName, 100);
                }
                Log.i(TAG, "writeFile done: " + outputFile.getAbsolutePath() + " size=" + written);
                return true;
            }
        } catch (IOException e) {
            Log.e(TAG, "writeFile failed: " + e.getMessage());
            return false;
        } finally {
            try {
                pfd.close();
            } catch (IOException ignored) {
            }
            if (dupFd != null) {
                try {
                    dupFd.close();
                } catch (IOException ignored) {
                }
            }
        }
    }

    private boolean ensureConnected() {
        synchronized (this) {
            if (responseHandler == null) {
                initHandler();
            }
            if (!connected.get()) {
                bindService();
            }
            return connected.get();
        }
    }

    private void initHandler() {
        releaseHandler();
        handlerThread = new HandlerThread(getVendorName() + "_msg_thread");
        handlerThread.start();
        responseHandler = new Handler(handlerThread.getLooper()) {
            @Override
            public void handleMessage(Message msg) {
                TransferSdkBackupClient.this.handleResponse(msg);
            }
        };
        clientMessenger = new Messenger(responseHandler);
    }

    private void releaseHandler() {
        if (responseHandler != null) {
            responseHandler.removeCallbacks(keepAliveRunnable);
        }
        if (handlerThread != null) {
            handlerThread.quitSafely();
            handlerThread = null;
        }
        responseHandler = null;
        clientMessenger = null;
    }

    private void bindService() {
        Intent intent = new Intent(getAction());
        intent.setPackage(getTargetPackage());
        Log.i(TAG, "[" + getVendorName() + "] bindService action:" + getAction() + " pkg:" + getTargetPackage());
        boolean bound = context.bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE);
        Log.i(TAG, "bindService ret:" + bound);
        int retries = 0;
        while (retries < 3 && !connected.get()) {
            retries++;
            synchronized (connectLock) {
                try {
                    connectLock.wait(retries * 1500L);
                } catch (InterruptedException e) {
                    Log.e(TAG, "bindService interrupted:" + e.getMessage());
                }
            }
        }
    }

    private final ServiceConnection serviceConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            Log.i(TAG, "[" + getVendorName() + "] onServiceConnected");
            synchronized (connectLock) {
                serverMessenger = new Messenger(service);
                connectLock.notifyAll();
                connected.set(true);
            }
            if (responseHandler != null) {
                responseHandler.postDelayed(keepAliveRunnable, 7000L);
            }
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            Log.i(TAG, "[" + getVendorName() + "] onServiceDisconnected");
            connected.set(false);
            serverMessenger = null;
            if (responseHandler != null) {
                responseHandler.removeCallbacks(keepAliveRunnable);
            }
            for (PendingRequest pr : pendingRequests.values()) {
                synchronized (pr.lock) {
                    pr.result = null;
                    pr.pending.set(false);
                    pr.lock.notifyAll();
                }
            }
            pendingRequests.clear();
        }
    };

    private Bundle sendAndWait(int what, Bundle args) throws Exception {
        if (serverMessenger == null) {
            throw new Exception("service not connected");
        }
        String token = what + "-" + tokenSeq.getAndIncrement();
        args.putString(KEY_PARAM_MSG_TOKEN, token);
        PendingRequest pr = new PendingRequest();
        pendingRequests.put(token, pr);
        synchronized (pr.lock) {
            sendMessage(what, args);
            Log.i(TAG, "sendAndWait token=" + token);
            long deadline = System.currentTimeMillis() + 120000L;
            while (pr.pending.get() && System.currentTimeMillis() < deadline) {
                if (serverMessenger == null) {
                    throw new Exception("service disconnected while waiting");
                }
                try {
                    pr.lock.wait(Math.min(5000L, deadline - System.currentTimeMillis()));
                } catch (InterruptedException e) {
                    Log.d(TAG, "sendAndWait interrupted " + e.getMessage());
                }
            }
            if (pr.pending.get()) {
                pendingRequests.remove(token);
                throw new Exception("timeout waiting for response what=" + what);
            }
        }
        return pr.result;
    }

    private void sendMessage(int what, Bundle args) {
        if (serverMessenger == null) {
            Log.e(TAG, "sendMessage serverMessenger is null");
            return;
        }
        try {
            Message msg = Message.obtain(null, what, 0, 0, args);
            msg.replyTo = clientMessenger;
            serverMessenger.send(msg);
        } catch (RemoteException e) {
            Log.e(TAG, "sendMessage " + e.getMessage());
        }
    }

    private void handleResponse(Message msg) {
        Bundle data;
        if (msg.obj instanceof Bundle) {
            data = (Bundle) msg.obj;
        } else {
            data = msg.getData();
        }
        String token = data.getString(KEY_PARAM_MSG_TOKEN, "");
        Log.d(TAG, "handleResponse what=" + msg.what + " token=" + token);
        PendingRequest pr = pendingRequests.remove(token);
        if (pr != null) {
            synchronized (pr.lock) {
                pr.result = data;
                pr.pending.set(false);
                pr.lock.notifyAll();
            }
        } else {
            Log.w(TAG, "handleResponse no pending request for token=" + token);
        }
    }

    private void notifyResult(String pkgName, boolean success, String errorDesc, File tempFile) {
        if (callback != null) {
            callback.onCompleted(pkgName, success, errorDesc, tempFile);
        }
        setAcquired(false);
    }

    private static class PendingRequest {
        final Object lock = new Object();
        final AtomicBoolean pending = new AtomicBoolean(true);
        Bundle result;
    }
}