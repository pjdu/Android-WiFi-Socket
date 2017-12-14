package com.hsi_sh.connection_test;

import android.app.Activity;
import android.content.Context;
import android.os.Bundle;
import android.text.method.ScrollingMovementMethod;
import android.util.Log;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;

import android.os.AsyncTask;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;

/**
 * Simple UI demonstrating how to open a serial communication link to a
 * remote host over WiFi, send and receive messages, and update the display.
 *
 * Author: Hayk Martirosyan
 */
public class WiFiActivity extends Activity {

    // Tag for logging
    private final String TAG = getClass().getSimpleName();

    // AsyncTask object that manages the connection in a separate thread
    WiFiSocketTask wifiTask = null;

    // UI elements
    TextView textStatus, textRX, textTX,tv_velocity,tv_depth;
    EditText editTextAddress, editTextPort, editSend;
    Button buttonConnect, buttonSend;

    float velocity;
    float depth;
    float depthVoltage;

    static final  byte [] Delivery_Conf = {(byte)0x41,(byte)0x31,(byte)0x20,(byte)0x4D,(byte)0x0D,(byte)0x0A};

    @Override
    protected void onCreate(Bundle savedInstanceState) {

        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_wi_fi);

        // Save references to UI elements
        textStatus = (TextView)findViewById(R.id.textStatus);
        textRX = (TextView)findViewById(R.id.textRX);
        textRX.setMovementMethod(ScrollingMovementMethod.getInstance());
        textTX = (TextView)findViewById(R.id.textTX);
        textTX.setMovementMethod(ScrollingMovementMethod.getInstance());
        editTextAddress = (EditText)findViewById(R.id.address);
        editTextPort = (EditText)findViewById(R.id.port);
        editSend = (EditText)findViewById(R.id.editSend);
        buttonConnect = (Button)findViewById(R.id.connect);
        buttonSend = (Button)findViewById(R.id.buttonSend);

