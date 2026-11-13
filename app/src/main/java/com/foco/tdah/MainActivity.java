package com.foco.tdah;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Toast;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;

public class MainActivity extends Activity {
    private WebView web;
    private static final String ASSET = "file:///android_asset/index.html";
    private static final long CHECK_EVERY_MS = 3 * 3600000L;

    private String launchInfo = "{}";
    private long lastCheck;

    @SuppressLint("SetJavaScriptEnabled")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        launchInfo = info(getIntent());

        web = new WebView(this);
        web.setBackgroundColor(0xFFF6F3EE);
        web.setOverScrollMode(WebView.OVER_SCROLL_NEVER);
        WebSettings ws = web.getSettings();
        ws.setJavaScriptEnabled(true);
        ws.setDomStorageEnabled(true);
        ws.setTextZoom(100);
        web.setWebViewClient(new WebViewClient());
        web.addJavascriptInterface(new Bridge(this), "Native");
        setContentView(web);
        loadUi();

        Alarms.schedule(this);
    }

    /** Carrega a interface: a baixada numa atualização, se houver, senão a que veio no APK. */
    private void loadUi() {
        String html = Updater.html(this);
        if (html == null) web.loadUrl(ASSET);
        else web.loadDataWithBaseURL(ASSET, html, "text/html", "UTF-8", null);
    }

    /** Procura atualização fora da tela. manual = pedido pelo botão, então sempre responde algo. */
    void checkUpdate(boolean manual) {
        lastCheck = System.currentTimeMillis();
        new Thread(() -> {
            String r = Updater.check(getApplicationContext());
            runOnUiThread(() -> {
                if (isDestroyed()) return;
                if (Updater.WEB.equals(r)) {
                    loadUi();
                    toast("App atualizado ✓");
                } else if (Updater.APK.equals(r)) {
                    web.evaluateJavascript("window.onUpdateInfo&&onUpdateInfo()", null);
                    if (manual) installUpdate();
                } else if (manual) {
                    toast(Updater.NONE.equals(r) ? "Você já está na versão mais recente." : "Não deu para verificar. Confira a internet.");
                }
                web.evaluateJavascript("window.onUpdateDone&&onUpdateDone()", null);
            });
        }).start();
    }

    void installUpdate() {
        runOnUiThread(() -> toast("Baixando a nova versão…"));
        new Thread(() -> {
            try {
                Updater.installApk(getApplicationContext());
            } catch (IOException e) {
                runOnUiThread(() -> {
                    toast("Não deu para instalar por aqui. Abrindo o download.");
                    try {
                        startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(Updater.APK_URL)));
                    } catch (RuntimeException ignored) {
                    }
                });
            }
        }).start();
    }

    private void toast(String text) {
        Toast.makeText(this, text, Toast.LENGTH_LONG).show();
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        String info = info(intent);
        if (!"{}".equals(info)) web.evaluateJavascript("window.onNativeIntent&&onNativeIntent(" + info + ")", null);
    }

    @Override
    protected void onResume() {
        super.onResume();
        WatchService.ensure(this);
        if (System.currentTimeMillis() - lastCheck > CHECK_EVERY_MS) checkUpdate(false);
        if (web != null) web.evaluateJavascript("window.onNativeResume&&onNativeResume()", null);
    }

    /** Voltar leva à tela inicial, para o app bloqueado não reaparecer por baixo. */
    @SuppressWarnings("deprecation")
    @Override
    public void onBackPressed() {
        try {
            startActivity(new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        } catch (RuntimeException e) {
            moveTaskToBack(true);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (web != null) web.evaluateJavascript("window.onNativeResume&&onNativeResume()", null);
    }

    /** Barras do sistema e fundo acompanham o tema escolhido na interface. */
    @SuppressWarnings("deprecation")
    void applyTheme(boolean dark) {
        int bg = dark ? 0xFF14171C : 0xFFF6F3EE;
        getWindow().setStatusBarColor(bg);
        getWindow().setNavigationBarColor(bg);
        getWindow().getDecorView().setBackgroundColor(bg);
        int light = View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR | View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR;
        getWindow().getDecorView().setSystemUiVisibility(dark ? 0 : light);
        if (web != null) web.setBackgroundColor(bg);
    }

    synchronized String takeLaunchInfo() {
        String s = launchInfo;
        launchInfo = "{}";
        return s;
    }

    private static String info(Intent i) {
        JSONObject o = new JSONObject();
        if (i != null) {
            try {
                if (i.hasExtra("blocked")) o.put("blocked", i.getStringExtra("blocked"));
                if (i.hasExtra("tab")) o.put("tab", i.getStringExtra("tab"));
            } catch (JSONException ignored) {
            }
        }
        return o.toString();
    }
}
