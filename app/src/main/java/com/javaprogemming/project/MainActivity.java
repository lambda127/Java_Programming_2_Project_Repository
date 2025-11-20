package com.javaprogemming.project;

import android.os.Bundle;
import android.os.Handler;
import android.view.View;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

public class MainActivity extends AppCompatActivity {

    private View overlay;
    private View overlayIntro;
    private TextView followText;
    private TextView loadingText;
    private View redDot;
    private LinearLayout bottomPanel;
    private View loadingDotsContainer;

    private Handler handler = new Handler();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main); // activity_main.xml 사용

        overlay = findViewById(R.id.overlay);
        overlayIntro = findViewById(R.id.overlayIntro);
        followText = findViewById(R.id.followText);
        loadingText = findViewById(R.id.loadingText);
        redDot = findViewById(R.id.redDot);
        bottomPanel = findViewById(R.id.bottomPanel);
        loadingDotsContainer = findViewById(R.id.loadingDotsContainer); // 통합본에서 사용한 id

        TextView startText = findViewById(R.id.startText);
        TextView endText = findViewById(R.id.endText);

        if (overlay != null) overlay.setVisibility(View.GONE);
        if (overlayIntro != null) overlayIntro.setVisibility(View.VISIBLE);
        if (redDot != null) redDot.setVisibility(View.INVISIBLE);
        if (bottomPanel != null) bottomPanel.setVisibility(View.INVISIBLE);
        if (loadingDotsContainer != null) loadingDotsContainer.setVisibility(View.GONE);

        if (followText != null) {
            followText.setScaleX(1f);
            followText.setScaleY(1f);
            followText.setAlpha(1f);
            followText.animate().scaleX(2f).scaleY(2f).alpha(0f).setDuration(4000).start();
        }

        if (loadingText != null) {
            handler.postDelayed(() -> {
                loadingText.setAlpha(1f);
                Animation blink = AnimationUtils.loadAnimation(this, android.R.anim.fade_in);
                loadingText.startAnimation(blink);
            }, 500);
        }

        handler.postDelayed(() -> {
            if (overlayIntro != null) overlayIntro.setVisibility(View.GONE);
            if (redDot != null) redDot.setVisibility(View.VISIBLE);
            if (bottomPanel != null) bottomPanel.setVisibility(View.VISIBLE);
        }, 4000);

        startText.setOnClickListener(v -> startAction());
        endText.setOnClickListener(v -> resetAction());
    }

    private void startAction() {
        if (overlay != null) overlay.setVisibility(View.VISIBLE);
        if (loadingDotsContainer != null) {
            loadingDotsContainer.setVisibility(View.VISIBLE);
            startDotAnimations();
        }

        handler.postDelayed(() -> {
            if (loadingDotsContainer != null) {
                stopDotAnimations();
                loadingDotsContainer.setVisibility(View.GONE);
            }
            if (overlay != null) overlay.setVisibility(View.GONE);
        }, 2000);
    }

    private void resetAction() {
        if (overlay != null) overlay.setVisibility(View.GONE);
        if (loadingDotsContainer != null) {
            stopDotAnimations();
            loadingDotsContainer.setVisibility(View.GONE);
        }
    }

    private void startDotAnimations() {
        if (loadingDotsContainer == null) return;

        int[] dotIds = new int[] {
                R.id.dot1, R.id.dot2, R.id.dot3, R.id.dot4,
                R.id.dot5, R.id.dot6, R.id.dot7, R.id.dot8
        };

        int[] animRes = new int[] {
                R.anim.dot_rotate_0, R.anim.dot_rotate_45, R.anim.dot_rotate_90, R.anim.dot_rotate_135,
                R.anim.dot_rotate_180, R.anim.dot_rotate_225, R.anim.dot_rotate_270, R.anim.dot_rotate_315
        };

        for (int i = 0; i < dotIds.length; i++) {
            View dot = findViewById(dotIds[i]);
            if (dot != null) {
                Animation a = AnimationUtils.loadAnimation(this, animRes[i]);
                dot.startAnimation(a);
            }
        }
    }

    private void stopDotAnimations() {
        int[] dotIds = new int[] {
                R.id.dot1, R.id.dot2, R.id.dot3, R.id.dot4,
                R.id.dot5, R.id.dot6, R.id.dot7, R.id.dot8
        };
        for (int id : dotIds) {
            View dot = findViewById(id);
            if (dot != null) dot.clearAnimation();
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        handler.removeCallbacksAndMessages(null);
    }
}
