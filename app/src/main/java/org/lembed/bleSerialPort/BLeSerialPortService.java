// Copyright (c) 2016 Thomas

// Permission is hereby granted, free of charge, to any person obtaining a
// copy of this software and associated documentation files (the "Software"),
// to deal in the Software without restriction, including without limitation
// the rights to use, copy, modify, merge, publish, distribute, sublicense,
// and/or sell copies of the Software, and to permit persons to whom the
// Software is furnished to do so, subject to the following conditions:

// The above copyright notice and this permission notice shall be included in
// all copies or substantial portions of the Software.

// THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
// IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
// FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
// AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
// LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING
// FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER
// DEALINGS IN THE SOFTWARE.

package org.lembed.bleSerialPort;

import android.app.Service;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;
import android.bluetooth.BluetoothGattService;
import android.content.Context;
import android.content.Intent;
import android.os.Binder;
import android.os.IBinder;
import android.util.Log;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.List;
import java.util.Queue;
import java.util.UUID;
import java.util.WeakHashMap;
import java.lang.String;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.TimeUnit;

@SuppressWarnings("deprecation")
public class BLeSerialPortService extends Service implements BluetoothAdapter.LeScanCallback {

    public static final UUID SERIAL_SERVICE_UUID = UUID.fromString("0000fff0-0000-1000-8000-00805f9b34fb");
    public static final UUID TX_CHAR_UUID = UUID.fromString("0000fff2-0000-1000-8000-00805f9b34fb");
    public static final UUID RX_CHAR_UUID = UUID.fromString("0000fff1-0000-1000-8000-00805f9b34fb");

    // UUID for the ble serial port client characteristic which is necessary for notifications.
    public final static UUID CLIENT_UUID = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb");

    public static class CommunicationStatus {
        public static final long SEND_TIME_OUT_MILLIS = TimeUnit.SECONDS.toMillis(2);
        public static final int COMMUNICATION_SUCCESS = 0;
        public static final int COMMUNICATION_TIMEOUT = -1;
    }

    private Context context;
    private WeakHashMap<Callback, Object> callbacks;
    private BluetoothAdapter adapter;
    private BluetoothGatt gatt;
    private BluetoothGattCharacteristic tx;
    private BluetoothGattCharacteristic rx;
    private boolean connectFirst;
    private boolean writeInProgress; // Flag to indicate a write is currently in progress

    // Device Information state.
    private BluetoothGattCharacteristic disManuf;
    private BluetoothGattCharacteristic disModel;
    private BluetoothGattCharacteristic disSWRev;
    private boolean disAvailable;

    private Queue<BluetoothGattCharacteristic> readQueue;
    // binder for service
    private final IBinder mBinder = new LocalBinder();

    // Interface for handler the serial port activity
    public interface Callback {
        public void onConnected(Context context);
        public void onConnectFailed(Context context);
        public void onDisconnected(Context context);
        public void onReceive(Context context, BluetoothGattCharacteristic rx);
        public void onDeviceFound(BluetoothDevice device);
        public void onDeviceInfoAvailable();
        public void onCommunicationError(int status, String msg);
    }

    // Return instance of BluetoothGatt.
    public BluetoothGatt getGatt() {
        return gatt;
    }

    public BLeSerialPortService() {
        super();
        this.callbacks = new WeakHashMap<Callback, Object>();
        this.adapter = BluetoothAdapter.getDefaultAdapter();
        this.gatt = null;
        this.tx = null;
        this.rx = null;
        this.disManuf = null;
        this.disModel = null;
        this.disSWRev = null;
        this.disAvailable = false;
        this.connectFirst = false;
        this.writeInProgress = false;
        this.readQueue = new ConcurrentLinkedQueue<BluetoothGattCharacteristic>();
    }

    public BLeSerialPortService setContext(Context context) {
        this.context = context;
        return this;
    }

    @Override
    public IBinder onBind(Intent intent) {
        return mBinder;
    }

    public class LocalBinder extends Binder {
        public BLeSerialPortService getService() {
            return  BLeSerialPortService.this;
        }
    }

    // Return true if connected to UART device, false otherwise.
    public boolean isConnected() {
        return (tx != null && rx != null);
    }

