package com.nudroidlabs.tvemulator;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.provider.Settings;
import android.widget.Button;
import android.view.Gravity;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.text.Collator;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class MainActivity extends Activity {
    private static final int BACKGROUND = 0xff101624;
    private static final int SURFACE = 0xff1a253a;
    private static final int MUTED = 0xffa9b7ce;
    private Button remoteSetup;

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(18), dp(14), dp(18), dp(14));
        root.setBackgroundColor(BACKGROUND);
        setContentView(root);
        showLibrary(root);
    }

    @Override protected void onResume() {
        super.onResume();
        TvRemoteService.arm(null);
        if (remoteSetup != null) {
            remoteSetup.setVisibility(TvRemoteService.isReady() ? android.view.View.GONE : android.view.View.VISIBLE);
        }
    }

    private void showLibrary(LinearLayout root) {
        root.addView(text("TV Emulator", 24, Color.WHITE, true));
        Button enable = new Button(this);
        enable.setAllCaps(false);
        enable.setText("Enable floating remote");
        enable.setOnClickListener(view ->
                startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)));
        root.addView(enable, new LinearLayout.LayoutParams(-2, -2));
        remoteSetup = enable;
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
            row.setOnClickListener(view -> launchApp(activity.packageName, label));
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

    private void launchApp(String packageName, CharSequence label) {
        PackageManager packages = getPackageManager();
        Intent launch = packages.getLaunchIntentForPackage(packageName);
        if (launch == null) {
            launch = packages.getLeanbackLaunchIntentForPackage(packageName);
        }
        if (launch != null) {
            try {
                TvRemoteService.arm(packageName);
                startActivity(launch);
                return;
            } catch (ActivityNotFoundException | SecurityException ignored) {
                TvRemoteService.arm(null);
                // The entry may have been removed or restricted since the list was loaded.
            }
        }
        Toast.makeText(this, "Couldn’t open " + label + ".", Toast.LENGTH_SHORT).show();
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
