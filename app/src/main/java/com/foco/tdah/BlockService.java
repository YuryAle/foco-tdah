package com.foco.tdah;

import android.accessibilityservice.AccessibilityService;
import android.content.Intent;
import android.os.Handler;
import android.os.Looper;
import android.view.accessibility.AccessibilityEvent;

/** Percebe quando um app bloqueado vem para a frente e mostra a lista no lugar. */
public class BlockService extends AccessibilityService {
    private static final long TICK_MS = 20000;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private String current;

    private final Runnable tick = new Runnable() {
        @Override
        public void run() {
            if (current == null) return;
            if (!check(current)) handler.postDelayed(this, TICK_MS);
        }
    };

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        if (event == null || event.getEventType() != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return;
        CharSequence name = event.getPackageName();
        if (name == null) return;
        String pkg = name.toString();

        if (Store.blockedApps(this).contains(pkg)) {
            current = pkg;
            handler.removeCallbacks(tick);
            // Enquanto o app estiver aberto, confere de novo: o tempo pode acabar no meio do uso.
            if (!check(pkg)) handler.postDelayed(tick, TICK_MS);
        } else if (!isOverlay(pkg)) {
            current = null;
            handler.removeCallbacks(tick);
        }
    }

    /** Teclado e barra do sistema aparecem por cima sem que o app de baixo tenha saído. */
    private static boolean isOverlay(String pkg) {
        return pkg.equals("com.android.systemui") || pkg.contains("inputmethod") || pkg.contains("keyboard");
    }

    private boolean check(String pkg) {
        if (!Blocker.shouldBlock(this)) return false;
        current = null;
        handler.removeCallbacks(tick);
        performGlobalAction(GLOBAL_ACTION_HOME);
        Intent i = new Intent(this, MainActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP)
                .putExtra("blocked", pkg);
        startActivity(i);
        return true;
    }

    @Override
    public void onInterrupt() {}

    @Override
    public void onDestroy() {
        handler.removeCallbacks(tick);
        super.onDestroy();
    }
}
