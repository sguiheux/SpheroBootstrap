package fr.sgu.sphero;

import android.Manifest;
import android.app.AlertDialog;
import android.app.FragmentTransaction;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.v4.app.ActivityCompat;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;
import android.widget.Button;

import com.orbotix.ConvenienceRobot;
import com.orbotix.Ollie;
import com.orbotix.Sphero;
import com.orbotix.async.CollisionDetectedAsyncData;
import com.orbotix.calibration.api.CalibrationEventListener;
import com.orbotix.calibration.api.CalibrationImageButtonView;
import com.orbotix.calibration.api.CalibrationView;
import com.orbotix.classic.DiscoveryAgentClassic;
import com.orbotix.classic.RobotClassic;
import com.orbotix.colorpicker.api.ColorPickerEventListener;
import com.orbotix.colorpicker.api.ColorPickerFragment;
import com.orbotix.command.RGBLEDOutputCommand;
import com.orbotix.common.DiscoveryAgentEventListener;
import com.orbotix.common.DiscoveryAgentProxy;
import com.orbotix.common.DiscoveryException;
import com.orbotix.common.ResponseListener;
import com.orbotix.common.Robot;
import com.orbotix.common.RobotChangedStateListener;
import com.orbotix.common.internal.AsyncMessage;
import com.orbotix.common.internal.DeviceResponse;
import com.orbotix.joystick.api.JoystickEventListener;
import com.orbotix.joystick.api.JoystickView;
import com.orbotix.le.DiscoveryAgentLE;
import com.orbotix.le.RobotLE;
import com.orbotix.robotpicker.RobotPickerDialog;
import com.orbotix.speedslider.api.SpeedSliderView;

import java.util.List;

