package com.example.bing;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.content.pm.PackageManager;
import android.os.Bundle;


import android.view.inputmethod.EditorInfo;
import android.widget.Button;
import android.widget.EditText;
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
import com.google.android.gms.maps.model.CameraPosition;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.Marker;
import com.google.android.gms.maps.model.MarkerOptions;
import com.google.android.gms.maps.model.Polyline;
import com.google.android.gms.maps.model.PolylineOptions;
import android.graphics.Color;
import android.widget.Toast;

public class MainActivity extends AppCompatActivity implements OnMapReadyCallback {
    private Thread listenThread;
    private static final String DEVICE_ADDRESS = "10:52:1C:69:3E:6E"; // MAC address of the Bluetooth device
    private static final UUID PORT_UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB"); // Serial Port Service ID
    private Marker mMarker;
    private PolylineOptions polylineOptions;
    private Polyline polyline;
    private BluetoothDevice device;
    private BluetoothSocket socket;
    private InputStream inputStream;
    private GoogleMap mMap;
    private Button centerButton;
    private TextView deviceNameTextView;
    private TextView nmeaDataTextView;
    private static final int REQUEST_ENABLE_BT = 1;
    private boolean bluetoothConnected = false;
    private float polylineWidthInMeters = 5.0f; // Initial polyline width in meters
    private boolean isTracing = false; // Keep track of whether or not to trace markers
    private Button traceButton; // Button to start and stop tracing
    private Marker markerA, markerB;
    private int markerAClicks = 0, markerBClicks = 0;
    private Button placeMarkersButton;
    private LatLng lastKnownDirection;
    private int numOfParallelLines = 5; // Adjust this value based on your requirements


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

