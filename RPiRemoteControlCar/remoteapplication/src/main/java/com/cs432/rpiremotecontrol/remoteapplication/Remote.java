package com.cs432.rpiremotecontrol.remoteapplication;

import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.graphics.Color;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.ToggleButton;


public class Remote extends Activity implements SensorEventListener{
    //Turn off debugging later
    private static final boolean D = true;
    private static final String TAG = "Remote";

    private SensorManager mSensorManager;
    private Sensor mAccelerometer;
    private long lastUpdateTime = 0;
    private static final int INTERVAL_RATE = 500;
    private int instruction = 0;
    private boolean rightSigOn = false;
    private boolean leftSigOn = false;
    private boolean accelStarted = false;

    private BluetoothAdapter bluetoothAdapter = null;
    private BluetoothRemoteService remoteService = null;

    // Intent request codes
    private static final int REQUEST_CONNECT_DEVICE = 1;
    private static final int REQUEST_ENABLE_BT = 2;

    //Bluetooth States
    public static final int MESSAGE_STATE_CHANGE = 1;
    public static final int MESSAGE_WRITE = 2;
    public static final int MESSAGE_READ = 3;
    public static final int MESSAGE_DEVICE_NAME = 4;
    public static final int MESSAGE_TOAST = 5;

    public static final String DEVICE_NAME = "device_name";
    public static final String TOAST = "toast";

    private String connectedDevice = null;

    protected void onResume() {
        super.onResume();
        mSensorManager.registerListener(this, mAccelerometer, SensorManager.SENSOR_DELAY_NORMAL);
        
        if (D) Log.d(TAG, "onResume() ");

        if (remoteService != null) {
            if (remoteService.getState() == BluetoothRemoteService.STATE_NONE) {
                remoteService.start();
            }
        }
    }

    protected void onPause() {
        super.onPause();
        mSensorManager.unregisterListener(this);
    }

    public void onAccuracyChanged(Sensor sensor, int accuracy) {}

    public void onSensorChanged(SensorEvent event) {
        if (accelStarted){                                         // checking for this causes data to not show up right away????
            long curTime = System.currentTimeMillis();

            if ((curTime - lastUpdateTime) > INTERVAL_RATE) {
                lastUpdateTime = curTime;

                float x = event.values[0]; //value of how much the phone is leaned forward or back
                float y = event.values[1]; //value of how much the phone is leaned left or right

                //these if statements determine which way to move
                if (D) Log.d(TAG, "In onSensorChanged().");
                if(Math.abs(x) > 3.4 || Math.abs(y) > 3.4) { //indicates how much you have to tilt for movement
                    if (Math.abs(x) > Math.abs(y)) { //check to see which way it is tilted more
                        //move forward or back
                        if( x < 0) { //if x is negative
                            goForward(findViewById(R.id.xyTxt));
                        } else {
                            goBackward(findViewById(R.id.xyTxt));
                        }
                    } else {
                        //move left or right
                        if( y < 0) { //if y is negative
                            goLeft(findViewById(R.id.xyTxt));
                        } else {
                            goRight(findViewById(R.id.xyTxt));
                        }
                    }
                } else { //if it is not tilted enough, stop the car
                    resetDirection();
                }
                TextView t = (TextView) findViewById(R.id.xyTxt);
                t.setText("X: " + (int) x + "\nY: " + (int) y); //display the values on the screen
            }
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_remote);

        mSensorManager = (SensorManager)getSystemService(SENSOR_SERVICE);
        mAccelerometer = mSensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);

