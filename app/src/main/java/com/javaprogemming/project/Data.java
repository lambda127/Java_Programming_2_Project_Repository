package com.javaprogemming.project;

import android.os.ParcelUuid;

import java.util.UUID;

public class Data {

    public static final String UWB_SERVICE_UUID_STRING = "0000FFF0-0000-1000-8000-00805F9B34FB";
    public static final ParcelUuid UWB_SERVICE_UUID = ParcelUuid.fromString(UWB_SERVICE_UUID_STRING);
    public static final UUID UWB_PARAMS_CHARACTERISTIC_UUID = UUID.fromString("0000FFF1-0000-1000-8000-00805F9B34FB");

    // UWB.UWB 주소 예시 (실제로는 GATT로 받아와야 함)
    public static final byte[] PEER_UWB_ADDRESS_EXAMPLE = new byte[]{0x0A, 0x0B};
}
