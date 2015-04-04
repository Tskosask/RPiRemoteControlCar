package com.cs432.rpiremotecontrol.remoteapplication;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothServerSocket;
import android.bluetooth.BluetoothSocket;
import android.content.Context;
import android.os.Bundle;
import android.os.Message;
import android.util.Log;
import android.os.Handler;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.UUID;

/**
 * Created by brad on 10/13/14.
 */
public class BluetoothRemoteService {
    // Debugging
    private static final String TAG = "BluetoothRemoteService";
    private static final boolean D = true;

    // Name for the SDP record when creating server socket
    private static final String CONNECTION_NAME = "BluetoothRPiRemote";

    //UUID for the project
    private UUID BluetoothUUID = UUID.fromString("8dc4b250-2483-4e66-b10c-1b8a60de64e2");

    //Threads to handle connection
    private AcceptThread acceptThread;
    private ConnectThread connectThread;
    private ConnectedThread connectedThread;

    // Constants that indicate the current connection state
    public static final int STATE_NONE = 0;       // we're doing nothing
    public static final int STATE_LISTEN = 1;     // now listening for incoming connections
    public static final int STATE_CONNECTING = 2; // now initiating an outgoing connection
    public static final int STATE_CONNECTED = 3;  // now connected to a remote device

    private int state;

    private final BluetoothAdapter bluetoothAdapter;
    private final Handler handler;

    //TODO:Test whether or not we need the context
    public BluetoothRemoteService(Context context, Handler handler) {
        this.handler = handler;
        bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        state = STATE_NONE;
    }

    private synchronized void setState(int state) {
        if (D) Log.d(TAG, "Setting state: " + this.state + " to " + state );

        this.state = state;

        // Give the new state to the Handler so the UI Activity can update
        handler.obtainMessage(Remote.MESSAGE_STATE_CHANGE, state, -1).sendToTarget();
    }

    public synchronized int getState() {
        return state;
    }

    public synchronized void start() {

        //TODO: Eliminate the AcceptThread
        if (D) Log.d(TAG, "start remote service");

        //Cancel any thread attempting to make a connection
        if (connectThread != null) {
            connectThread.cancel();
            connectThread = null;
        }

        //Cancel any thread currently running a connection
        if (connectedThread != null) {
            connectedThread.cancel();
            connectedThread = null;
        }

        setState(STATE_LISTEN);

        //Start the thread to listen on a BluetoothServerSocket
        if (acceptThread == null) {
            acceptThread = new AcceptThread();
            acceptThread.start();
        }
    }

    public synchronized void connected(BluetoothSocket socket, BluetoothDevice device) {
        if (D) Log.d(TAG, "connected");

        //cancel the thread that completed the connection
        if (connectThread != null) {
            connectThread.cancel();
            connectThread = null;
        }

        //Cancel the thread  currently running a connection
        if (connectedThread != null) {
            connectedThread.cancel();
            connectedThread = null;
        }

        //cancel the accept thread because we only want to connect to on car
        if (acceptThread != null) {
            acceptThread.cancel();
            acceptThread = null;
        }

        //Start the thread to manage the connection
        connectedThread = new ConnectedThread(socket);
        connectedThread.start();

        //Send the name of the connected device back to the UI Activity
        Message msg = handler.obtainMessage(Remote.MESSAGE_DEVICE_NAME);
        Bundle bundle = new Bundle();
        bundle.putString(Remote.DEVICE_NAME, device.getName());
        msg.setData(bundle);
        handler.sendMessage(msg);

        setState(STATE_CONNECTED);
    }

    public synchronized void connect(BluetoothDevice device) {
        if (D) Log.d(TAG, "connect to: " + device);

        //TODO: Refactor the thread cancelling in the various functions
        //Consolidate them into one function, if possible

        //Cancel any thread attempting to make a connection
        if (state == STATE_CONNECTING) {
            if (connectThread != null) {
                connectThread.cancel();
                connectThread = null;
            }
        }

        //Cancel any thread currently running a connection
        if (connectedThread != null) {
            connectedThread.cancel();
            connectedThread = null;
        }

        connectThread = new ConnectThread(device);
        connectThread.start();
        setState(STATE_CONNECTING);
    }

    public void write(byte[] output) {
        //Create a temporary object
        ConnectedThread connection;

        //Synchronize a copy of the ConnectedThread
        synchronized (this) {
            if (state != STATE_CONNECTED) {
                return;
            }

            connection = connectedThread;
        }

        connection.write(output);
    }

    public synchronized void Disconnect() {
        //Cancel any connection thread
        if (state == STATE_CONNECTING) {
            if (connectThread != null) {
                connectThread.cancel();
                connectThread = null;
            }
        }

        //Cancel any thread currently running a connection
        if (connectedThread != null) {
            connectedThread.cancel();
            connectedThread = null;
        }

        //Cancel any thread waiting for connections
        if (acceptThread != null) {
            acceptThread.cancel();
            acceptThread = null;
        }
    }

    private void connectionFailed() {
        //Send a failure message back to the Activity
        Message msg = handler.obtainMessage(Remote.MESSAGE_TOAST);
        Bundle bundle = new Bundle();
        bundle.putString(Remote.TOAST, "Unable to connect device");
        msg.setData(bundle);
        handler.sendMessage(msg);

        //Start the service over to restart
        BluetoothRemoteService.this.start();
    }

