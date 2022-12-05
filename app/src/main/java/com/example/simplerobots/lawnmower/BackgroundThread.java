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
                // TODO:
                sleep(500);
                isClientConnected = true;
            } else {
                if (wantNotification) {
                    Log.i(LOG_TAG, "connected");
                    wantNotification = false;
                    notify.notifyConnected();
                }
                waitForRequest();
            }
        } else {
            if (isClientConnected) {
                // TODO:
                sleep(500);
                isClientConnected = false;
            } else if (isNetworkActive) {
                isNetworkActive = false;
            } else if (isNetworkInitialized) {
                deinitNetwork();
            } else {
                if (wantNotification) {
                    Log.i(LOG_TAG, "disconnected");
                    wantNotification = false;
                    notify.notifyDisconnected();
                }
                waitForRequest();
            }
        }
    }

    public synchronized void requestConnect() {
        Log.i(LOG_TAG, "request connect");
        wantConnected = true;
        wantNotification = true;
        notify();
    }

    public synchronized void requestDisconnect() {
        Log.i(LOG_TAG, "request disconnect");
        wantConnected = false;
        wantNotification = true;
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
                connectionFailed();
                return;
            }

            NetworkRequest networkRequest = new NetworkRequest.Builder()
                    .addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
                    .removeCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                    .build();

            if (!wifiManager.enableNetwork(networkId, true)) {
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

    static final String ROBOT_WIFI_SSID = "RLM3";
    static final String ROBOT_WIFI_PASSWORD = "ABCD1234";

    Context appContext;
    BackgroundNotifyInterface notify;

    boolean stop = false;
    boolean wantConnected = false;
    boolean wantNotification = false;

    boolean isNetworkInitialized = false;
    boolean isNetworkActive = false;
    boolean isClientConnected = false;

    ConnectivityManager.NetworkCallback callback = null;
    Network network;
    int networkId = -1;

    static final String LOG_TAG = "RLM3-BACK";
}