    public String getDeviceInfo() {
        if (tx == null || !disAvailable ) {
            // Do nothing if there is no connection.
            return "";
        }
        StringBuilder sb = new StringBuilder();
        sb.append("Manufacturer : " + disManuf.getStringValue(0) + "\n");
        sb.append("Model        : " + disModel.getStringValue(0) + "\n");
        sb.append("Firmware     : " + disSWRev.getStringValue(0) + "\n");
        return sb.toString();
    };

    public boolean deviceInfoAvailable() { return disAvailable; }

    // Send data to connected ble serial port device.
    public void send(byte[] data) {
        long beginMillis = System.currentTimeMillis();
        if (tx == null || data == null || data.length == 0) {
            // Do nothing if there is no connection or message to send.
            return;
        }
        // Update TX characteristic value.  Note the setValue overload that takes a byte array must be used.
        tx.setValue(data);
        writeInProgress = true; // Set the write in progress flag
        gatt.writeCharacteristic(tx);
        while (writeInProgress) {
            if (System.currentTimeMillis() - beginMillis > CommunicationStatus.SEND_TIME_OUT_MILLIS) {
                notifyOnCommunicationError(CommunicationStatus.COMMUNICATION_TIMEOUT, null);
                break;
            }
        } ; // Wait for the flag to clear in onCharacteristicWrite
    }

    // Send data to connected ble serial port device. We can only send 20 bytes per packet,
    // so break longer messages up into 20 byte payloads
    public void send(String string) {
        int len = string.length(); int pos = 0;
        StringBuilder stringBuilder = new StringBuilder();

        while (len != 0) {
            stringBuilder.setLength(0);
            if (len >= 20) {
                stringBuilder.append(string.toCharArray(), pos, 20);
                len -= 20;
                pos += 20;
            } else {
                stringBuilder.append(string.toCharArray(), pos, len);
                len = 0;
            }
            send(stringBuilder.toString().getBytes());
        }
    }

    public void readCharacteristic(BluetoothGattCharacteristic characteristic) {
        if (gatt != null) {
            gatt.readCharacteristic(characteristic);
        }
    }

    public boolean setCharacteristicNotification(BluetoothGattCharacteristic characteristic, boolean enabled) {
        if (adapter != null || gatt != null) {
            if (gatt.setCharacteristicNotification(characteristic, enabled)) {
                BluetoothGattDescriptor descriptor = characteristic.getDescriptor(CLIENT_UUID);

                if (descriptor != null) {
                    byte[] data = enabled ? BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE : BluetoothGattDescriptor.DISABLE_NOTIFICATION_VALUE;
                    if (descriptor.setValue(data)) {
                        gatt.writeDescriptor(descriptor);
                    } else {
                        connectFailure();
                    }
                } else {
                    connectFailure();
                }
            } else {
                connectFailure();
            }
        }
        return true;
    }

    public boolean enableRXNotification() {
        if (gatt == null)   return false;

        BluetoothGattService SerialService = gatt.getService(SERIAL_SERVICE_UUID);
        if (SerialService == null)  return false;

        BluetoothGattCharacteristic RxChar = SerialService.getCharacteristic(RX_CHAR_UUID);
        if (RxChar == null) {
            connectFailure();
            return false;
        }

        if (!setCharacteristicNotification(RxChar, true)) {
            connectFailure();
            return false;
        }

        return true;
    }

    // Register the specified callback to receive serial port callbacks.
    public BLeSerialPortService registerCallback(Callback callback) {
        if ((!callbacks.containsKey(callback)) && (callback != null))
            callbacks.put(callback, null);

        return this;
    }

    // Unregister the specified callback.
    public BLeSerialPortService unregisterCallback(Callback callback) {
        if (callbacks.containsKey(callback) && (callback != null))
            callbacks.remove(callback);

        return this;
    }

    // Disconnect to a device if currently connected.
    public BLeSerialPortService disconnect() {
        if (gatt != null) {
            gatt.disconnect();
        }
        gatt = null;
        tx = null;
        rx = null;

        return this;
    }

    //After using a given BLE device, the app must call this method to ensure resources are released properly.
    public BLeSerialPortService close() {
        if (gatt != null) {
            disconnect();
            gatt.close();
            gatt = null;
        }

        return this;
    }

    // Stop any in progress bluetooth device scan.
    public BLeSerialPortService stopScan() {
        if (adapter != null) {
            adapter.stopLeScan(this);
        }

        return this;
    }

    // Start scanning for BLE devices.  Registered callback's onDeviceFound method will be called
    // when devices are found during scanning.
    public BLeSerialPortService startScan() {
        if (adapter != null) {
            adapter.startLeScan(this);
        }

        return this;
    }