    private void connectionLost() {
        Message msg = handler.obtainMessage(Remote.MESSAGE_TOAST);
        Bundle bundle = new Bundle();
        bundle.putString(Remote.TOAST, "Device connection was lost");
        msg.setData(bundle);
        handler.sendMessage(msg);

        //Start the service ove to restart listening mode
        BluetoothRemoteService.this.start();
    }

    private class AcceptThread extends Thread {
        private final BluetoothServerSocket serverSocket;

        public AcceptThread() {
            BluetoothServerSocket tmp = null;

            //Create the listening socket
            try {
                tmp = bluetoothAdapter.listenUsingRfcommWithServiceRecord(CONNECTION_NAME, BluetoothUUID);
            }
            catch (IOException e) {
                Log.e(TAG, "Listen() failed on server socket", e);
            }

            serverSocket = tmp;
        }

        public void run() {
            if (D) Log.d(TAG, "Beginning acceptThread." + this);

            //Thread name
            setName("AcceptThread");

            BluetoothSocket socket;

            //Listen to the server socket if we're not connected
            while (state != STATE_CONNECTED) {
                try {
                    //This is a blocking call and will only return on a
                    //successful connection or an exception
                    socket = serverSocket.accept();
                }
                catch (IOException e) {
                    Log.e(TAG, "accept() failed", e);
                    break;
                }

                //If a connection was accepted
                if (socket != null) {
                    synchronized (BluetoothRemoteService.this) {
                        switch (state) {
                            case STATE_LISTEN:
                            case STATE_CONNECTING:
                                //Start the connected thread
                                connected(socket, socket.getRemoteDevice());
                                break;
                            case STATE_NONE:
                            case STATE_CONNECTED:
                                //Either not ready or already connected. Terminate new socket.
                                try {
                                    socket.close();
                                }
                                catch (IOException e) {
                                    Log.e(TAG, "Could not close unwanted socket", e);
                                }
                                break;
                        }
                    }
                }
            }
            if (D) Log.i(TAG, "END acceptThread.");
        }

        public void cancel() {
            if (D) Log.d(TAG, "acceptThread cancel " + this);
            try {
                serverSocket.close();
            }
            catch (IOException e) {
                Log.e(TAG, "close() of the server socket failed ", e);
            }
        }
    }

    private class ConnectThread extends Thread {
        private final BluetoothSocket socket;
        private final BluetoothDevice device;

        private ConnectThread(BluetoothDevice device) {
            this.device = device;
            BluetoothSocket tmp = null;

            //Get a BluetoothSocket for a connection with the given device
            try {
                tmp = device.createRfcommSocketToServiceRecord(BluetoothUUID);
            }
            catch (IOException e) {
                Log.e(TAG, "Socket create() failed", e);
            }
            socket = tmp;
        }

        public void run() {
            Log.i(TAG, "BEGIN connectThread");

            //Always cancel discovery because it will slow down a connection
            bluetoothAdapter.cancelDiscovery();

            //Make a connection to the BluetoothSocket
            try {
                //This is a blocking call and will only return on
                //a successful connection or an exception
                socket.connect();
            }
            catch (IOException e) {
                Log.e(TAG, "Unable to connect socket.", e);
                try {
                    socket.close();
                }
                catch (IOException e2) {
                    Log.e(TAG, "Unable to close() socket during connection failure", e2);
                }
                connectionFailed();
                return;
            }

            synchronized (BluetoothRemoteService.this) {
                connectThread = null;
            }

            //Start the connected thread
            connected(socket, device);
        }

        public void cancel() {
            try {
                socket.close();
            }
            catch (IOException e) {
                Log.e(TAG, "close() of connect socket failed", e);
            }
        }
    }

    private class ConnectedThread extends Thread {
        private final BluetoothSocket socket;
        private final InputStream inStream;
        private final OutputStream outStream;

        public ConnectedThread(BluetoothSocket socket) {
            Log.d(TAG, "create ConnectedThread");
            this.socket = socket;
            InputStream tmpIn = null;
            OutputStream tmpOut = null;

            //Get the BluetoothSocket input and output streams
            try {
                tmpIn = socket.getInputStream();
                tmpOut = socket.getOutputStream();
            }
            catch (IOException e) {
                Log.e(TAG, "temp sockets not created", e);
            }

            inStream = tmpIn;
            outStream = tmpOut;
        }

        public void run() {
            Log.i(TAG, "BEGIN connectedThread");
            byte[] buffer = new byte[1024];
            int bytes;

            //Keep listening to the InputStream while connected
            while (true) {
                try {
                    //Read from the InputStream
                    bytes = inStream.read(buffer);

                    //Send the obtained bytes to the UI Activity
                    handler.obtainMessage(Remote.MESSAGE_READ, bytes, -1, buffer).sendToTarget();
                } catch (IOException e) {
                    Log.e(TAG, "disconnected", e);
                    connectionLost();
                    //STart the service over to restart listening mode
                    BluetoothRemoteService.this.start();
                    break;
                }
            }
        }

        public void write(byte[] buffer) {
            try {
                outStream.write(buffer);

                //Share the sent message back to the UI Activity
                handler.obtainMessage(Remote.MESSAGE_WRITE, -1, -1, buffer).sendToTarget();
            }
            catch (IOException e) {
                Log.e(TAG, "Exception during write", e);
            }
        }

        public void cancel() {
            try {
               socket.close();
            }
            catch (IOException e) {
                Log.e(TAG, "Exception during write", e);
            }
        }
    }
}