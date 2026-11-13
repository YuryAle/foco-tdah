package com.foco.tdah;

import android.Manifest;
import android.content.ComponentName;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.net.Uri;
import android.os.Build;
import android.provider.Settings;
import android.webkit.JavascriptInterface;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/** O que a interface (HTML) pode pedir ao Android. */
public final class Bridge {
    private final MainActivity act;

    Bridge(MainActivity act) {
        this.act = act;
    }

    @JavascriptInterface
    public String load() {
        return Store.raw(act);
    }

    @JavascriptInterface
    public void save(String json) {
        Store.prefs(act).edit().putString("data", json).apply();
        Alarms.schedule(act);
    }

    /** Aba ou bloqueio que abriu o app (lido uma vez). */
    @JavascriptInterface
    public String launchInfo() {
        return act.takeLaunchInfo();
    }

    @JavascriptInterface
    public String status() {
        JSONObject o = new JSONObject();
        try {
            o.put("usage", Usage.hasAccess(act));
            o.put("acc", accessibilityOn());
            o.put("notif", Build.VERSION.SDK_INT < 33
                    || act.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED);
            o.put("exact", Alarms.exactAllowed(act));
            o.put("passes", Blocker.passesLeft(act));
            o.put("passUntil", Store.prefs(act).getLong("passUntil", 0));
            o.put("blocking", Blocker.shouldBlock(act));
        } catch (JSONException ignored) {
        }
        return o.toString();
    }

    private boolean accessibilityOn() {
        String on = Settings.Secure.getString(act.getContentResolver(), Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES);
        if (on == null) return false;
        ComponentName me = new ComponentName(act, BlockService.class);
        return on.contains(me.flattenToString()) || on.contains(me.flattenToShortString());
    }

    /** Apps instalados que aparecem na tela inicial: [{pkg, label}], em ordem alfabética. */
    @JavascriptInterface
    public String apps() {
        PackageManager pm = act.getPackageManager();
        Intent main = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER);
        List<ResolveInfo> list = pm.queryIntentActivities(main, 0);
        Map<String, String> byLabel = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
        for (ResolveInfo ri : list) {
            String pkg = ri.activityInfo.packageName;
            if (pkg.equals(act.getPackageName())) continue;
            byLabel.put(String.valueOf(ri.loadLabel(pm)) + "\u0000" + pkg, pkg);
        }
        JSONArray out = new JSONArray();
        try {
            for (Map.Entry<String, String> e : byLabel.entrySet()) {
                String label = e.getKey().substring(0, e.getKey().indexOf('\u0000'));
                out.put(new JSONObject().put("pkg", e.getValue()).put("label", label));
            }
        } catch (JSONException ignored) {
        }
        return out.toString();
    }

    /** Minutos de uso hoje dos apps bloqueados: {pacote: minutos}. */
    @JavascriptInterface
    public String usage() {
        Set<String> pkgs = Store.blockedApps(act);
        JSONObject o = new JSONObject();
        try {
            for (Map.Entry<String, Long> e : Usage.today(act, pkgs).entrySet()) {
                o.put(e.getKey(), Math.round(e.getValue() / 60000.0));
            }
        } catch (JSONException ignored) {
        }
        return o.toString();
    }

    @JavascriptInterface
    public void openUsageSettings() {
        open(new Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS));
    }

    @JavascriptInterface
    public void openAccessibilitySettings() {
        open(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS));
    }

    @JavascriptInterface
    public void openAppInfo() {
        open(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:" + act.getPackageName())));
    }

    @JavascriptInterface
    public void requestNotifications() {
        if (Build.VERSION.SDK_INT < 33) return;
        act.runOnUiThread(() -> act.requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, 1));
    }

    @JavascriptInterface
    public int emergencyPass() {
        return Blocker.usePass(act);
    }

    @JavascriptInterface
    public void startFocus(double endAtMs) {
        Alarms.startFocus(act, (long) endAtMs);
    }

    @JavascriptInterface
    public void cancelFocus() {
        Alarms.cancelFocus(act);
    }

    private void open(Intent i) {
        try {
            act.startActivity(i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        } catch (RuntimeException e) {
            act.startActivity(new Intent(Settings.ACTION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        }
    }
}
