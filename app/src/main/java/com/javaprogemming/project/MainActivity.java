package com.javaprogemming.project;


import android.Manifest;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.Handler;
import android.view.View;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import android.util.Log;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import java.util.ArrayList;
import java.util.Arrays;

import Bluetooth.Bluetooth;
import UWB.UwbRangingHelper;
import UWB.UwbRangingCallback;

public class MainActivity extends AppCompatActivity implements Bluetooth.UwbParametersListener, UwbRangingCallback {

    private static final String TAG = "UWB_BT_App_Java";

    private Bluetooth bcl;
    private UwbRangingHelper uwbRangingHelper;

    private Button scanButton;
    private ListView deviceListView;
    private TextView rangingResultTextView;
    private TextView localAddressTextView;

    private ArrayAdapter<String> bleDeviceAdapter;


    // 권한 요청 런처
    private final ActivityResultLauncher<String[]> requestPermissionLauncher =
            registerForActivityResult(new ActivityResultContracts.RequestMultiplePermissions(), permissions -> {
                boolean allGranted = true;
                for (Boolean granted : permissions.values()) {
                    allGranted &= granted;
                }
                if (allGranted) {
                    onPermissionsGranted();
                } else {
                    Toast.makeText(this, "모든 권한이 필요합니다.", Toast.LENGTH_SHORT).show();
                    finish();
                }
            });

    private RelativeLayout mainLayout;
    private View overlay;
    private View overlayIntro;
    private TextView followText;
    private TextView loadingText;
    private View redDot;
    private LinearLayout bottomPanel;
    private View loadingRing;

    private Handler handler = new Handler();
    private List<View> smallDots = new ArrayList<View>();

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



    private void checkPermissions() {
        // API 31+ 권한 목록
        String[] PERMISSIONS = {
                Manifest.permission.BLUETOOTH_SCAN,
                Manifest.permission.BLUETOOTH_ADVERTISE,
                Manifest.permission.BLUETOOTH_CONNECT,
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION,
                Manifest.permission.UWB_RANGING
        };

        boolean allGranted = true;
        for (String permission : PERMISSIONS) {
            if (ContextCompat.checkSelfPermission(this, permission) != PackageManager.PERMISSION_GRANTED) {
                allGranted = false;
                break;
            }
        }

        if (!allGranted) {
            requestPermissionLauncher.launch(PERMISSIONS);
        } else {
            onPermissionsGranted();
        }
    }
    private void onPermissionsGranted() {
        Log.d(TAG, "All permissions granted.");
        if (!bcl.checkBluetoothSupoort()) {
            Toast.makeText(this, "Bluetooth가 지원되지 않습니다.", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }
        bcl.setUwbParametersListener(this);

        // GATT 서버 시작
        bcl.startGattServer();
        // 광고 시작
        bcl.startAdvertise();
        // 로컬 UWB 주소 미리 가져오기
        uwbRangingHelper.prepareLocalAddress(false); // We can be a controlee
    }

    @Override
    public void onUwbParametersReceived(byte[] params) {
        Log.d(TAG, "UWB Parameters received: " + Bluetooth.bytesToHex(params));
        // Assuming 8-byte address and 4-byte session ID
        if (params.length >= 8) { // Assuming at least 8-byte address
            byte[] remoteAddress = Arrays.copyOfRange(params, 0, 8);
            int sessionId = 0; // Default session id
            if (params.length >= 12) { // if session id is provided
                 sessionId = java.nio.ByteBuffer.wrap(params, 8, 4).getInt();
            }
            // We are the controller because we initiated the connection
            uwbRangingHelper.startRanging(remoteAddress, sessionId, true);
        } else {
            Log.e(TAG, "Invalid UWB parameters received.");
            onRangingError("Invalid UWB parameters");
        }
    }

    @Override
    public void onLocalAddressReceived(byte[] address) {
        // GATT 서버에 로컬 UWB 주소 설정
        bcl.setLocalUwbAddress(address);

        runOnUiThread(() -> {
            localAddressTextView.setText("Local UWB Address: " + Bluetooth.bytesToHex(address));
        });
        Log.d(TAG, "Local UWB address: " + Bluetooth.bytesToHex(address));
    }

    @Override
    public void onRangingResult(float distance) {
        runOnUiThread(() -> {
            rangingResultTextView.setText("Ranging Result: " + distance + "m");
        });
        Log.d(TAG, "Ranging result: " + distance + "m");
    }

    @Override
    public void onRangingError(String error) {
        runOnUiThread(() -> {
            rangingResultTextView.setText("Ranging Error: " + error);
        });
        Log.e(TAG, "Ranging error: " + error);
    }

    @Override
    public void onRangingComplete() {
        runOnUiThread(() -> {
            rangingResultTextView.setText("Ranging Complete");
        });
        Log.d(TAG, "Ranging complete");
    }
}

