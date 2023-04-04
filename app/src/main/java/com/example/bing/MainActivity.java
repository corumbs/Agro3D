package com.example.bing;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.content.pm.PackageManager;
import android.os.Bundle;


import android.widget.Button;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.UUID;
import android.Manifest;
//libraries
import com.google.android.gms.maps.CameraUpdate;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.Marker;
import com.google.android.gms.maps.model.MarkerOptions;

public class MainActivity extends AppCompatActivity implements OnMapReadyCallback {
    private Thread listenThread;

    private static final String DEVICE_ADDRESS = "10:52:1C:69:3E:6E"; // MAC address of the Bluetooth device
    private static final UUID PORT_UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB"); // Serial Port Service ID
    private Marker mMarker;

    private BluetoothDevice device;
    private BluetoothSocket socket;
    private InputStream inputStream;
    private GoogleMap mMap;
    private Button centerButton;
    private TextView deviceNameTextView;
    private TextView nmeaDataTextView;

    private static final int REQUEST_ENABLE_BT = 1;

    private boolean bluetoothConnected = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        SupportMapFragment mapFragment = (SupportMapFragment) getSupportFragmentManager()
                .findFragmentById(R.id.map);
        mapFragment.getMapAsync(this);
        centerButton = findViewById(R.id.center_button);
        centerButton.setOnClickListener(view -> centerMapOnMarker());
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
    public void onMapReady(GoogleMap googleMap) {
        mMap = googleMap;
        // Move the camera to the current location


        // Add a marker in Sydney and move the camera
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
    private void centerMapOnMarker() {
        if (mMap != null && mMarker != null) {
            mMap.moveCamera(CameraUpdateFactory.newLatLngZoom(mMarker.getPosition(), 18.0f));
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
                        //System.out.println("nmea: " + data);
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
                            //System.out.println("Latitude: " + latitude);
                            //System.out.println("Longitude: " + longitude);
                        } else {
                            System.out.println("Invalid sentence format: " + data);
                        }


                        // Use a latitude e longitude extraídas aqui
                        //System.out.println("Latitude: " + latitude + " " + sentenceParts[3]);
                        //System.out.println("Longitude: " + longitude + " " + sentenceParts[5]);

                        // Display latitude and longitude on the screen
                        double finalLatitude = latitude;
                        double finalLongitude = longitude;

                        double finalLatitude1 = latitude;
                        double finalLongitude1 = longitude;
                        runOnUiThread(() -> {

                            nmeaDataTextView.setText(data);


                            // Update marker position on the map
                            LatLng newPoint = new LatLng(finalLatitude1, finalLongitude1);
                            if (mMarker == null) {
                                mMarker = mMap.addMarker(new MarkerOptions()
                                        .position(newPoint)
                                        .title("this is you"));
                            } else {
                                mMarker.setPosition(newPoint);
                            }




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
}
