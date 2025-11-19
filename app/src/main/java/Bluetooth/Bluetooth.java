package Bluetooth;




import android.Manifest;
import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothManager;
import android.bluetooth.BluetoothProfile;
import android.bluetooth.le.AdvertiseCallback;
import android.bluetooth.le.AdvertiseData;
import android.bluetooth.le.AdvertiseSettings;
import android.bluetooth.le.BluetoothLeAdvertiser;
import android.bluetooth.le.BluetoothLeScanner;
import android.bluetooth.le.ScanCallback;
import android.bluetooth.le.ScanFilter;
import android.bluetooth.le.ScanResult;
import android.bluetooth.le.ScanSettings;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.util.Log;
import android.widget.ArrayAdapter;

import androidx.core.app.ActivityCompat;

import com.javaprogemming.project.Data;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class Bluetooth {
    private static final String TAG = "BT";

    public interface UwbParametersListener {
        void onUwbParametersReceived(byte[] params);
    }

    private UwbParametersListener uwbParametersListener;

    public void setUwbParametersListener(UwbParametersListener listener) {
        this.uwbParametersListener = listener;
    }


    private BluetoothManager bluetoothManager;
    private BluetoothAdapter bluetoothAdapter;
    private BluetoothLeScanner bluetoothLeScanner;

    private BluetoothLeAdvertiser bluetoothLeAdvertiser;
    private BluetoothGatt bluetoothGatt;

    private Context applicationContext; // Add this field

    public Bluetooth(Context context) {
        this.applicationContext = context.getApplicationContext();
        bluetoothManager = (BluetoothManager) applicationContext.getSystemService(Context.BLUETOOTH_SERVICE);
        if (bluetoothManager != null) {
            bluetoothAdapter = bluetoothManager.getAdapter();
            if (bluetoothAdapter != null) {
                bluetoothLeScanner = bluetoothAdapter.getBluetoothLeScanner();
                bluetoothLeAdvertiser = bluetoothAdapter.getBluetoothLeAdvertiser();
            }
        }
    }

    private final Map<String, ScanResult> bleDevicesMap = new HashMap<>();
    private final Map<String, BluetoothDevice> classicDevicesMap = new HashMap<>();
    private ArrayAdapter<String> bleDeviceAdapter;
    private final ArrayList<String> bleDeviceList = new ArrayList<>();
    private final ArrayList<String> classicDeviceList = new ArrayList<>();

    private boolean isScanning = false;
    private boolean isAdvertising = false;

    public void setBleDeviceAdapter(ArrayAdapter<String> adapter, ArrayList<String> deviceList) {
        this.bleDeviceAdapter = adapter;
        bleDeviceList.clear();
        bleDeviceList.addAll(deviceList);
    }

    // 기기가 Bluetooth를 지원하는지 확인하는 메소드
    public boolean checkBluetoothSupoort(){
        return bluetoothAdapter != null;
    }

    public void startAdvertise(){
        if(isAdvertising) return;
        if (ActivityCompat.checkSelfPermission(applicationContext, Manifest.permission.BLUETOOTH_ADVERTISE) != PackageManager.PERMISSION_GRANTED) {
            Log.e(TAG, "BLUETOOTH_ADVERTISE permission not granted");
            return;
        }

        AdvertiseSettings settings = new AdvertiseSettings.Builder()
                .setAdvertiseMode(AdvertiseSettings.ADVERTISE_MODE_LOW_LATENCY)
                .setTxPowerLevel(AdvertiseSettings.ADVERTISE_TX_POWER_HIGH)
                .setConnectable(true) // GATT 연결을 위해 true로 설정
                .build();

        AdvertiseData data = new AdvertiseData.Builder()
                .setIncludeDeviceName(true) // 기기 이름 포함 (패킷 공간 부족 시 false로 변경)
                .addServiceUuid(Data.UWB_SERVICE_UUID)
                .build();

        isAdvertising = true;
        try {
            bluetoothLeAdvertiser.startAdvertising(settings, data, advertiseCallback);
            Log.d("BLE", "Advertising 시작됨: " + Data.UWB_SERVICE_UUID.getUuid().toString());
        }
        catch (SecurityException e) {
            Log.e("BLE", "권한 부족: " + e.getMessage());
        }
    }

    public void stopAdvertise(){
        if (!isAdvertising || bluetoothLeAdvertiser == null) return;
        if (ActivityCompat.checkSelfPermission(applicationContext, Manifest.permission.BLUETOOTH_ADVERTISE) != PackageManager.PERMISSION_GRANTED) {
            Log.e(TAG, "BLUETOOTH_SCAN permission not granted");
            return;
        }
        isAdvertising = false;
        bluetoothLeAdvertiser.stopAdvertising(advertiseCallback);
        Log.d(TAG, "BLE Advertise stopped.");
    }

    private final AdvertiseCallback advertiseCallback = new AdvertiseCallback() {
        @Override
        public void onStartSuccess(AdvertiseSettings settingsInEffect) {
            super.onStartSuccess(settingsInEffect);
            Log.d("BLE", "Advertising 성공!");
        }

        @Override
        public void onStartFailure(int errorCode) {
            super.onStartFailure(errorCode);
            Log.e("BLE", "Advertising 실패 에러코드: " + errorCode);
        }
    };

    public void startScan() {
        if (isScanning) return;
        if (ActivityCompat.checkSelfPermission(applicationContext, Manifest.permission.BLUETOOTH_SCAN) != PackageManager.PERMISSION_GRANTED) {
            Log.e(TAG, "BLUETOOTH_SCAN permission not granted");
            return;
        }
        bleDevicesMap.clear();
        bleDeviceList.clear();
        if(bleDeviceAdapter != null) {
            bleDeviceAdapter.clear();
            bleDeviceAdapter.notifyDataSetChanged();
        }

        List<ScanFilter> filters = new ArrayList<>();
        ScanFilter filter = new ScanFilter.Builder()
                .setServiceUuid(Data.UWB_SERVICE_UUID)
                .build();
        filters.add(filter);

        ScanSettings settings = new ScanSettings.Builder()
                .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
                .build();

        isScanning = true;
        bluetoothLeScanner.startScan(filters, settings, leScanCallback);
        Log.d(TAG, "BLE Scan started.");

        // Stop scan after 10 seconds
        new android.os.Handler().postDelayed(this::stopScan, 10000);
    }

    public void stopScan() {
        if (!isScanning || bluetoothLeScanner == null) return;
        if (ActivityCompat.checkSelfPermission(applicationContext, Manifest.permission.BLUETOOTH_SCAN) != PackageManager.PERMISSION_GRANTED) {
            Log.e(TAG, "BLUETOOTH_SCAN permission not granted");
            return;
        }
        isScanning = false;
        bluetoothLeScanner.stopScan(leScanCallback);
        Log.d(TAG, "BLE Scan stopped.");
    }

    public void connectToDevice(String address) {
        if (ActivityCompat.checkSelfPermission(applicationContext, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
            Log.e(TAG, "BLUETOOTH_CONNECT permission not granted");
            return;
        }
        ScanResult scanResult = bleDevicesMap.get(address);
        if (scanResult != null) {
            BluetoothDevice device = scanResult.getDevice();
            bluetoothGatt = device.connectGatt(applicationContext, false, gattCallback);
        }
    }


    private final BroadcastReceiver classicScanReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (BluetoothDevice.ACTION_FOUND.equals(action)) {
                BluetoothDevice device = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
                if (ActivityCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
                    return;
                }
                if (device != null && device.getName() != null && device.getAddress() != null) {
                    if (!classicDevicesMap.containsKey(device.getAddress())) {
                        Log.d(TAG, "Classic Device Found: " + device.getName() + " - " + device.getAddress());
                        classicDevicesMap.put(device.getAddress(), device);
                        classicDeviceList.add(device.getName() + "\n" + device.getAddress());
                        // classicDeviceAdapter.notifyDataSetChanged();
                    }
                }
            } else if (BluetoothAdapter.ACTION_DISCOVERY_FINISHED.equals(action)) {
                Log.d(TAG, "Classic Discovery Finished");
                setScanning(false);
            }
        }
    };

    private final ScanCallback leScanCallback = new ScanCallback() {
        @Override
        public void onScanResult(int callbackType, ScanResult result) {
            super.onScanResult(callbackType, result);
            BluetoothDevice device = result.getDevice();

            if (ActivityCompat.checkSelfPermission(applicationContext, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
                Log.w(TAG, "BLUETOOTH_CONNECT permission not granted, cannot get device name.");
                return;
            }

            if (device.getName() != null && device.getAddress() != null) {
                if (!bleDevicesMap.containsKey(device.getAddress())) {
                    Log.d(TAG, "BLE Device Found: " + device.getName() + " - " + device.getAddress());
                    bleDevicesMap.put(device.getAddress(), result);
                    bleDeviceList.add(device.getName() + "\n" + device.getAddress());
                    if(bleDeviceAdapter != null) bleDeviceAdapter.notifyDataSetChanged();
                }
            }
        }

        @Override
        public void onScanFailed(int errorCode) {
            Log.e(TAG, "BLE Scan Failed: " + errorCode);
            setScanning(false);
        }
    };


    private final BluetoothGattCallback gattCallback = new BluetoothGattCallback() {
        @Override
        public void onConnectionStateChange(BluetoothGatt gatt, int status, int newState) {
            if (newState == BluetoothProfile.STATE_CONNECTED) {
                Log.i(TAG, "GATT Connected. Discovering services...");
                bluetoothGatt = gatt;
                if (ActivityCompat.checkSelfPermission(applicationContext, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
                    return;
                }
                gatt.discoverServices();
            } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                Log.i(TAG, "GATT Disconnected.");
                if (bluetoothGatt != null) {
                    bluetoothGatt.close();
                    bluetoothGatt = null;
                }
            }
        }

        @Override
        public void onServicesDiscovered(BluetoothGatt gatt, int status) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                Log.i(TAG, "Services Discovered.");
                BluetoothGattService service = gatt.getService(Data.UWB_SERVICE_UUID.getUuid());
                if (service == null) {
                    Log.e(TAG, "UWB.UWB Service not found.");
                    return;
                }
                BluetoothGattCharacteristic characteristic =
                        service.getCharacteristic(Data.UWB_PARAMS_CHARACTERISTIC_UUID);
                if (characteristic == null) {
                    Log.e(TAG, "UWB.UWB Characteristic not found.");
                    return;
                }
                if (ActivityCompat.checkSelfPermission(applicationContext, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {

                    return;
                }
                gatt.readCharacteristic(characteristic);
            } else {
                Log.w(TAG, "onServicesDiscovered received: " + status);
            }
        }

        @Override
        public void onCharacteristicRead(BluetoothGatt gatt,
                                         BluetoothGattCharacteristic characteristic,
                                         byte[] value,
                                         int status) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                if (Data.UWB_PARAMS_CHARACTERISTIC_UUID.equals(characteristic.getUuid())) {
                    Log.i(TAG, "UWB.UWB Params Read: " + bytesToHex(value));
                    if (uwbParametersListener != null) {
                        uwbParametersListener.onUwbParametersReceived(value);
                    }
                    // 파라미터 획득 후 GATT 연결 해제
                    if (ActivityCompat.checkSelfPermission(applicationContext, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
                        return;
                    }
                    gatt.disconnect();
                }
            } else {
                Log.e(TAG, "Characteristic Read Failed: " + status);
            }
        }
    };




    public boolean getScanning(){
        return isScanning;
    }

    private void setScanning(boolean scanning) {
        isScanning = scanning;
    }


    private static final char[] HEX_ARRAY = "0123456789ABCDEF".toCharArray();
    public static String bytesToHex(byte[] bytes) {
        char[] hexChars = new char[bytes.length * 2];
        for (int j = 0; j < bytes.length; j++) {
            int v = bytes[j] & 0xFF;
            hexChars[j * 2] = HEX_ARRAY[v >>> 4];
            hexChars[j * 2 + 1] = HEX_ARRAY[v & 0x0F];
        }
        return new String(hexChars);
    }

}