package com.example.bing;

import androidx.appcompat.app.AppCompatActivity;

import android.bluetooth.BluetoothAdapter;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.res.Configuration;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.TextView;
import android.widget.Toast;
import com.google.android.gms.maps.GoogleMapOptions;
import android.content.SharedPreferences;

public class MainActivity extends AppCompatActivity implements NMEADataParser.NMEADataListener {
    private TextView latitudeTextView;
    private TextView longitudeTextView;
    private TextView nmeaTextView;
    private MapHandler mapHandler;
    private NMEADataParser nmeaDataParser;
    private boolean isTrailActive = false;
    private static final String PREFS_NAME = "com.example.bing_preferences";
    private static final String PREFS_POLYLINE_WIDTH = "polyline_width";
    private BluetoothHandler bluetoothHandler;
    private TrailDatabaseHelper trailDatabaseHelper;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        GoogleMapOptions mapOptions = new GoogleMapOptions().maxZoomPreference(100); // Replace 25 with your desired maximum zoom level
        mapHandler = new MapHandler(this, R.id.map, mapOptions);

        // inicializa o map handler
        latitudeTextView = findViewById(R.id.latitudeTextView);
        longitudeTextView = findViewById(R.id.longitudeTextView);

        // cria uma instancia do NMEADataParser e poe esta atividade como seu NMEADataListener
        nmeaDataParser = new NMEADataParser();
        nmeaDataParser.setNMEADataListener(this);

        // registra BroadcastReceiver para as mudancas de estado do Bluetooth
        IntentFilter filter = new IntentFilter(BluetoothAdapter.ACTION_STATE_CHANGED);
        registerReceiver(bluetoothStateReceiver, filter);

        //declara o botao de de comecar o tracejado
        Button startStopTrailButton = findViewById(R.id.startStopTrailButton);

        //salva o tamanho do tracejado
        float savedPolylineWidth = loadPolylineWidth();
        mapHandler.setPolylineWidthInMeters(savedPolylineWidth);

        EditText polylineWidthEditText = findViewById(R.id.polylineWidthEditText);
        Button setPolylineWidthButton = findViewById(R.id.setPolylineWidthButton);

        setPolylineWidthButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                String inputText = polylineWidthEditText.getText().toString();
                if (!inputText.isEmpty()) {
                    float newPolylineWidth = Float.parseFloat(inputText);
                    mapHandler.setPolylineWidthInMeters(newPolylineWidth);
                    savePolylineWidth(newPolylineWidth);

                } else {
                    Toast.makeText(MainActivity.this, "Please enter a valid width", Toast.LENGTH_SHORT).show();
                }
            }
        });
        Button toggleMapViewButton = findViewById(R.id.toggleMapViewButton);
        toggleMapViewButton.setOnClickListener(new View.OnClickListener() {
            private boolean isSatelliteView = false;

            @Override
            public void onClick(View view) {
                if (isSatelliteView) {
                    mapHandler.setMapTypeNormal();
                    toggleMapViewButton.setText("Satellite View");
                } else {
                    mapHandler.setMapTypeSatellite();
                    toggleMapViewButton.setText("Normal View");
                }
                isSatelliteView = !isSatelliteView;
            }
        });


        Button placePointsButton = findViewById(R.id.placePointsButton);

        placePointsButton.setOnClickListener(new View.OnClickListener() {
            boolean pointAAdded = false;

            @Override
            public void onClick(View view) {
                if (!pointAAdded) {
                    mapHandler.placePointA();
                    placePointsButton.setText("Place Point B");
                } else {
                    float userDefinedWidth = Float.parseFloat(polylineWidthEditText.getText().toString());
                    mapHandler.placePointB(userDefinedWidth);
                    placePointsButton.setText("Place Point A");
                }
                pointAAdded = !pointAAdded;
            }
        });

        startStopTrailButton.setOnClickListener(new View.OnClickListener() {
            private boolean isTracing = false;

            @Override
            public void onClick(View view) {
                if (isTracing) {
                    mapHandler.stopTrail();
                    startStopTrailButton.setText("Start Trail");
                } else {
                    mapHandler.startTrail();
                    startStopTrailButton.setText("Stop Trail");
                }
                isTracing = !isTracing;
            }
        });


        Button clearTrailButton = findViewById(R.id.clearTrailButton);
        clearTrailButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                mapHandler.clearTrail();
                trailDatabaseHelper.clearAllTrails(); // Add this line to clear the saved trail data
            }
        });




        // Set up the Bluetooth connection
        setupBluetooth();
        Button centerButton = findViewById(R.id.centerButton);
        centerButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                mapHandler.centerCameraOnMarker();
            }
        });




        // Add the zoom in button and its click listener
        Button zoomInButton = findViewById(R.id.zoomInButton);
        zoomInButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                setMapScale(2); // Replace 2 with the desired scale factor for zooming in
            }
        });

        // Add the zoom out button and its click listener
        Button zoomOutButton = findViewById(R.id.zoomOutButton);
        zoomOutButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                setMapScale(1); // Set the scale factor to 1 for zooming out (original scale)
            }
        });
        trailDatabaseHelper = new TrailDatabaseHelper(this);

    }
    private void savePolylineWidth(float polylineWidth) {
        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        SharedPreferences.Editor editor = prefs.edit();
        editor.putFloat(PREFS_POLYLINE_WIDTH, polylineWidth);
        editor.apply();
    }

    private float loadPolylineWidth() {
        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        return prefs.getFloat(PREFS_POLYLINE_WIDTH, 10); // Default value is 10
    }

    private void setMapScale(float scale) {
        FrameLayout mapContainer = findViewById(R.id.map_container);
        mapContainer.setScaleX(scale);
        mapContainer.setScaleY(scale);
    }

    // Configura a conexão Bluetooth criando uma instância de BluetoothHandler, definindo seu BluetoothDataListener
