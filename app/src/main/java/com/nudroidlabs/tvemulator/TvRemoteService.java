package com.nudroidlabs.tvemulator;

import android.accessibilityservice.AccessibilityService;
import android.content.Intent;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.WindowManager;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;
import android.view.accessibility.AccessibilityWindowInfo;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.Toast;

import java.util.ArrayDeque;

public final class TvRemoteService extends AccessibilityService {
    private static TvRemoteService connected;

    private String targetPackage;
    private WindowManager windows;
    private WindowManager.LayoutParams position;
    private LinearLayout overlay;
    private boolean collapsed;

    static boolean isReady() {
        return connected != null;
    }

    static void arm(String packageName) {
        if (connected == null) return;
        connected.targetPackage = packageName;
        connected.collapsed = false;
        connected.syncVisibility();
    }

    @Override protected void onServiceConnected() {
        windows = getSystemService(WindowManager.class);
        connected = this;
    }

    @Override public void onAccessibilityEvent(AccessibilityEvent event) {
        syncVisibility();
    }

    @Override public void onInterrupt() {
        hideRemote();
    }

    @Override public boolean onUnbind(Intent intent) {
        disconnect();
        return super.onUnbind(intent);
    }

    @Override public void onDestroy() {
        disconnect();
        super.onDestroy();
    }

    private void disconnect() {
        hideRemote();
        targetPackage = null;
        if (connected == this) connected = null;
    }

    private AccessibilityNodeInfo targetRoot() {
        if (targetPackage == null) return null;
        for (AccessibilityWindowInfo window : getWindows()) {
            if (window.getType() == AccessibilityWindowInfo.TYPE_APPLICATION && window.isFocused()) {
                AccessibilityNodeInfo root = window.getRoot();
                if (root != null && root.getPackageName() != null
                        && targetPackage.contentEquals(root.getPackageName())) return root;
            }
        }
        return null;
    }

    private void syncVisibility() {
        if (targetRoot() == null) {
            hideRemote();
        } else if (overlay == null) {
            showRemote();
        }
    }

    private void hideRemote() {
        if (overlay != null) {
            windows.removeViewImmediate(overlay);
            overlay = null;
        }
    }

    private void showRemote() {
        if (position == null) {
            position = new WindowManager.LayoutParams(-2, -2,
                    WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                    WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
                    PixelFormat.TRANSLUCENT);
            position.gravity = Gravity.BOTTOM | Gravity.RIGHT;
            position.x = dp(12);
            position.y = dp(24);
        }
        overlay = new LinearLayout(this);
        overlay.setOrientation(LinearLayout.VERTICAL);
        overlay.setPadding(dp(6), dp(6), dp(6), dp(6));
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(0xee17243b);
        bg.setCornerRadius(dp(16));
        overlay.setBackground(bg);

        Button mode = button(collapsed ? "Remote" : "Touch mode", () -> {
            collapsed = !collapsed;
            windows.removeViewImmediate(overlay);
            overlay = null;
            showRemote();
        });
        overlay.addView(mode, new LinearLayout.LayoutParams(dp(collapsed ? 86 : 144), dp(40)));
        attachDrag(mode);

        if (!collapsed) {
            LinearLayout top = row();
            add(top, "↑", "Up", () -> move(View.FOCUS_UP));
            LinearLayout middle = row();
            add(middle, "←", "Left", () -> move(View.FOCUS_LEFT));
            add(middle, "OK", "Select", this::clickSelected);
            add(middle, "→", "Right", () -> move(View.FOCUS_RIGHT));
            LinearLayout bottom = row();
            add(bottom, "↓", "Down", () -> move(View.FOCUS_DOWN));
            Button back = button("Back", () -> {
                if (targetRoot() != null) performGlobalAction(GLOBAL_ACTION_BACK);
            });
            overlay.addView(back, new LinearLayout.LayoutParams(-1, dp(42)));
        }
        windows.addView(overlay, position);
    }

    private LinearLayout row() {
        LinearLayout row = new LinearLayout(this);
        row.setGravity(Gravity.CENTER);
        overlay.addView(row, new LinearLayout.LayoutParams(dp(144), dp(44)));
        return row;
    }

    private Button button(String text, Runnable click) {
        Button button = new Button(this);
        button.setText(text);
        button.setAllCaps(false);
        button.setMinWidth(0);
        button.setMinimumWidth(0);
        button.setPadding(0, 0, 0, 0);
        button.setOnClickListener(view -> click.run());
        return button;
    }

    private void add(LinearLayout row, String text, String description, Runnable click) {
        Button button = button(text, click);
        button.setContentDescription(description);
        row.addView(button, new LinearLayout.LayoutParams(dp(48), -1));
    }

