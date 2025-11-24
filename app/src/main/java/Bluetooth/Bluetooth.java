package Bluetooth;




import android.Manifest;
import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattServer;
import android.bluetooth.BluetoothGattServerCallback;
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

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

public class Bluetooth {
    private static final String TAG = "BT";

    public interface UwbParametersListener {
        void onUwbParametersReceived(byte[] params);
        void onControllerAddressReceived(byte[] address);
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
    private BluetoothGattServer bluetoothGattServer;


    private byte[] localUwbAddress;
    private int sessionId;

    private Context currentContext;

    public Bluetooth(Context context) {
        this.currentContext = context;
        bluetoothManager = (BluetoothManager) currentContext.getSystemService(Context.BLUETOOTH_SERVICE);
        if (bluetoothManager != null) {
            bluetoothAdapter = bluetoothManager.getAdapter();
            if (bluetoothAdapter != null) {
                bluetoothLeScanner = bluetoothAdapter.getBluetoothLeScanner();
                bluetoothLeAdvertiser = bluetoothAdapter.getBluetoothLeAdvertiser();
            }
        }
        // Generate a random session ID for this device instance
        this.sessionId = new Random().nextInt();
    }

    private ArrayAdapter<String> bleDeviceAdapter;

    private boolean isScanning = false;
    private boolean isAdvertising = false;

    public void setBleDeviceAdapter(ArrayAdapter<String> adapter, ArrayList<String> deviceList) {
        this.bleDeviceAdapter = adapter;
        Data.bleDeviceList.clear();
        Data.bleDeviceList.addAll(deviceList);
    }

    public void setLocalUwbAddress(byte[] address) {
        this.localUwbAddress = address;
        Log.d(TAG, "Local UWB address set in Bluetooth class: " + bytesToHex(address));
    }

    public int getSessionId() {
        return sessionId;
    }


    // 기기가 Bluetooth를 지원하는지 확인하는 메소드
    public boolean checkBluetoothSupoort(){
        return bluetoothAdapter != null;
    }

