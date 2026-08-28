package vivo.app.backup;

import android.os.Binder;
import android.os.IBinder;
import android.os.IInterface;
import android.os.Parcel;
import android.os.RemoteException;

/* loaded from: classes4.dex */
public interface IPackageBackupRestoreObserver extends IInterface {
    public static final String DESCRIPTOR = "vivo.app.backup.IPackageBackupRestoreObserver";

    public static class Default implements IPackageBackupRestoreObserver {
        @Override // android.os.IInterface
        public IBinder asBinder() {
            return null;
        }

        @Override // vivo.app.backup.IPackageBackupRestoreObserver
        public void onEnd(String str, int i10) throws RemoteException {
        }

        @Override // vivo.app.backup.IPackageBackupRestoreObserver
        public void onError(String str, int i10, int i11) throws RemoteException {
        }

        @Override // vivo.app.backup.IPackageBackupRestoreObserver
        public void onProgress(String str, int i10, long j10, long j11) throws RemoteException {
        }

        @Override // vivo.app.backup.IPackageBackupRestoreObserver
        public void onStart(String str, int i10) throws RemoteException {
        }
    }

    public static abstract class Stub extends Binder implements IPackageBackupRestoreObserver {
        static final int TRANSACTION_onEnd = 2;
        static final int TRANSACTION_onError = 4;
        static final int TRANSACTION_onProgress = 3;
        static final int TRANSACTION_onStart = 1;

        private static class Proxy implements IPackageBackupRestoreObserver {
            private IBinder mRemote;

            Proxy(IBinder iBinder) {
                this.mRemote = iBinder;
            }

            @Override // android.os.IInterface
            public IBinder asBinder() {
                return this.mRemote;
            }

            public String getInterfaceDescriptor() {
                return IPackageBackupRestoreObserver.DESCRIPTOR;
            }

            @Override // vivo.app.backup.IPackageBackupRestoreObserver
            public void onEnd(String str, int i10) throws RemoteException {
                Parcel parcelObtain = Parcel.obtain();
                Parcel parcelObtain2 = Parcel.obtain();
                try {
                    parcelObtain.writeInterfaceToken(IPackageBackupRestoreObserver.DESCRIPTOR);
                    parcelObtain.writeString(str);
                    parcelObtain.writeInt(i10);
                    this.mRemote.transact(2, parcelObtain, parcelObtain2, 0);
                    parcelObtain2.readException();
                } finally {
                    parcelObtain2.recycle();
                    parcelObtain.recycle();
                }
            }

            @Override // vivo.app.backup.IPackageBackupRestoreObserver
            public void onError(String str, int i10, int i11) throws RemoteException {
                Parcel parcelObtain = Parcel.obtain();
                Parcel parcelObtain2 = Parcel.obtain();
                try {
                    parcelObtain.writeInterfaceToken(IPackageBackupRestoreObserver.DESCRIPTOR);
                    parcelObtain.writeString(str);
                    parcelObtain.writeInt(i10);
                    parcelObtain.writeInt(i11);
                    this.mRemote.transact(4, parcelObtain, parcelObtain2, 0);
                    parcelObtain2.readException();
                } finally {
                    parcelObtain2.recycle();
                    parcelObtain.recycle();
                }
            }

            @Override // vivo.app.backup.IPackageBackupRestoreObserver
            public void onProgress(String str, int i10, long j10, long j11) throws RemoteException {
                Parcel parcelObtain = Parcel.obtain();
                Parcel parcelObtain2 = Parcel.obtain();
                try {
                    parcelObtain.writeInterfaceToken(IPackageBackupRestoreObserver.DESCRIPTOR);
                    parcelObtain.writeString(str);
                    parcelObtain.writeInt(i10);
                    parcelObtain.writeLong(j10);
                    parcelObtain.writeLong(j11);
                    this.mRemote.transact(3, parcelObtain, parcelObtain2, 0);
                    parcelObtain2.readException();
                } finally {
                    parcelObtain2.recycle();
                    parcelObtain.recycle();
                }
            }

            @Override // vivo.app.backup.IPackageBackupRestoreObserver
            public void onStart(String str, int i10) throws RemoteException {
                Parcel parcelObtain = Parcel.obtain();
                Parcel parcelObtain2 = Parcel.obtain();
                try {
                    parcelObtain.writeInterfaceToken(IPackageBackupRestoreObserver.DESCRIPTOR);
                    parcelObtain.writeString(str);
                    parcelObtain.writeInt(i10);
                    this.mRemote.transact(1, parcelObtain, parcelObtain2, 0);
                    parcelObtain2.readException();
                } finally {
                    parcelObtain2.recycle();
                    parcelObtain.recycle();
                }
            }
        }

        public Stub() {
            attachInterface(this, IPackageBackupRestoreObserver.DESCRIPTOR);
        }

        public static IPackageBackupRestoreObserver asInterface(IBinder iBinder) {
            if (iBinder == null) {
                return null;
            }
            IInterface iInterfaceQueryLocalInterface = iBinder.queryLocalInterface(IPackageBackupRestoreObserver.DESCRIPTOR);
            return (iInterfaceQueryLocalInterface == null || !(iInterfaceQueryLocalInterface instanceof IPackageBackupRestoreObserver)) ? new Proxy(iBinder) : (IPackageBackupRestoreObserver) iInterfaceQueryLocalInterface;
        }

        @Override // android.os.IInterface
        public IBinder asBinder() {
            return this;
        }

        @Override // android.os.Binder
        public boolean onTransact(int i10, Parcel parcel, Parcel parcel2, int i11) throws RemoteException {
            if (i10 >= 1 && i10 <= 16777215) {
                parcel.enforceInterface(IPackageBackupRestoreObserver.DESCRIPTOR);
            }
            if (i10 == 1598968902) {
                parcel2.writeString(IPackageBackupRestoreObserver.DESCRIPTOR);
                return true;
            }
            if (i10 == 1) {
                onStart(parcel.readString(), parcel.readInt());
            } else if (i10 == 2) {
                onEnd(parcel.readString(), parcel.readInt());
            } else if (i10 == 3) {
                onProgress(parcel.readString(), parcel.readInt(), parcel.readLong(), parcel.readLong());
            } else {
                if (i10 != 4) {
                    return super.onTransact(i10, parcel, parcel2, i11);
                }
                onError(parcel.readString(), parcel.readInt(), parcel.readInt());
            }
            parcel2.writeNoException();
            return true;
        }
    }

    void onEnd(String str, int i10) throws RemoteException;

    void onError(String str, int i10, int i11) throws RemoteException;

    void onProgress(String str, int i10, long j10, long j11) throws RemoteException;

    void onStart(String str, int i10) throws RemoteException;
}