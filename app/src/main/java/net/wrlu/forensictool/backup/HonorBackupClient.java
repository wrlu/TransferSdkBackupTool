package net.wrlu.forensictool.backup;

import android.content.Context;

public class HonorBackupClient extends TransferSdkBackupClient {

    public HonorBackupClient(Context context) {
        super(context);
    }

    @Override
    protected String getAction() {
        return "com.mov.action.backupmanager.impl";
    }

    @Override
    protected String getTargetPackage() {
        return "com.hihonor.android.clone";
    }
}