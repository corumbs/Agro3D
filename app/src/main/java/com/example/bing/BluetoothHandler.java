package com.example.bing;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.util.Log;
import java.io.IOException;
import java.io.InputStream;
import java.util.Set;
import java.util.UUID;

public class BluetoothHandler {
    private static final String TAG = "BluetoothHandler";
    private static final UUID MY_UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB");

    private BluetoothAdapter bluetoothAdapter;
    private BluetoothDevice bluetoothDevice;
    private BluetoothSocket bluetoothSocket;
    private InputStream inputStream;
    private String deviceName;
    private static final long RECONNECT_DELAY = 5000; // 5 seconds

    private BluetoothDataListener bluetoothDataListener;

    public BluetoothHandler(String deviceName) {
        // Obtém o adaptador Bluetooth padrão
        bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        this.deviceName = deviceName;
        bluetoothDevice = findPairedDevice();
    }


    private BluetoothDevice findPairedDevice() {
        Set<BluetoothDevice> pairedDevices = bluetoothAdapter.getBondedDevices();

        // Se achar aparelhos conectados, confere se ele e o desejado
        if (pairedDevices.size() > 0) {
            for (BluetoothDevice device : pairedDevices) {
                if (device.getName().equals(deviceName)) {
                    return device;
                }
            }
        }
        return null;
    }


    public void connect() {
// Cria uma nova thread para conectar ao dispositivo Bluetooth
        if (bluetoothDevice == null) {
            Log.e(TAG, "Device not found. Ensure it is paired.");
            return;
        }
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
// Cria um socket Bluetooth e conecta ao dispositivo
                    bluetoothSocket = bluetoothDevice.createRfcommSocketToServiceRecord(MY_UUID);
                    bluetoothSocket.connect();
// Abre um fluxo de entrada para ler dados do dispositivo
                    inputStream = bluetoothSocket.getInputStream();
// Escuta dados recebidos
                    readData();
                } catch (IOException e) {
// Se a conexão falhar, registra o erro e fecha a conexão
                    Log.e(TAG, "Falha na conexão: ", e);
                    closeConnection();
                }
            }
        }).start();
    }

    private void readData() {
        byte[] buffer = new byte[1024];
        int bytes;
        while (true) {
            try {
// Lê dados recebidos do fluxo de entrada
                bytes = inputStream.read(buffer);
// Converte os dados recebidos para uma String
                String data = new String(buffer, 0, bytes);
// Se um BluetoothDataListener estiver definido, chama o método onDataReceived() e passa os dados recebidos
                if (bluetoothDataListener != null) {
                    bluetoothDataListener.onDataReceived(data);
                }
            } catch (IOException e) {
// Se houver um erro na leitura dos dados, registra o erro e fecha a conexão
                Log.e(TAG, "Falha na leitura de dados: ", e);
                closeConnection();
                break;
            }
        }
    }

    public void disconnect() {
// Chama o método closeConnection() para fechar o fluxo de entrada e o socket Bluetooth
        closeConnection();
    }

    private void closeConnection() {
        try {
// Fecha o fluxo de entrada
            if (inputStream != null) {
                inputStream.close();
            }
// Fecha o socket Bluetooth
            if (bluetoothSocket != null) {
                bluetoothSocket.close();
            }
        } catch (IOException e) {
// Se houver um erro ao fechar a conexão, registra o erro
            Log.e(TAG, "Falha ao fechar a conexão: ", e);
        }
        new Thread(() -> attemptReconnect()).start();

    }
    private void attemptReconnect() {
        try {
            // espera um pouco e tenta reconectar
            Thread.sleep(RECONNECT_DELAY);
        } catch (InterruptedException e) {
            Log.e(TAG, "Reconnect delay interrupted", e);
        }

        // comeca uma nova coneccao
        connect();
    }

    public void setBluetoothDataListener(BluetoothDataListener listener) {
// Define o BluetoothDataListener para esta instância de BluetoothHandler
        this.bluetoothDataListener = listener;
    }

    public interface BluetoothDataListener {
        // Define um método que será chamado quando dados forem recebidos do dispositivo Bluetooth
        void onDataReceived(String data);
    }


}