package com.example.bing;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.content.pm.PackageManager;
import android.os.Bundle;

import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import java.io.IOException;
import java.io.InputStream;
import java.util.UUID;
import android.Manifest;
//libraries

public class MainActivity extends AppCompatActivity {
    private Thread listenThread;

    private static final String DEVICE_ADDRESS = "10:52:1C:69:3E:6E"; // MAC address of the Bluetooth device
    private static final UUID PORT_UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB"); // Serial Port Service ID

    private BluetoothDevice device;
    private BluetoothSocket socket;
    private InputStream inputStream;

    private TextView deviceNameTextView;
    private TextView nmeaDataTextView;

    private static final int REQUEST_ENABLE_BT = 1;

    private boolean bluetoothConnected = false;
//variables
    @Override
    protected void onCreate(Bundle savedInstanceState) {//O codigo roda por este objeto
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        deviceNameTextView = findViewById(R.id.device_name);
        nmeaDataTextView = findViewById(R.id.nmea_data);
        //envia os dados para a tela visual
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH) != PackageManager.PERMISSION_GRANTED ||
                ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_ADMIN) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.BLUETOOTH, Manifest.permission.BLUETOOTH_ADMIN}, REQUEST_ENABLE_BT);
        } else {
            if (initBluetooth()) {
                connectToDevice();
                startListening();
            }
        }
        //checks if it is connected with bluetooth and only runs if it is
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        //se if it has permission to bluetooth
        if (requestCode == REQUEST_ENABLE_BT) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED && grantResults[1] == PackageManager.PERMISSION_GRANTED) {
                if (initBluetooth()) {
                    connectToDevice();
                    startListening();
                }
            } else {
                // Permission denied
            }
        }
    }

    private boolean initBluetooth() {
        //initiate the bluetooth
        BluetoothAdapter bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        if (bluetoothAdapter == null) {
            // Device doesn't support Bluetooth
            return false;
        }

        if (!bluetoothAdapter.isEnabled()) {
            // Bluetooth is not enabled
            return false;
        }

        device = bluetoothAdapter.getRemoteDevice(DEVICE_ADDRESS);
        deviceNameTextView.setText(device.getName());

        return true;
    }

    private void connectToDevice() {
        //connect to device
        try {
            socket = device.createRfcommSocketToServiceRecord(PORT_UUID);
            socket.connect();
            bluetoothConnected = true;
        } catch (IOException e) {
            e.printStackTrace();
            bluetoothConnected = false;
        }
    }

    private void startListening() {
        //start to get the nmea data
        listenThread = new Thread(this::listenForData);
        listenThread.start();
    }

    private void listenForData() {
        try {
            //way to get the nmea data
            inputStream = socket.getInputStream();
            byte[] buffer = new byte[1024];
            int bytes;
            String data = "";

            while (inputStream != null) {
                try {
                    bytes = inputStream.read(buffer);
                    String newData = new String(buffer, 0, bytes);
                    data += newData;

                    // Find the index of the last complete NMEA sentence in the data
                    int lastNmeaIndex = data.lastIndexOf("$");
                    if (lastNmeaIndex != -1) {
                        // Extract the most recent complete NMEA sentence
                        String mostRecentNmea = data.substring(lastNmeaIndex);
                        // Update the UI with the most recent NMEA data
                        runOnUiThread(() -> nmeaDataTextView.setText(mostRecentNmea));
                    }
                } catch (IOException e) {
                    // An IOException was thrown, so the Bluetooth device is probably disconnected
                    e.printStackTrace();
                    inputStream = null;
                    socket.close();
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }


}
