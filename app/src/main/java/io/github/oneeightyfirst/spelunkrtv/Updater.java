package io.github.oneeightyfirst.spelunkrtv;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.PendingIntent;
import android.content.Intent;
import android.content.pm.PackageInstaller;
import android.net.Uri;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.widget.Toast;

import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.security.MessageDigest;

/**
 * Update check. On launch, reads one small file from this project's GitHub Pages site:
 *
 *     https://oneeightyfirst.github.io/spelunkr-tv/version.json
 *
 * If it lists a newer version, asks whether to install it. Only on "Update" does it
 * download the APK (from the same site), check its SHA-256 against version.json, and hand
 * it to Android's installer, which shows its own Install screen. Android refuses the update
 * unless it is signed with the same key as the installed app.
 *
 * Nothing about you or your device is sent. It is a plain GET of a public file.
 */
final class Updater {

    static final String SITE = "https://oneeightyfirst.github.io/spelunkr-tv/";
    static final String ACTION_INSTALL_STATUS = BuildConfig.APPLICATION_ID + ".INSTALL_STATUS";

    private final Activity activity;
    private final Handler main = new Handler(Looper.getMainLooper());

    Updater(Activity activity) {
        this.activity = activity;
    }

    void checkInBackground() {
        new Thread(() -> {
            try {
                JSONObject v = new JSONObject(new String(get(SITE + "version.json"), "UTF-8"));
                if (v.getInt("versionCode") > BuildConfig.VERSION_CODE) {
                    String name = v.optString("versionName"), sha = v.getString("sha256");
                    main.post(() -> offer(name, sha));
                }
            } catch (Exception ignored) {
                // Offline or no version file: just carry on.
            }
        }, "update-check").start();
    }

    private void offer(String versionName, String sha256) {
        if (activity.isFinishing()) return;
        new AlertDialog.Builder(activity)
                .setTitle("Update available")
                .setMessage("Spelunkr TV " + versionName + " is available (you have "
                        + BuildConfig.VERSION_NAME + "). Install it now?")
                .setPositiveButton("Update", (d, w) -> download(sha256))
                .setNegativeButton("Later", null)
                .show();
    }

    private void download(String expectedSha256) {
        if (Build.VERSION.SDK_INT >= 26 && !activity.getPackageManager().canRequestPackageInstalls()) {
            askForInstallPermission();
            return;
        }
        Toast.makeText(activity, "Downloading update…", Toast.LENGTH_LONG).show();
        new Thread(() -> {
            try {
                File apk = new File(activity.getCacheDir(), "update.apk");
                byte[] bytes = get(SITE + "spelunkr-tv.apk");
                if (!sha256(bytes).equalsIgnoreCase(expectedSha256)) {
                    throw new SecurityException("Downloaded file doesn't match its checksum");
                }
                try (OutputStream out = new FileOutputStream(apk)) { out.write(bytes); }
                install(apk);
            } catch (Exception e) {
                main.post(() -> Toast.makeText(activity, "Update failed: " + e.getMessage(),
                        Toast.LENGTH_LONG).show());
            }
        }, "update-download").start();
    }

    /** Fire OS 7+ needs "Install unknown apps" allowed for this app, once. */
    private void askForInstallPermission() {
        Toast.makeText(activity, "Allow Spelunkr TV to install apps, then choose Update again",
                Toast.LENGTH_LONG).show();
        try {
            activity.startActivity(new Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                    Uri.parse("package:" + activity.getPackageName())));
        } catch (Exception e) {
            Toast.makeText(activity, "Settings → My Fire TV → Developer Options → Install unknown apps → Spelunkr TV",
                    Toast.LENGTH_LONG).show();
        }
    }

    private void install(File apk) throws Exception {
        PackageInstaller installer = activity.getPackageManager().getPackageInstaller();
        PackageInstaller.SessionParams params =
                new PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL);
        int id = installer.createSession(params);
        try (PackageInstaller.Session session = installer.openSession(id)) {
            try (InputStream in = new FileInputStream(apk);
                 OutputStream out = session.openWrite("update.apk", 0, apk.length())) {
                byte[] buf = new byte[65536];
                for (int n; (n = in.read(buf)) > 0; ) out.write(buf, 0, n);
                session.fsync(out);
            }
            // Android reports back to MainActivity, which shows the system's Install screen.
            Intent status = new Intent(activity, MainActivity.class).setAction(ACTION_INSTALL_STATUS);
            int flags = PendingIntent.FLAG_UPDATE_CURRENT | (Build.VERSION.SDK_INT >= 31 ? PendingIntent.FLAG_MUTABLE : 0);
            session.commit(PendingIntent.getActivity(activity, id, status, flags).getIntentSender());
        }
    }

    /** Called from MainActivity.onNewIntent with the installer's status. */
    static void onInstallStatus(Activity activity, Intent intent) {
        int status = intent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE);
        if (status == PackageInstaller.STATUS_PENDING_USER_ACTION) {
            Intent confirm = intent.getParcelableExtra(Intent.EXTRA_INTENT);
            if (confirm != null) activity.startActivity(confirm);
        } else if (status != PackageInstaller.STATUS_SUCCESS) {
            String msg = intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE);
            Toast.makeText(activity, "Update not installed" + (msg != null ? ": " + msg : ""),
                    Toast.LENGTH_LONG).show();
        }
    }

    private static byte[] get(String url) throws Exception {
        HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
        c.setConnectTimeout(10000);
        c.setReadTimeout(30000);
        c.setUseCaches(false);
        try (InputStream in = c.getInputStream()) {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buf = new byte[65536];
            for (int n; (n = in.read(buf)) > 0; ) out.write(buf, 0, n);
            return out.toByteArray();
        } finally {
            c.disconnect();
        }
    }

    private static String sha256(byte[] data) throws Exception {
        StringBuilder sb = new StringBuilder();
        for (byte b : MessageDigest.getInstance("SHA-256").digest(data)) sb.append(String.format("%02x", b));
        return sb.toString();
    }
}
