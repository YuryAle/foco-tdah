package com.foco.tdah;

import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageInstaller;
import android.os.Build;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

/**
 * Atualizações a partir do repositório no GitHub.
 * A interface (index.html) é trocada sem reinstalar; mudanças na parte nativa exigem um APK novo.
 * version.json no repositório diz qual é a versão atual de cada uma.
 */
final class Updater {
    static final String NONE = "none", WEB = "web", APK = "apk", ERROR = "error";

    private static final String RAW = "https://raw.githubusercontent.com/YuryAle/foco-tdah/main/";
    static final String APK_URL = "https://github.com/YuryAle/foco-tdah/releases/download/latest/FocoTDAH.apk";

    private Updater() {}

    private static File bundle(Context c) {
        return new File(c.getFilesDir(), "web/index.html");
    }

    /** Versão da interface baixada, ou 0 se a que vale é a que veio no APK. */
    private static int downloadedWeb(Context c) {
        SharedPreferences p = Store.prefs(c);
        // Uma interface baixada só vale para o APK em que foi baixada: um APK novo já traz a sua.
        if (p.getInt("webApk", 0) != BuildConfig.VERSION_CODE || !bundle(c).isFile()) return 0;
        int v = p.getInt("webVersion", 0);
        return v > BuildConfig.WEB_VERSION ? v : 0;
    }

    static int webVersion(Context c) {
        return Math.max(BuildConfig.WEB_VERSION, downloadedWeb(c));
    }

    /** HTML baixado mais novo que o do APK, ou null para usar o do APK. */
    static String html(Context c) {
        if (downloadedWeb(c) == 0) return null;
        try (InputStream in = new FileInputStream(bundle(c))) {
            return new String(readAll(in), StandardCharsets.UTF_8);
        } catch (IOException e) {
            return null;
        }
    }

    /** Consulta o repositório. Se só a interface mudou, já baixa e guarda. Bloqueia: chame fora da tela. */
    static String check(Context c) {
        try {
            JSONObject v = new JSONObject(new String(get(RAW + "version.json"), StandardCharsets.UTF_8));
            if (v.optInt("apk", 0) > BuildConfig.VERSION_CODE) return APK;
            int web = v.optInt("web", 0);
            if (web <= webVersion(c)) return NONE;

            byte[] html = get(RAW + "app/src/main/assets/index.html");
            String text = new String(html, StandardCharsets.UTF_8);
            if (html.length < 5000 || !text.contains("</html>")) return ERROR;
            File f = bundle(c);
            File dir = f.getParentFile();
            if (dir != null) dir.mkdirs();
            File tmp = new File(dir, "index.tmp");
            try (OutputStream out = new FileOutputStream(tmp)) {
                out.write(html);
            }
            if (!tmp.renameTo(f)) return ERROR;
            Store.prefs(c).edit().putInt("webVersion", web).putInt("webApk", BuildConfig.VERSION_CODE).apply();
            return WEB;
        } catch (IOException | JSONException | RuntimeException e) {
            return ERROR;
        }
    }

    /** Baixa o APK novo e entrega ao instalador do Android, que pede (no máximo) uma confirmação. */
    static void installApk(Context c) throws IOException {
        PackageInstaller installer = c.getPackageManager().getPackageInstaller();
        PackageInstaller.SessionParams params = new PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL);
        params.setAppPackageName(c.getPackageName());
        if (Build.VERSION.SDK_INT >= 31) params.setRequireUserAction(PackageInstaller.SessionParams.USER_ACTION_NOT_REQUIRED);

        int id = installer.createSession(params);
        try (PackageInstaller.Session session = installer.openSession(id)) {
            HttpURLConnection con = open(APK_URL);
            try (InputStream in = con.getInputStream(); OutputStream out = session.openWrite("FocoTDAH.apk", 0, -1)) {
                byte[] buf = new byte[65536];
                int n;
                while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
                session.fsync(out);
            } finally {
                con.disconnect();
            }
            Intent result = new Intent(c, Alarms.class).setAction(Alarms.ACT_INSTALL);
            int flags = PendingIntent.FLAG_UPDATE_CURRENT | (Build.VERSION.SDK_INT >= 31 ? PendingIntent.FLAG_MUTABLE : 0);
            session.commit(PendingIntent.getBroadcast(c, 3, result, flags).getIntentSender());
        } catch (IOException | RuntimeException e) {
            try {
                installer.abandonSession(id);
            } catch (RuntimeException ignored) {
            }
            throw e instanceof IOException ? (IOException) e : new IOException(e);
        }
    }

    private static HttpURLConnection open(String url) throws IOException {
        // O parâmetro extra evita receber uma cópia antiga guardada no caminho.
        HttpURLConnection con = (HttpURLConnection) new URL(url + "?t=" + System.currentTimeMillis()).openConnection();
        con.setConnectTimeout(10000);
        con.setReadTimeout(20000);
        con.setUseCaches(false);
        if (con.getResponseCode() != 200) {
            con.disconnect();
            throw new IOException("HTTP " + con.getResponseCode());
        }
        return con;
    }

    private static byte[] get(String url) throws IOException {
        HttpURLConnection con = open(url);
        try (InputStream in = con.getInputStream()) {
            return readAll(in);
        } finally {
            con.disconnect();
        }
    }

    private static byte[] readAll(InputStream in) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buf = new byte[16384];
        int n;
        while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
        return out.toByteArray();
    }
}