        // Disable send button until a connection is made
        buttonSend.setEnabled(true);
    }

    /**
     * Helper function, print a status to both the UI and program log.
     */
    void setStatus(String s) {
        Log.v(TAG, s);
        textStatus.setText(s);
    }

    /**
     * Try to start a connection with the specified remote host.
     */
    public void connectButtonPressed(View v) {

        if(wifiTask != null) {
            setStatus("Already connected!");
            return;
        }

        try {
            // Get the remote host from the UI and start the thread
            String host = editTextAddress.getText().toString();
            int port = Integer.parseInt(editTextPort.getText().toString());

            // Start the asyncronous task thread
            setStatus("Attempting to connect...");
            wifiTask = new WiFiSocketTask(host, port);
            wifiTask.execute();

        } catch (Exception e) {
            e.printStackTrace();
            setStatus("Invalid address/port!");
        }

        InputMethodManager imm = (InputMethodManager)this.getSystemService(Context.INPUT_METHOD_SERVICE);
        imm.hideSoftInputFromWindow(v.getWindowToken(), 0);
    }

    /**
     * Disconnect from the connection.
     */
    public void disconnectButtonPressed(View v) {

        if(wifiTask == null) {
            setStatus("Already disconnected!");
            return;
        }

        setStatus("Disconnecting...");
        wifiTask.disconnect();

        InputMethodManager imm = (InputMethodManager)this.getSystemService(Context.INPUT_METHOD_SERVICE);
        imm.hideSoftInputFromWindow(v.getWindowToken(), 0);
    }

    /**
     * Invoked by the AsyncTask when the connection is successfully established.
     */
    private void connected() {
        setStatus("Connected.");
        buttonSend.setEnabled(true);
    }

    /**
     * Invoked by the AsyncTask when the connection ends..
     */
    private void disconnected() {
        setStatus("Disconnected.");
        buttonSend.setEnabled(true);
        textRX.setText("");
        textTX.setText("");
        wifiTask = null;
    }

    /**
     * Invoked by the AsyncTask when a newline-delimited message is received.
     */
    private void gotMessage(String msg) {
        textRX.append("\n" + msg);
        Log.v(TAG, "[RX] " + msg);
//        Log.v(TAG, "[sub(1,4)] " + msg.substring(1,4));
//        Log.v(TAG, "[sub(0,0)] " + msg.substring(0,0));

    }

    /**
     * Send the message typed in the input field using the AsyncTask.
     */
    public void sendButtonPressed(View v) {

        if(wifiTask == null) return;

        String msg = editSend.getText().toString();
        if(msg.length() == 0) return;

        wifiTask.sendMessage(msg);
//        editSend.setText("");

        textTX.append("\n" + msg);
        Log.v(TAG, "[TX] " + msg);
    }

    /**
     * AsyncTask that connects to a remote host over WiFi and reads/writes the connection
     * using a socket. The read loop of the AsyncTask happens in a separate thread, so the
     * main UI thread is not blocked. However, the AsyncTask has a way of sending data back
     * to the UI thread. Under the hood, it is using Threads and Handlers.
     */
    public class WiFiSocketTask extends AsyncTask<Void, String, Void> {

        // Location of the remote host
        String address;
        int port;

        // Special messages denoting connection status
        private static final String PING_MSG = "SOCKET_PING";
        private static final String CONNECTED_MSG = "SOCKET_CONNECTED";
        private static final String DISCONNECTED_MSG = "SOCKET_DISCONNECTED";

        Socket socket = null;
        BufferedReader inStream = null;
        OutputStream outStream = null;

        // Signal to disconnect from the socket
        private boolean disconnectSignal = false;

        // Socket timeout - close if no messages received (ms)
        private int timeout = 5000;

        // Constructor
        WiFiSocketTask(String address, int port) {
            this.address = address;
            this.port = port;
        }

        /**
         * Main method of AsyncTask, opens a socket and continuously reads from it
         */
        @Override
        protected Void doInBackground(Void... arg) {

            try {

                // Open the socket and connect to it
                socket = new Socket();
//                socket = new Socket(address,port);
                socket.connect(new InetSocketAddress(address, port), timeout);

                // Get the input and output streams
                inStream = new BufferedReader(new InputStreamReader(socket.getInputStream()));
                outStream = socket.getOutputStream();

                // Confirm that the socket opened
                if(socket.isConnected()) {
                    publishProgress(CONNECTED_MSG);
                    disconnectSignal = false;

//                    // Make sure the input stream becomes ready, or timeout
//                    long start = System.currentTimeMillis();
//                    while(!inStream.ready()) {
//                        long now = System.currentTimeMillis();
//                        if(now - start > timeout) {
//                            Log.e(TAG, "Input stream timeout, disconnecting!");
//                            disconnectSignal = true;
//                            break;
//                        }
//                    }
                } else {
                    Log.e(TAG, "Socket did not connect!");
                    disconnectSignal = true;

                    // Once disconnected, try to close the streams
                    try {
                        if (socket != null) socket.close();
                        if (inStream != null) inStream.close();
                        if (outStream != null) outStream.close();
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                    publishProgress(DISCONNECTED_MSG);
                }

                // Read messages in a loop until disconnected
                while(! disconnectSignal) {

                    // Parse a message with a newline character
                    String msg = inStream.readLine();

                    // Send it to the UI thread
                    publishProgress(msg);

                    Log.v(TAG, "[READLINE] " + msg);
                }

            } catch (Exception e) {
                e.printStackTrace();
                Log.e(TAG, "Error in socket thread!");
                Log.e("MYAPP", "exception", e);
            }



            return null;
        }

        /**
         * This function runs in the UI thread but receives data from the
         * doInBackground() function running in a separate thread when
         * publishProgress() is called.
         */
        @Override
        protected void onProgressUpdate(String... values) {

            String msg = values[0];
            if(msg == null) return;

            // Handle meta-messages
            if(msg.equals(CONNECTED_MSG)) {
                connected();
            } else if(msg.equals(DISCONNECTED_MSG)) {
                disconnected();
            } else {// Invoke the gotMessage callback for all other messages
                gotMessage(msg);
            }
            super.onProgressUpdate(values);
        }

        /**
         * Write a message to the connection. Runs in UI thread.
         */
        public void sendMessage(String data) {

            try {
                outStream.write(data.getBytes());
                outStream.write('\n');

            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        /**
         * Set a flag to disconnect from the socket.
         */
        public void disconnect() {
            disconnectSignal = true;
            try {
                if (socket != null) socket.close();
                if (inStream != null) inStream.close();
                if (outStream != null) outStream.close();
            } catch (Exception e) {
                e.printStackTrace();
            }
            publishProgress(DISCONNECTED_MSG);
        }
    }
}