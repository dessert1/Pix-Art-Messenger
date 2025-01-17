package de.pixart.messenger.services;

import android.content.Context;
import android.os.PowerManager;
import android.os.SystemClock;
import android.util.Log;

import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.security.InvalidAlgorithmParameterException;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.security.NoSuchProviderException;
import java.util.concurrent.atomic.AtomicLong;

import javax.crypto.Cipher;
import javax.crypto.CipherInputStream;
import javax.crypto.CipherOutputStream;
import javax.crypto.NoSuchPaddingException;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;

import de.pixart.messenger.Config;
import de.pixart.messenger.R;
import de.pixart.messenger.entities.DownloadableFile;
import de.pixart.messenger.utils.Compatibility;

public class AbstractConnectionManager {
    private static final String KEYTYPE = "AES";
    private static final String CIPHERMODE = "AES/GCM/NoPadding";
    private static final String PROVIDER = "BC";
    private static final int UI_REFRESH_THRESHOLD = 250;
    private static final AtomicLong LAST_UI_UPDATE_CALL = new AtomicLong(0);
    protected XmppConnectionService mXmppConnectionService;

    public AbstractConnectionManager(XmppConnectionService service) {
        this.mXmppConnectionService = service;
    }

    public static InputStream upgrade(DownloadableFile file, InputStream is) throws InvalidAlgorithmParameterException, NoSuchAlgorithmException, InvalidKeyException, NoSuchPaddingException, NoSuchProviderException {
        if (file.getKey() != null && file.getIv() != null) {
            final Cipher cipher = Compatibility.twentyEight() ? Cipher.getInstance(CIPHERMODE) : Cipher.getInstance(CIPHERMODE, PROVIDER);
            SecretKeySpec keySpec = new SecretKeySpec(file.getKey(), KEYTYPE);
            IvParameterSpec ivSpec = new IvParameterSpec(file.getIv());
            cipher.init(Cipher.ENCRYPT_MODE, keySpec, ivSpec);
            return new CipherInputStream(is, cipher);
        } else {
            return is;
        }
    }


    public static OutputStream createAppendedOutputStream(DownloadableFile file) {
        return createOutputStream(file, false, true);
    }

    public static OutputStream createOutputStream(DownloadableFile file) {
        return createOutputStream(file, false);
    }

    public static OutputStream createOutputStream(DownloadableFile file, boolean gcm) {
        return createOutputStream(file, gcm, false);
    }

    private static OutputStream createOutputStream(DownloadableFile file, boolean gcm, boolean append) {
        FileOutputStream os;
        try {
            os = new FileOutputStream(file, append);
            if (file.getKey() == null) {
                return os;
            }
        } catch (FileNotFoundException e) {
            Log.d(Config.LOGTAG, "unable to create output stream", e);
            return null;
        }
        try {
            if (gcm) {
                final Cipher cipher = Compatibility.twentyEight() ? Cipher.getInstance(CIPHERMODE) : Cipher.getInstance(CIPHERMODE, PROVIDER);
                SecretKeySpec keySpec = new SecretKeySpec(file.getKey(), KEYTYPE);
                IvParameterSpec ivSpec = new IvParameterSpec(file.getIv());
                cipher.init(Cipher.DECRYPT_MODE, keySpec, ivSpec);
                return new CipherOutputStream(os, cipher);
            } else {
                IvParameterSpec ips = new IvParameterSpec(file.getIv());
                final Cipher cipher = Cipher.getInstance("AES/CBC/PKCS5Padding");
                cipher.init(Cipher.DECRYPT_MODE, new SecretKeySpec(file.getKey(), KEYTYPE), ips);
                return new CipherOutputStream(os, cipher);
            }
        } catch (Exception e) {
            Log.d(Config.LOGTAG, "unable to create cipher output stream", e);
            return null;
        }
    }

    public XmppConnectionService getXmppConnectionService() {
        return this.mXmppConnectionService;
    }

    public long getAutoAcceptFileSize() {
        long defaultValue_wifi = this.getXmppConnectionService().getResources().getInteger(R.integer.auto_accept_filesize_wifi);
        long defaultValue_mobile = this.getXmppConnectionService().getResources().getInteger(R.integer.auto_accept_filesize_mobile);
        long defaultValue_roaming = this.getXmppConnectionService().getResources().getInteger(R.integer.auto_accept_filesize_roaming);

        String config = "0";
        if (mXmppConnectionService.isWIFI()) {
            config = this.mXmppConnectionService.getPreferences().getString(
                    "auto_accept_file_size_wifi", String.valueOf(defaultValue_wifi));
        } else if (mXmppConnectionService.isMobile()) {
            config = this.mXmppConnectionService.getPreferences().getString(
                    "auto_accept_file_size_mobile", String.valueOf(defaultValue_mobile));
        } else if (mXmppConnectionService.isMobileRoaming()) {
            config = this.mXmppConnectionService.getPreferences().getString(
                    "auto_accept_file_size_roaming", String.valueOf(defaultValue_roaming));
        }
        try {
            return Long.parseLong(config);
        } catch (NumberFormatException e) {
            return defaultValue_mobile;
        }
    }

    public boolean hasStoragePermission() {
        return Compatibility.hasStoragePermission(mXmppConnectionService);
    }

    public void updateConversationUi(boolean force) {
        synchronized (LAST_UI_UPDATE_CALL) {
            if (force || SystemClock.elapsedRealtime() - LAST_UI_UPDATE_CALL.get() >= UI_REFRESH_THRESHOLD) {
                LAST_UI_UPDATE_CALL.set(SystemClock.elapsedRealtime());
                mXmppConnectionService.updateConversationUi();
            }
        }
    }

    public PowerManager.WakeLock createWakeLock(String name) {
        PowerManager powerManager = (PowerManager) mXmppConnectionService.getSystemService(Context.POWER_SERVICE);
        return powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, name);
    }
}