        getWindow().addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON); //force the app to stop the screen from shutting off
        setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE); //force the app to display in landscape
        getWindow().getDecorView().setBackgroundColor(Color.DKGRAY);

        setConnectionStatus(getString(R.string.bt_not_connected));

        bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();

        if (bluetoothAdapter == null) {
            //Bluetooth not supported
            Toast.makeText(this, getString(R.string.bt_not_supported), Toast.LENGTH_LONG).show();
            finish();
        }
    }

    @Override
    protected void onStart() {
        super.onStart();

        //turn off directional pad
        (findViewById(R.id.leftBtn)).setEnabled(false);
        (findViewById(R.id.rightBtn)).setEnabled(false);
        (findViewById(R.id.downBtn)).setEnabled(false);
        (findViewById(R.id.upBtn)).setEnabled(false);

        if (!bluetoothAdapter.isEnabled()) {
            Intent enableBluetooth = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
            startActivityForResult(enableBluetooth, REQUEST_ENABLE_BT); //turn on bluetooth
        }
        else {
            if (remoteService == null) {
                remoteService = new BluetoothRemoteService(this, handler);
            }
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.remote, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.

        int id = item.getItemId();
        if (id == R.id.action_settings) {
            return true;
        }
        else if (id == R.id.action_exit_remote) {
            sendInstruction("-1");
            remoteService.Disconnect();
            finish();
        }
        else if (id == R.id.action_connect) {
            //Launce the DeviceListActivity to choose a device
            Intent serverIntent = new Intent(this, DeviceListActivity.class);
            startActivityForResult(serverIntent, REQUEST_CONNECT_DEVICE);
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if(D) Log.d(TAG, "onActivityResult " + resultCode);
        switch (requestCode) {
            case REQUEST_ENABLE_BT:
                // When the request to enable Bluetooth returns
                if (resultCode == Activity.RESULT_OK) {
                    // Bluetooth is now enabled, so set up a chat session
                    if(D) Log.d(TAG, "Setting up remote.");
                } else {
                    // User did not enable Bluetooth or an error occurred
                    Log.d(TAG, "BT not enabled");
                    Toast.makeText(this, R.string.bt_was_not_enabled, Toast.LENGTH_SHORT).show();
                    finish();
                }
                break;
            case REQUEST_CONNECT_DEVICE:
                //When DeviceListActivity returns with a device to connect
                if (resultCode == Activity.RESULT_OK) {
                    connectDevice(data);
                }
        }
    }

    private  void connectDevice(Intent data) {
        //Get the device MAC address
        String address = data.getExtras().getString(DeviceListActivity.EXTRA_DEVICE_ADDRESS);

        //Get the BluetoothDevice object
        BluetoothDevice device = bluetoothAdapter.getRemoteDevice(address);

        //Attempt to connect to the device
        remoteService.connect(device);
    }

    private void sendInstruction(String instruction) {
        //Check if we are connected
        if (remoteService.getState() != BluetoothRemoteService.STATE_CONNECTED) {
            Toast.makeText(this, "You are not connected to a car.", Toast.LENGTH_SHORT).show();
            return;
        }

        //Verify there is actually an instruction
        if (instruction.length() > 0) {
            //Convert the message to bytes so it can be sent as a stream socket
            byte[] output = instruction.getBytes();

            //Send to the Remote Service
            remoteService.write(output);
        }
    }

    private final Handler handler = new Handler() {
        @Override
        public void handleMessage(Message msg) {
            switch (msg.what) {
                case MESSAGE_STATE_CHANGE:
                    if(D) Log.i(TAG, "MESSAGE_STATE_CHANGE: " + msg.arg1);
                    switch (msg.arg1) {
                        //Connection state change
                        case BluetoothRemoteService.STATE_CONNECTED:
                            setConnectionStatus(getString(R.string.bt_connected_to_device, connectedDevice));
                            break;
                        case BluetoothRemoteService.STATE_CONNECTING:
                            setConnectionStatus(getString(R.string.bt_connecting));
                            break;
                        case BluetoothRemoteService.STATE_LISTEN:
                        case BluetoothRemoteService.STATE_NONE:
                            setConnectionStatus(getString(R.string.bt_not_connected));
                            break;
                    }
                    break;
                case MESSAGE_WRITE:
                    //Writing out a Bluetooth message
                    byte[] writeBuffer = (byte[]) msg.obj;
                    if (D) Log.d(TAG, "Message write: " + new String(writeBuffer));
                    //TODO:Finish the write.
                    //Actually I think this part goes away
                    break;
                case MESSAGE_READ:
                    //Receiving a message from Bluetooth
                    byte[] readBuffer = (byte[]) msg.obj;
                    String readMessage = new String(readBuffer, 0, msg.arg1);
                    if (D) Log.d(TAG, "Message read: " + readMessage);
                    //TODO:Send reads to function for processing.
                    break;
                case MESSAGE_DEVICE_NAME:
                    //Lookup the device on by the key and store for later use
                    connectedDevice = msg.getData().getString(DEVICE_NAME);
                    if(D) Log.d(TAG, "Connected to " + connectedDevice);
                    break;
                case MESSAGE_TOAST:
                    Toast.makeText(getApplicationContext(), msg.getData().getString(TOAST), Toast.LENGTH_SHORT).show();
                    break;
            }
        }
    };

    private void setConnectionStatus(String status) {
        //Display the current connection status in the TextView.
        TextView t = (TextView) findViewById(R.id.directionTxt);
        t.setText(status);
    }

    public void goLeft(View view) {
        instruction = 4;
        move();
        sendInstruction("4"); //go left
    }

    public void goRight(View view){
        instruction = 6;
        move();
        sendInstruction("6"); //go right
    }

    public void goForward(View view){
        instruction = 8;
        move();
        sendInstruction("8"); //go forward
    }

    public void goBackward(View view){
        instruction = 2;
        move();
        sendInstruction("2"); //go backward
    }

    public void move(){
        TextView t = (TextView) findViewById(R.id.directionTxt);
        t.setText("Direction: " + instruction); //indicate a move
    }

    private void resetDirection() { //reset the instruction
        Handler mHandler = new Handler();

        mHandler.postDelayed(new Runnable() {
            public void run() {
                instruction = 5;
                move();
                sendInstruction("5"); //brake lights here, stop the car
            }
        }, 1000);
    }

    public void rightSig(View view) throws InterruptedException {
        if (rightSigOn) {
            instruction = 9;
            //send signal to turn OFF right blinker
            findViewById(R.id.rightSigBtn).setBackgroundColor(Color.TRANSPARENT);
            rightSigOn = false;
        } else {
            //send signal to turn ON right blinker
            instruction = 9;
            findViewById(R.id.rightSigBtn).setBackgroundColor(Color.GREEN);
            rightSigOn = true;
        }
        sendInstruction("9");
    }

    public void leftSig(View view) throws InterruptedException {
        if (leftSigOn) {
            instruction = 7;
            //send signal to turn OFF left blinker
            findViewById(R.id.leftSigBtn).setBackgroundColor(Color.TRANSPARENT);
            leftSigOn = false;
        }
        else {
            instruction = 7;
            //send signal to turn ON left blinker
            findViewById(R.id.leftSigBtn).setBackgroundColor(Color.GREEN);
            leftSigOn = true;
        }
        sendInstruction("7");
    }

    public void toggleGlow(View view){
        if (((ToggleButton)view).isChecked()){
            instruction = 0;
            //send signal to turn ON underglow
            findViewById(R.id.glowBtn).setBackgroundColor(Color.BLUE);
        }
        else {
            instruction = 0;
            //send signal to turn OFF underglow
            (findViewById(R.id.glowBtn)).setBackgroundColor(Color.TRANSPARENT);
        }
        sendInstruction("0");
    }

    public void toggleHeadLights(View view){
        if (((ToggleButton)view).isChecked()){
            instruction = 1;
            //send signal to turn ON headlights
            findViewById(R.id.hLightsBtn).setBackgroundColor(Color.YELLOW);
        }
        else {
            instruction = 1;
            //send signal to turn OFF underglow
            (findViewById(R.id.hLightsBtn)).setBackgroundColor(Color.TRANSPARENT);
        }
        sendInstruction("1");
    }

    public void startStop(View view) throws InterruptedException {
        if (!accelStarted) {  //if it is not already running, start it
            findViewById(R.id.startStopBtn).setBackgroundColor(Color.RED);
            Button b = (Button) findViewById(R.id.startStopBtn);
            b.setText("Stop");
            findViewById(R.id.xyTxt).setVisibility(View.VISIBLE);
            // enable driving here
            (findViewById(R.id.leftBtn)).setEnabled(true);
            (findViewById(R.id.rightBtn)).setEnabled(true);
            (findViewById(R.id.downBtn)).setEnabled(true);
            (findViewById(R.id.upBtn)).setEnabled(true);
            accelStarted = true;
            Toast.makeText(this, "Accelerometer Started!", Toast.LENGTH_SHORT).show(); //might cause accel data delay?
        } else { //if it is running, stop it
            findViewById(R.id.startStopBtn).setBackgroundColor(Color.GREEN);
            findViewById(R.id.xyTxt).setVisibility(View.INVISIBLE);
            accelStarted = false;
            Button b = (Button) findViewById(R.id.startStopBtn);
            b.setText("Start");
            //disable buttons here
            (findViewById(R.id.leftBtn)).setEnabled(false);
            (findViewById(R.id.rightBtn)).setEnabled(false);
            (findViewById(R.id.downBtn)).setEnabled(false);
            (findViewById(R.id.upBtn)).setEnabled(false);
            Toast.makeText(this, "Accelerometer Stopped!", Toast.LENGTH_SHORT).show();
        }
    }
}
