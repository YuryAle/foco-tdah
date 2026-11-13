package com.foco.tdah;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
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
        if (web != null) web.evaluateJavascript("window.onNativeResume&&onNativeResume()", null);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (web != null) web.evaluateJavascript("window.onNativeResume&&onNativeResume()", null);
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
