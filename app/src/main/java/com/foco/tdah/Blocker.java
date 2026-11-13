package com.foco.tdah;

import android.content.Context;

import org.json.JSONArray;
import org.json.JSONObject;

/** Regra do bloqueio: passou do tempo combinado e a lista de hoje não está concluída. */
final class Blocker {
    static final int PASSES_PER_DAY = 2;
    static final long PASS_MS = 5 * 60000L;

    private Blocker() {}

    static boolean shouldBlock(Context c) {
        JSONObject s = Store.settings(c);
        if (!s.optBoolean("blockOn", false)) return false;
        if (System.currentTimeMillis() < Store.prefs(c).getLong("passUntil", 0)) return false;

        JSONArray tasks = Store.tasks(c, Store.todayKey());
        if (tasks.length() == 0) {
            if (!s.optBoolean("requireList", true)) return false;
        } else if (Store.pending(c, Store.todayKey()).isEmpty()) {
            return false;
        }

        int limit = s.optInt("limitMin", 30);
        if (limit <= 0) return true;
        // Sem permissão para medir o uso, não dá para dar o tempo livre: bloqueia direto.
        if (!Usage.hasAccess(c)) return true;
        return Usage.totalMs(c, Store.blockedApps(c)) >= limit * 60000L;
    }

    static int passesLeft(Context c) {
        String day = Store.prefs(c).getString("passDay", "");
        int used = day.equals(Store.todayKey()) ? Store.prefs(c).getInt("passUsed", 0) : 0;
        return Math.max(0, PASSES_PER_DAY - used);
    }

    /** Libera os apps por 5 minutos. Devolve quantas liberações restam, ou -1 se acabaram. */
    static int usePass(Context c) {
        int left = passesLeft(c);
        if (left <= 0) return -1;
        Store.prefs(c).edit()
                .putString("passDay", Store.todayKey())
                .putInt("passUsed", PASSES_PER_DAY - left + 1)
                .putLong("passUntil", System.currentTimeMillis() + PASS_MS)
                .apply();
        return left - 1;
    }
}
