package com.foco.tdah;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;

import org.json.JSONException;
import org.json.JSONObject;

public class MainActivity extends Activity {
    private WebView web;
    private String launchInfo = "{}";

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
        web.loadUrl("file:///android_asset/index.html");

        Alarms.schedule(this);
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
