package com.example.javaproject;

import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.animation.ValueAnimator;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public class MainActivity extends AppCompatActivity {

    private RelativeLayout mainLayout;
    private View redDot;
    private View overlayIntro;
    private LinearLayout bottomPanel;
    private View overlay;
    private List<View> extraDots = new ArrayList<>();
    private List<View> loadingDots = new ArrayList<>();
    private Handler handler = new Handler();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        mainLayout = new RelativeLayout(this);
        mainLayout.setBackgroundColor(Color.parseColor("#BDBDBD"));
        mainLayout.setLayoutParams(new RelativeLayout.LayoutParams(
                RelativeLayout.LayoutParams.MATCH_PARENT,
                RelativeLayout.LayoutParams.MATCH_PARENT));

        redDot = new View(this);
        RelativeLayout.LayoutParams dotParams = new RelativeLayout.LayoutParams(40, 40);
        dotParams.addRule(RelativeLayout.CENTER_HORIZONTAL);
        dotParams.topMargin = 1000;
        redDot.setLayoutParams(dotParams);
        redDot.setBackgroundColor(Color.RED);
        redDot.setElevation(10);
        redDot.setVisibility(View.INVISIBLE);
        mainLayout.addView(redDot);

        overlayIntro = createIntroOverlay();
        mainLayout.addView(overlayIntro);

        bottomPanel = new LinearLayout(this);
        bottomPanel.setOrientation(LinearLayout.HORIZONTAL);
        bottomPanel.setGravity(Gravity.CENTER);
        bottomPanel.setBackgroundColor(Color.WHITE);

        RelativeLayout.LayoutParams bottomParams = new RelativeLayout.LayoutParams(
                RelativeLayout.LayoutParams.MATCH_PARENT,
                getResources().getDisplayMetrics().heightPixels / 5
        );
        bottomParams.addRule(RelativeLayout.ALIGN_PARENT_BOTTOM);
        bottomPanel.setLayoutParams(bottomParams);

        TextView startText = new TextView(this);
        startText.setText("시작");
        startText.setTextColor(Color.WHITE);
        startText.setTextSize(20);
        startText.setPadding(80, 30, 80, 30);
        startText.setBackground(createRoundedButton(Color.parseColor("#00C853")));
        startText.setGravity(Gravity.CENTER);

        TextView endText = new TextView(this);
        endText.setText("종료");
        endText.setTextColor(Color.WHITE);
        endText.setTextSize(20);
        endText.setPadding(80, 30, 80, 30);
        endText.setBackground(createRoundedButton(Color.RED));
        endText.setGravity(Gravity.CENTER);

        LinearLayout.LayoutParams startParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        );
        startParams.setMargins(50, 0, 50, 0);
        startText.setLayoutParams(startParams);

        LinearLayout.LayoutParams endParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        );
        endParams.setMargins(50, 0, 50, 0);
        endText.setLayoutParams(endParams);

        bottomPanel.addView(startText);
        bottomPanel.addView(endText);
        bottomPanel.setVisibility(View.INVISIBLE);

        overlay = new View(this);
        overlay.setBackgroundColor(Color.parseColor("#88000000"));
        overlay.setLayoutParams(new RelativeLayout.LayoutParams(
                RelativeLayout.LayoutParams.MATCH_PARENT,
                RelativeLayout.LayoutParams.MATCH_PARENT
        ));
        overlay.setVisibility(View.GONE);
        mainLayout.addView(overlay);

        mainLayout.addView(bottomPanel);
        setContentView(mainLayout);

        startText.setOnClickListener(v -> startAction());
        endText.setOnClickListener(v -> resetAction());
    }

    private View createIntroOverlay() {

        GradientDrawable gradient = new GradientDrawable(
                GradientDrawable.Orientation.TOP_BOTTOM,
                new int[]{Color.parseColor("#006B3C"), Color.parseColor("#00C853"), Color.parseColor("#B9F6CA")}
        );

        RelativeLayout overlay = new RelativeLayout(this);
        overlay.setLayoutParams(new RelativeLayout.LayoutParams(
                RelativeLayout.LayoutParams.MATCH_PARENT,
                RelativeLayout.LayoutParams.MATCH_PARENT
        ));
        overlay.setBackground(gradient);

        TextView followText = new TextView(this);
        followText.setText("FOLLOW");
        followText.setTextColor(Color.BLACK);
        followText.setTextSize(45);
        followText.setGravity(Gravity.CENTER);
        followText.setTypeface(null, Typeface.BOLD);

        RelativeLayout.LayoutParams followParams = new RelativeLayout.LayoutParams(
                RelativeLayout.LayoutParams.WRAP_CONTENT,
                RelativeLayout.LayoutParams.WRAP_CONTENT);
        followParams.addRule(RelativeLayout.CENTER_HORIZONTAL);
        followParams.topMargin = getResources().getDisplayMetrics().heightPixels / 2 - 150;
        overlay.addView(followText, followParams);

        TextView loadingText = new TextView(this);
        loadingText.setText("LOADING...");
        loadingText.setTextColor(Color.DKGRAY);
        loadingText.setTextSize(18);
        loadingText.setTypeface(null, Typeface.BOLD);
        loadingText.setAlpha(0f);

        RelativeLayout.LayoutParams loadingParams = new RelativeLayout.LayoutParams(
                RelativeLayout.LayoutParams.WRAP_CONTENT,
                RelativeLayout.LayoutParams.WRAP_CONTENT);
        loadingParams.addRule(RelativeLayout.CENTER_HORIZONTAL);
        loadingParams.topMargin = getResources().getDisplayMetrics().heightPixels / 2 + 20;
        overlay.addView(loadingText, loadingParams);

        followText.setScaleX(1f);
        followText.setScaleY(1f);

        ObjectAnimator scaleX = ObjectAnimator.ofFloat(followText, "scaleX", 1f, 2f);
        ObjectAnimator scaleY = ObjectAnimator.ofFloat(followText, "scaleY", 1f, 2f);
        ObjectAnimator fadeOut = ObjectAnimator.ofFloat(followText, "alpha", 1f, 0f);
        scaleX.setDuration(4000);
        scaleY.setDuration(4000);
        fadeOut.setDuration(4000);
        scaleX.start();
        scaleY.start();
        fadeOut.start();

        handler.postDelayed(() -> {
            loadingText.setAlpha(1f);
            ObjectAnimator blink = ObjectAnimator.ofFloat(loadingText, "alpha", 1f, 0.3f, 1f);
            blink.setDuration(1500);
            blink.setRepeatCount(ValueAnimator.INFINITE);
            blink.start();
        }, 500);

        new Handler().postDelayed(() -> {
            overlay.setVisibility(View.GONE);
            redDot.setVisibility(View.VISIBLE);
            bottomPanel.setVisibility(View.VISIBLE);
        }, 4000);

        return overlay;
    }

    private void startAction() {
        overlay.setVisibility(View.VISIBLE);
        createRotatingLoadingDots();
        handler.postDelayed(() -> {
            removeLoadingAnimation();
            overlay.setVisibility(View.GONE);
            createExtraDots(3);
        }, 2000);
    }

    private void resetAction() {
        for (View dot : extraDots) {
            mainLayout.removeView(dot);
        }
        extraDots.clear();
        removeLoadingAnimation();
        overlay.setVisibility(View.GONE);
    }

    private void createExtraDots(int count) {
        Random rand = new Random();
        int baseX = redDot.getLeft() + redDot.getWidth() / 2;
        int baseY = redDot.getTop() + redDot.getHeight() / 2;

        for (int i = 0; i < count; i++) {
            View dot = new View(this);
            RelativeLayout.LayoutParams params = new RelativeLayout.LayoutParams(30, 30);
            params.leftMargin = baseX + rand.nextInt(700) - 350;
            params.topMargin = baseY + rand.nextInt(700) - 350;
            dot.setLayoutParams(params);
            dot.setBackgroundColor(Color.RED);
            dot.setElevation(8);
            mainLayout.addView(dot);
            extraDots.add(dot);
        }
    }

    private void createRotatingLoadingDots() {
        int centerX = mainLayout.getWidth() / 2;
        int centerY = redDot.getTop() + redDot.getHeight() / 2;
        int radius = 100;
        int dotSize = 20;
        int dotCount = 8;

        for (int i = 0; i < dotCount; i++) {
            View dot = new View(this);
            RelativeLayout.LayoutParams params = new RelativeLayout.LayoutParams(dotSize, dotSize);
            dot.setLayoutParams(params);
            dot.setBackgroundColor(Color.BLUE);
            dot.setElevation(12);
            mainLayout.addView(dot);
            loadingDots.add(dot);

            final int index = i;
            ValueAnimator animator = ValueAnimator.ofFloat(0, 360);
            animator.setDuration(1000);
            animator.setRepeatCount(ValueAnimator.INFINITE);
            animator.addUpdateListener(animation -> {
                float angle = (float) animation.getAnimatedValue() + 360f / dotCount * index;
                int x = centerX + (int)(radius * Math.cos(Math.toRadians(angle))) - dotSize / 2;
                int y = centerY + (int)(radius * Math.sin(Math.toRadians(angle))) - dotSize / 2;
                dot.setX(x);
                dot.setY(y);
            });
            animator.start();
        }
    }

    private void removeLoadingAnimation() {
        for (View dot : loadingDots) {
            mainLayout.removeView(dot);
        }
        loadingDots.clear();
    }

    private GradientDrawable createRoundedButton(int color) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(color);
        drawable.setCornerRadius(200);
        return drawable;
    }
}
