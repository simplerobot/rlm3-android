package com.example.simplerobots.lawnmower;

import androidx.appcompat.app.AppCompatActivity;

import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.ProgressBar;

public class MainActivity extends AppCompatActivity implements BackgroundNotifyInterface {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_main);
        uiSetStateIdle();

        if (connectThread == null) {
            connectThread = new BackgroundThread(this, this);
            connectThread.start();
        }
    }
    @Override
    public void onStop() {
        if (connectThread != null) {
            connectThread.requestStop();
            connectThread.joinSafe(1000);
            connectThread = null;
        }

        super.onStop();
    }

    public void onConnectButtonClick(View view) {
        uiSetStateConnecting();
        connectThread.requestConnect();
    }

    public void onStopConnectButtonClick(View view) {
        uiSetStateDisconnecting();
        connectThread.requestDisconnect();
    }

    public void onPingButtonClick(View view) {
        connectThread.requestPing();
    }

    private void uiSetRawState(boolean showConnectButton, boolean showStopButton, boolean showSpinner, boolean showPingButton) {
        Button connectButton = (Button) findViewById(R.id.connectButton);
        connectButton.setVisibility(showConnectButton ? View.VISIBLE : View.INVISIBLE);
        Button stopButton = (Button) findViewById(R.id.stopConnectButton);
        stopButton.setVisibility(showStopButton ? View.VISIBLE : View.INVISIBLE);
        ProgressBar spinner = (ProgressBar) findViewById(R.id.progressBar);
        spinner.setVisibility(showSpinner ? View.VISIBLE : View.INVISIBLE);
        Button pingButton = (Button) findViewById(R.id.pingButton);
        pingButton.setVisibility(showPingButton ? View.VISIBLE : View.INVISIBLE);
    }

    private void uiSetStateIdle() {
        uiSetRawState(true, false, false, false);
    }

    private void uiSetStateConnecting() {
        uiSetRawState(false, true, true, false);
    }

    private void uiSetStateConnected() {
        uiSetRawState(false, true, false, true);
    }

    private void uiSetStateDisconnecting() {
        uiSetRawState(false, false, true, false);
    }

    @Override
    public void notifyConnected() {
        runOnUiThread(this::uiSetStateConnected);
    }

    @Override
    public void notifyDisconnected() {
        runOnUiThread(this::uiSetStateIdle);
    }

    @Override
    public void notifyConnectFailed() {
        runOnUiThread(this::uiSetStateIdle);
    }

    BackgroundThread connectThread = null;
}