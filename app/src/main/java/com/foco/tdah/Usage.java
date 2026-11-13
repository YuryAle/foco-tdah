package com.foco.tdah;

import android.app.AppOpsManager;
import android.app.usage.UsageEvents;
import android.app.usage.UsageStatsManager;
import android.content.Context;
import android.os.Process;

import java.util.Calendar;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/** Tempo de uso de hoje por app, a partir do histórico de uso do Android. */
final class Usage {
    private Usage() {}

    @SuppressWarnings("deprecation")
    static boolean hasAccess(Context c) {
        AppOpsManager ops = (AppOpsManager) c.getSystemService(Context.APP_OPS_SERVICE);
        if (ops == null) return false;
        int mode = ops.checkOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), c.getPackageName());
        return mode == AppOpsManager.MODE_ALLOWED;
    }

    /** Milissegundos em primeiro plano hoje, para cada pacote pedido. */
    static Map<String, Long> today(Context c, Set<String> pkgs) {
        Map<String, Long> total = new HashMap<>();
        if (pkgs.isEmpty() || !hasAccess(c)) return total;
        UsageStatsManager usm = (UsageStatsManager) c.getSystemService(Context.USAGE_STATS_SERVICE);
        if (usm == null) return total;

        Calendar cal = Calendar.getInstance();
        cal.set(Calendar.HOUR_OF_DAY, 0);
        cal.set(Calendar.MINUTE, 0);
        cal.set(Calendar.SECOND, 0);
        cal.set(Calendar.MILLISECOND, 0);
        long start = cal.getTimeInMillis();
        long now = System.currentTimeMillis();

        // Um app pode ter várias telas abertas em sequência; o uso só termina quando todas saem.
        Map<String, Long> openSince = new HashMap<>();
        Map<String, Integer> openCount = new HashMap<>();
        Set<String> seen = new HashSet<>();
        UsageEvents events = usm.queryEvents(start, now);
        UsageEvents.Event ev = new UsageEvents.Event();
        while (events != null && events.hasNextEvent()) {
            events.getNextEvent(ev);
            String pkg = ev.getPackageName();
            if (pkg == null || !pkgs.contains(pkg)) continue;
            int type = ev.getEventType();
            if (type == UsageEvents.Event.MOVE_TO_FOREGROUND) {
                Integer n = openCount.get(pkg);
                if (n == null || n == 0) openSince.put(pkg, ev.getTimeStamp());
                openCount.put(pkg, (n == null ? 0 : n) + 1);
                seen.add(pkg);
            } else if (type == UsageEvents.Event.MOVE_TO_BACKGROUND) {
                Integer n = openCount.get(pkg);
                if (n == null || n == 0) {
                    // Sem início registrado hoje: o app já estava aberto à meia-noite.
                    if (seen.add(pkg)) add(total, pkg, ev.getTimeStamp() - start);
                } else {
                    openCount.put(pkg, n - 1);
                    if (n == 1) add(total, pkg, ev.getTimeStamp() - openSince.get(pkg));
                }
            }
        }
        for (Map.Entry<String, Integer> e : openCount.entrySet()) {
            if (e.getValue() > 0) add(total, e.getKey(), now - openSince.get(e.getKey()));
        }
        return total;
    }

    static long totalMs(Context c, Set<String> pkgs) {
        long sum = 0;
        for (long v : today(c, pkgs).values()) sum += v;
        return sum;
    }

    private static void add(Map<String, Long> m, String k, long v) {
        if (v <= 0) return;
        Long cur = m.get(k);
        m.put(k, (cur == null ? 0 : cur) + v);
    }
}