    // Connect to the first available ble device.
    public BLeSerialPortService connectFirstAvailable() {
        // Disconnect to any connected device.
        disconnect();
        // Stop any in progress device scan.
        stopScan();
        // Start scan and connect to first available device.
        connectFirst = true;
        startScan();

        return this;
    }

    public BluetoothGattCallback mGattCallback  = new BluetoothGattCallback() {

        @Override
        public void onConnectionStateChange(BluetoothGatt gatt, int status, int newState) {
            super.onConnectionStateChange(gatt, status, newState);
            if (newState == BluetoothGatt.STATE_CONNECTED) {
                if (status == BluetoothGatt.GATT_SUCCESS) {
                    // Connected to device, start discovering services.
                    if (!gatt.discoverServices()) {
                        // Error starting service discovery.
                        connectFailure();
                    }
                } else {
                    // Error connecting to device.
                    connectFailure();
                }
            } else if (newState == BluetoothGatt.STATE_DISCONNECTED) {
                // Disconnected, notify callbacks of disconnection.
                rx = null;
                tx = null;
                notifyOnDisconnected(context);
            }
        }

        @Override
        public void onServicesDiscovered(BluetoothGatt gatt, int status) {
            super.onServicesDiscovered(gatt, status);
            // Notify connection failure if service discovery failed.
            if (status == BluetoothGatt.GATT_FAILURE) {
                connectFailure();
                return;
            }

            // Save reference to each UART characteristic.
            tx = gatt.getService(SERIAL_SERVICE_UUID).getCharacteristic(TX_CHAR_UUID);
            rx = gatt.getService(SERIAL_SERVICE_UUID).getCharacteristic(RX_CHAR_UUID);

            enableRXNotification();
            // Notify of connection completion.
            notifyOnConnected(context);
        }

        @Override
        public void onCharacteristicChanged(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic) {
            super.onCharacteristicChanged(gatt, characteristic);
            notifyOnReceive(context, characteristic);
        }

        @Override
        public void onCharacteristicRead (BluetoothGatt gatt, BluetoothGattCharacteristic characteristic, int status) {
            super.onCharacteristicRead(gatt, characteristic, status);

            if (status == BluetoothGatt.GATT_SUCCESS) {
                //Log.w("DIS", characteristic.getStringValue(0));
                // Check if there is anything left in the queue
                BluetoothGattCharacteristic nextRequest = readQueue.poll();
                if (nextRequest != null) {
                    // Send a read request for the next item in the queue
                    gatt.readCharacteristic(nextRequest);
                } else {
                    // We've reached the end of the queue
                    disAvailable = true;
                    notifyOnDeviceInfoAvailable();
                }
            } else {

            }
        }

        @Override
        public void onCharacteristicWrite(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic, int status) {
            super.onCharacteristicWrite(gatt, characteristic, status);

            if (status == BluetoothGatt.GATT_SUCCESS) {
                notifyOnCommunicationError(characteristic.getStringValue(0).length(), characteristic.getStringValue(0));
            }
            writeInProgress = false;
        }

        @Override
        public void onDescriptorRead(BluetoothGatt gatt, BluetoothGattDescriptor descriptor, int status) {
            super.onDescriptorRead(gatt, descriptor, status);
        }

        @Override
        public void onDescriptorWrite(BluetoothGatt gatt, BluetoothGattDescriptor descriptor, int status) {
            super.onDescriptorWrite(gatt, descriptor, status);
        }

        @Override
        public void onReliableWriteCompleted(BluetoothGatt gatt, int status) {
            super.onReliableWriteCompleted(gatt, status);
        }

        @Override
        public void onReadRemoteRssi(BluetoothGatt gatt, int rssi, int status) {
            super.onReadRemoteRssi(gatt, rssi, status);
        }

        @Override
        public void onMtuChanged(BluetoothGatt gatt, int mtu, int status) {
            super.onMtuChanged(gatt, mtu, status);
        }
    };

    // Handlers for BluetoothGatt and LeScan events.
    public BLeSerialPortService connect(BluetoothDevice device) {
        gatt = device.connectGatt(context, false, mGattCallback);
        return this;
    }


