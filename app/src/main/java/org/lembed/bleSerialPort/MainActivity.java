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

import android.app.*;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGattCharacteristic;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.pm.ActivityInfo;
import android.os.Bundle;
import android.os.IBinder;
import android.text.method.ScrollingMovementMethod;
import android.util.Log;
import android.view.MenuItem;
import android.view.View;

import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;



public class MainActivity extends Activity implements BLeSerialPortService.Callback, View.OnClickListener {

    // UI elements
    private TextView messages;
    private EditText input;
    private Button   connect;

    // BLE serial port instance.  This is defined in BLeSerialPortService.java.
    private BLeSerialPortService serialPort;
    private final int REQUEST_DEVICE = 3;
    private final int REQUEST_ENABLE_BT = 2;
    private  int rindex = 0;

    private ServiceConnection mServiceConnection = new ServiceConnection() {
        public void onServiceConnected(ComponentName className, IBinder rawBinder) {
            serialPort = ((BLeSerialPortService.LocalBinder) rawBinder).getService()

                         //register the application context to service for callback
                         .setContext(getApplicationContext())
                         .registerCallback(MainActivity.this);
        }

        public void onServiceDisconnected(ComponentName classname) {
            serialPort.unregisterCallback(MainActivity.this)
            //Close the bluetooth gatt connection.
            .close();
        }
    };

    // Write some text to the messages text view.
    private void writeLine(final CharSequence text) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                messages.append(text);
                messages.append("\n");
            }
        });
    }

    // Handler for mouse click on the send button.
    public void sendView(View view) {
        String message = input.getText().toString();
        serialPort.send(message);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // Grab references to UI elements.
        messages = (TextView) findViewById(R.id.messages);
        input = (EditText) findViewById(R.id.input);

        // Enable auto-scroll in the TextView
        messages.setMovementMethod(new ScrollingMovementMethod());

        connect = (Button) findViewById(R.id.connect);
        connect.setOnClickListener(this);

        // bind and start the bluetooth service
        Intent bindIntent = new Intent(this, BLeSerialPortService.class);
        bindService(bindIntent, mServiceConnection, Context.BIND_AUTO_CREATE);
    }

    // OnResume, called right before UI is displayed.  Connect to the bluetooth device.
    @Override
    protected void onResume() {
        super.onResume();

        // set the screen to portrait
        if (getRequestedOrientation() != ActivityInfo.SCREEN_ORIENTATION_PORTRAIT) {
            setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
        }

        // if the bluetooth adatper is not support and enabled
        BluetoothAdapter mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        if (mBluetoothAdapter == null) {
            finish();
        }

        // request to open the bluetooth adapter
        if (!mBluetoothAdapter.isEnabled()) {
            Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
            startActivityForResult(enableBtIntent, REQUEST_ENABLE_BT);
        }

    }

    // OnStop, close the service connection
    @Override
    protected void onStop() {
        super.onStop();
        serialPort.stopSelf();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        unbindService(mServiceConnection);
    }

    @Override
    public  void onBackPressed() {
        finish();
    }

    @Override
    public void onClick(View v) {
        Button bt = (Button) v;
        if (v.getId() == R.id.connect) {
            // the device can send data to
            if (bt.getText().equals(getResources().getString(R.string.send))) {
                sendView(v);
            }
            // if the device is not connectted
            if (bt.getText().equals(getResources().getString(R.string.connect))) {
                Intent intent = new Intent(this, DeviceListActivity.class);
                startActivityForResult(intent, REQUEST_DEVICE);
            }
        }
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();
        if (id == R.id.action_about) {
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    // serial port Callback handlers.
    @Override
    public void onConnected(Context context) {
        // when serial port device is connected
        writeLine("Connected!");
        // Enable the send button
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                connect = (Button) findViewById(R.id.connect);
                connect.setText(R.string.send);
            }
        });
    }

    @Override
    public void onConnectFailed(Context context) {
        // when some error occured which prevented serial port connection from completing.
        writeLine("Error connecting to device!");
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                connect = (Button) findViewById(R.id.connect);
                connect.setText(R.string.connect);
            }
        });
    }

    @Override
    public void onDisconnected(Context context) {
        //when device disconnected.
        writeLine("Disconnected!");
        // update the send button text to connect
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                connect = (Button)findViewById(R.id.connect);
                connect.setText(R.string.connect);
            }
        });
    }

    @Override
    public void onCommunicationError(int status, String msg) {
        // get the send value bytes
        if (status > 0) {
        }// when the send process found error, for example the send thread  time out
        else {
            writeLine("send error status = " + status);
        }
    }

    @Override
    public void onReceive(Context context, BluetoothGattCharacteristic rx) {
        String msg = rx.getStringValue(0);
        rindex = rindex + msg.length();
        writeLine("> " + rindex + ":" + msg);
    }

    @Override
    public void onDeviceFound(BluetoothDevice device) {
        // Called when a UART device is discovered (after calling startScan).
        writeLine("Found device : " + device.getAddress());
        writeLine("Waiting for a connection ...");

    }

    @Override
    public void onDeviceInfoAvailable() {
        writeLine(serialPort.getDeviceInfo());
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        switch (requestCode) {
        case REQUEST_DEVICE:
            //When the DeviceListActivity return, with the selected device address
            if (resultCode == Activity.RESULT_OK && data != null) {
                String deviceAddress = data.getStringExtra(BluetoothDevice.EXTRA_DEVICE);
                BluetoothDevice device = BluetoothAdapter.getDefaultAdapter().getRemoteDevice(deviceAddress);
                serialPort.connect(device);
                showMessage(device.getName());
            }
            break;
        case REQUEST_ENABLE_BT:
            // When the request to enable Bluetooth returns
            if (resultCode == Activity.RESULT_OK) {
                Toast.makeText(this, "Bluetooth has turned on ", Toast.LENGTH_SHORT).show();
            } else {
                // User did not enable Bluetooth or an error occurred
                Toast.makeText(this, "Problem in BT Turning ON ", Toast.LENGTH_SHORT).show();
            }
            break;
        default:
            break;
        }
    }

    private void showMessage(String msg) {
        Log.e(MainActivity.class.getSimpleName(), msg);
    }

}
