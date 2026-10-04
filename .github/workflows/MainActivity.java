package com.android.settings.overlay;

import android.animation.ObjectAnimator;
import android.animation.PropertyValuesHolder;
import android.app.Activity;
import android.content.Intent;
import android.graphics.drawable.GradientDrawable;
import android.media.projection.MediaProjectionManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.AccelerateDecelerateInterpolator;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

public class MainActivity extends Activity {
    private static final int REQ_OVERLAY = 100;
    private static final int REQ_CAPTURE = 101;
    private MediaProjectionManager mpm;
    private ObjectAnimator glowAnim;

    private BubbleShapeView preview;
    private final TextView[] shapeBtns = new TextView[4];
    private final View[] swatches = new View[BubblePrefs.COLORS.length];
    private TextView alphaVal, sizeVal;

    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        setContentView(R.layout.activity_main);

        // STEALTH: exclude from recent apps immediately
        if (Build.VERSION.SDK_INT >= 21) {
            finishAndRemoveTask();
            // Restart without the recents flag
            return;
        }

        mpm = (MediaProjectionManager) getSystemService(MEDIA_PROJECTION_SERVICE);

        View glow = findViewById(R.id.glow);
        if (glow != null) {
            glowAnim = ObjectAnimator.ofPropertyValuesHolder(glow,
                    PropertyValuesHolder.ofFloat("scaleX", 0.85f, 1.12f),
                    PropertyValuesHolder.ofFloat("scaleY", 0.85f, 1.12f),
                    PropertyValuesHolder.ofFloat("alpha", 1f, 0.35f));
            glowAnim.setDuration(1800);
            glowAnim.setRepeatCount(ObjectAnimator.INFINITE);
            glowAnim.setRepeatMode(ObjectAnimator.REVERSE);
            glowAnim.setInterpolator(new AccelerateDecelerateInterpolator());
            glowAnim.start();
        }