    @Override
    public void onLeScan(BluetoothDevice device, int rssi, byte[] scanRecord) {
        List<UUID> uuids = parseUUIDs(scanRecord);

        // Stop if the device doesn't have the UART service.
        if (uuids.contains(SERIAL_SERVICE_UUID)) {

            // Notify registered callbacks of found device.
            notifyOnDeviceFound(device);

            // Connect to first found device if required.
            if (connectFirst) {
                // Stop scanning for devices.
                stopScan();
                // Prevent connections to future found devices.
                connectFirst = false;
                // Connect to device.
                gatt = device.connectGatt(context, true, mGattCallback);
            }
        }

    }

    // Private functions to simplify the notification of all callbacks of a certain event.
    private void notifyOnConnected(Context context) {
        for (Callback cb : callbacks.keySet()) {
            if (cb != null) {
                cb.onConnected(context);
            }
        }
    }

    private void notifyOnConnectFailed(Context context) {
        for (Callback cb : callbacks.keySet()) {
            if (cb != null) {
                cb.onConnectFailed(context);
            }
        }
    }

    private void notifyOnDisconnected(Context context) {
        for (Callback cb : callbacks.keySet()) {
            if (cb != null) {
                cb.onDisconnected(context);
            }
        }
    }

    private void notifyOnReceive(Context context, BluetoothGattCharacteristic rx) {
        for (Callback cb : callbacks.keySet()) {
            if (cb != null ) {
                cb.onReceive(context, rx);
                showMessage(rx.getStringValue(0));
            }
        }
    }

    private void notifyOnDeviceFound(BluetoothDevice device) {
        for (Callback cb : callbacks.keySet()) {
            if (cb != null) {
                cb.onDeviceFound(device);
            }
        }
    }

    private void notifyOnDeviceInfoAvailable() {
        for (Callback cb : callbacks.keySet()) {
            if (cb != null) {
                cb.onDeviceInfoAvailable();
            }
        }
    }
    private void notifyOnCommunicationError(int status, String msg) {
        for (Callback cb : callbacks.keySet()) {
            if (cb != null) {
                cb.onCommunicationError(status, msg);
            }
        }
    }

    // Notify callbacks of connection failure, and reset connection state.
    private void connectFailure() {
        rx = null;
        tx = null;
        notifyOnConnectFailed(context);
    }

    // Filtering by custom UUID is broken in Android 4.3 and 4.4, see:
    //   http://stackoverflow.com/questions/18019161/startlescan-with-128-bit-uuids-doesnt-work-on-native-android-ble-implementation?noredirect=1#comment27879874_18019161
    // This is a workaround function from the SO thread to manually parse advertisement data.
    private List<UUID> parseUUIDs(final byte[] advertisedData) {
        List<UUID> uuids = new ArrayList<UUID>();

        int offset = 0;
        while (offset < (advertisedData.length - 2)) {
            int len = advertisedData[offset++];
            if (len == 0)
                break;

            int type = advertisedData[offset++];
            switch (type) {
            case 0x02: // Partial list of 16-bit UUIDs
            case 0x03: // Complete list of 16-bit UUIDs
                while (len > 1) {
                    int uuid16 = advertisedData[offset++];
                    uuid16 += (advertisedData[offset++] << 8);
                    len -= 2;
                    uuids.add(UUID.fromString(String.format("%08x-0000-1000-8000-00805f9b34fb", uuid16)));
                }
                break;
            case 0x06:// Partial list of 128-bit UUIDs
            case 0x07:// Complete list of 128-bit UUIDs
                // Loop through the advertised 128-bit UUID's.
                while (len >= 16) {
                    try {
                        // Wrap the advertised bits and order them.
                        ByteBuffer buffer = ByteBuffer.wrap(advertisedData, offset++, 16).order(ByteOrder.LITTLE_ENDIAN);
                        long mostSignificantBit = buffer.getLong();
                        long leastSignificantBit = buffer.getLong();
                        uuids.add(new UUID(leastSignificantBit,
                                           mostSignificantBit));
                    } catch (IndexOutOfBoundsException e) {
                        // Defensive programming.
                        //Log.e(LOG_TAG, e.toString());
                        continue;
                    } finally {
                        // Move the offset to read the next uuid.
                        offset += 15;
                        len -= 16;
                    }
                }
                break;
            default:
                offset += (len - 1);
                break;
            }
        }
        return uuids;
    }

    private void showMessage(String msg){
        Log.e(BLeSerialPortService.class.getSimpleName(),msg);
    }
}
