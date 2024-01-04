package com.example.simplerobots.lawnmower;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.NetworkRequest;
import android.net.wifi.WifiConfiguration;
import android.net.wifi.WifiManager;
import android.net.wifi.WifiNetworkSpecifier;
import android.util.Log;

import androidx.annotation.NonNull;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;

public class BackgroundThread extends Thread {

    public BackgroundThread(Context context, BackgroundNotifyInterface notify) {
        this.appContext = context.getApplicationContext();
        this.notify = notify;
    }

    @Override
    public void run() {
        Log.i(LOG_TAG, "startup");

        while (!stop) {
            try {
                step();
            } catch (InterruptedException ignored) {
            }
        }

        Log.i(LOG_TAG, "shutdown");
    }

    public void step() throws InterruptedException {
        if (wantConnected) {
            if (!isNetworkInitialized) {
                initNetwork();
            } else if (!isNetworkActive) {
                waitForRequest();
            } else if (!isClientConnected) {
                clientConnect();
            } else if (wantNotification) {
                Log.i(LOG_TAG, "connected");
                wantNotification = false;
                notify.notifyConnected();
            } else if (wantPing) {
                sendPing();
                wantPing = false;
            } else {
                waitForRequest();
            }
        } else {
            if (isClientConnected) {
                clientDisconnect();
            } else if (isNetworkActive) {
                isNetworkActive = false;
            } else if (isNetworkInitialized) {
                deinitNetwork();
            } else if (wantNotification) {
                Log.i(LOG_TAG, "disconnected");
                wantNotification = false;
                notify.notifyDisconnected();
            } else {
                waitForRequest();
            }
        }
    }

    public synchronized void requestConnect() {
        Log.i(LOG_TAG, "request connect");
        wantConnected = true;
        wantNotification = true;
        wantPing = false;
        notify();
    }

    public synchronized void requestDisconnect() {
        Log.i(LOG_TAG, "request disconnect");
        wantConnected = false;
        wantNotification = true;
        wantPing = false;
        notify();
    }

    public synchronized void requestPing() {
        Log.i(LOG_TAG, "request ping");
        wantPing = true;
        notify();
    }

    public synchronized void requestStop() {
        Log.i(LOG_TAG, "request stop");
        stop = true;
        interrupt();
    }

    private synchronized void waitForRequest() {
        try {
            wait();
        } catch (InterruptedException ignored) {
        }
    }

    private synchronized void connectionFailed() {
        Log.i(LOG_TAG, "failure");
        notify.notifyConnectFailed();
        wantConnected = false;
    }

    public void joinSafe(long millis) {
        while (true) {
            try {
                join(millis);
                return;
            } catch (InterruptedException ignored) {
            }
        }
    }

    private void initNetwork() {
        Log.i(LOG_TAG, "init network");

        ConnectivityManager connectivityManager = (ConnectivityManager) appContext.getSystemService(Context.CONNECTIVITY_SERVICE);
        WifiManager wifiManager = (WifiManager) appContext.getSystemService(Context.WIFI_SERVICE);

        // Create a callback that will forward network state information to this thread.
        callback = new ConnectivityManager.NetworkCallback() {
            @Override
            public void onAvailable(@NonNull Network network) {
                networkCallbackOnAvailable(network);
            }

            @Override
            public void onLost(@NonNull Network network) {
                networkCallbackOnLost(network);
            }

            @Override
            public void onUnavailable() {
                networkCallbackOnUnavailable();
            }
        };

        if (android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.Q) {
            WifiConfiguration wifiConfig = new WifiConfiguration();
            wifiConfig.SSID = "\"" + ROBOT_WIFI_SSID + "\"";
            wifiConfig.preSharedKey = "\"" + ROBOT_WIFI_PASSWORD + "\"";
            wifiConfig.hiddenSSID = true;

            networkId = wifiManager.addNetwork(wifiConfig);
            if (networkId == -1) {
                Log.i(LOG_TAG, "addNetwork failed");
                connectionFailed();
                return;
            }

            NetworkRequest networkRequest = new NetworkRequest.Builder()
                    .addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
                    .removeCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                    .build();

            if (!wifiManager.enableNetwork(networkId, true)) {
                Log.i(LOG_TAG, "enableNetwork failed");
                connectionFailed();
                return;
            }

            connectivityManager.requestNetwork(networkRequest, callback);

        } else {
            WifiNetworkSpecifier networkSpecifier = new WifiNetworkSpecifier.Builder()
                    .setSsid(ROBOT_WIFI_SSID)
                    .setWpa2Passphrase(ROBOT_WIFI_PASSWORD)
                    .setIsHiddenSsid(true)
                    .build();

            NetworkRequest networkRequest = new NetworkRequest.Builder()
                    .addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
                    .removeCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                    .setNetworkSpecifier(networkSpecifier)
                    .build();

            connectivityManager.requestNetwork(networkRequest, callback);

        }
        isNetworkInitialized = true;
    }

