package com.javaprogemming.project;


import android.Manifest;
import android.content.pm.PackageManager;
import android.os.Bundle;

import android.os.Handler;
import android.view.View;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.widget.LinearLayout;
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

import androidx.core.uwb.UwbComplexChannel;

public class MainActivity extends AppCompatActivity implements Bluetooth.UwbParametersListener, UwbRangingCallback, Bluetooth.BluetoothRangingListener, Bluetooth.DeviceFoundListener {
    private static final String TAG = "UWB_BT_App_Java";


    private Bluetooth bcl;
    private UwbRangingHelper uwbRangingHelper;
    private byte[] localUwbAddress;

    private Button scanButton;
    private Button startAutoRangingButton;
    private Button stopRangingButton;
    private ListView deviceListView;
    private TextView rangingResultTextView;
    private TextView localAddressTextView;
    private TextView statusTextView;
    private TextView sessionInfoTextView;

    private ArrayAdapter<String> bleDeviceAdapter;
    private boolean isAutoRangingActive = false;
    private boolean isRunningAsController = false;

    ///
    private View overlay;
    private View overlayIntro;
    private TextView followText;
    private TextView loadingText;
    private View redDot;
    private LinearLayout bottomPanel;
    private View loadingDotsContainer;

    private Handler handler = new Handler();



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



    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(Data.isTesting ? R.layout.activity_test : R.layout.activity_main);
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });


        /// Ranging을 위한 기본 세팅

        bcl = new Bluetooth(this);
        bcl.setControllerMode(Data.isControllerDevice());
        bcl.setConnectionStateListener(new Bluetooth.ConnectionStateListener() {
            @Override
            public void onBleConnected() {
                updateStatus(Data.isControllerDevice() ? "BLE 연결 완료. UWB 준비 중..." : "컨트롤러 연결됨. 명령 대기...");
            }

            @Override
            public void onBleDisconnected() {
                updateStatus("BLE 연결 끊김. 세션 재준비 중...");
                uwbRangingHelper.stopRanging();
            }
        });

        bleDeviceAdapter = new ArrayAdapter<>(this, android.R.layout.simple_list_item_1, Data.bleDeviceList);

        bcl.setBleDeviceAdapter(bleDeviceAdapter, Data.bleDeviceList);

        uwbRangingHelper = new UwbRangingHelper(this, this);
        checkPermissions();


        /// Test UI
        if(Data.isTesting) testUiSetting();
        else uiSetting();
    }

    private void testUiSetting(){
        startAutoRangingButton = findViewById(R.id.startAutoRangingButton);
        stopRangingButton = findViewById(R.id.stopRangingButton);
        statusTextView = findViewById(R.id.statusTextView);
        rangingResultTextView = findViewById(R.id.rangingResultTextView);
        localAddressTextView = findViewById(R.id.localAddressTextView);
        sessionInfoTextView = findViewById(R.id.sessionInfoTextView);

        startAutoRangingButton.setOnClickListener(v -> {
            startAutoRanging();
        });

        stopRangingButton.setOnClickListener(v -> {
            stopAutoRanging();
        });
    }

    private void uiSetting(){
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
        bcl.setBluetoothRangingListener(this);
        bcl.setDeviceFoundListener(this);

        if (Data.isTesting) {
            // 테스트 모드: 버튼을 누를 때까지 대기
            updateStatus("준비 완료. 시작 버튼을 누르세요.");
        } else {
            // 자동 모드: 역할에 따라 즉시 준비
            if (!Data.isControllerDevice()) {
                bcl.startGattServer();
                bcl.startAdvertise();
            }
            runOnUiThread(() -> {
                if (sessionInfoTextView != null) {
                    sessionInfoTextView.setText("--");
                }
            });
        }
    }

    // 상태 업데이트
    private void updateStatus(String status) {
        runOnUiThread(() -> {
            if (statusTextView != null) {
                statusTextView.setText(status);
            }
        });
        Log.d(TAG, "Status: " + status);
    }

    // 자동 Ranging 시작 및 스캔 로직
    private void startAutoRanging() {
        if (isAutoRangingActive) {
            updateStatus("이미 Ranging이 진행 중입니다.");
            return;
        }

        isAutoRangingActive = true;
        Log.d(TAG, "Starting auto ranging with Dynamic Role Negotiation");
        
        // Start BOTH Advertising and Scanning
        updateStatus("탐색 중... (ID: " + bcl.getLocalDeviceId() + ")");
        
        // 1. Start Advertising (to be found by others)
        bcl.startGattServer();
        bcl.startAdvertise();
        
        // 2. Start Scanning (to find others)
        bcl.startScan();
        
        // Prepare local address (just in case we become Controlee, or for Controller to send)
        // Note: Role is not decided yet.
    }

    // 자동 Ranging 중지 로직
    private void stopAutoRanging() {
        updateStatus("Ranging 중지 중...");
        Log.d(TAG, "Stopping auto ranging");

        // 스캔 중지 (필요 시)
        bcl.stopScan();
        // Ranging 및 광고 정리
        uwbRangingHelper.stopRanging();
        bcl.stopAdvertise();
        bcl.stopGattServer();

        isAutoRangingActive = false;
        updateStatus("대기 중");

        // UI 초기화
        if (rangingResultTextView != null) {
            runOnUiThread(() -> rangingResultTextView.setText("--"));
        }
        if (sessionInfoTextView != null) {
            runOnUiThread(() -> sessionInfoTextView.setText("--"));
        }
    }

    @Override
    public void onUwbParametersReceived(byte[] params) {
        Log.d(TAG, "UWB Parameters received: " + Bluetooth.bytesToHex(params));
        
        if (params == null || params.length < 2) {
             Log.e(TAG, "Invalid UWB parameters received: too short.");
             onRangingError("Invalid UWB parameters");
             return;
        }

        try {
            java.nio.ByteBuffer buffer = java.nio.ByteBuffer.wrap(params);
            int addressLength = buffer.get(); // Read length prefix
            
            if (params.length >= 1 + addressLength + 4) {
                byte[] remoteAddress = new byte[addressLength];
                buffer.get(remoteAddress);
                int sessionId = buffer.getInt();
                
                // Check if we are already ranging (e.g. as Controlee)
                if (uwbRangingHelper.isRanging()) {
                    Log.w(TAG, "Already ranging. Ignoring UWB parameters to avoid role conflict.");
                    return;
                }

                // We are the Controller (Scanner/GATT Client)
                // We should use OUR Session ID to ensure consistency.
                // The Controlee might have sent a session ID, but we override it with ours
                // because we will send OUR Session ID to the Controlee in the write request.
                int localSessionId = bcl.getSessionId();
                Log.d(TAG, "Controller using local Session ID: " + localSessionId + " (Remote sent: " + sessionId + ")");

                isRunningAsController = true;
                uwbRangingHelper.startRanging(remoteAddress, localSessionId, true, null);
            } else {
                onRangingError("Parsing error");
            }
        } catch (Exception e) {
            Log.e(TAG, "Error parsing UWB parameters", e);
            onRangingError("Parsing exception: " + e.getMessage());
        }
    }

    @Override
    public void onControllerAddressReceived(byte[] address, int channel, int preambleIndex, int sessionId) {
        Log.d(TAG, "Controller address received: " + Bluetooth.bytesToHex(address) + ", Ch: " + channel + ", Preamble: " + preambleIndex + ", SessionId: " + sessionId);
        
        if (uwbRangingHelper.isRanging()) {
            if (!isRunningAsController) {
                Log.w(TAG, "Already ranging as Controlee. Ignoring new controller address.");
                return;
            }

            // We are currently ranging as Controller.
            // Check for tie-breaker: Yield to higher address.
            if (localUwbAddress == null) return;

            boolean remoteIsHigher = false;
            int len = Math.min(localUwbAddress.length, address.length);
            for (int i = 0; i < len; i++) {
                int localByte = localUwbAddress[i] & 0xFF;
                int remoteByte = address[i] & 0xFF;
                if (remoteByte > localByte) {
                    remoteIsHigher = true;
                    break;
                } else if (remoteByte < localByte) {
                    remoteIsHigher = false;
                    break;
                }
            }

            if (remoteIsHigher) {
                Log.i(TAG, "Race condition: Remote address is higher. Yielding and becoming Controlee.");
                uwbRangingHelper.stopRanging();
                // Proceed to start as Controlee below
            } else {
                Log.w(TAG, "Race condition: Local address is higher. Ignoring remote controller request.");
                return;
            }
        }

        // We are the controlee (Advertiser)
        // Use the session ID received from the Controller
        
        UwbComplexChannel complexChannel = new UwbComplexChannel(channel, preambleIndex);
        isRunningAsController = false;
        uwbRangingHelper.startRanging(address, sessionId, false, complexChannel);
    }

    @Override
    public void onLocalAddressReceived(byte[] address) {
        bcl.setLocalUwbAddress(address);
        this.localUwbAddress = address;

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

    @Override
    public void onRangingStarted(boolean isController, UwbComplexChannel complexChannel) {
        Log.d(TAG, "onRangingStarted: isController=" + isController + ", channel=" + complexChannel);
        if (isController) {
            // Write our local address AND the assigned complex channel AND session ID to the remote device
            if (localUwbAddress != null && complexChannel != null) {
                int sessionId = bcl.getSessionId(); // Controller uses its own session ID
                bcl.writeUwbAddress(localUwbAddress, complexChannel.getChannel(), complexChannel.getPreambleIndex(), sessionId);
            } else {
                Log.e(TAG, "Cannot write local address/channel because it is null");
            }
        }
    }

    @Override
    public void onBluetoothRssiResult(String deviceName, int rssi, double distance) {
        runOnUiThread(() -> {
            String currentText = rangingResultTextView.getText().toString();
            // Avoid overwriting UWB result if possible, or just append
            // Simple approach: Show both
            // If text contains "UWB", keep it.
            String uwbText = "";
            if (currentText.contains("UWB:")) {
                 uwbText = currentText.substring(currentText.indexOf("UWB:"));
            } else if (currentText.contains("m")) {
                 // Assume existing text is UWB if it has "m" and not "BT"
                 if (!currentText.contains("BT:")) {
                     uwbText = "UWB: " + currentText;
                 }
            }
            
            String btText = String.format("BT(%s): %.2fm (RSSI: %d)", deviceName, distance, rssi);
            rangingResultTextView.setText(btText + "\n" + uwbText);
        });
        Log.d(TAG, "Bluetooth RSSI: " + rssi + ", Distance: " + distance + ", Device: " + deviceName);
    }

    @Override
    public void onDeviceFound(String address, long remoteDeviceId) {
        long localDeviceId = bcl.getLocalDeviceId();
        Log.d(TAG, "Device Found: " + address + ", Remote ID: " + remoteDeviceId + ", Local ID: " + localDeviceId);
        
        if (localDeviceId > remoteDeviceId) {
            // I am the Controller
            Log.i(TAG, "Role Negotiation: I am CONTROLLER (My ID " + localDeviceId + " > " + remoteDeviceId + ")");
            updateStatus("역할 결정: Controller (연결 시도 중...)");
            
            // Stop Advertising (Controller doesn't need to advertise for connection)
            bcl.stopAdvertise();
            // Stop Scanning (Found a target)
            bcl.stopScan();
            
            bcl.setControllerMode(true);
            isRunningAsController = true;
            
            // Connect to the found device
            bcl.connectToDevice(address);
            
            // Prepare UWB as Controller
            uwbRangingHelper.prepareLocalAddress(true);
            
        } else if (localDeviceId < remoteDeviceId) {
            // I am the Controlee
            Log.i(TAG, "Role Negotiation: I am CONTROLEE (My ID " + localDeviceId + " < " + remoteDeviceId + ")");
            updateStatus("역할 결정: Controlee (연결 대기 중...)");
            
            // Stop Scanning (I will be connected to)
            bcl.stopScan();
            // Continue Advertising (so Controller can connect)
            
            bcl.setControllerMode(false);
            isRunningAsController = false;
            
            // Prepare UWB as Controlee
            uwbRangingHelper.prepareLocalAddress(false);
            
        } else {
            // IDs are equal (Very rare)
            Log.w(TAG, "Role Negotiation: IDs are equal! Ignoring.");
        }
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

        bcl.stopAdvertise();
        bcl.stopGattServer();
    }
}
