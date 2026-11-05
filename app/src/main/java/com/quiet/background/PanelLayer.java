package com.quiet.background;

import android.accessibilityservice.AccessibilityService;
import android.content.res.Configuration;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;

/** Optional window host. The tile asks for its own position only on a user tap. */
public final class PanelLayer extends AccessibilityService {
    private static PanelLayer connected;
    private View panel;
    private Runnable onClosed;
    private final Rect anchor = new Rect();

    @Override protected void onServiceConnected() { connected = this; }
    static PanelLayer connected() { return connected; }
    @Override public void onAccessibilityEvent(AccessibilityEvent event) {}
    @Override public void onInterrupt() { remove(); }
    @Override public void onConfigurationChanged(Configuration config) {
        super.onConfigurationChanged(config);
        remove();
    }
    @Override public void onDestroy() {
        if (connected == this) connected = null;
        remove();
        super.onDestroy();
    }

    boolean show(View content, Runnable closed) {
        if (panel != null) return false;
        AccessibilityNodeInfo root = getRootInActiveWindow();
        if (root == null || !"com.android.systemui".contentEquals(root.getPackageName())) return false;
        Rect screen = new Rect();
        root.getBoundsInScreen(screen);
        anchor.setEmpty();
        for (AccessibilityNodeInfo match : root.findAccessibilityNodeInfosByText(getString(R.string.tile_name))) {
            while (match != null && !match.isClickable()) match = match.getParent();
            if (match != null && match.isVisibleToUser()) {
                match.getBoundsInScreen(anchor);
                if (!anchor.isEmpty()) break;
            }
        }
        if (anchor.isEmpty() || !screen.contains(anchor)) return false;
        int margin = dp(10);
        int width = Math.min(dp(316), screen.width() - 2 * margin);
        int height = Math.min(dp(242), screen.height() - 2 * margin);
        if (width <= 0 || height <= 0) return false;
        WindowManager.LayoutParams lp = new WindowManager.LayoutParams(width, height,
                WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
                        | WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH, PixelFormat.TRANSLUCENT);
        lp.gravity = Gravity.TOP | Gravity.LEFT;
        lp.x = Math.max(screen.left + margin, Math.min(anchor.centerX() - width / 2, screen.right - width - margin));
        lp.y = Math.max(screen.top + margin, Math.min(anchor.centerY() - height / 2, screen.bottom - height - margin));
        content.setAlpha(0);
        content.setPivotX(0);
        content.setPivotY(0);
        content.setOnTouchListener((v, e) -> {
            if (e.getAction() == MotionEvent.ACTION_OUTSIDE) { collapse(); return true; }
            return false;
        });
        try {
            getSystemService(WindowManager.class).addView(content, lp);
        } catch (WindowManager.BadTokenException | SecurityException denied) {
            return false;
        }
        panel = content;
        onClosed = closed;
        content.post(() -> {
            if (panel != content) return;
            int[] xy = new int[2];
            content.getLocationOnScreen(xy);
            content.setTranslationX(anchor.left - xy[0]);
            content.setTranslationY(anchor.top - xy[1]);
            content.setScaleX((float) anchor.width() / width);
            content.setScaleY((float) anchor.height() / height);
            content.animate().translationX(0).translationY(0).scaleX(1).scaleY(1).alpha(1).setDuration(240).start();
        });
        return true;
    }

    void collapse() {
        View view = panel;
        if (view == null) return;
        int[] xy = new int[2];
        view.getLocationOnScreen(xy);
        view.animate().translationX(anchor.left - xy[0] + view.getTranslationX())
                .translationY(anchor.top - xy[1] + view.getTranslationY())
                .scaleX((float) anchor.width() / Math.max(1, view.getWidth()))
                .scaleY((float) anchor.height() / Math.max(1, view.getHeight()))
                .alpha(0).setDuration(180).withEndAction(this::remove).start();
    }

    void remove() {
        if (panel == null) return;
        View old = panel;
        panel = null;
        old.animate().cancel();
        getSystemService(WindowManager.class).removeViewImmediate(old);
        Runnable closed = onClosed;
        onClosed = null;
        closed.run();
    }
    private int dp(int value) { return Math.round(value * getResources().getDisplayMetrics().density); }
}
