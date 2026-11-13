package com.foco.tdah;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/** Dados do app: um JSON único salvo pela interface e lido pelas partes nativas. */
final class Store {
    private static String cacheRaw;
    private static JSONObject cacheObj;

    private Store() {}

    static SharedPreferences prefs(Context c) {
        return c.getApplicationContext().getSharedPreferences("foco", Context.MODE_PRIVATE);
    }

    static String raw(Context c) {
        return prefs(c).getString("data", "");
    }

    static synchronized JSONObject data(Context c) {
        String s = raw(c);
        if (cacheObj != null && s.equals(cacheRaw)) return cacheObj;
        JSONObject o;
        try {
            o = s.isEmpty() ? new JSONObject() : new JSONObject(s);
        } catch (JSONException e) {
            o = new JSONObject();
        }
        cacheRaw = s;
        cacheObj = o;
        return o;
    }

    static JSONObject settings(Context c) {
        JSONObject s = data(c).optJSONObject("settings");
        return s == null ? new JSONObject() : s;
    }

    static String dayKey(Calendar cal) {
        return String.format(Locale.US, "%04d-%02d-%02d",
                cal.get(Calendar.YEAR), cal.get(Calendar.MONTH) + 1, cal.get(Calendar.DAY_OF_MONTH));
    }

    static String todayKey() {
        return dayKey(Calendar.getInstance());
    }

    static JSONArray tasks(Context c, String key) {
        JSONObject t = data(c).optJSONObject("tasks");
        if (t == null) return new JSONArray();
        JSONArray a = t.optJSONArray(key);
        return a == null ? new JSONArray() : a;
    }

    /** Textos das tarefas ainda não concluídas do dia. */
    static List<String> pending(Context c, String key) {
        List<String> out = new ArrayList<>();
        JSONArray a = tasks(c, key);
        for (int i = 0; i < a.length(); i++) {
            JSONObject t = a.optJSONObject(i);
            if (t != null && !t.optBoolean("done", false)) out.add(t.optString("text", ""));
        }
        return out;
    }

    static Set<String> blockedApps(Context c) {
        Set<String> out = new HashSet<>();
        JSONArray a = settings(c).optJSONArray("blockedApps");
        if (a != null) for (int i = 0; i < a.length(); i++) out.add(a.optString(i));
        return out;
    }
}