    public void startGattServer() {
        if (ActivityCompat.checkSelfPermission(currentContext, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
            Log.e(TAG, "Missing BLUETOOTH_CONNECT permission for GATT server");
            return;
        }

        if (bluetoothGattServer != null) {
            Log.w(TAG, "GATT Server already open. Skipping start.");
            return;
        }

        bluetoothGattServer = bluetoothManager.openGattServer(currentContext, gattServerCallback);
        if (bluetoothGattServer == null) {
            Log.e(TAG, "Failed to open GATT server.");
            return;
        }

        BluetoothGattService service = new BluetoothGattService(Data.UWB_SERVICE_UUID.getUuid(), BluetoothGattService.SERVICE_TYPE_PRIMARY);
        BluetoothGattCharacteristic characteristic = new BluetoothGattCharacteristic(
                Data.UWB_PARAMS_CHARACTERISTIC_UUID,
                BluetoothGattCharacteristic.PROPERTY_READ | BluetoothGattCharacteristic.PROPERTY_WRITE,
                BluetoothGattCharacteristic.PERMISSION_READ | BluetoothGattCharacteristic.PERMISSION_WRITE
        );
        service.addCharacteristic(characteristic);
        bluetoothGattServer.addService(service);
        Log.d(TAG, "GATT Server started and service added.");
    }

    public void stopGattServer() {
        if (bluetoothGattServer == null) return;
        if (ActivityCompat.checkSelfPermission(currentContext, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
            Log.e(TAG, "Missing BLUETOOTH_CONNECT permission for stopping GATT server");
            return;
        }
        bluetoothGattServer.close();
        bluetoothGattServer = null;
        Log.d(TAG, "GATT Server stopped.");
    }


    public void startAdvertise(){
        if(isAdvertising) return;
        if (ActivityCompat.checkSelfPermission(currentContext, Manifest.permission.BLUETOOTH_ADVERTISE) != PackageManager.PERMISSION_GRANTED) {
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
        if (ActivityCompat.checkSelfPermission(currentContext, Manifest.permission.BLUETOOTH_ADVERTISE) != PackageManager.PERMISSION_GRANTED) {
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
        if (ActivityCompat.checkSelfPermission(currentContext, Manifest.permission.BLUETOOTH_SCAN) != PackageManager.PERMISSION_GRANTED) {
            Log.e(TAG, "BLUETOOTH_SCAN permission not granted");
            return;
        }
        Data.bleDevicesMap.clear();
        Data.bleDeviceList.clear();
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
        if (ActivityCompat.checkSelfPermission(currentContext, Manifest.permission.BLUETOOTH_SCAN) != PackageManager.PERMISSION_GRANTED) {
            Log.e(TAG, "BLUETOOTH_SCAN permission not granted");
            return;
        }
        isScanning = false;
        bluetoothLeScanner.stopScan(leScanCallback);
        Log.d(TAG, "BLE Scan stopped.");
    }

    public void connectToDevice(String address) {
        if (ActivityCompat.checkSelfPermission(currentContext, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
            Log.e(TAG, "BLUETOOTH_CONNECT permission not granted");
            return;
        }

        // Prevent multiple GATT connections
        if (bluetoothGatt != null) {
            Log.d(TAG, "Closing existing GATT connection before new connection.");
            bluetoothGatt.close();
            bluetoothGatt = null;
        }

        ScanResult scanResult = Data.bleDevicesMap.get(address);
        if (scanResult != null) {
            BluetoothDevice device = scanResult.getDevice();
            bluetoothGatt = device.connectGatt(currentContext, false, gattCallback);
            Log.d(TAG, "Connecting to " + address);
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
                    if (!Data.classicDevicesMap.containsKey(device.getAddress())) {
                        Log.d(TAG, "Classic Device Found: " + device.getName() + " - " + device.getAddress());
                        Data.classicDevicesMap.put(device.getAddress(), device);
                        Data.classicDeviceList.add(device.getName() + "\n" + device.getAddress());
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

            if (ActivityCompat.checkSelfPermission(currentContext, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
                Log.w(TAG, "BLUETOOTH_CONNECT permission not granted, cannot get device name.");
                return;
            }

            if (device.getName() != null && device.getAddress() != null) {
                if (!Data.bleDevicesMap.containsKey(device.getAddress())) {
                    Log.d(TAG, "BLE Device Found: " + device.getName() + " - " + device.getAddress());
                    Data.bleDevicesMap.put(device.getAddress(), result);
                    Data.bleDeviceList.add(device.getName() + "\n" + device.getAddress());
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
                if (ActivityCompat.checkSelfPermission(currentContext, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
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
                if (ActivityCompat.checkSelfPermission(currentContext, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {

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
                    // if (ActivityCompat.checkSelfPermission(currentContext, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
                    //    return;
                    // }
                    // gatt.disconnect();
                }
            } else {
                Log.e(TAG, "Characteristic Read Failed: " + status);
            }
        }
    };

    public void writeUwbAddress(byte[] address) {
        if (bluetoothGatt == null) {
            Log.e(TAG, "GATT is not connected.");
            return;
        }
        BluetoothGattService service = bluetoothGatt.getService(Data.UWB_SERVICE_UUID.getUuid());
        if (service == null) {
            Log.e(TAG, "UWB Service not found.");
            return;
        }
        BluetoothGattCharacteristic characteristic = service.getCharacteristic(Data.UWB_PARAMS_CHARACTERISTIC_UUID);
        if (characteristic == null) {
            Log.e(TAG, "UWB Characteristic not found.");
            return;
        }

        if (ActivityCompat.checkSelfPermission(currentContext, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
            Log.e(TAG, "Missing BLUETOOTH_CONNECT permission for writing characteristic");
            return;
        }

        characteristic.setValue(address);
        characteristic.setWriteType(BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT);
        boolean success = bluetoothGatt.writeCharacteristic(characteristic);
        Log.d(TAG, "Write UWB Address initiated: " + success);
    }

    private final BluetoothGattServerCallback gattServerCallback = new BluetoothGattServerCallback() {
        @Override
        public void onConnectionStateChange(BluetoothDevice device, int status, int newState) {
            super.onConnectionStateChange(device, status, newState);
            if (newState == BluetoothProfile.STATE_CONNECTED) {
                Log.i(TAG, "GATT Server: Device Connected - " + device.getAddress());
            } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                Log.i(TAG, "GATT Server: Device Disconnected - " + device.getAddress());
            }
        }

        @Override
        public void onServiceAdded(int status, BluetoothGattService service) {
            super.onServiceAdded(status, service);
            if (status == BluetoothGatt.GATT_SUCCESS) {
                Log.d(TAG, "GATT Server: Service Added - " + service.getUuid());
            } else {
                Log.e(TAG, "GATT Server: Service Add Failed - " + status);
            }
        }

        @Override
        public void onCharacteristicReadRequest(BluetoothDevice device, int requestId, int offset,
                                                BluetoothGattCharacteristic characteristic) {
            super.onCharacteristicReadRequest(device, requestId, offset, characteristic);
            if (Data.UWB_PARAMS_CHARACTERISTIC_UUID.equals(characteristic.getUuid())) {
                if (ActivityCompat.checkSelfPermission(currentContext, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
                    Log.e(TAG, "GATT Server: Missing BLUETOOTH_CONNECT permission for read request");
                    bluetoothGattServer.sendResponse(device, requestId, BluetoothGatt.GATT_FAILURE, 0, null);
                    return;
                }

                if (localUwbAddress == null) {
                    Log.e(TAG, "GATT Server: Local UWB address is not set yet.");
                    bluetoothGattServer.sendResponse(device, requestId, BluetoothGatt.GATT_FAILURE, 0, null);
                    return;
                }

                // UWB Address (8 bytes) + Session ID (4 bytes) = 12 bytes
                byte[] value = new byte[12];
                ByteBuffer buffer = ByteBuffer.wrap(value);
                buffer.put(localUwbAddress);
                buffer.putInt(sessionId);

                Log.d(TAG, "GATT Server: Responding to read request with UWB params: " + bytesToHex(value));

                bluetoothGattServer.sendResponse(device, requestId, BluetoothGatt.GATT_SUCCESS, 0, value);
            } else {
                Log.w(TAG, "GATT Server: Read request for unknown characteristic: " + characteristic.getUuid());
                bluetoothGattServer.sendResponse(device, requestId, BluetoothGatt.GATT_FAILURE, 0, null);
            }
        }

        @Override
        public void onCharacteristicWriteRequest(BluetoothDevice device, int requestId,
                                                 BluetoothGattCharacteristic characteristic,
                                                 boolean preparedWrite, boolean responseNeeded,
                                                 int offset, byte[] value) {
            super.onCharacteristicWriteRequest(device, requestId, characteristic, preparedWrite, responseNeeded, offset, value);
            
            if (Data.UWB_PARAMS_CHARACTERISTIC_UUID.equals(characteristic.getUuid())) {
                if (ActivityCompat.checkSelfPermission(currentContext, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
                     Log.e(TAG, "GATT Server: Missing BLUETOOTH_CONNECT permission for write request");
                     if (responseNeeded) {
                         bluetoothGattServer.sendResponse(device, requestId, BluetoothGatt.GATT_FAILURE, 0, null);
                     }
                     return;
                }

                Log.i(TAG, "GATT Server: Write request received: " + bytesToHex(value));
                
                if (responseNeeded) {
                    bluetoothGattServer.sendResponse(device, requestId, BluetoothGatt.GATT_SUCCESS, 0, null);
                }

                if (value != null && value.length >= 8) {
                     if (uwbParametersListener != null) {
                         uwbParametersListener.onControllerAddressReceived(value);
                     }
                }
            } else {
                 if (responseNeeded) {
                     bluetoothGattServer.sendResponse(device, requestId, BluetoothGatt.GATT_FAILURE, 0, null);
                 }
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
        if (bytes == null) return "null";
        char[] hexChars = new char[bytes.length * 2];
        for (int j = 0; j < bytes.length; j++) {
            int v = bytes[j] & 0xFF;
            hexChars[j * 2] = HEX_ARRAY[v >>> 4];
            hexChars[j * 2 + 1] = HEX_ARRAY[v & 0x0F];
        }
        return new String(hexChars);
    }

}