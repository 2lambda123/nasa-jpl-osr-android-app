package ejunkins.rovercontroller;

import android.app.AlertDialog;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Vibrator;
import android.support.annotation.RequiresApi;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.Spinner;
import android.widget.ToggleButton;

import java.io.IOException;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

import ejunkins.rovercontroller.utils.AlertDialogHelper;

import static java.lang.Boolean.FALSE;
import static java.lang.Boolean.TRUE;

//import com.cardiomood.android.controls.gauge.SpeedometerGauge;

public class MainActivity extends AppCompatActivity
        implements AdapterView.OnItemSelectedListener, ControlStick.JoystickListener,
        RightControlStick.JoystickListener, View.OnClickListener {

    private static final String TAG = "MainActivity";

    private Vibrator mVibrator; // For Haptic Feedback

    StringBuffer mThrottle;
    StringBuffer mSteering;
    StringBuffer mRoverFace;
    StringBuffer mServo;
    StringBuffer mExit;
    StringBuffer mConnectedStatus;
    CheckBox mConnected;

    BluetoothSocket mSocket;
    BluetoothDevice mBluetoothDevice = null;
    Button mSocketButton;
    Button mDisconnectButton;
    String mConnectionName;
    ToggleButton mToggleButton;
    Handler mConnectionStatusHandler;
    SocketRunnable socketrunnable;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN,
                WindowManager.LayoutParams.FLAG_FULLSCREEN);
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        setContentView(R.layout.activity_main);

        // FIXME: ControlStick should use a setter for the listener instead of using Context
        ControlStick controlStick = new ControlStick(this);
        RightControlStick rightControlStick = new RightControlStick(this);

        mThrottle = new StringBuffer();
        mSteering = new StringBuffer();
        mRoverFace = new StringBuffer();
        mServo = new StringBuffer();
        mExit = new StringBuffer();
        mConnectedStatus = new StringBuffer();
        mConnectionStatusHandler = new Handler();

        mThrottle.append(0);
        mSteering.append(0);
        mRoverFace.append(0);
        mConnectedStatus.append("50");
        mServo.append(0);
        mExit.append("False");
        mConnectionStatusHandler = new Handler();
        mConnected = (CheckBox)findViewById(R.id.checkBox);
        mSocketButton = (Button)findViewById(R.id.socketConnect);
        mDisconnectButton = (Button)findViewById(R.id.disconnect);
        mToggleButton = (ToggleButton)findViewById(R.id.toggleButton);

        mVibrator = (Vibrator)this.getSystemService(VIBRATOR_SERVICE); //For Haptic Feedback
        mSocketButton.setOnClickListener(this);
        mDisconnectButton.setOnClickListener(this);

        socketrunnable =
                new SocketRunnable(mThrottle, mSteering, mRoverFace, mServo, mSocket, mExit,
                        mConnectedStatus);

        Spinner dropdown = (Spinner)findViewById(R.id.spinner1);
        String[] items = new String[] {"None", "8Bit", "Wink", "Happy"};
        ArrayAdapter<String> adapter =
                new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, items);
        dropdown.setAdapter(adapter);
        dropdown.setOnItemSelectedListener(this);

        /*
        Spinner connection = (Spinner)findViewById(R.id.connectionSpinner);
        String[] items2 = new String[]{"raspberrypi","demoRover"};
        ArrayAdapter<String> adapter2 = new ArrayAdapter<String>(this, android.R.layout.simple_spinner_dropdown_item, items2);
        connection.setAdapter(adapter2);
        connection.setOnItemSelectedListener(this);
        */
    }

    public void onItemSelected(AdapterView<?> parent, View view, int pos, long id) {

        Spinner spinner = (Spinner)parent;
        if (spinner.getId() == R.id.spinner1) {
            switch (pos) {
                case 0:
                    mRoverFace.delete(0, mRoverFace.length());
                    mRoverFace.append(0);
                    break;
                case 1:
                    mRoverFace.delete(0, mRoverFace.length());
                    mRoverFace.append(1);
                    break;
                case 2:
                    mRoverFace.delete(0, mRoverFace.length());
                    mRoverFace.append(2);
                    break;
                case 3:
                    mRoverFace.delete(0, mRoverFace.length());
                    mRoverFace.append(3);
            }
        } else {
            /*
            switch (pos) {
                case 0:
                    mConnectionName = "raspberrypi";
                    break;
                case 1:
                    mConnectionName = "demoRover";
            }
            */
        }
    }

    public void onNothingSelected(AdapterView<?> parent) {
        // Another interface callback
    }

    @Override
    public void onJoystickMoved(float xPercent, float yPercent, int source) {
        switch (source) {
            case R.id.JoystickRight:
                mSteering.delete(0, mSteering.length());
                mSteering.append((int)(xPercent * 100));
                mVibrator.vibrate((int)Math.abs((100 * xPercent) / 3));
                break;

            case R.id.JoystickLeft:
                mThrottle.delete(0, mThrottle.length());
                if (!mToggleButton.isChecked()) {
                    yPercent = yPercent / 2;
                    mToggleButton.setBackgroundResource(R.drawable.button_bg_round_red);
                } else {
                    mToggleButton.setBackgroundResource(R.drawable.button_bg_round);
                }
                mThrottle.append((int)(yPercent * -100));
                mVibrator.vibrate((int)Math.abs((100 * yPercent) / 3));
                break;
        }
    }

    @RequiresApi(api = Build.VERSION_CODES.KITKAT)
    @Override
    public void onClick(View v) {
        if (connectRover()) {
            switch (v.getId()) {
                case R.id.socketConnect:
                    subscribeToRover();
                    break;
                case R.id.disconnect:
                    unsubscribeFromRover();
                    break;
            }
        }
    }

    @Override
    public void onPause() {
        super.onPause();
        unsubscribeFromRover();
    }

    @Override
    public void onStart() {
        super.onStart();
        mConnectionStatusHandler.postDelayed(new Runnable() {
            public void run() {
                Log.d(TAG, "ConnectionStatus = [" + mConnectedStatus.toString() + "]");
                switch (mConnectedStatus.toString()) {
                    case "49":
                        mConnected.setChecked(TRUE);
                        mDisconnectButton.setBackgroundResource(R.drawable.disconnect_button_red);
                        mSocketButton.setBackgroundResource(R.drawable.rounded_button_grey);
                        break;
                    case "48":
                        unsubscribeFromRover();
                        mConnected.setChecked(FALSE);
                        mSocketButton.setBackgroundResource(R.drawable.rounded_button);
                        mDisconnectButton.setBackgroundResource(R.drawable.rounded_button_grey);
                        break;
                    default:
                        mConnected.setChecked(FALSE);
                        mSocketButton.setBackgroundResource(R.drawable.rounded_button);
                        mDisconnectButton.setBackgroundResource(R.drawable.rounded_button_grey);
                        break;
                }
                mConnectionStatusHandler.postDelayed(this, 1000);
            }
        }, 1000);
    }

    /**
     * Attempts to create and open a bluetooth socket with the Rover
     * <p>
     * Should this operation fail an appropriate alert dialog will be displayed to the user.
     *
     * @return {@code true} if the socket is successfully create, {@code false} otherwise.
     */
    private boolean connectRover() {
        mConnectionName = "demoRover";
        final BluetoothAdapter bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();

        if (bluetoothAdapter != null) {
            Set<BluetoothDevice> pairedDevices = bluetoothAdapter.getBondedDevices();
            if (!pairedDevices.isEmpty()) {
                for (BluetoothDevice device : pairedDevices) {
                    if (Objects.equals(device.getName(), mConnectionName)) {
                        mBluetoothDevice = device;
                    }
                }

                if (mBluetoothDevice != null) {
                    BluetoothSocket tmpSocket;
                    String uuid = "94f39d29-7d6d-437d-973b-fba39e49d4ee";
                    try {
                        tmpSocket = mBluetoothDevice
                                .createRfcommSocketToServiceRecord(UUID.fromString(uuid));

                        mSocket = tmpSocket;
                        bluetoothAdapter.cancelDiscovery();
                        mSocket.connect();

                        return true;
                    } catch (IOException e) {
                        Log.e(TAG, "onClick: Failed to establish socket.", e);
                        showConnectionError(R.string.alert_message_bluetooth_connection_error);
                    }
                } else {
                    showConnectionError(R.string.alert_message_bluetooth_device_not_found);
                }
            } else {
                showConnectionError(R.string.alert_message_bluetooth_device_not_found);
            }
        } else {
            showConnectionError(R.string.alert_message_bluetooth_not_supported);
        }
        return false;
    }

    private void subscribeToRover() {
        mExit.delete(0, mExit.length());
        mExit.append("False");
        SocketRunnable socketrunnable =
                new SocketRunnable(mThrottle, mSteering, mRoverFace, mServo, mSocket, mExit,
                        mConnectedStatus);
        new Thread(socketrunnable).start();
    }

    private void unsubscribeFromRover() {
        mExit.delete(0, mExit.length());
        mExit.append("True");
    }

    private AlertDialog showConnectionError(int messageId) {
        return AlertDialogHelper.showConnectionErrorDialog(this, messageId);
    }
}