        // Get reference to trace button and set click listener
        traceButton = findViewById(R.id.trace_button);
        traceButton.setOnClickListener(view -> {
            isTracing = !isTracing; // Toggle tracing state
            traceButton.setText(isTracing ? "Stop Tracing" : "Start Tracing"); // Update button text
        });
        placeMarkersButton = findViewById(R.id.place_markers_button);
        placeMarkersButton.setOnClickListener(view -> {
            if (markerAClicks == 0) {
                markerA = mMap.addMarker(new MarkerOptions()
                        .position(mMarker.getPosition())
                        .title("Marker A"));
                markerAClicks++;
            } else if (markerBClicks == 0) {
                markerB = mMap.addMarker(new MarkerOptions()
                        .position(mMarker.getPosition())
                        .title("Marker B"));
                markerBClicks++;

                drawParallelLines(markerA.getPosition(), markerB.getPosition(), polylineWidthInMeters);
            }
        });
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
        EditText polylineWidthInput = findViewById(R.id.polyline_width_input);
        polylineWidthInput.setOnEditorActionListener((textView, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_DONE) {
                try {
                    float meters = Float.parseFloat(textView.getText().toString());
                    updatePolylineWidth(meters);
                } catch (NumberFormatException e) {
                    Toast.makeText(MainActivity.this, "Invalid input. Please enter a valid number.", Toast.LENGTH_SHORT).show();
                }
                return true;
            }
            return false;
        });

    }

    @Override
    public void onMapReady(GoogleMap googleMap) {
        mMap = googleMap;
        polylineOptions = new PolylineOptions()
                .color(Color.argb(80, 255, 255, 0)); // Set color to semi-transparent yellow

        // Set an initial polyline width
        updatePolylineWidth(polylineWidthInMeters);

        // Update polyline width when the camera position changes
        mMap.setOnCameraIdleListener(() -> updatePolylineWidth(polylineWidthInMeters));

        // Set minimum and maximum zoom levels
        float minZoom = 17.0f; // Adjust this value to set the minimum zoom level
        float maxZoom = mMap.getMaxZoomLevel();
        mMap.setMinZoomPreference(minZoom);
        mMap.setMaxZoomPreference(maxZoom);
    }

    private void drawParallelLines(LatLng pointA, LatLng pointB, float polylineWidth) {
        double distance = calculateDistance(pointA, pointB);
        int numOfLines = (int) Math.ceil(distance / polylineWidth);

        double dx = (pointB.longitude - pointA.longitude) / numOfLines;
        double dy = (pointB.latitude - pointA.latitude) / numOfLines;

        double extendDistance = 1000; // The distance (in meters) to extend the lines
        double angle = Math.atan2(dy, dx);
        double extendDx = extendDistance * Math.cos(angle) / 111320; // Convert meters to degrees longitude
        double extendDy = extendDistance * Math.sin(angle) / 111320; // Convert meters to degrees latitude

        // Calculate the perpendicular angle and distance
        double perpendicularAngle = angle + Math.PI / 2;
        double dPerpendicularX = polylineWidth * Math.cos(perpendicularAngle) / 111320;
        double dPerpendicularY = polylineWidth * Math.sin(perpendicularAngle) / 111320;

        int numOfParallelLines = 3; // Number of parallel lines to create on each side of the main line

        for (int i = 0; i < numOfLines; i++) {
            for (int j = -numOfParallelLines; j <= numOfParallelLines; j++) {
                LatLng start = new LatLng(pointA.latitude + i * dy - extendDy + j * dPerpendicularY,
                        pointA.longitude + i * dx - extendDx + j * dPerpendicularX);
                LatLng end = new LatLng(pointA.latitude + (i + 1) * dy + extendDy + j * dPerpendicularY,
                        pointA.longitude + (i + 1) * dx + extendDx + j * dPerpendicularX);

                Polyline line = mMap.addPolyline(new PolylineOptions()
                        .add(start, end)
                        .width(3) // Width of the line (make it thin)
                        .color(Color.BLACK)); // Set the color to black
            }
        }

        // Remove markers A and B after drawing the lines
        if (markerA != null) {
            markerA.remove();
        }
        if (markerB != null) {
            markerB.remove();
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
    private void updatePolylineWidth(float meters) {
        polylineWidthInMeters = meters;
        if (mMap != null) {
            float zoomLevel = mMap.getCameraPosition().zoom;
            float metersPerPixel = (float) (156543.03392 * Math.cos(mMap.getCameraPosition().target.latitude * Math.PI / 180) / Math.pow(2, zoomLevel));
            float widthInPixels = meters / metersPerPixel;
            if (polyline != null) {
                polyline.setWidth(widthInPixels);
            }
            if (polylineOptions != null) {
                polylineOptions.width(widthInPixels);
            }
        }
    }
    private void updateCameraPosition(Float zoomLevel) {
        if (mMap != null && mMarker != null) {
            if (zoomLevel != null) {
                mMap.moveCamera(CameraUpdateFactory.newLatLngZoom(mMarker.getPosition(), zoomLevel));
            } else {
                mMap.moveCamera(CameraUpdateFactory.newLatLng(mMarker.getPosition()));
            }
        }
    }
    private double calculateDistance(LatLng point1, LatLng point2) {
        double R = 6371000; // Earth radius in meters
        double dLat = Math.toRadians(point2.latitude - point1.latitude);
        double dLng = Math.toRadians(point2.longitude - point1.longitude);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2) +
                Math.cos(Math.toRadians(point1.latitude)) * Math.cos(Math.toRadians(point2.latitude)) *
                        Math.sin(dLng / 2) * Math.sin(dLng / 2);
        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
        return R * c;
    }

    private void extendParallelLines(LatLng currentPoint, LatLng direction, float polylineWidth) {
        if (lastKnownDirection == null) {
            return;
        }

        LatLng newPointA = new LatLng(markerA.getPosition().latitude + direction.latitude,
                markerA.getPosition().longitude + direction.longitude);
        LatLng newPointB = new LatLng(markerB.getPosition().latitude + direction.latitude,
                markerB.getPosition().longitude + direction.longitude);

        // Extend lines using newPointA and newPointB
        drawParallelLines(newPointA, newPointB, polylineWidth);

        // Update markerA and markerB positions
        markerA.setPosition(newPointA);
        markerB.setPosition(newPointB);
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

                    if (sentenceType.equals("GGA")) {
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
                        } else {
                            System.out.println("Invalid sentence format: " + data);
                        }

                        double finalLatitude = latitude;
                        double finalLongitude = longitude;

                        runOnUiThread(() -> {
                            nmeaDataTextView.setText(data);

                            LatLng newPoint = new LatLng(finalLatitude, finalLongitude);
                            if (mMarker == null) {
                                mMarker = mMap.addMarker(new MarkerOptions()
                                        .position(newPoint)
                                        .title("this is you"));
                                Float minZoom = null;
                                updateCameraPosition(minZoom);
                            } else {
                                mMarker.setPosition(newPoint);
                            }

                            // Store the last known direction
                            LatLng lastPoint = new LatLng(finalLatitude, finalLongitude);
                            if (mMarker != null) {
                                lastKnownDirection = new LatLng(mMarker.getPosition().latitude - lastPoint.latitude,
                                        mMarker.getPosition().longitude - lastPoint.longitude);
                            }

                            if (isTracing) {
                                polylineOptions.add(newPoint);
                                if (polyline != null) {
                                    polyline.remove();
                                }
                                polyline = mMap.addPolyline(polylineOptions);
                            }

                            if (markerA != null && markerB != null) {
                                double distanceToLineA = calculateDistance(newPoint, markerA.getPosition());
                                double distanceToLineB = calculateDistance(newPoint, markerB.getPosition());
                                double maxDistance = polylineWidthInMeters * (2 * numOfParallelLines + 1);

                                if (distanceToLineA > maxDistance || distanceToLineB > maxDistance) {
                                    extendParallelLines(newPoint, lastKnownDirection, polylineWidthInMeters);
                                }
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

        while (!bluetoothConnected) {
            try {
                Thread.sleep(1000);
                if (connectToDevice()) {
                    startListening();
                }
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
    }

}
