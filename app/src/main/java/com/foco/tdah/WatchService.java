package com.foco.tdah;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.app.usage.UsageEvents;
import android.app.usage.UsageStatsManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.ServiceInfo;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.provider.Settings;

/**
 * Vigia qual app está na frente (pelo histórico de uso do Android) e, quando é um app
 * bloqueado e a regra manda bloquear, abre a tela do Foco TDAH por cima.
 */
public class WatchService extends Service {
    private static final String CHANNEL = "bloqueio";
    private static final long POLL_MS = 1500;
    private static final long RECHECK_MS = 10000;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private UsageStatsManager usm;
    private long lastEvent;
    private long lastCheck;
    private String current;
    private String currentClass;
    private boolean polling;

    private final Runnable poll = new Runnable() {
        @Override
        public void run() {
            if (!polling) return;
            step();
            handler.postDelayed(this, POLL_MS);
        }
    };

    private final BroadcastReceiver screen = new BroadcastReceiver() {
        @Override
        public void onReceive(Context c, Intent i) {
            if (Intent.ACTION_SCREEN_OFF.equals(i.getAction())) stopPolling();
            else startPolling();
        }
    };

    /** O serviço só precisa rodar com o bloqueio ligado, apps escolhidos e as duas permissões. */
    static boolean wanted(Context c) {
        return Store.settings(c).optBoolean("blockOn", false)
                && !Store.blockedApps(c).isEmpty()
                && Usage.hasAccess(c)
                && Settings.canDrawOverlays(c);
    }

    /** Liga ou desliga o serviço conforme a configuração atual. */
    static void ensure(Context c) {
        Intent i = new Intent(c, WatchService.class);
        try {
            if (wanted(c)) c.startForegroundService(i);
            else c.stopService(i);
        } catch (RuntimeException ignored) {
            // O Android pode recusar o início em segundo plano; a próxima abertura do app liga de novo.
        }
    }

    @Override
    public void onCreate() {
        super.onCreate();
        usm = (UsageStatsManager) getSystemService(Context.USAGE_STATS_SERVICE);
        IntentFilter f = new IntentFilter();
        f.addAction(Intent.ACTION_SCREEN_ON);
        f.addAction(Intent.ACTION_SCREEN_OFF);
        registerReceiver(screen, f);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Notification n = notification();
        if (Build.VERSION.SDK_INT >= 34) startForeground(7, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE);
        else startForeground(7, n);
        if (!wanted(this)) {
            stopSelf();
            return START_NOT_STICKY;
        }
        startPolling();
        return START_STICKY;
    }

    private Notification notification() {
        NotificationManager nm = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        NotificationChannel ch = new NotificationChannel(CHANNEL, "Bloqueio ativo", NotificationManager.IMPORTANCE_MIN);
        ch.setShowBadge(false);
        if (nm != null) nm.createNotificationChannel(ch);
        PendingIntent pi = PendingIntent.getActivity(this, 70, new Intent(this, MainActivity.class).putExtra("tab", "bloqueio"),
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        return new Notification.Builder(this, CHANNEL)
                .setSmallIcon(R.drawable.ic_notif)
                .setContentTitle("Bloqueio de redes sociais ligado")
                .setContentText("Termine a lista de hoje para liberar os apps.")
                .setContentIntent(pi)
                .setOngoing(true)
                .build();
    }

    private void startPolling() {
        if (polling) return;
        polling = true;
        // Olha um pouco para trás para saber o que já está aberto.
        lastEvent = System.currentTimeMillis() - 60000;
        current = null;
        handler.post(poll);
    }

    private void stopPolling() {
        polling = false;
        handler.removeCallbacks(poll);
    }

    private void step() {
        if (usm == null) return;
        long now = System.currentTimeMillis();
        boolean changed = false;
        UsageEvents events = usm.queryEvents(lastEvent + 1, now + 1000);
        UsageEvents.Event ev = new UsageEvents.Event();
        while (events != null && events.hasNextEvent()) {
            events.getNextEvent(ev);
            if (ev.getTimeStamp() > lastEvent) lastEvent = ev.getTimeStamp();
            int type = ev.getEventType();
            if (type == UsageEvents.Event.ACTIVITY_RESUMED) {
                changed = changed || !ev.getPackageName().equals(current);
                current = ev.getPackageName();
                currentClass = ev.getClassName();
            } else if (type == UsageEvents.Event.ACTIVITY_PAUSED) {
                // Só conta como saída se for a mesma tela que estava na frente.
                if (ev.getPackageName().equals(current) && (currentClass == null || currentClass.equals(ev.getClassName()))) {
                    current = null;
                }
            }
        }
        if (current == null || current.equals(getPackageName())) return;
        if (!Store.blockedApps(this).contains(current)) return;
        // Com o app aberto, confere de tempos em tempos: o tempo livre pode acabar no meio do uso.
        if (!changed && now - lastCheck < RECHECK_MS) return;
        lastCheck = now;
        if (Blocker.shouldBlock(this)) {
            String pkg = current;
            current = null;
            Intent i = new Intent(this, MainActivity.class)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP)
                    .putExtra("blocked", pkg);
            try {
                startActivity(i);
            } catch (RuntimeException ignored) {
            }
        }
    }

    @Override
    public void onDestroy() {
        stopPolling();
        unregisterReceiver(screen);
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
