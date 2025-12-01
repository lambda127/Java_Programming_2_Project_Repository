package com.javaprogemming.project;

import android.bluetooth.BluetoothDevice;
import android.bluetooth.le.ScanResult;
import android.os.ParcelUuid;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class Data {

    public static final String UWB_SERVICE_UUID_STRING = "0000FFF0-0000-1000-8000-00805F9B34FB";
    public static final ParcelUuid UWB_SERVICE_UUID = ParcelUuid.fromString(UWB_SERVICE_UUID_STRING);
    public static final UUID UWB_PARAMS_CHARACTERISTIC_UUID = UUID.fromString("0000FFF1-0000-1000-8000-00805F9B34FB");


    public static final Map<String, ScanResult> bleDevicesMap = new HashMap<>();
    public static final Map<String, BluetoothDevice> classicDevicesMap = new HashMap<>();
    public static final ArrayList<String> bleDeviceList = new ArrayList<>();
    public static final ArrayList<String> classicDeviceList = new ArrayList<>();
}