public class MainActivity extends AppCompatActivity implements RobotPickerDialog.RobotPickerListener,
        DiscoveryAgentEventListener,
        RobotChangedStateListener {

    // Log TAG
    private static final String TAG = "SPHERO_MainFragment";

    // ////////////////////////////////////   Robot

    //Robot connected
    private ConvenienceRobot mRobot;
    // max speed for the robot
    private float maxSpeed = 1f;


    // ////////////////////////////////////   View
    // Joystick
    private JoystickView _joystick;
    // Robot calibration
    private CalibrationView _calibrationView;
    private CalibrationImageButtonView _calibrationButtonView;
    // Robot colour
    private ColorPickerFragment _colorPicker;
    private Button _colorPickerButton;


    // Robot choice
    private DiscoveryAgentProxy _currentDiscoveryAgent;
    private RobotPickerDialog.RobotPicked robotPicked;
    private RobotPickerDialog _robotPickerDialog;
    private AlertDialog alertDialog;

    @Override
    protected void onResume() {
        super.onResume();
        if (robotPicked == null){
            chooseRobot();
        } else if( (mRobot == null || !mRobot.isConnected()) &&(!_currentDiscoveryAgent.isDiscovering()) ){
            initDiscovery();
            startDiscovery();
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        setupJoystick();
        setupCalibration();
        setupColorPicker();
        setupSpeederSlider();

        findViewById(R.id.entire_view).setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                _joystick.interpretMotionEvent(event);
                _calibrationView.interpretMotionEvent(event);
                return true;
            }
        });

        // Check Permission
        if (this.checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.ACCESS_COARSE_LOCATION},
                    1);
        } else {
            chooseRobot();
        }
    }

    /**
     * Result for location permission
     */
    @Override
    public void onRequestPermissionsResult(int requestCode,
                                           @NonNull String permissions[], @NonNull int[] grantResults) {
        switch (requestCode) {
            case 1: {
                // If request is cancelled, the result arrays are empty.
                if (grantResults.length > 0
                        && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    chooseRobot();
                }
            }
        }
    }

    private void chooseRobot() {
        if (_robotPickerDialog == null) {
            _robotPickerDialog = new RobotPickerDialog(this, this);
        }
        if (!_robotPickerDialog.isShowing()) {
            _robotPickerDialog.show();
        }
    }

    @Override
    public void handleRobotsAvailable(List<Robot> robots) {
        if (_currentDiscoveryAgent instanceof DiscoveryAgentClassic) {
            _currentDiscoveryAgent.connect(robots.get(0));
        }
    }

    @Override
    public void onRobotPicked(RobotPickerDialog.RobotPicked robotPicked) {
        _robotPickerDialog.dismiss();
        this.robotPicked = robotPicked;
        initDiscovery();
    }

    private void initDiscovery() {
        switch (this.robotPicked) {
            case Sphero:
                _currentDiscoveryAgent = DiscoveryAgentClassic.getInstance();
                break;
            case Ollie:
                _currentDiscoveryAgent = DiscoveryAgentLE.getInstance();
                break;
        }
        startDiscovery();
    }

    /**
     * Starts discovery on the set discovery agent and look for robots
     */
    private void startDiscovery() {
        if(!_currentDiscoveryAgent.isDiscovering()){
            try {
                AlertDialog.Builder adBuilder = new AlertDialog.Builder(this);
                adBuilder.setTitle("Connecting...");
                alertDialog = adBuilder.show();
                _currentDiscoveryAgent.addDiscoveryListener(this);
                _currentDiscoveryAgent.addRobotStateListener(this);
                _currentDiscoveryAgent.startDiscovery(this);
            } catch (DiscoveryException e) {
                Log.e(TAG, "Could not start discovery. Reason: " + e.getMessage(), e);
            }
        }

    }

    @Override
    public void handleRobotChangedState(Robot robot, RobotChangedStateListener.RobotChangedStateNotificationType type) {
        switch (type) {
            case Online:
                alertDialog.dismiss();
                _currentDiscoveryAgent.stopDiscovery();
                _currentDiscoveryAgent.removeDiscoveryListener(this);
                _joystick.setEnabled(true);
                _calibrationView.setEnabled(true);
                _colorPickerButton.setEnabled(true);
                _calibrationButtonView.setEnabled(true);


                // Bluetooth Classic (Sphero)
                if (robot instanceof RobotClassic) {
                    mRobot = new Sphero(robot);
                }
                // Bluetooth LE (Ollie)
                if (robot instanceof RobotLE) {
                    mRobot = new Ollie(robot);
                }
                init();
                break;
            case Disconnected:
                _joystick.setEnabled(false);
                _calibrationView.setEnabled(false);
                _calibrationButtonView.setEnabled(false);
                _colorPickerButton.setEnabled(false);
                break;
        }
    }

    /**
     * Init after sphero connected
     */
    private void init() {
        mRobot.enableCollisions(true);

        // robot listener
        mRobot.addResponseListener(new ResponseListener() {
            @Override
            public void handleResponse(DeviceResponse deviceResponse, Robot robot) {

            }

            @Override
            public void handleStringResponse(String s, Robot robot) {

            }

            @Override
            public void handleAsyncMessage(AsyncMessage asyncMessage, Robot robot) {
                if (asyncMessage instanceof CollisionDetectedAsyncData) {
                    Log.d("SPHERO", ">>>Collision");
                }
            }
        });

    }


    /**
     * Sets up the calibration gesture and button
     */
    private void setupCalibration() {
        _calibrationView = (CalibrationView) findViewById(R.id.calibrationView);
        _calibrationView.setShowGlow(true);
        _calibrationView.setCalibrationEventListener(new CalibrationEventListener() {

            @Override
            public void onCalibrationBegan() {
                mRobot.calibrating(true);
            }

            @Override
            public void onCalibrationChanged(float angle) {
                mRobot.rotate(angle);
            }

            /**
             * Invoked when the user stops the calibration process
             */
            @Override
            public void onCalibrationEnded() {
                mRobot.stop();
                mRobot.calibrating(false);
            }
        });
        _calibrationView.setEnabled(false);

        _calibrationButtonView = (CalibrationImageButtonView) findViewById(R.id.calibrateButton);
        _calibrationButtonView.setCalibrationView(_calibrationView);
        _calibrationButtonView.setEnabled(false);
    }

    private void setupColorPicker() {
        _colorPicker = new ColorPickerFragment();
        _colorPicker.setColorPickerEventListener(new ColorPickerEventListener() {
            @Override
            public void onColorPickerChanged(int red, int green, int blue) {
                Log.d(TAG, String.format("%d, %d, %d", red, green, blue));
                mRobot.sendCommand(new RGBLEDOutputCommand(red, green, blue, false));
                _colorPickerButton.setBackgroundColor(Color.rgb(red, green, blue));
                //getFragmentManager().popBackStack();
                //getFragmentManager().popBackStackImmediate();
                //getFragmentManager().beginTransaction().remove(_colorPicker).commit();
            }

            @Override
            public void validate() {
                _colorPickerButton.setVisibility(View.VISIBLE);
                getFragmentManager().beginTransaction().remove(_colorPicker).commit();
            }
        });

        // Find the color picker fragment and add a click listener to show the color picker
        _colorPickerButton = (Button) findViewById(R.id.colorPickerButton);
        _colorPickerButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                _colorPickerButton.setVisibility(View.GONE);
                FragmentTransaction transaction = getFragmentManager().beginTransaction();
                transaction.add(R.id.fragment_root, _colorPicker, "ColorPicker");
                transaction.show(_colorPicker);
                transaction.addToBackStack("DriveSample");
                transaction.commit();
            }
        });

    }


    private void setupSpeederSlider() {
        SpeedSliderView _speederSliderView = (SpeedSliderView) findViewById(R.id.slider);
        _speederSliderView.setOnSpeedChangedListener(new SpeedSliderView.OnSpeedChangedListener() {
            @Override
            public void onSpeedChanged(float speed) {
                Log.d(TAG, "Speed:" + speed);
                maxSpeed = speed;
            }
        });
    }

    /**
     * Sets up the joystick from scratch
     */
    private void setupJoystick() {
        _joystick = (JoystickView) findViewById(R.id.joystickView);
        _joystick.setJoystickEventListener(new JoystickEventListener() {

            @Override
            public void onJoystickBegan() {
            }

            @Override
            public void onJoystickMoved(double distanceFromCenter, double angle) {
                double speed = distanceFromCenter > maxSpeed ? maxSpeed : distanceFromCenter;
                mRobot.drive((float) angle, (float) speed);
            }

            @Override
            public void onJoystickEnded() {
                mRobot.stop();
            }
        });
        _joystick.setEnabled(false);
    }

    @Override
    public void onStop() {
        if (mRobot instanceof Ollie) {
            mRobot.sleep();
        } else if (mRobot instanceof Sphero) {
            mRobot.disconnect();
        }
        super.onStop();
    }

    @Override
    public void onPause() {
        super.onPause();
        if (_currentDiscoveryAgent != null) {
            _currentDiscoveryAgent.removeRobotStateListener(this);

            for (Robot r : _currentDiscoveryAgent.getConnectedRobots()) {
                r.sleep();
            }
        }
    }
}