// e conectando ao dispositivo
    private void setupBluetooth() {
// Replace "HC-05" with the desired device name
        BluetoothHandler bluetoothHandler = new BluetoothHandler("3D AGRO");
        bluetoothHandler.setBluetoothDataListener(new BluetoothHandler.BluetoothDataListener() {

            @Override
            public void onDataReceived(String data) {
                if (data == null) {
                    return;
                }

                // Atualiza o nmeaTextView com os dados recebidos


                // Analisa os dados recebidos e atualiza a UI com a latitude e longitude se elas forem válidas
                String[] lines = data.split("\n");
                for (String line : lines) {
                    NMEADataParser.ParsedGGAData parsedData = NMEADataParser.parseGGAData(line);

                    if (parsedData != null) {
                        double latitude = parsedData.latitude;
                        double longitude = parsedData.longitude;

                        if (isValidCoordinates(latitude, longitude)) {
                            runOnUiThread(new Runnable() {
                                @Override
                                public void run() {
                                    latitudeTextView.setText(String.format("Latitude: %.5f", latitude));
                                    longitudeTextView.setText(String.format("Longitude: %.5f", longitude));
                                }
                            });

                            // Atualiza o marcador no mapa
                            runOnUiThread(new Runnable() {
                                @Override
                                public void run() {
                                    mapHandler.updateMarker(latitude, longitude);
                                }
                            });
                        }
                    }
                }
            }
        });

        bluetoothHandler.connect();
    }
    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        // Do any custom handling if needed, otherwise leave it empty
    }

    // Chamado quando o NMEADataParser termina de analisar uma sentença, atualiza o latitudeTextView e o longitudeTextView
    @Override
    public void onNMEADataParsed(double latitude, double longitude) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                latitudeTextView.setText(String.format("Latitude: %.5f", latitude));
                longitudeTextView.setText(String.format("Longitude: %.5f", longitude));
            }
        });
    }

    private boolean isValidCoordinates(double latitude, double longitude) {
// Verifica se a latitude está entre -34 e 6
        if (latitude < -34 || latitude > 6) {
            return false;
        }

// Verifica se a longitude está entre -74 e -35
        if (longitude < -74 || longitude > -35) {
            return false;
        }

        return true;
    }
    private final BroadcastReceiver bluetoothStateReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            // Verifica se a ação está relacionada a mudanças de estado do Bluetooth


            if (BluetoothAdapter.ACTION_STATE_CHANGED.equals(action)) {
                int state = intent.getIntExtra(BluetoothAdapter.EXTRA_STATE, BluetoothAdapter.ERROR);

                if (state == BluetoothAdapter.STATE_ON) {
                    // O Bluetooth agora está ligado, tente reconectar

                    setupBluetooth();
                }
            }
        }
    };


    @Override
    protected void onDestroy() {
        super.onDestroy();
        unregisterReceiver(bluetoothStateReceiver);
        // Desconecta do dispositivo Bluetooth


        if (bluetoothHandler != null) {
            bluetoothHandler.disconnect();
        }
    }
}