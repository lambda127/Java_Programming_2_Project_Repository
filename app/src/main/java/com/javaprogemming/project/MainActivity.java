package com.example.test;

import android.os.Bundle;
import android.os.Handler;
import android.view.View;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;
import java.util.ArrayList;
import java.util.List;

public class MainActivity extends AppCompatActivity {

    private RelativeLayout mainLayout;
    private View overlay;
    private View overlayIntro;
    private TextView followText;
    private TextView loadingText;
    private View redDot;
    private LinearLayout bottomPanel;
    private View loadingRing;

    private Handler handler = new Handler();
    private List<View> smallDots = new ArrayList<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        mainLayout = findViewById(R.id.mainLayout);
        overlay = findViewById(R.id.overlay);
        overlayIntro = findViewById(R.id.overlayIntro);
        followText = findViewById(R.id.followText);
        loadingText = findViewById(R.id.loadingText);
        redDot = findViewById(R.id.redDot);
        bottomPanel = findViewById(R.id.bottomPanel);
        loadingRing = findViewById(R.id.loadingRing);

        TextView startText = findViewById(R.id.startText);
        TextView endText = findViewById(R.id.endText);

        overlay.setVisibility(View.GONE);
        overlayIntro.setVisibility(View.VISIBLE);
        redDot.setVisibility(View.INVISIBLE);
        bottomPanel.setVisibility(View.INVISIBLE);
        loadingRing.setVisibility(View.GONE);

        followText.setScaleX(1f);
        followText.setScaleY(1f);
        followText.setAlpha(1f);
        followText.animate().scaleX(2f).scaleY(2f).alpha(0f).setDuration(4000).start();

        handler.postDelayed(() -> {
            loadingText.setAlpha(1f);
            Animation a = AnimationUtils.loadAnimation(this, android.R.anim.fade_in);
            loadingText.startAnimation(a);
        }, 500);

        handler.postDelayed(() -> {
            overlayIntro.setVisibility(View.GONE);
            redDot.setVisibility(View.VISIBLE);
            bottomPanel.setVisibility(View.VISIBLE);
        }, 4000);

        startText.setOnClickListener(v -> startAction());
        endText.setOnClickListener(v -> resetAction());
    }

    private void startAction() {
        overlay.setVisibility(View.VISIBLE);
        loadingRing.setVisibility(View.VISIBLE);
        startRingAnimation();

        handler.postDelayed(() -> {
            createRandomSmallDots();
            stopRingAnimation();
            loadingRing.setVisibility(View.GONE);
            overlay.setVisibility(View.GONE);
        }, 2000);
    }

    private void resetAction() {
        for (View dot : smallDots) mainLayout.removeView(dot);
        smallDots.clear();
        overlay.setVisibility(View.GONE);
        stopRingAnimation();
        loadingRing.setVisibility(View.GONE);
    }

    private void createRandomSmallDots() {
        int count = 3;
        int size = 60;
        int width = mainLayout.getWidth();
        int height = bottomPanel.getTop() - size;

        for (int i = 0; i < count; i++) {
            View dot = new View(this);
            RelativeLayout.LayoutParams params = new RelativeLayout.LayoutParams(size, size);
            dot.setLayoutParams(params);
            dot.setBackgroundResource(R.drawable.red_circle);
            float x = (float)(Math.random() * (width - size));
            float y = (float)(Math.random() * height);
            dot.setX(x);
            dot.setY(y);
            mainLayout.addView(dot);
            smallDots.add(dot);
        }
    }

    private void startRingAnimation() {
        if (loadingRing == null) return;
        Animation a = AnimationUtils.loadAnimation(this, R.anim.rotate_ring);
        loadingRing.startAnimation(a);
    }

    private void stopRingAnimation() {
        if (loadingRing != null) loadingRing.clearAnimation();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        handler.removeCallbacksAndMessages(null);
    }
}
