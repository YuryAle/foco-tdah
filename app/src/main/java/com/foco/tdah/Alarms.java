package com.foco.tdah;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Build;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;

/**
 * Lembretes. Mantém um único alarme armado para o próximo evento; quando ele toca,
 * mostra o que venceu e arma o seguinte.
 */
public class Alarms extends BroadcastReceiver {
    static final String ACT_TICK = "com.foco.tdah.TICK";
    static final String ACT_FOCUS = "com.foco.tdah.FOCUS";

    private static final int TASK = 0, PLAN = 1, MORNING = 2, NAG = 3;
    private static final long STALE_MS = 15 * 60000L;

    private static final class Ev {
        final long at;
        final int type;
        final String text;

        Ev(long at, int type, String text) {
            this.at = at;
            this.type = type;
            this.text = text;
        }
    }

    @Override
    public void onReceive(Context c, Intent intent) {
        String action = intent == null ? null : intent.getAction();
        if (ACT_FOCUS.equals(action)) {
            Notif.show(c, 9001, "✅ Tempo de foco concluído!", "Boa! Levante, beba água e faça uma pausa curta.", "foco");
            return;
        }
        if (ACT_TICK.equals(action)) {
            long now = System.currentTimeMillis();
            long last = Store.prefs(c).getLong("lastFire", now - 60000);
            for (Ev e : events(c)) {
                if (e.at > last && e.at <= now + 5000 && e.at > now - STALE_MS) fire(c, e);
            }
        }
        schedule(c);
        WatchService.ensure(c);
    }

    /** Arma o alarme do próximo evento. Chamado sempre que os dados mudam e ao ligar o aparelho. */
    static void schedule(Context c) {
        long now = System.currentTimeMillis();
        Store.prefs(c).edit().putLong("lastFire", now).apply();
        long next = Long.MAX_VALUE;
        for (Ev e : events(c)) if (e.at > now && e.at < next) next = e.at;

        AlarmManager am = (AlarmManager) c.getSystemService(Context.ALARM_SERVICE);
        if (am == null) return;
        PendingIntent pi = pending(c, ACT_TICK, 1);
        am.cancel(pi);
        if (next != Long.MAX_VALUE) set(am, next, pi);
    }

    static void startFocus(Context c, long endAt) {
        AlarmManager am = (AlarmManager) c.getSystemService(Context.ALARM_SERVICE);
        if (am != null) set(am, endAt, pending(c, ACT_FOCUS, 2));
    }

    static void cancelFocus(Context c) {
        AlarmManager am = (AlarmManager) c.getSystemService(Context.ALARM_SERVICE);
        if (am != null) am.cancel(pending(c, ACT_FOCUS, 2));
    }

    static boolean exactAllowed(Context c) {
        if (Build.VERSION.SDK_INT < 31) return true;
        AlarmManager am = (AlarmManager) c.getSystemService(Context.ALARM_SERVICE);
        return am != null && am.canScheduleExactAlarms();
    }

    private static void set(AlarmManager am, long at, PendingIntent pi) {
        boolean exact = Build.VERSION.SDK_INT < 31 || am.canScheduleExactAlarms();
        try {
            if (exact) am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi);
            else am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi);
        } catch (SecurityException e) {
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi);
        }
    }

    private static PendingIntent pending(Context c, String action, int code) {
        Intent i = new Intent(c, Alarms.class).setAction(action);
        return PendingIntent.getBroadcast(c, code, i, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }

    /** Todos os eventos de hoje e de amanhã. */
    private static List<Ev> events(Context c) {
        List<Ev> out = new ArrayList<>();
        JSONObject s = Store.settings(c);
        for (int d = 0; d < 2; d++) {
            Calendar day = Calendar.getInstance();
            day.add(Calendar.DAY_OF_MONTH, d);

            JSONArray tasks = Store.tasks(c, Store.dayKey(day));
            for (int i = 0; i < tasks.length(); i++) {
                JSONObject t = tasks.optJSONObject(i);
                if (t == null || t.optBoolean("done", false)) continue;
                long at = at(day, t.optString("time", ""));
                if (at > 0) out.add(new Ev(at, TASK, t.optString("text", "")));
            }
            if (s.optBoolean("planOn", true)) {
                long at = at(day, s.optString("planTime", "21:00"));
                if (at > 0) out.add(new Ev(at, PLAN, ""));
            }
            if (s.optBoolean("morningOn", true)) {
                long at = at(day, s.optString("morningTime", "08:00"));
                if (at > 0) out.add(new Ev(at, MORNING, ""));
            }
            if (s.optBoolean("nagOn", true)) {
                int every = Math.max(30, s.optInt("nagEvery", 120));
                for (int m = 9 * 60; m <= 21 * 60; m += every) {
                    out.add(new Ev(at(day, m / 60, m % 60), NAG, ""));
                }
            }
        }
        return out;
    }

    private static long at(Calendar day, String hhmm) {
        if (hhmm == null || hhmm.length() < 4 || hhmm.indexOf(':') < 1) return 0;
        try {
            String[] p = hhmm.split(":");
            return at(day, Integer.parseInt(p[0]), Integer.parseInt(p[1]));
        } catch (RuntimeException e) {
            return 0;
        }
    }

    private static long at(Calendar day, int h, int m) {
        Calendar cal = (Calendar) day.clone();
        cal.set(Calendar.HOUR_OF_DAY, h);
        cal.set(Calendar.MINUTE, m);
        cal.set(Calendar.SECOND, 0);
        cal.set(Calendar.MILLISECOND, 0);
        return cal.getTimeInMillis();
    }

    private static void fire(Context c, Ev e) {
        List<String> pend = Store.pending(c, Store.todayKey());
        int todayCount = Store.tasks(c, Store.todayKey()).length();
        switch (e.type) {
            case TASK:
                Notif.show(c, 1000 + (e.text.hashCode() & 0xFFFF), "⏰ Agora: " + e.text,
                        "Está na hora. Toque para abrir sua lista.", "agenda");
                break;
            case PLAN: {
                Calendar tomorrow = Calendar.getInstance();
                tomorrow.add(Calendar.DAY_OF_MONTH, 1);
                int n = Store.tasks(c, Store.dayKey(tomorrow)).length();
                Notif.show(c, 2, "📝 Hora de planejar amanhã",
                        n == 0 ? "Escreva agora o que você precisa fazer amanhã. Leva 2 minutos."
                                : "Amanhã já tem " + n + (n == 1 ? " tarefa" : " tarefas") + ". Dê uma revisada na lista.",
                        "plan");
                break;
            }
            case MORNING:
                if (todayCount == 0) {
                    Notif.show(c, 3, "☀️ Bom dia!", "Você ainda não tem lista para hoje. Monte agora, só o essencial.", "agenda");
                } else if (!pend.isEmpty()) {
                    Notif.show(c, 3, "☀️ Bom dia! " + pend.size() + (pend.size() == 1 ? " tarefa hoje" : " tarefas hoje"),
                            "Comece por: " + pend.get(0), "agenda");
                }
                break;
            case NAG:
                if (!pend.isEmpty()) {
                    Notif.show(c, 4, pend.size() == 1 ? "Falta 1 tarefa hoje" : "Faltam " + pend.size() + " tarefas hoje",
                            "Próxima: " + pend.get(0), "agenda");
                }
                break;
            default:
                break;
        }
    }
}