    private void attachDrag(Button handle) {
        int slop = ViewConfiguration.get(this).getScaledTouchSlop();
        handle.setOnTouchListener(new View.OnTouchListener() {
            private float x, y;
            private int offsetX, offsetY;
            private boolean moving;

            @Override public boolean onTouch(View view, MotionEvent event) {
                switch (event.getActionMasked()) {
                    case MotionEvent.ACTION_DOWN:
                        x = event.getRawX();
                        y = event.getRawY();
                        offsetX = position.x;
                        offsetY = position.y;
                        moving = false;
                        return true;
                    case MotionEvent.ACTION_MOVE:
                        float dx = event.getRawX() - x;
                        float dy = event.getRawY() - y;
                        if (!moving && Math.hypot(dx, dy) > slop) moving = true;
                        if (moving && overlay != null) {
                            Rect viewport = new Rect();
                            overlay.getWindowVisibleDisplayFrame(viewport);
                            position.x = Math.max(0, Math.min(viewport.width() - overlay.getWidth(),
                                    offsetX - Math.round(dx)));
                            position.y = Math.max(0, Math.min(viewport.height() - overlay.getHeight(),
                                    offsetY - Math.round(dy)));
                            windows.updateViewLayout(overlay, position);
                        }
                        return true;
                    case MotionEvent.ACTION_UP:
                        if (!moving) view.performClick();
                        return true;
                    default:
                        return true;
                }
            }
        });
    }

    private AccessibilityNodeInfo selected(AccessibilityNodeInfo root) {
        AccessibilityNodeInfo current = root.findFocus(AccessibilityNodeInfo.FOCUS_ACCESSIBILITY);
        return current != null ? current : root.findFocus(AccessibilityNodeInfo.FOCUS_INPUT);
    }

    private void move(int direction) {
        AccessibilityNodeInfo root = targetRoot();
        if (root == null) return;
        AccessibilityNodeInfo current = selected(root);
        Rect origin = current == null ? null : bounds(current);
        AccessibilityNodeInfo next = nearest(root, origin, direction);
        if (next == null) return;
        boolean focused = next.performAction(AccessibilityNodeInfo.ACTION_FOCUS);
        focused = next.performAction(AccessibilityNodeInfo.ACTION_ACCESSIBILITY_FOCUS) || focused;
        if (!focused) {
            Toast.makeText(this, "This control does not support focus.", Toast.LENGTH_SHORT).show();
            return;
        }
    }

    private AccessibilityNodeInfo nearest(AccessibilityNodeInfo root, Rect origin, int direction) {
        AccessibilityNodeInfo best = null;
        long bestScore = Long.MAX_VALUE;
        ArrayDeque<AccessibilityNodeInfo> pending = new ArrayDeque<>();
        pending.add(root);
        for (int visited = 0; !pending.isEmpty() && visited < 1500; visited++) {
            AccessibilityNodeInfo node = pending.removeFirst();
            if (!node.isVisibleToUser()) continue;
            for (int index = 0; index < node.getChildCount(); index++) {
                AccessibilityNodeInfo child = node.getChild(index);
                if (child != null) pending.addLast(child);
            }
            if (!node.isEnabled() || !(node.isFocusable() || node.isClickable())) continue;
            Rect rect = bounds(node);
            if (rect.isEmpty()) continue;
            if (origin == null) return node;
            int dx = rect.centerX() - origin.centerX();
            int dy = rect.centerY() - origin.centerY();
            int primary;
            int secondary;
            if (direction == View.FOCUS_LEFT || direction == View.FOCUS_RIGHT) {
                primary = direction == View.FOCUS_LEFT ? -dx : dx;
                secondary = dy;
            } else {
                primary = direction == View.FOCUS_UP ? -dy : dy;
                secondary = dx;
            }
            if (primary <= 0 || rect.equals(origin)) continue;
            long score = (long) primary * primary + 4L * secondary * secondary;
            if (score < bestScore) {
                best = node;
                bestScore = score;
            }
        }
        return best;
    }

    private void clickSelected() {
        AccessibilityNodeInfo root = targetRoot();
        if (root == null) return;
        AccessibilityNodeInfo node = selected(root);
        while (node != null && node.getWindowId() == root.getWindowId()) {
            if (node.isEnabled() && node.isClickable()
                    && node.performAction(AccessibilityNodeInfo.ACTION_CLICK)) return;
            node = node.getParent();
        }
        Toast.makeText(this, "Select an app control with the arrows first.", Toast.LENGTH_SHORT).show();
    }

    private Rect bounds(AccessibilityNodeInfo node) {
        Rect rect = new Rect();
        node.getBoundsInScreen(rect);
        return rect;
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
