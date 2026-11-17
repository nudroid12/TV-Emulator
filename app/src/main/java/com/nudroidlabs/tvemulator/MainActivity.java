package com.nudroidlabs.tvemulator;

import android.app.Activity;
import android.app.ActivityManager;
import android.app.ActivityOptions;
import android.content.ComponentName;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.Color;
import android.graphics.SurfaceTexture;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.hardware.display.DisplayManager;
import android.hardware.display.VirtualDisplay;
import android.os.Build;
import android.os.Bundle;
import android.view.KeyEvent;
import android.view.Surface;
import android.view.TextureView;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import com.nudroidlabs.tvadapter.TvAdapter;

import java.text.Collator;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class MainActivity extends Activity {
    private static final int BACKGROUND = 0xff101624;
    private static final int SURFACE = 0xff1a253a;
    private static final int MUTED = 0xffa9b7ce;
    private static final int TV_WIDTH = 960;
    private static final int TV_HEIGHT = 540;
    private static final int TV_DENSITY = 160;

    private ComponentName selection;
    private VirtualDisplay display;
    private Surface displaySurface;

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        if (state != null) {
            selection = ComponentName.unflattenFromString(state.getString("selection", ""));
        }
        render();
    }

    @Override protected void onSaveInstanceState(Bundle state) {
        super.onSaveInstanceState(state);
        if (selection != null) state.putString("selection", selection.flattenToString());
    }

    @Override public void onBackPressed() {
        if (selection == null) {
            super.onBackPressed();
            return;
        }
        closeDisplay();
        selection = null;
        render();
    }

    @Override protected void onDestroy() {
        closeDisplay();
        super.onDestroy();
    }

    private void closeDisplay() {
        if (display != null) {
            display.release();
            display = null;
        }
        if (displaySurface != null) {
            displaySurface.release();
            displaySurface = null;
        }
    }

    private void render() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(18), dp(14), dp(18), dp(14));
        root.setBackgroundColor(BACKGROUND);
        setContentView(root);
        if (selection != null) {
            showSession(root);
        } else {
            showLibrary(root);
        }
    }

    private void showLibrary(LinearLayout root) {
        root.addView(text("TV Emulator", 24, Color.WHITE, true));
        TextView title = text("Installed apps", 15, MUTED, true);
        title.setPadding(0, dp(20), 0, dp(8));
        root.addView(title);

        ScrollView scroll = new ScrollView(this);
        root.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1f));
        LinearLayout list = new LinearLayout(this);
        list.setOrientation(LinearLayout.VERTICAL);
        scroll.addView(list);

        List<ResolveInfo> apps = installedApps();
        PackageManager packages = getPackageManager();
        if (apps.isEmpty()) {
            list.addView(text("No launchable apps found.", 15, MUTED, false));
        }
        for (ResolveInfo app : apps) {
            ActivityInfo activity = app.activityInfo;
            CharSequence label = app.loadLabel(packages);
            LinearLayout row = new LinearLayout(this);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setPadding(dp(12), dp(9), dp(12), dp(9));
            row.setBackground(background(SURFACE, 12));
            row.setContentDescription(label + ", " + activity.packageName);
            row.setFocusable(true);
            ImageView icon = new ImageView(this);
            icon.setImageDrawable(app.loadIcon(packages));
            row.addView(icon, new LinearLayout.LayoutParams(dp(38), dp(38)));
            LinearLayout labels = new LinearLayout(this);
            labels.setOrientation(LinearLayout.VERTICAL);
            labels.setPadding(dp(12), 0, 0, 0);
            labels.addView(text(label, 16, Color.WHITE, true));
            labels.addView(text(activity.packageName, 12, MUTED, false));
            row.addView(labels, new LinearLayout.LayoutParams(0, -2, 1f));
            row.setOnClickListener(view -> {
                selection = new ComponentName(activity.packageName, activity.name);
                render();
            });
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, -2);
            params.bottomMargin = dp(6);
            list.addView(row, params);
        }
    }

    private List<ResolveInfo> installedApps() {
        PackageManager packages = getPackageManager();
        Map<String, ResolveInfo> byPackage = new HashMap<>();
        for (String category : new String[] {
                Intent.CATEGORY_LAUNCHER, Intent.CATEGORY_LEANBACK_LAUNCHER }) {
            Intent launch = new Intent(Intent.ACTION_MAIN).addCategory(category);
            for (ResolveInfo info : packages.queryIntentActivities(launch, 0)) {
                ActivityInfo activity = info.activityInfo;
                if (activity != null && activity.exported
                        && !getPackageName().equals(activity.packageName)) {
                    // A TV entry point takes precedence when both launchers exist.
                    byPackage.put(activity.packageName, info);
                }
            }
        }
        List<ResolveInfo> apps = new ArrayList<>(byPackage.values());
        Collator collator = Collator.getInstance();
        apps.sort((left, right) -> {
            int label = collator.compare(left.loadLabel(packages).toString(),
                    right.loadLabel(packages).toString());
            return label != 0 ? label : left.activityInfo.packageName
                    .compareTo(right.activityInfo.packageName);
        });
        return apps;
    }

    private void showSession(LinearLayout root) {
        Button back = new Button(this);
        back.setText("Installed apps");
        back.setAllCaps(false);
        back.setOnClickListener(v -> onBackPressed());
        root.addView(back, new LinearLayout.LayoutParams(-2, -2));

        PackageManager packages = getPackageManager();
        try {
            ActivityInfo info = packages.getActivityInfo(selection, 0);
            root.addView(text(info.loadLabel(packages), 22, Color.WHITE, true));
        } catch (PackageManager.NameNotFoundException e) {
            selection = null;
            root.removeAllViews();
            showLibrary(root);
            return;
        }

        FrameLayout screen = new FrameLayout(this);
        screen.setBackgroundColor(0xff090d15);
        TextureView texture = new TextureView(this);
        screen.addView(texture, new FrameLayout.LayoutParams(-1, -1));
        TextView status = text("Opening app on the TV display…", 24, MUTED, false);
        status.setGravity(Gravity.CENTER);
        status.setPadding(24, 12, 24, 12);
        screen.addView(status, new FrameLayout.LayoutParams(-1, -1));

        ComponentName target = selection;
        texture.setSurfaceTextureListener(new TextureView.SurfaceTextureListener() {
            @Override public void onSurfaceTextureAvailable(SurfaceTexture surface, int width, int height) {
                if (target.equals(selection)) openDisplay(surface, target, status);
            }

            @Override public void onSurfaceTextureSizeChanged(SurfaceTexture surface, int width, int height) {}

            @Override public boolean onSurfaceTextureDestroyed(SurfaceTexture surface) {
                closeDisplay();
                return true;
            }

            @Override public void onSurfaceTextureUpdated(SurfaceTexture surface) {}
        });

        Runnable hideStatus = () -> status.setVisibility(View.GONE);
        TvAdapter adapter = new TvAdapter(this, screen, TV_WIDTH, TV_HEIGHT, keyCode -> {
            if (keyCode == KeyEvent.KEYCODE_BACK) {
                onBackPressed();
                return;
            }
            if (display != null) {
                status.setText("Remote input to another app is unavailable in this build.");
                status.setVisibility(View.VISIBLE);
                status.removeCallbacks(hideStatus);
                status.postDelayed(hideStatus, 2400);
            }
        });
        root.addView(adapter, new LinearLayout.LayoutParams(-1, 0, 1f));
    }

    private void openDisplay(SurfaceTexture texture, ComponentName target, TextView status) {
        if (!getPackageManager().hasSystemFeature(PackageManager.FEATURE_ACTIVITIES_ON_SECONDARY_DISPLAYS)) {
            status.setText("This phone does not support opening apps on a secondary display.");
            return;
        }
        closeDisplay();
        texture.setDefaultBufferSize(TV_WIDTH, TV_HEIGHT);
        displaySurface = new Surface(texture);
        DisplayManager displays = getSystemService(DisplayManager.class);
        Intent launch = new Intent(Intent.ACTION_MAIN)
                .setComponent(target)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_MULTIPLE_TASK);
        try {
            display = displays.createVirtualDisplay("TV Emulator", TV_WIDTH, TV_HEIGHT,
                    TV_DENSITY, displaySurface, DisplayManager.VIRTUAL_DISPLAY_FLAG_OWN_CONTENT_ONLY);
            if (display == null) {
                closeDisplay();
                status.setText("Android could not create the TV display.");
                return;
            }
            int displayId = display.getDisplay().getDisplayId();
            if (Build.VERSION.SDK_INT >= 29
                    && !getSystemService(ActivityManager.class)
                            .isActivityStartAllowedOnDisplay(this, displayId, launch)) {
                closeDisplay();
                status.setText("Android does not allow this app on the TV display.");
                return;
            }
            ActivityOptions options = ActivityOptions.makeBasic().setLaunchDisplayId(displayId);
            startActivity(launch, options.toBundle());
            status.setVisibility(View.GONE);
        } catch (SecurityException | IllegalArgumentException | android.content.ActivityNotFoundException error) {
            closeDisplay();
            status.setText("Android could not open this app on the TV display.");
        }
    }

    private TextView text(CharSequence value, int size, int color, boolean bold) {
        TextView view = new TextView(this);
        view.setText(value);
        view.setTextSize(size);
        view.setTextColor(color);
        if (bold) view.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        return view;
    }

    private GradientDrawable background(int color, int radius) {
        GradientDrawable shape = new GradientDrawable();
        shape.setColor(color);
        shape.setCornerRadius(dp(radius));
        return shape;
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