    private void deinitNetwork() {
        ConnectivityManager connectivityManager = (ConnectivityManager) appContext.getSystemService(Context.CONNECTIVITY_SERVICE);

        connectivityManager.unregisterNetworkCallback(callback);
        callback = null;

        if (android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.Q) {
            WifiManager wifiManager = (WifiManager) appContext.getSystemService(Context.WIFI_SERVICE);
            wifiManager.removeNetwork(networkId);
        }

        isNetworkInitialized = false;
    }

    private void clientConnect() {
        try {
            socket = network.getSocketFactory().createSocket(ROBOT_WIFI_SERVER, ROBOT_WIFI_PORT);
            output = socket.getOutputStream();
            input = socket.getInputStream();
            isClientConnected = true;

            // Create a client connection that will forward messages to this object.
            clientThread = new Thread() {
                public void run() {
                    try {
                        Crc8 crc = new Crc8();

                        while (length > 0) {
                            int type = input.read();
                            if (type < 0)
                                return;
                            int length = getMessageLength((byte)type);
                            if (length < 2)
                            {
                                Log.e(LOG_TAG, "Received invalid message type " + type + " length " + length);
                                clientCallbackInputMessageError();
                                return;
                            }

                            byte[] data = new byte[length - 2];
                            input.read(data);

                            crc.add(type);

                            int length = getMessageLength(type);

                            switch (type) {
                                case MESSAGE_TYPE_NONE:
                                    byte crc = (byte)input.read();
                                    break;

                                case MESSAGE_TYPE_VERSION:
                                    break;

                                case MESSAGE_TYPE_SYNC:
                                    break;

                                case MESSAGE_TYPE_INVALID:
                                default:
                                    Log.e(LOG_TAG, "Received invalid message type " + type);
                                    clientCallbackInputMessageError();
                                    break;

                            }
                        }
                    } catch (IOException e) {
                    }
                }
            };

        } catch (IOException e) {
            Log.i(LOG_TAG, "createSocket failed " + e.getLocalizedMessage());
            connectionFailed();
        }
    }

    private void clientDisconnect() {
        try {
            socket.close();
            clientThread.wait();
        } catch (IOException e) {
        } catch (InterruptedException e) {
        }
        output = null;
        socket = null;
        isClientConnected = false;
    }

    private void sendPing() {
        try {
            ByteBuffer ping = ByteBuffer.allocate();
            output.write("a\n".getBytes());
        } catch (IOException e) {
            Log.i(LOG_TAG, "sendPing failed " + e.getLocalizedMessage());
            connectionFailed();
        }
    }

    private synchronized void networkCallbackOnAvailable(Network network) {
        Log.i(LOG_TAG, "net available");
        this.network = network;
        isNetworkActive = true;
        notify();
    }

    private synchronized void networkCallbackOnLost(Network network) {
        Log.i(LOG_TAG, "net lost");
        isNetworkActive = false;
        connectionFailed();
        notify();
    }

    private synchronized void networkCallbackOnUnavailable() {
        Log.i(LOG_TAG, "net unavailable");
        isNetworkActive = false;
        connectionFailed();
        notify();
    }

    private synchronized void clientCallbackInputMessageError() {
        Log.i(LOG_TAG, "client error");
        isNetworkActive = false;
        connectionFailed();
        notify();
    }

    private static int getMessageLength(byte type) {
        switch (type) {
            case MESSAGE_TYPE_NONE:
                return 2;
            case MESSAGE_TYPE_VERSION:
                return 10;
            case MESSAGE_TYPE_SYNC:
                return 6;
            case MESSAGE_TYPE_INVALID:
            default:
                return 0;
        }
    }

    static final String ROBOT_WIFI_SSID = "RLM3";
    static final String ROBOT_WIFI_PASSWORD = "ABCD1234";
    static final String ROBOT_WIFI_SERVER = "10.168.154.1";
    static final int ROBOT_WIFI_PORT = 37649;

    Context appContext;
    BackgroundNotifyInterface notify;

    boolean stop = false;
    boolean wantConnected = false;
    boolean wantNotification = false;
    boolean wantPing = false;

    boolean isNetworkInitialized = false;
    boolean isNetworkActive = false;
    boolean isClientConnected = false;

    ConnectivityManager.NetworkCallback callback = null;
    Network network;
    Socket socket;
    OutputStream output;
    InputStream input;
    int networkId = -1;
    Thread clientThread;

    static final String LOG_TAG = "RLM3-BACK";

    static final byte MESSAGE_TYPE_INVALID  = 0;
    static final byte MESSAGE_TYPE_NONE		= 1;
    static final byte MESSAGE_TYPE_VERSION	= 2;
    static final byte MESSAGE_TYPE_SYNC		= 3;
    static final byte MESSAGE_TYPE_CONTROL	= 4;
}
