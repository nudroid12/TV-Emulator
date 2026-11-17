package com.nudroidlabs.tvemulator;

import android.app.Activity;
import android.content.ComponentName;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.text.Collator;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class MainActivity extends Activity {
    private static final int BACKGROUND = 0xff101624;
    private static final int SURFACE = 0xff1a253a;
    private static final int MUTED = 0xffa9b7ce;
    private ComponentName selection;

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
        selection = null;
        render();
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

        FrameLayout preview = new FrameLayout(this) {
            @Override protected void onMeasure(int widthSpec, int heightSpec) {
                int availableWidth = MeasureSpec.getSize(widthSpec);
                int availableHeight = MeasureSpec.getSize(heightSpec);
                int width = Math.min(availableWidth, availableHeight * 16 / 9);
                int height = width * 9 / 16;
                super.onMeasure(MeasureSpec.makeMeasureSpec(width, MeasureSpec.EXACTLY),
                        MeasureSpec.makeMeasureSpec(height, MeasureSpec.EXACTLY));
            }
        };
        preview.setBackground(background(0xff090d15, 12));
        TextView status = text("Android TV runtime is not available in this build.", 15, MUTED, false);
        status.setGravity(Gravity.CENTER);
        status.setPadding(dp(12), dp(4), dp(12), dp(4));
        preview.addView(status, new FrameLayout.LayoutParams(-1, -1));
        FrameLayout viewport = new FrameLayout(this);
        viewport.addView(preview, new FrameLayout.LayoutParams(-1, -1, Gravity.CENTER));
        LinearLayout.LayoutParams previewParams = new LinearLayout.LayoutParams(-1, 0, 1f);
        previewParams.topMargin = dp(10);
        root.addView(viewport, previewParams);

        LinearLayout remote = new LinearLayout(this);
        remote.setGravity(Gravity.CENTER);
        remote.setPadding(0, dp(8), 0, 0);
        for (String label : new String[] {"←", "↑", "↓", "→", "OK", "Back"}) {
            Button control = new Button(this);
            control.setText(label);
            control.setAllCaps(false);
            control.setMinWidth(0);
            control.setMinimumWidth(0);
            control.setPadding(0, 0, 0, 0);
            control.setEnabled(false);
            remote.addView(control, new LinearLayout.LayoutParams(0, dp(44), 1f));
        }
        root.addView(remote, new LinearLayout.LayoutParams(-1, ViewGroup.LayoutParams.WRAP_CONTENT));
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