        View start = findViewById(R.id.btnStart);
        if (start != null) {
            start.setOnClickListener(v -> {
                if (Build.VERSION.SDK_INT >= 23 && !Settings.canDrawOverlays(this)) {
                    Intent i = new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                            Uri.parse("package:" + getPackageName()));
                    startActivityForResult(i, REQ_OVERLAY);
                } else requestCapture();
            });
        }

        View stop = findViewById(R.id.btnStop);
        if (stop != null) {
            stop.setOnClickListener(v -> {
                stopService(new Intent(this, FloatingService.class));
                Toast.makeText(this, "Service stopped", Toast.LENGTH_SHORT).show();
            });
        }

        setupSettings();
    }

    private int dp(int v) { return Math.round(v * getResources().getDisplayMetrics().density); }

    private void setupSettings() {
        preview = findViewById(R.id.bubblePreview);
        if (preview == null) return;

        int[] ids = {R.id.shape0, R.id.shape1, R.id.shape2, R.id.shape3};
        for (int i = 0; i < 4; i++) {
            final int idx = i;
            shapeBtns[i] = findViewById(ids[i]);
            if (shapeBtns[i] != null)
                shapeBtns[i].setOnClickListener(v -> {
                    BubblePrefs.sp(this).edit().putInt("shape", idx).apply();
                    refreshUi();
                });
        }

        LinearLayout row = findViewById(R.id.colorRow);
        if (row != null) {
            for (int i = 0; i < swatches.length; i++) {
                final int c = BubblePrefs.COLORS[i];
                View sw = new View(this);
                LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, dp(30), 1f);
                lp.setMargins(dp(3), 0, dp(3), 0);
                sw.setLayoutParams(lp);
                sw.setOnClickListener(x -> {
                    BubblePrefs.sp(this).edit().putInt("color", c).apply();
                    refreshUi();
                });
                row.addView(sw);
                swatches[i] = sw;
            }
        }

        View colorReset = findViewById(R.id.colorReset);
        if (colorReset != null) colorReset.setOnClickListener(v -> {
            BubblePrefs.sp(this).edit().putInt("color", BubblePrefs.DEFAULT_COLOR).apply();
            refreshUi();
        });

        alphaVal = findViewById(R.id.alphaVal);
        sizeVal = findViewById(R.id.sizeVal);
        SeekBar seekAlpha = findViewById(R.id.seekAlpha);
        SeekBar seekSize = findViewById(R.id.seekSize);
        if (seekAlpha != null) {
            seekAlpha.setMax(80);
            seekAlpha.setProgress(BubblePrefs.alpha(this) - 20);
            seekAlpha.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
                @Override public void onProgressChanged(SeekBar s, int p, boolean fromUser) {
                    if (fromUser) { BubblePrefs.sp(MainActivity.this).edit().putInt("alpha", p + 20).apply(); refreshUi(); }
                }
                @Override public void onStartTrackingTouch(SeekBar s) {}
                @Override public void onStopTrackingTouch(SeekBar s) {}
            });
        }
        if (seekSize != null) {
            seekSize.setMax(40);
            seekSize.setProgress(BubblePrefs.size(this) - 32);
            seekSize.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
                @Override public void onProgressChanged(SeekBar s, int p, boolean fromUser) {
                    if (fromUser) { BubblePrefs.sp(MainActivity.this).edit().putInt("size", p + 32).apply(); refreshUi(); }
                }
                @Override public void onStartTrackingTouch(SeekBar s) {}
                @Override public void onStopTrackingTouch(SeekBar s) {}
            });
        }
        refreshUi();
    }

    private void refreshUi() {
        int shape = BubblePrefs.shape(this);
        int color = BubblePrefs.color(this);
        int alpha = BubblePrefs.alpha(this);
        int size = BubblePrefs.size(this);
        for (int i = 0; i < shapeBtns.length; i++) {
            if (shapeBtns[i] == null) continue;
            boolean sel = i == shape;
            shapeBtns[i].setBackgroundResource(sel ? R.drawable.bg_chip_sel : R.drawable.bg_chip);
            shapeBtns[i].setTextColor(sel ? 0xFF00E5FF : 0xFFFFFFFF);
        }
        for (int i = 0; i < swatches.length; i++) {
            if (swatches[i] == null) continue;
            boolean sel = BubblePrefs.COLORS[i] == color;
            GradientDrawable g = new GradientDrawable();
            g.setColor(BubblePrefs.COLORS[i]);
            g.setCornerRadius(dp(8));
            g.setStroke(dp(sel ? 3 : 1), sel ? 0xFFFFFFFF : 0x55FFFFFF);
            swatches[i].setBackground(g);
        }
        if (preview != null) {
            preview.setStyle(shape, color);
            preview.setAlpha(alpha / 100f);
            ViewGroup.LayoutParams lp = preview.getLayoutParams();
            lp.width = dp(size);
            lp.height = dp(size);
            preview.setLayoutParams(lp);
        }
        if (alphaVal != null) alphaVal.setText(alpha + "%");
        if (sizeVal != null) sizeVal.setText(size + " dp");
    }

    private void requestCapture() { startActivityForResult(mpm.createScreenCaptureIntent(), REQ_CAPTURE); }

    @Override protected void onActivityResult(int req, int res, Intent data) {
        super.onActivityResult(req, res, data);
        if (req == REQ_OVERLAY && Settings.canDrawOverlays(this)) requestCapture();
        else if (req == REQ_CAPTURE && res == RESULT_OK && data != null) {
            Intent svc = new Intent(this, FloatingService.class);
            svc.putExtra("code", res);
            svc.putExtra("data", data);
            if (Build.VERSION.SDK_INT >= 26) startForegroundService(svc); else startService(svc);
            finish();
        }
    }

    @Override protected void onDestroy() {
        if (glowAnim != null) glowAnim.cancel();
        super.onDestroy();
    }
}
