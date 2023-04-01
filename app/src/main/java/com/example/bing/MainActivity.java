package com.example.bing;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.os.Bundle;

import android.preference.PreferenceManager;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;


import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.UUID;
import android.Manifest;

import org.osmdroid.api.IMapController;
import org.osmdroid.config.Configuration;
import org.osmdroid.tileprovider.tilesource.TileSourceFactory;
import org.osmdroid.util.GeoPoint;
import org.osmdroid.views.MapView;
import org.osmdroid.views.overlay.Marker;



//libraries

public class MainActivity extends AppCompatActivity {

    private Thread listenThread;

    private static final String DEVICE_ADDRESS = "10:52:1C:69:3E:6E"; // MAC address of the Bluetooth device
    private static final UUID PORT_UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB"); // Serial Port Service ID

    private BluetoothDevice device;
    private BluetoothSocket socket;
    private InputStream inputStream;
    private Marker mMarker;

    private TextView deviceNameTextView;
    private TextView nmeaDataTextView;

    private static final int REQUEST_ENABLE_BT = 1;

    private boolean bluetoothConnected = false;
    private MapView map;
    private Marker marker;
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        IntentFilter filter = new IntentFilter(BluetoothAdapter.ACTION_STATE_CHANGED);
        registerReceiver(bluetoothReceiver, filter);

        // Set the user agent
        Configuration.getInstance().load(this, PreferenceManager.getDefaultSharedPreferences(this));
        Configuration.getInstance().setUserAgentValue(BuildConfig.APPLICATION_ID);

// Initialize the MapView
        map = (MapView) findViewById(R.id.map);
        map.setTileSource(TileSourceFactory.MAPNIK);

        IMapController mapController = map.getController();
        mapController.setZoom(15.0);
        GeoPoint startPoint = new GeoPoint( -24.050397, -52.384150);
        mapController.setCenter(startPoint);
        marker = new Marker(map);
        marker.setPosition(startPoint);
        marker.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM);
        map.getOverlays().add(marker);

        deviceNameTextView = findViewById(R.id.device_name);
        nmeaDataTextView = findViewById(R.id.nmea_data);

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH) != PackageManager.PERMISSION_GRANTED ||
                ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_ADMIN) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.BLUETOOTH, Manifest.permission.BLUETOOTH_ADMIN}, REQUEST_ENABLE_BT);
        } else {
            if (initBluetooth()) {
                if (connectToDevice()) {
                    startListening();
                }
            }
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);

        if (requestCode == REQUEST_ENABLE_BT) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED && grantResults[1] == PackageManager.PERMISSION_GRANTED) {
                if (initBluetooth()) {
                    if (connectToDevice()) {
                        startListening();
                    }
                }
            } else {
                // Permission denied
            }
        }
    }

    private boolean initBluetooth() {
        BluetoothAdapter bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        if (bluetoothAdapter == null) {
            return false;
        }

        if (!bluetoothAdapter.isEnabled()) {
            return false;
        }

        device = bluetoothAdapter.getRemoteDevice(DEVICE_ADDRESS);
        deviceNameTextView.setText(device.getName());

        return true;
    }

    private boolean connectToDevice() {
        try {
            socket = device.createRfcommSocketToServiceRecord(PORT_UUID);
            socket.connect();
            bluetoothConnected = true;
            return true;
        } catch (IOException e) {
            e.printStackTrace();
            bluetoothConnected = false;
            return false;
        }
    }

    private void startListening() {
        if (bluetoothConnected) {
            listenThread = new Thread(this::listenForData);
            listenThread.start();
        }
    }
    private final BroadcastReceiver bluetoothReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            final String action = intent.getAction();
            if (action.equals(BluetoothAdapter.ACTION_STATE_CHANGED)) {
                final int state = intent.getIntExtra(BluetoothAdapter.EXTRA_STATE, BluetoothAdapter.ERROR);
                switch (state) {
                    case BluetoothAdapter.STATE_OFF:
                        // Bluetooth turned off, close the socket and stop listening for data
                        closeSocket();
                        break;
                    case BluetoothAdapter.STATE_TURNING_OFF:
                        // Bluetooth turning off, close the socket and stop listening for data
                        closeSocket();
                        break;
                    case BluetoothAdapter.STATE_ON:
                        // Bluetooth turned on, try to reconnect to the device
                        if (!bluetoothConnected) {
                            connectToDevice();
                        }
                        break;
                    case BluetoothAdapter.STATE_TURNING_ON:
                        // Bluetooth turning on, do nothing
                        break;
                }
            }
        }
    };


    private void listenForData() {

        try {
            inputStream = socket.getInputStream();
            BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream));

            while (bluetoothConnected) {
                String data = reader.readLine();
                if (data != null) {
                    runOnUiThread(() -> nmeaDataTextView.setText(data));


                    String[] sentenceParts = data.split(",");
                    String sentenceType = sentenceParts[0].substring(3);
                    // Check if the NMEA sentence is of the correct type
                    if (sentenceType.equals("GGA")) {
                        System.out.println("nmea: " + data);
                        // Extract latitude and longitude values from the sentence
                        double latitude = 0;
                        double longitude = 0;
                        if (sentenceParts.length >= 6 && sentenceParts[2].length() >= 4 && sentenceParts[4].length() >= 5) {
                            latitude = Double.parseDouble(sentenceParts[2].substring(0, 2));
                            latitude += Double.parseDouble(sentenceParts[2].substring(2)) / 60;
                            if (sentenceParts[3].equals("S")) {
                                latitude = -latitude;
                            }

                            longitude = Double.parseDouble(sentenceParts[4].substring(0, 3));
                            longitude += Double.parseDouble(sentenceParts[4].substring(3)) / 60;
                            if (sentenceParts[5].equals("W")) {
                                longitude = -longitude;
                            }
                            // Use latitude and longitude values here
                            System.out.println("Latitude: " + latitude);
                            System.out.println("Longitude: " + longitude);
                        } else {
                            System.out.println("Invalid sentence format: " + data);
                        }


                        // Use a latitude e longitude extraídas aqui
                        System.out.println("Latitude: " + latitude + " " + sentenceParts[3]);
                        System.out.println("Longitude: " + longitude + " " + sentenceParts[5]);

                        // Display latitude and longitude on the screen
                        double finalLatitude = latitude;
                        double finalLongitude = longitude;

                        runOnUiThread(() -> {

                            nmeaDataTextView.setText(data);
                            TextView latitudeTextView = findViewById(R.id.latitude);
                            TextView longitudeTextView = findViewById(R.id.longitude);
                            latitudeTextView.setText(String.format("%.4f", finalLatitude));
                            longitudeTextView.setText(String.format("%.4f", finalLongitude));

                            // Update marker position on the map
                            GeoPoint newPoint = new GeoPoint(finalLatitude, finalLongitude);
                            marker.setPosition(newPoint);
                            map.getController().animateTo(newPoint);
                        });
                    }
                } else {
                    break;
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }

        bluetoothConnected = false;

        // Reconnect and restart data flow
        while (!bluetoothConnected) {
            try {
                Thread.sleep(1000); // Wait for 1 second before attempting reconnection
                if (connectToDevice()) {
                    startListening();
                }
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
    }
    private void closeSocket() {
        bluetoothConnected = false;
        try {
            socket.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        unregisterReceiver(bluetoothReceiver);

        if (bluetoothConnected) {
            try {
                socket.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }




}

