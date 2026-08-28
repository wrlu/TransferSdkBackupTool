package net.wrlu.forensictool.backup;

import android.content.Context;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.Signature;
import android.util.Log;

public final class PlatformSignatureChecker {

    private static final String TAG = "PlatformSigChecker";

    public static boolean isPlatformSigned(Context context) {
        try {
            PackageManager pm = context.getPackageManager();
            Signature[] ownSigs = getSignatures(pm, context.getPackageName());
            Signature[] platformSigs = getSignatures(pm, "android");

            if (ownSigs == null || ownSigs.length == 0
                    || platformSigs == null || platformSigs.length == 0) {
                Log.w(TAG, "Cannot retrieve signatures");
                return false;
            }

            if (ownSigs.length != platformSigs.length) {
                Log.w(TAG, "Not platform signed: signature count mismatch");
                return false;
            }

            for (Signature own : ownSigs) {
                boolean found = false;
                for (Signature platform : platformSigs) {
                    if (own.equals(platform)) {
                        found = true;
                        break;
                    }
                }
                if (!found) {
                    Log.w(TAG, "Not platform signed: signature mismatch");
                    return false;
                }
            }

            Log.i(TAG, "Platform signature verified");
            return true;
        } catch (PackageManager.NameNotFoundException e) {
            Log.e(TAG, "Package not found: " + e.getMessage());
            return false;
        }
    }

    private static Signature[] getSignatures(PackageManager pm, String packageName)
            throws PackageManager.NameNotFoundException {
        PackageInfo info = pm.getPackageInfo(packageName, PackageManager.GET_SIGNING_CERTIFICATES);

        if (info.signingInfo != null) {
            return info.signingInfo.getApkContentsSigners();
        }
        return null;
    }

    private PlatformSignatureChecker() {}
}