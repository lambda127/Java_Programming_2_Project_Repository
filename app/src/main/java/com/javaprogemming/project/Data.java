package com.javaprogemming.project;

import android.bluetooth.BluetoothDevice;
import android.bluetooth.le.ScanResult;
import android.os.Build;
import android.os.ParcelUuid;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class Data {

    //테스트 UI 사용 여부
    public static final boolean isTesting = true;

    //서비스 UUID 포함 여부
    public static final boolean isIncludingUuid = true;

    //Bluetooth Ranging (BR) 활성화 여부
    public static final boolean isActivatedBR = false;

    // Android 16(API 36) 이상 기기는 Controller, 그보다 낮으면 Controlee로 고정한다.
    private static final int CONTROLLER_MIN_API_LEVEL = 36;

    public static boolean isControllerDevice() {
        return Build.VERSION.SDK_INT >= CONTROLLER_MIN_API_LEVEL;
    }

    public static final String UWB_SERVICE_UUID_STRING = "0000FFF0-0000-1000-8000-00805F9B34FB";
    public static final ParcelUuid UWB_SERVICE_UUID = ParcelUuid.fromString(UWB_SERVICE_UUID_STRING);
    public static final UUID UWB_PARAMS_CHARACTERISTIC_UUID = UUID.fromString("0000FFF1-0000-1000-8000-00805F9B34FB");


    public static final Map<String, ScanResult> bleDevicesMap = new HashMap<>();
    public static final Map<String, BluetoothDevice> classicDevicesMap = new HashMap<>();
    public static final ArrayList<String> bleDeviceList = new ArrayList<>();
    public static final ArrayList<String> classicDeviceList = new ArrayList<>();
}
