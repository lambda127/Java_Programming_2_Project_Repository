package com.javaprogemming.project;


import android.Manifest;
import android.content.pm.PackageManager;
import android.os.Bundle;
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

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_main);
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        bcl = new Bluetooth(this);

        scanButton = findViewById(R.id.scanButton);
        deviceListView = findViewById(R.id.deviceListView);
        rangingResultTextView = findViewById(R.id.rangingResultTextView);
        localAddressTextView = findViewById(R.id.localAddressTextView);

        bleDeviceAdapter = new ArrayAdapter<>(this, android.R.layout.simple_list_item_1, Data.bleDeviceList);
        deviceListView.setAdapter(bleDeviceAdapter);

        bcl.setBleDeviceAdapter(bleDeviceAdapter, Data.bleDeviceList);


        scanButton.setOnClickListener(v -> {
            bcl.startScan();
        });

        deviceListView.setOnItemClickListener((parent, view, position, id) -> {
            String deviceInfo = (String) parent.getItemAtPosition(position);
            String address = deviceInfo.split("\n")[1];
            bcl.connectToDevice(address);
        });

        uwbRangingHelper = new UwbRangingHelper(this, this);
        checkPermissions();
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

    @Override
    protected void onDestroy() {
        super.onDestroy();
        bcl.stopAdvertise();
        bcl.stopGattServer();
    }
}

