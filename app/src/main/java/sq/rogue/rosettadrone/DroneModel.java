package sq.rogue.rosettadrone;

import androidx.annotation.NonNull;

import android.app.AlertDialog;
import android.content.DialogInterface;
import android.location.Location;
import android.media.Ringtone;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import com.MAVLink.MAVLinkPacket;
import com.MAVLink.Messages.MAVLinkMessage;
import com.MAVLink.common.msg_altitude;
import com.MAVLink.common.msg_attitude;
import com.MAVLink.common.msg_autopilot_version;
import com.MAVLink.common.msg_battery_status;
import com.MAVLink.common.msg_command_ack;
import com.MAVLink.common.msg_global_position_int;
import com.MAVLink.common.msg_gps_raw_int;
import com.MAVLink.common.msg_heartbeat;
import com.MAVLink.common.msg_home_position;
import com.MAVLink.common.msg_mission_ack;
import com.MAVLink.common.msg_mission_count;
import com.MAVLink.common.msg_mission_item;
import com.MAVLink.common.msg_mission_item_reached;
import com.MAVLink.common.msg_mission_request;
import com.MAVLink.common.msg_mission_request_list;
import com.MAVLink.common.msg_param_value;
import com.MAVLink.common.msg_power_status;
import com.MAVLink.common.msg_radio_status;
import com.MAVLink.common.msg_rc_channels;
import com.MAVLink.common.msg_statustext;
import com.MAVLink.common.msg_sys_status;
import com.MAVLink.common.msg_vfr_hud;
import com.MAVLink.common.msg_vibration;
import com.MAVLink.enums.GPS_FIX_TYPE;
import com.MAVLink.enums.MAV_AUTOPILOT;
import com.MAVLink.enums.MAV_FRAME;
import com.MAVLink.enums.MAV_MISSION_RESULT;
import com.MAVLink.enums.MAV_MISSION_TYPE;
import com.MAVLink.enums.MAV_MODE_FLAG;
import com.MAVLink.enums.MAV_MODE_FLAG_DECODE_POSITION;
import com.MAVLink.enums.MAV_PROTOCOL_CAPABILITY;
import com.MAVLink.enums.MAV_RESULT;
import com.MAVLink.enums.MAV_STATE;
import com.MAVLink.enums.MAV_TYPE;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.PortUnreachableException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Objects;
import java.util.Timer;
import java.util.TimerTask;

import dji.common.airlink.SignalQualityCallback;
import dji.common.battery.AggregationState;
import dji.common.battery.BatteryState;
import dji.common.camera.SettingsDefinitions;
import dji.common.error.DJIError;
import dji.common.flightcontroller.Attitude;
import dji.common.flightcontroller.ConnectionFailSafeBehavior;
import dji.common.flightcontroller.ControlMode;
import dji.common.flightcontroller.FlightControllerState;
import dji.common.flightcontroller.FlightMode;
import dji.common.flightcontroller.FlightOrientationMode;
import dji.common.flightcontroller.GPSSignalLevel;
import dji.common.flightcontroller.LEDsSettings;
import dji.common.flightcontroller.LocationCoordinate3D;
import dji.common.flightcontroller.RemoteControllerFlightMode;
import dji.common.flightcontroller.simulator.InitializationData;
import dji.common.flightcontroller.virtualstick.FlightControlData;
import dji.common.flightcontroller.virtualstick.FlightCoordinateSystem;
import dji.common.flightcontroller.virtualstick.RollPitchControlMode;
import dji.common.flightcontroller.virtualstick.VerticalControlMode;
import dji.common.flightcontroller.virtualstick.YawControlMode;
import dji.common.gimbal.GimbalMode;
import dji.common.gimbal.Rotation;
import dji.common.gimbal.RotationMode;
import dji.common.mission.MissionState;
import dji.common.mission.followme.FollowMeHeading;
import dji.common.mission.followme.FollowMeMission;
import dji.common.mission.followme.FollowMeMissionState;
import dji.common.mission.waypoint.Waypoint;
import dji.common.mission.waypoint.WaypointMission;
import dji.common.mission.waypoint.WaypointMissionState;
import dji.common.model.LocationCoordinate2D;
import dji.common.remotecontroller.ChargeRemaining;
import dji.common.remotecontroller.HardwareState;
import dji.common.util.CommonCallbacks;
import dji.sdk.battery.Battery;
import dji.sdk.flightcontroller.FlightController;
import dji.sdk.gimbal.Gimbal;
import dji.sdk.mission.MissionControl;
import dji.sdk.mission.followme.FollowMeMissionOperator;
import dji.sdk.mission.waypoint.WaypointMissionOperator;
import dji.sdk.products.Aircraft;

import dji.sdk.sdkmanager.DJISDKManager;
import dji.sdksharedlib.keycatalog.extension.InternalKey;

import static com.MAVLink.enums.MAV_CMD.MAV_CMD_COMPONENT_ARM_DISARM;
import static com.MAVLink.enums.MAV_CMD.MAV_CMD_DO_DIGICAM_CONTROL;
import static com.MAVLink.enums.MAV_CMD.MAV_CMD_NAV_TAKEOFF;
import static com.MAVLink.enums.MAV_CMD.MAV_CMD_VIDEO_START_CAPTURE;
import static com.MAVLink.enums.MAV_CMD.MAV_CMD_VIDEO_STOP_CAPTURE;
import static com.MAVLink.enums.MAV_COMPONENT.MAV_COMP_ID_AUTOPILOT1;
import static com.mapbox.mapboxsdk.Mapbox.getApplicationContext;
import static sq.rogue.rosettadrone.util.getTimestampMicroseconds;
import static sq.rogue.rosettadrone.util.safeSleep;


public class DroneModel implements CommonCallbacks.CompletionCallback {
    private static final int NOT_USING_GCS_COMMANDED_MODE = -1;
    private static HardwareState.FlightModeSwitch avtivemode = HardwareState.FlightModeSwitch.POSITION_THREE; // Change this to decide what position the mode switch should be inn.
    private final String TAG = "RosettaDrone";
    public DatagramSocket socket;
    DatagramSocket secondarySocket;
    private Aircraft djiAircraft;
    private ArrayList<MAVParam> params = new ArrayList<>();
    private long ticks = 0;
    private MainActivity parent;

    private int mSystemId = 1;
    private int mGCSCommandedMode;

    private int mThrottleSetting=0;
    private int mLeftStickVertical=0;
    private int mLeftStickHorisontal=0;
    private int mRightStickVertical=0;
    private int mRightStickHorisontal=0;
    private boolean mC1=false;
    private boolean mC2=false;
    private boolean mC3=false;

    private int mCFullChargeCapacity_mAh=0;
    private int mCChargeRemaining_mAh=0;
    private int mCVoltage_mV=0;
    private int mCVoltage_pr=0;
    private int mCCurrent_mA=0;
    private float mCBatteryTemp_C=0;

    private int mFullChargeCapacity_mAh=0;
    private int mChargeRemaining_mAh=0;
    private int mVoltage_mV=0;
    private int mVoltage_pr=0;
    private int mCurrent_mA=0;
    private int[] mCellVoltages = new int[10];
    private int mDownlinkQuality = 0;
    private int mUplinkQuality = 0;
    private int mControllerVoltage_pr=0;

    private float mPitch=0;
    private float mRoll=0;
    private float mYaw=0;
    private float mThrottle=0;
    private double m_Latitude=0;
    private double m_Longitude=0;
    private float  m_alt=0;

    private double m_Destination_Lat = 0;
    private double m_Destination_Lon = 0;
    private float m_Destination_Alt = 0;
    private double m_Destination_Yaw = 0;

    private FlightMode lastMode = FlightMode.ATTI_HOVER;
    private HardwareState.FlightModeSwitch rcmode = HardwareState.FlightModeSwitch.POSITION_ONE;

    private SendVelocityDataTask mSendVirtualStickDataTask = null;;
    private Timer mSendVirtualStickDataTimer = null;

    private MoveTo mMoveToDataTask = null;;
    private Timer mMoveToDataTimer = null;


    private boolean mSafetyEnabled = true;
    private boolean mMotorsArmed = false;
    private FollowMeMissionOperator fmmo;
    private FlightController mFlightController;
    private Gimbal mGimbal = null;

    private int mAIfunction_activation = 0;
    private boolean mAutonomy = false;

    int mission_loaded = -1;

    DroneModel(MainActivity parent, DatagramSocket socket, boolean sim) {
        this.parent = parent;
        this.socket = socket;
        initFlightController(sim);
    }

    public float get_battery_status(){
        if(mCFullChargeCapacity_mAh > 0) {
            return (mCVoltage_pr); //mCChargeRemaining_mAh * 100 / mCFullChargeCapacity_mAh);
        }
        return 0;
    }

    public float get_drone_battery_prosentage(){
        return mCVoltage_pr;
    }

    public float get_controller_battery_prosentage(){
        return mControllerVoltage_pr;
    }


    private void initFlightController(boolean sim) {
        parent.logMessageDJI("Starting FlightController...");

        Aircraft aircraft = (Aircraft) RDApplication.getProductInstance(); //DJISimulatorApplication.getAircraftInstance();
        if (aircraft == null || !aircraft.isConnected()) {
            parent.logMessageDJI("No target...");
            mFlightController = null;
            return;
        } else {
            mGimbal = aircraft.getGimbal();
            mFlightController = aircraft.getFlightController();
            mFlightController.setRollPitchControlMode(RollPitchControlMode.VELOCITY);
            mFlightController.setYawControlMode(YawControlMode.ANGULAR_VELOCITY);
            mFlightController.setVerticalControlMode(VerticalControlMode.VELOCITY);
            mFlightController.setRollPitchCoordinateSystem(FlightCoordinateSystem.BODY);
            mFlightController.setFlightOrientationMode(FlightOrientationMode.COURSE_LOCK,null);
            if(sim) {
                parent.logMessageDJI("Starting Simulator...");
                mFlightController.getSimulator().setStateCallback(stateData -> new Handler(Looper.getMainLooper()).post(() -> {
/*
                    String yaw = String.format("%.2f", stateData.getYaw());
                    String pitch = String.format("%.2f", stateData.getPitch());
                    String roll = String.format("%.2f", stateData.getRoll());
                    String positionX = String.format("%.2f", stateData.getPositionX());
                    String positionY = String.format("%.2f", stateData.getPositionY());
                    String positionZ = String.format("%.2f", stateData.getPositionZ());

                    Log.v("SIM", "Yaw : " + yaw + ", Pitch : " + pitch + ", Roll : " + roll + "\n" + ", PosX : " + positionX +
                            ", PosY : " + positionY +
                            ", PosZ : " + positionZ);

*/
                }));
            }
        }

        if (mFlightController != null) {
            parent.logMessageDJI("Target found...");

            if(sim) {
                mFlightController.getSimulator()
                        .start(InitializationData.createInstance(new LocationCoordinate2D(60.25, 10.29), 10, 10),
                                djiError -> {
                                    if (djiError != null) {
                                        parent.logMessageDJI(djiError.getDescription());
                                    } else {
                                        parent.logMessageDJI("Start Simulator Success");
                                    }
                                });
            }
        }
        //  SetMesasageBox("Controller Ready!!!!!");
    }


    int getSystemId() {
        return mSystemId;
    }

    void setSystemId(int id) {
        mSystemId = id;
    }

    void setRTLAltitude(final int altitude) {
        djiAircraft.getFlightController().setGoHomeHeightInMeters(altitude, djiError -> {
            if (djiError == null) {
                parent.logMessageDJI("RTL altitude set to " + altitude + "m");

            } else {
                parent.logMessageDJI("Error setting RTL altitude " + djiError.getDescription());
            }
        });
    }

    void setMaxHeight(final int height) {
        djiAircraft.getFlightController().setMaxFlightHeight(height, djiError -> {
            if (djiError == null) {
                parent.logMessageDJI("Max height set to " + height + "m");

            } else {
                parent.logMessageDJI("Error setting max height " + djiError.getDescription());
            }
        });
    }

    void setSmartRTLEnabled(final boolean enabled) {
        djiAircraft.getFlightController().setSmartReturnToHomeEnabled(enabled, djiError -> {
            if (djiError == null) {
                parent.logMessageDJI("Smart RTL set to " + enabled);

            } else {
                parent.logMessageDJI("Error setting smart RTL " + djiError.getDescription());
            }
        });
    }

    void setMultiModeEnabled(final boolean enabled) {
        djiAircraft.getFlightController().setMultipleFlightModeEnabled(enabled, djiError -> {
            if (djiError == null) {
                parent.logMessageDJI("Multi Mode set to " + enabled);

            } else {
                parent.logMessageDJI("Error setting multiple flight modes to  " + enabled + djiError.getDescription());
            }
        });
    }

    void setForwardLEDsEnabled(final boolean enabled) {
        LEDsSettings.Builder Tmp = new LEDsSettings.Builder().frontLEDsOn(enabled);
        assert Tmp != null;
        djiAircraft.getFlightController().setLEDsEnabledSettings(Tmp.build(), djiError -> {
            if (djiError == null) {
                parent.logMessageDJI("Front LEDs set to " + enabled);

            } else {
                parent.logMessageDJI("Error setting front LEDs" + djiError.getDescription());
            }
        });
    }

    void setCollisionAvoidance(final boolean enabled) {
        if (djiAircraft.getFlightController().getFlightAssistant() != null) {
            djiAircraft.getFlightController().getFlightAssistant().setCollisionAvoidanceEnabled(enabled, new CommonCallbacks.CompletionCallback() {
                @Override
                public void onResult(DJIError djiError) {
                    if (djiError == null) {
                        parent.logMessageDJI("Collision avoidance set to " + enabled);

                    } else {
                        parent.logMessageDJI("Error setting collision avoidance  " + djiError.getDescription());
                    }
                }
            });
        } else {
            parent.logMessageDJI("Error setting collision avoidance to " + enabled);
        }
    }

    void setUpwardCollisionAvoidance(final boolean enabled) {
        if (djiAircraft.getFlightController().getFlightAssistant() != null) {
            djiAircraft.getFlightController().getFlightAssistant().setUpwardsAvoidanceEnabled(enabled, new CommonCallbacks.CompletionCallback() {
                @Override
                public void onResult(DJIError djiError) {
                    if (djiError == null) {
                        parent.logMessageDJI("Upward collision avoidance set to " + enabled);

                    } else {
                        parent.logMessageDJI("Error setting upward collision avoidance  " + djiError.getDescription());
                    }
                }
            });
        } else {
            parent.logMessageDJI("Error setting upward collision avoidance to " + enabled);
        }
    }

    void setLandingProtection(final boolean enabled) {
        if (djiAircraft.getFlightController().getFlightAssistant() != null) {
            djiAircraft.getFlightController().getFlightAssistant().setLandingProtectionEnabled(enabled, djiError -> {
                if (djiError == null) {
                    parent.logMessageDJI("Landing Protection set to " + enabled);

                } else {
                    parent.logMessageDJI("Error setting landing protection " + djiError.getDescription());
                }
            });
        } else {
            parent.logMessageDJI("Error setting landing protection to " + enabled);
        }
    }

    public void setHeadingMode(int headingValue) {
        switch (headingValue) {
            case 0:
                break;
            case 1:
                break;
            case 2:
                break;
            case 3:
                break;
            case 4:
                break;
            default:
                parent.logMessageDJI("Invalid heading mode.");
        }
    }

    public boolean isMotorsArmed() {
        return mMotorsArmed;
    }

    public void setMotorsArmed(boolean motorsArmed) {
        mMotorsArmed = motorsArmed;
    }

    public int getGCSCommandedMode() {
        return mGCSCommandedMode;
    }

    void setGCSCommandedMode(int GCSCommandedMode) {
        mGCSCommandedMode = GCSCommandedMode;
    }

    void setWaypointMission(final WaypointMission wpMission) {
        mission_loaded = -1;
        DJIError load_error = getWaypointMissionOperator().loadMission(wpMission);
        if (load_error != null)
            parent.logMessageDJI("loadMission() returned error: " + load_error.toString());
        else {
            parent.logMessageDJI("Uploading mission");
            getWaypointMissionOperator().uploadMission(
                    djiError -> {
                        if (djiError == null) {
                            while (getWaypointMissionOperator().getCurrentState() == WaypointMissionState.UPLOADING) {
                                safeSleep(200);
                            }
                            if (getWaypointMissionOperator().getCurrentState() == WaypointMissionState.READY_TO_EXECUTE){
                                parent.logMessageDJI("Mission uploaded and ready to execute!");
                                mission_loaded = MAV_MISSION_RESULT.MAV_MISSION_ACCEPTED;
                                send_mission_ack(mission_loaded);
                            }
                            else {
                                parent.logMessageDJI("Error uploading waypoint mission to drone.");
                                mission_loaded = MAV_MISSION_RESULT.MAV_MISSION_ERROR;
                                send_mission_ack(mission_loaded);
                            }

                        } else {
                            parent.logMessageDJI("Error uploading: " + djiError.getDescription());
                            parent.logMessageDJI(("Please try re-uploading"));
                            mission_loaded = MAV_MISSION_RESULT.MAV_MISSION_ERROR;
                            send_mission_ack(mission_loaded);
                        }
                        //parent.logMessageDJI("New state: " + getWaypointMissionOperator().getCurrentState().getName());
                    });
        }
    }

    private Aircraft getDjiAircraft() {
        return djiAircraft;
    }

    boolean isSafetyEnabled() {
        return mSafetyEnabled;
    }

    void setSafetyEnabled(boolean SafetyEnabled) {
        mSafetyEnabled = SafetyEnabled;
    }

    private void SetMesasageBox(String msg) {
        AlertDialog.Builder alertDialog2 = new AlertDialog.Builder(parent);
        alertDialog2.setTitle(msg);
        alertDialog2.setMessage("Please Land !!!");
        alertDialog2.setPositiveButton("Accept",
                (dialog, which) -> {
                    dialog.cancel();
                    //dismiss the dialog
                });

        parent.runOnUiThread(() -> {
  //          Uri notification = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION);
  //          Ringtone r = RingtoneManager.getRingtone(getApplicationContext(), notification);
  //          r.play();
            alertDialog2.show();
        });

    }

    boolean setDjiAircraft(Aircraft djiAircraft) {

        if (djiAircraft == null || djiAircraft.getRemoteController() == null)
            return false;

        this.djiAircraft = djiAircraft;

        Arrays.fill(mCellVoltages, 0xffff); // indicates no cell per mavlink definition

        /**************************************************
         * Called whenever RC state changes               *
         **************************************************/

        this.djiAircraft.getRemoteController().setHardwareStateCallback(new HardwareState.HardwareStateCallback() {
            boolean lastState = false;

            @Override
            public void onUpdate(@NonNull HardwareState rcHardwareState) {
                // DJI: range [-660,660]
                mThrottleSetting = (Objects.requireNonNull(rcHardwareState.getLeftStick()).getVerticalPosition() + 660) / 1320;

                // Mavlink: 1000 to 2000 with 1500 = 1.5ms as center...
                mLeftStickVertical    = (int)(rcHardwareState.getLeftStick().getVerticalPosition() * 0.8 ) + 1500;
                mLeftStickHorisontal  = (int)(rcHardwareState.getLeftStick().getHorizontalPosition() * 0.8 ) + 1500;
                mRightStickVertical   = (int)(rcHardwareState.getRightStick().getVerticalPosition() * 0.8 ) + 1500;
                mRightStickHorisontal = (int)(rcHardwareState.getRightStick().getHorizontalPosition() * 0.8 ) + 1500;
                mC1 = Objects.requireNonNull(rcHardwareState.getC1Button()).isClicked();
                mC2 = Objects.requireNonNull(rcHardwareState.getC2Button()).isClicked();
                mC3 = Objects.requireNonNull(rcHardwareState.getC3Button()).isClicked();

                rcmode = Objects.requireNonNull(rcHardwareState.getFlightModeSwitch());
//                parent.logMessageDJI("FlighMode switch : "+ rcmode);

                // If C3 is pressed...
                /*
                if(mC3 == true && !lastState) {
                    parent.logMessageDJI("DoTakeoff");
                    do_takeoff();
                    lastState = true;
                }
                else if(mC3 == false && lastState){
                    lastState = false;
                }
                 */
            }
        });


        this.djiAircraft.getRemoteController().setChargeRemainingCallback(new ChargeRemaining.Callback() {
            int lastState = 100;

            @Override
            public void onUpdate(@NonNull ChargeRemaining rcChargeRemaining) {
                mControllerVoltage_pr = (rcChargeRemaining.getRemainingChargeInPercent());
                if(mControllerVoltage_pr > 90) lastState = 100;
                if(mControllerVoltage_pr < 20 && lastState == 100){
                    lastState = 20;
                    SetMesasageBox("Controller Battery Warning 20% !!!!!");
                }
                if(mControllerVoltage_pr < 10 && lastState == 20){
                    lastState = 10;
                    SetMesasageBox("Controller Battery Warning 10% !!!!!");
                }
                if(mControllerVoltage_pr < 5 && lastState == 10){
                    lastState = 5;
                    SetMesasageBox("Controller Battery Warning 5% !!!!!");
                }
            }
        });

        /**************************************************
         * Called whenever battery state changes          *
         **************************************************/

        if (this.djiAircraft != null) {
            parent.logMessageDJI("setBatteryCallback");
            this.djiAircraft.getBattery().setStateCallback(new BatteryState.Callback() {
                int lastState = 100;

                @Override
                public void onUpdate(BatteryState batteryState) {
                    //     Log.d(TAG, "Battery State callback");
                    mCFullChargeCapacity_mAh = batteryState.getFullChargeCapacity();
                    mCChargeRemaining_mAh = batteryState.getChargeRemaining();
                    mCVoltage_mV = batteryState.getVoltage();
                    mCCurrent_mA = Math.abs(batteryState.getCurrent());
                    mCBatteryTemp_C = batteryState.getTemperature();
                    mCVoltage_pr = batteryState.getChargeRemainingInPercent();

                    if(mCVoltage_pr > 90) lastState = 100;
                    if(mCVoltage_pr <= 20 && lastState == 100){
                        lastState = 20;
                        SetMesasageBox("Drone Battery Warning 20% !!!!!");
                    }
                    if(mCVoltage_pr <= 10 && lastState == 20){
                        lastState = 10;
                        SetMesasageBox("Drone Battery Warning 10% !!!!!");
                    }
                    if(mCVoltage_pr <= 5 && lastState == 10){
                        lastState = 5;
                        SetMesasageBox("Drone Battery Warning 5% !!!!!");
                    }

                    Log.d(TAG, "Voltage %: " + mCVoltage_pr);
                }
            });
            this.djiAircraft.getBattery().getCellVoltages(new CellVoltageCompletionCallback());
        } else {
            Log.e(TAG, "djiAircraft.getBattery() IS NULL");
            return false;
        }

        Battery.setAggregationStateCallback(aggregationState -> {
            Log.d(TAG, "Aggregation State callback");
            mFullChargeCapacity_mAh = aggregationState.getFullChargeCapacity();
            mChargeRemaining_mAh = aggregationState.getChargeRemaining();
            mVoltage_mV = aggregationState.getVoltage();
            mCurrent_mA = aggregationState.getCurrent();
            mVoltage_pr = aggregationState.getChargeRemainingInPercent();
            Log.d(TAG, "Aggregated voltage: " + String.valueOf(aggregationState.getVoltage()));
        });

        /**************************************************
         * Called whenever airlink quality changes        *
         **************************************************/

        djiAircraft.getAirLink().setDownlinkSignalQualityCallback(i -> mDownlinkQuality = i);

        djiAircraft.getAirLink().setUplinkSignalQualityCallback(i -> mUplinkQuality = i);

        initMissionOperator();

        return true;
    }

    WaypointMissionOperator getWaypointMissionOperator() {
        return MissionControl.getInstance().getWaypointMissionOperator();
    }

    private void initMissionOperator() {
        getWaypointMissionOperator().removeListener(null);
        RosettaMissionOperatorListener mMissionOperatorListener = new RosettaMissionOperatorListener();
        mMissionOperatorListener.setMainActivity(parent);
        getWaypointMissionOperator().addListener(mMissionOperatorListener);
    }

    public ArrayList<MAVParam> getParams() {
        return params;
    }

    public DatagramSocket getSocket() {
        return socket;
    }

    public void setSocket(DatagramSocket socket) {
        this.socket = socket;
    }

    void setSecondarySocket(DatagramSocket socket) {
        this.secondarySocket = socket;
    }


    void tick() {
        ticks += 100;

        if (djiAircraft == null)
            return;

        try {
            if (ticks % 100 == 0) {
                send_attitude();
                send_altitude();
                send_vibration();
                send_vfr_hud();
            }
            if (ticks % 300 == 0) {
                send_global_position_int();
                send_gps_raw_int();
                send_radio_status();
                send_rc_channels();
            }
            if (ticks % 1000 == 0) {
                send_heartbeat();
                send_sys_status();
                send_power_status();
                send_battery_status();
            }
            if (ticks % 5000 == 0) {
                send_home_position();
            }

        } catch (Exception e) {
            Log.d(TAG, "exception", e);
        }
    }

    void armMotors() {
        if (mSafetyEnabled) {
            parent.logMessageDJI(parent.getResources().getString(R.string.safety_launch));
            send_command_ack(MAV_CMD_COMPONENT_ARM_DISARM, MAV_RESULT.MAV_RESULT_DENIED);
        } else {
            send_command_ack(MAV_CMD_COMPONENT_ARM_DISARM, MAV_RESULT.MAV_RESULT_ACCEPTED);
            mMotorsArmed = true;
        }

//
//        djiAircraft.getFlightController().turnOnMotors(new CommonCallbacks.CompletionCallback() {
//            @Override
//            public void onResult(DJIError djiError) {
//                // TODO reattempt if arming/disarming fails
//                if (djiError == null) {
//                    send_command_ack(MAV_CMD_COMPONENT_ARM_DISARM, MAV_RESULT.MAV_RESULT_ACCEPTED);
//                    mSafetyEnabled = false;
//                } else
//                    send_command_ack(MAV_CMD_COMPONENT_ARM_DISARM, MAV_RESULT.MAV_RESULT_FAILED);
//                Log.d(TAG, "onResult()");
//            }
//        });
    }

    void disarmMotors() {
        djiAircraft.getFlightController().turnOffMotors(new CommonCallbacks.CompletionCallback() {

            @Override
            public void onResult(DJIError djiError) {
                // TODO reattempt if arming/disarming fails
                if (djiError == null)
                    send_command_ack(MAV_CMD_COMPONENT_ARM_DISARM, MAV_RESULT.MAV_RESULT_ACCEPTED);
                else
                    send_command_ack(MAV_CMD_COMPONENT_ARM_DISARM, MAV_RESULT.MAV_RESULT_FAILED);
                Log.d(TAG, "onResult()");
                mMotorsArmed = false;
            }
        });
    }

    private void sendMessage(MAVLinkMessage msg) {
        if (socket == null)
            return;

        MAVLinkPacket packet = msg.pack();

        packet.sysid = mSystemId;
        packet.compid = MAV_COMP_ID_AUTOPILOT1;

        byte[] bytes = packet.encodePacket();

        try {
            DatagramPacket p = new DatagramPacket(bytes, bytes.length, socket.getInetAddress(), socket.getPort());
            socket.send(p);
            parent.logMessageToGCS(msg.toString());

            if (secondarySocket != null) {
                DatagramPacket secondaryPacket = new DatagramPacket(bytes, bytes.length, secondarySocket.getInetAddress(), secondarySocket.getPort());
                secondarySocket.send(secondaryPacket);
//                parent.logMessageDJI("SECONDARY PACKET SENT");
            }
//            if(msg.msgid != MAVLINK_MSG_ID_POWER_STATUS &&
//                    msg.msgid != MAVLINK_MSG_ID_SYS_STATUS &&
//                    msg.msgid != MAVLINK_MSG_ID_VIBRATION &&
//                    msg.msgid != MAVLINK_MSG_ID_ATTITUDE &&
//                    msg.msgid != MAVLINK_MSG_ID_VFR_HUD &&
//                    msg.msgid != MAVLINK_MSG_ID_GLOBAL_POSITION_INT &&
//                    msg.msgid != MAVLINK_MSG_ID_GPS_RAW_INT &&
//                    msg.msgid != MAVLINK_MSG_ID_RADIO_STATUS)
//                parent.logMessageToGCS(msg.toString());

        } catch (PortUnreachableException ignored) {

        } catch (IOException e) {

        }
    }

    void send_autopilot_version() {
        msg_autopilot_version msg = new msg_autopilot_version();
        msg.capabilities = MAV_PROTOCOL_CAPABILITY.MAV_PROTOCOL_CAPABILITY_COMMAND_INT;
        msg.capabilities |= MAV_PROTOCOL_CAPABILITY.MAV_PROTOCOL_CAPABILITY_MISSION_INT;
        sendMessage(msg);
    }

    private void send_heartbeat() {
        msg_heartbeat msg = new msg_heartbeat();
        msg.type = MAV_TYPE.MAV_TYPE_QUADROTOR;
        msg.autopilot = MAV_AUTOPILOT.MAV_AUTOPILOT_ARDUPILOTMEGA;

        // For base mode logic, see Copter::sendHeartBeat() in ArduCopter/GCS_Mavlink.cpp
        msg.base_mode = MAV_MODE_FLAG.MAV_MODE_FLAG_CUSTOM_MODE_ENABLED;
        msg.base_mode |= MAV_MODE_FLAG.MAV_MODE_FLAG_MANUAL_INPUT_ENABLED;

        //parent.logMessageDJI("FlightMode: "+djiAircraft.getFlightController().getState().getFlightMode());

        lastMode = djiAircraft.getFlightController().getState().getFlightMode();
        switch (lastMode) {
            case MANUAL:
                msg.custom_mode = ArduCopterFlightModes.STABILIZE;
                break;
            case ATTI_HOVER:
                break;
            case HOVER:
                break;
            case GPS_BLAKE:
                break;
            case ATTI_LANDING:
                break;
            case CLICK_GO:
                break;
            case CINEMATIC:
                break;
            case ATTI_LIMITED:
                break;
            case PANO:
                break;
            case FARMING:
                break;
            case FPV:
                break;
            case PALM_CONTROL:
                break;
            case QUICK_SHOT:
                break;
            case DETOUR:
                break;
            case TIME_LAPSE:
                break;
            case POI2:
                break;
            case OMNI_MOVING:
                break;
            case ADSB_AVOIDING:
                break;
            case ATTI:
                msg.custom_mode = ArduCopterFlightModes.LOITER;
                break;
            case ATTI_COURSE_LOCK:
                break;
            case GPS_ATTI:
                msg.custom_mode = ArduCopterFlightModes.STABILIZE;
                break;
            case GPS_COURSE_LOCK:
                break;
            case GPS_HOME_LOCK:
                break;
            case GPS_HOT_POINT:
                break;
            case ASSISTED_TAKEOFF:
                break;
            case AUTO_TAKEOFF:
                msg.custom_mode = ArduCopterFlightModes.GUIDED;
                msg.base_mode |= MAV_MODE_FLAG.MAV_MODE_FLAG_GUIDED_ENABLED;
                break;
            case AUTO_LANDING:
                msg.custom_mode = ArduCopterFlightModes.LAND;
                msg.base_mode |= MAV_MODE_FLAG.MAV_MODE_FLAG_GUIDED_ENABLED;
                break;
            case GPS_WAYPOINT:
                msg.custom_mode = ArduCopterFlightModes.AUTO;
                msg.base_mode |= MAV_MODE_FLAG.MAV_MODE_FLAG_GUIDED_ENABLED;
                break;
            case GO_HOME:
                msg.custom_mode = ArduCopterFlightModes.RTL;
                msg.base_mode |= MAV_MODE_FLAG.MAV_MODE_FLAG_GUIDED_ENABLED;
                break;
            case JOYSTICK:
                msg.custom_mode = ArduCopterFlightModes.GUIDED;
                msg.base_mode |= MAV_MODE_FLAG.MAV_MODE_FLAG_GUIDED_ENABLED;
                break;
            case GPS_ATTI_WRISTBAND:
                break;
            case DRAW:
                msg.base_mode |= MAV_MODE_FLAG.MAV_MODE_FLAG_GUIDED_ENABLED;
                break;
            case GPS_FOLLOW_ME:
                msg.custom_mode = ArduCopterFlightModes.GUIDED;
                msg.base_mode |= MAV_MODE_FLAG.MAV_MODE_FLAG_GUIDED_ENABLED;
                break;
            case ACTIVE_TRACK:
                msg.custom_mode = ArduCopterFlightModes.GUIDED;
                msg.base_mode |= MAV_MODE_FLAG.MAV_MODE_FLAG_GUIDED_ENABLED;
                break;
            case TAP_FLY:
                msg.custom_mode = ArduCopterFlightModes.GUIDED;
                break;
            case GPS_SPORT:
                break;
            case GPS_NOVICE:
                break;
            case UNKNOWN:
                break;
            case CONFIRM_LANDING:
                break;
            case TERRAIN_FOLLOW:
                break;
            case TRIPOD:
                break;
            case TRACK_SPOTLIGHT:
                break;
            case MOTORS_JUST_STARTED:
                break;
        }
        if (mGCSCommandedMode == ArduCopterFlightModes.GUIDED)
            msg.custom_mode = ArduCopterFlightModes.GUIDED;
        if (mGCSCommandedMode == ArduCopterFlightModes.BRAKE)
            msg.custom_mode = ArduCopterFlightModes.BRAKE;

        if (mMotorsArmed)
            msg.base_mode |= MAV_MODE_FLAG.MAV_MODE_FLAG_SAFETY_ARMED;

        if (mAutonomy)
            msg.base_mode |= MAV_MODE_FLAG.MAV_MODE_FLAG_AUTO_ENABLED;

        // Catches manual landings
        // Automatically disarm motors if aircraft is on the ground and a takeoff is not in progress
        if (!getDjiAircraft().getFlightController().getState().isFlying() && mGCSCommandedMode != ArduCopterFlightModes.GUIDED)
            mMotorsArmed = false;

        // Catches manual takeoffs
        if (getDjiAircraft().getFlightController().getState().areMotorsOn())
            mMotorsArmed = true;

        msg.system_status = MAV_STATE.MAV_STATE_ACTIVE;
        msg.mavlink_version = 3;
        sendMessage(msg);
    }

    private void send_attitude() {
        msg_attitude msg = new msg_attitude();
        // TODO: this next line causes an exception
        //msg.time_boot_ms = getTimestampMilliseconds();
        Attitude att = djiAircraft.getFlightController().getState().getAttitude();
        msg.roll = (float) (att.roll * Math.PI / 180);
        msg.pitch = (float) (att.pitch * Math.PI / 180);
        msg.yaw = (float) (att.yaw * Math.PI / 180);
        // TODO msg.rollspeed = 0;
        // TODO msg.pitchspeed = 0;
        // TODO msg.yawspeed = 0;
        sendMessage(msg);
    }

    private void send_altitude() {
        msg_altitude msg = new msg_altitude();
        LocationCoordinate3D coord = djiAircraft.getFlightController().getState().getAircraftLocation();
        msg.altitude_relative = (int) (coord.getAltitude() * 1000);
        sendMessage(msg);
    }

    public void setAIfunction(int ai){
        mAIfunction_activation = ai;
    }
    // Does not work, use RC ch 8...
    void send_AI_Function(int num) {
        msg_statustext msg = new msg_statustext();
        String data = "Mgs: RosettaDrone: AI Fuction "+num+" True";
        byte[] txt = data.getBytes();
        msg.text = txt;
        sendMessage(msg);
    }

    void send_command_ack(int message_id, int result) {
        msg_command_ack msg = new msg_command_ack();
        msg.command = message_id;
        msg.result = (short) result;
        sendMessage(msg);
    }

    private void send_global_position_int() {
        msg_global_position_int msg = new msg_global_position_int();

        LocationCoordinate3D coord = djiAircraft.getFlightController().getState().getAircraftLocation();
        msg.lat = (int) (coord.getLatitude() * Math.pow(10, 7));
        msg.lon = (int) (coord.getLongitude() * Math.pow(10, 7));

        // NOTE: Commented out this field, because msg.relative_alt seems to be intended for altitude above the current terrain,
        // but DJI reports altitude above home point.
        // Mavlink: Millimeters above ground (unspecified: presumably above home point?)
        // DJI: relative altitude of the aircraft relative to take off location, measured by barometer, in meters.
        msg.relative_alt = (int) (coord.getAltitude() * 1000);

        // Mavlink: Millimeters AMSL
        // msg.alt = ??? No method in SDK for obtaining MSL altitude.
        // djiAircraft.getFlightController().getState().getHomePointAltitude()) seems promising, but always returns 0

        // Mavlink: m/s*100
        // DJI: m/s
        msg.vx = (short) (djiAircraft.getFlightController().getState().getVelocityX() * 100); // positive values N
        msg.vy = (short) (djiAircraft.getFlightController().getState().getVelocityY() * 100); // positive values E
        msg.vz = (short) (djiAircraft.getFlightController().getState().getVelocityZ() * 100); // positive values down

        // DJI=[-180,180] where 0 is true north, Mavlink=degrees
        // TODO unspecified in Mavlink documentation whether this heading is true or magnetic
        double yaw = djiAircraft.getFlightController().getState().getAttitude().yaw;
        if (yaw < 0)
            yaw += 360;
        msg.hdg = (int) (yaw * 100);

        sendMessage(msg);
    }

    public double get_current_lat() {
        LocationCoordinate3D coord = djiAircraft.getFlightController().getState().getAircraftLocation();
        return coord.getLatitude();
    }
    public double get_current_lon() {
        LocationCoordinate3D coord = djiAircraft.getFlightController().getState().getAircraftLocation();
        return coord.getLongitude();
    }
    public float get_current_alt() {
        LocationCoordinate3D coord = djiAircraft.getFlightController().getState().getAircraftLocation();
        return coord.getAltitude();
    }

    private void send_gps_raw_int() {
        msg_gps_raw_int msg = new msg_gps_raw_int();

        LocationCoordinate3D coord = djiAircraft.getFlightController().getState().getAircraftLocation();


        msg.time_usec = getTimestampMicroseconds();
        msg.lat = (int) (coord.getLatitude() * Math.pow(10, 7));
        msg.lon = (int) (coord.getLongitude() * Math.pow(10, 7));
        // TODO msg.alt
        // TODO msg.eph
        // TODO msg.epv
        // TODO msg.vel
        // TODO msg.cog
        msg.satellites_visible = (short) djiAircraft.getFlightController().getState().getSatelliteCount();

        // DJI reports signal quality on a scale of 1-5
        // Mavlink has separate codes for fix type.
        GPSSignalLevel gpsLevel = djiAircraft.getFlightController().getState().getGPSSignalLevel();
        if (gpsLevel == GPSSignalLevel.NONE)
            msg.fix_type = GPS_FIX_TYPE.GPS_FIX_TYPE_NO_FIX;
        if (gpsLevel == GPSSignalLevel.LEVEL_0 || gpsLevel == GPSSignalLevel.LEVEL_1)
            msg.fix_type = GPS_FIX_TYPE.GPS_FIX_TYPE_NO_FIX;
        if (gpsLevel == GPSSignalLevel.LEVEL_2)
            msg.fix_type = GPS_FIX_TYPE.GPS_FIX_TYPE_2D_FIX;
        if (gpsLevel == GPSSignalLevel.LEVEL_3 || gpsLevel == GPSSignalLevel.LEVEL_4 ||
                gpsLevel == GPSSignalLevel.LEVEL_5)
            msg.fix_type = GPS_FIX_TYPE.GPS_FIX_TYPE_3D_FIX;

        sendMessage(msg);
    }

    private void send_sys_status() {
        msg_sys_status msg = new msg_sys_status();

        Log.d(TAG, "Full charge capacity: " + String.valueOf(mCFullChargeCapacity_mAh));
        Log.d(TAG, "Charge remaining: " + String.valueOf(mCChargeRemaining_mAh));
        Log.d(TAG, "Full charge capacity: " + String.valueOf(mCFullChargeCapacity_mAh));

        if (mCFullChargeCapacity_mAh > 0) {
            msg.battery_remaining = (byte) ((float) mCChargeRemaining_mAh / (float) mCFullChargeCapacity_mAh * 100.0);
            Log.d(TAG, "calc'ed bat remain: " + String.valueOf(msg.battery_remaining));
        } else {
            Log.d(TAG, "divide by zero");
            msg.battery_remaining = 100; // Prevent divide by zero
        }
        msg.voltage_battery = mVoltage_mV;
        msg.current_battery = (short) mCurrent_mA;
        sendMessage(msg);
    }

    private void send_power_status() {
        msg_power_status msg = new msg_power_status();
        sendMessage(msg);
    }

    private void send_radio_status() {
        msg_radio_status msg = new msg_radio_status();
        msg.rssi = 0; // TODO: work out units conversion (see issue #1)
        msg.remrssi = 0; // TODO: work out units conversion (see issue #1)
        sendMessage(msg);
    }

    private void send_rc_channels() {
        msg_rc_channels msg = new msg_rc_channels();
        msg.rssi = (short) mUplinkQuality;
        msg.chan1_raw = mLeftStickVertical;
        msg.chan2_raw = mLeftStickHorisontal;
        msg.chan3_raw = mRightStickVertical;
        msg.chan4_raw = mRightStickHorisontal;
        msg.chan5_raw = mC1 ?1000:2000;
        msg.chan6_raw = mC2 ?1000:2000;
        msg.chan7_raw = mC3 ?1000:2000;

        // Cancel all AI modes if stick is moved...
        if ((mLeftStickVertical    > 1550 || mLeftStickVertical    < 1450) ||
            (mLeftStickHorisontal  > 1550 || mLeftStickHorisontal  < 1450) ||
            (mRightStickVertical   > 1550 || mRightStickVertical   < 1450) ||
            (mRightStickHorisontal > 1550 || mRightStickHorisontal < 1450)) {
            if(mAIfunction_activation != 0) {
                parent.logMessageDJI("AI Mode Canceled...");
                mAIfunction_activation = 0;
            }
        }

        msg.chan8_raw = (mAIfunction_activation*100)+1000;
        msg.chancount = 8;
        sendMessage(msg);
    }

    private void send_vibration() {
        msg_vibration msg = new msg_vibration();
        sendMessage(msg);
    }

    private void send_battery_status() {
        msg_battery_status msg = new msg_battery_status();
        msg.current_consumed = mCFullChargeCapacity_mAh - mCChargeRemaining_mAh;
        msg.voltages = mCellVoltages;
        float mBatteryTemp_C = 0;
        msg.temperature = (short) (mBatteryTemp_C * 100);
        msg.current_battery = (short) (mCurrent_mA * 10);
        Log.d(TAG, "temp: " + String.valueOf(mBatteryTemp_C));
        Log.d(TAG, "send_battery_status() complete");
        // TODO cell voltages
        sendMessage(msg);
    }

    private void send_vfr_hud() {
        msg_vfr_hud msg = new msg_vfr_hud();

        // Mavlink: Current airspeed in m/s
        // DJI: unclear whether getState() returns airspeed or groundspeed
        msg.airspeed = (float) (Math.sqrt(Math.pow(djiAircraft.getFlightController().getState().getVelocityX(), 2) +
                Math.pow(djiAircraft.getFlightController().getState().getVelocityY(), 2)));

        // Mavlink: Current ground speed in m/s. For now, just echoing airspeed.
        msg.groundspeed = msg.airspeed;

        // Mavlink: Current heading in degrees, in compass units (0..360, 0=north)
        // TODO: unspecified in Mavlink documentation whether this heading is true or magnetic
        // DJI=[-180,180] where 0 is true north, Mavlink=degrees
        double yaw = djiAircraft.getFlightController().getState().getAttitude().yaw;
        if (yaw < 0)
            yaw += 360;
        msg.heading = (short) yaw;

        // Mavlink: Current throttle setting in integer percent, 0 to 100
        msg.throttle = mThrottleSetting;

        // Mavlink: Current altitude (MSL), in meters
        // DJI: relative altitude is altitude of the aircraft relative to take off location, measured by barometer, in meters.
        // DJI: home altitude is home point's altitude. Units unspecified in DJI SDK documentation. Presumably meters AMSL.
        LocationCoordinate3D coord = djiAircraft.getFlightController().getState().getAircraftLocation();
        msg.alt = (int) (coord.getAltitude());

        // Mavlink: Current climb rate in meters/second
        // DJI: m/s, positive values down
        msg.climb = -(short) (djiAircraft.getFlightController().getState().getVelocityZ());

        sendMessage(msg);
    }

    void send_home_position() {
        msg_home_position msg = new msg_home_position();

        msg.latitude = (int) (djiAircraft.getFlightController().getState().getHomeLocation().getLatitude() * Math.pow(10, 7));
        msg.longitude = (int) (djiAircraft.getFlightController().getState().getHomeLocation().getLongitude() * Math.pow(10, 7));
        // msg.altitude = (int) (djiAircraft.getFlightController().getState().getHomePointAltitude());
        msg.altitude = (int) (djiAircraft.getFlightController().getState().getGoHomeHeight());

        // msg.x = 0;
        // msg.y = 0;
        // msg.z = 0;
        // msg.approach_x = 0;
        // msg.approach_y = 0;
        // msg.approach_z = 0;
        sendMessage(msg);
    }

    public void send_statustext(String text, int severity) {
        msg_statustext msg = new msg_statustext();
        msg.text = text.getBytes();
        msg.severity = (short) severity;
        sendMessage(msg);
    }

    void send_param(int index) {
        MAVParam param = params.get(index);
        send_param(param.getParamName(),
                param.getParamValue(),
                param.getParamType(),
                params.size(),
                index);
    }

    private void send_param(String key, float value, short type, int count, int index) {

        msg_param_value msg = new msg_param_value();
        msg.setParam_Id(key);
        msg.param_value = value;
        msg.param_type = type;
        msg.param_count = count;
        msg.param_index = index;

        Log.d("Rosetta", "Sending param: " + msg.toString());

        sendMessage(msg);
    }

    void send_all_params() {
        for (int i = 0; i < params.size(); i++)
            send_param(i);
    }

    boolean loadParamsFromDJI() {
        if (getDjiAircraft() == null)
            return false;
        for (int i = 0; i < getParams().size(); i++) {
            switch (getParams().get(i).getParamName()) {
                case "DJI_CTRL_MODE":
                    getDjiAircraft().getFlightController().getControlMode(new ParamControlModeCallback(i));
                    break;
                case "DJI_ENBL_LEDS":
//                    getDjiAircraft().getFlightController().getLEDsEnabled(new ParamBooleanCallback(i));
                    break;
                case "DJI_ENBL_QSPIN":
                    getDjiAircraft().getFlightController().getQuickSpinEnabled(new ParamBooleanCallback(i));
                    break;
                case "DJI_ENBL_RADIUS":
                    getDjiAircraft().getFlightController().getMaxFlightRadiusLimitationEnabled(new ParamBooleanCallback(i));
                    break;
                case "DJI_ENBL_TFOLLOW":
                    getDjiAircraft().getFlightController().getTerrainFollowModeEnabled(new ParamBooleanCallback(i));
                    break;
                case "DJI_ENBL_TRIPOD":
                    getDjiAircraft().getFlightController().getTripodModeEnabled(new ParamBooleanCallback(i));
                    break;
                case "DJI_FAILSAFE":
                    getDjiAircraft().getFlightController().getConnectionFailSafeBehavior(new ParamConnectionFailSafeBehaviorCallback(i));
                    break;
                case "DJI_LOW_BAT":
                    getDjiAircraft().getFlightController().getLowBatteryWarningThreshold(new ParamIntegerCallback(i));
                    break;
                case "DJI_MAX_HEIGHT":
                    getDjiAircraft().getFlightController().getMaxFlightHeight(new ParamIntegerCallback(i));
                    break;
                case "DJI_MAX_RADIUS":
                    getDjiAircraft().getFlightController().getMaxFlightRadius(new ParamIntegerCallback(i));
                    break;
                case "DJI_RLPCH_MODE":
                    if (getDjiAircraft().getFlightController().getRollPitchControlMode() == RollPitchControlMode.ANGLE)
                        getParams().get(i).setParamValue(0f);
                    else if (getDjiAircraft().getFlightController().getRollPitchControlMode() == RollPitchControlMode.VELOCITY)
                        getParams().get(i).setParamValue(1f);
                    break;
                case "DJI_RTL_HEIGHT":
                    getDjiAircraft().getFlightController().getGoHomeHeightInMeters(new ParamIntegerCallback(i));
                    break;
                case "DJI_SERIOUS_BAT":
                    getDjiAircraft().getFlightController().getSeriousLowBatteryWarningThreshold(new ParamIntegerCallback(i));
                    break;
                case "DJI_SMART_RTH":
                    getDjiAircraft().getFlightController().getSmartReturnToHomeEnabled(new ParamBooleanCallback(i));
                    break;
                case "DJI_VERT_MODE":
                    if (getDjiAircraft().getFlightController().getVerticalControlMode() == VerticalControlMode.VELOCITY)
                        getParams().get(i).setParamValue(0f);
                    else if (getDjiAircraft().getFlightController().getVerticalControlMode() == VerticalControlMode.POSITION)
                        getParams().get(i).setParamValue(1f);
                    break;
                case "DJI_YAW_MODE":
                    if (getDjiAircraft().getFlightController().getYawControlMode() == YawControlMode.ANGLE)
                        getParams().get(i).setParamValue(0f);
                    else if (getDjiAircraft().getFlightController().getYawControlMode() == YawControlMode.ANGULAR_VELOCITY)
                        getParams().get(i).setParamValue(1f);
                    break;
            }
        }
        return true;
    }

    void changeParam(MAVParam param) {
        for (int i = 0; i < getParams().size(); i++) {
            if (getParams().get(i).getParamName().equals(param.getParamName())) {
                getParams().get(i).setParamValue(param.getParamValue());
                switch (param.getParamName()) {
                    case "DJI_CTRL_MODE":
                        if (param.getParamValue() == 0)
                            getDjiAircraft().getFlightController().setControlMode(ControlMode.MANUAL, new ParamWriteCompletionCallback(i));
                        else if (param.getParamValue() == 2)
                            getDjiAircraft().getFlightController().setControlMode(ControlMode.SMART, new ParamWriteCompletionCallback(i));
                        else if (param.getParamValue() == 255)
                            getDjiAircraft().getFlightController().setControlMode(ControlMode.UNKNOWN, new ParamWriteCompletionCallback(i));
                        break;
                    case "DJI_ENBL_LEDS":
//                        getDjiAircraft().getFlightController().setLEDsEnabled(param.getParamValue() > 0, new ParamWriteCompletionCallback(i));
                        break;
                    case "DJI_ENBL_QSPIN":
                        getDjiAircraft().getFlightController().setAutoQuickSpinEnabled(param.getParamValue() > 0, new ParamWriteCompletionCallback(i));
                        break;
                    case "DJI_ENBL_RADIUS":
                        getDjiAircraft().getFlightController().setMaxFlightRadiusLimitationEnabled((param.getParamValue() > 0), new ParamWriteCompletionCallback(i));
                        break;
                    case "DJI_ENBL_TFOLLOW":
                        getDjiAircraft().getFlightController().setTerrainFollowModeEnabled(param.getParamValue() > 0, new ParamWriteCompletionCallback(i));
                        break;
                    case "DJI_ENBL_TRIPOD":
                        getDjiAircraft().getFlightController().setTripodModeEnabled(param.getParamValue() > 0, new ParamWriteCompletionCallback(i));
                        break;
                    case "DJI_FAILSAFE":
                        if (param.getParamValue() == 0)
                            getDjiAircraft().getFlightController().setConnectionFailSafeBehavior(ConnectionFailSafeBehavior.HOVER, new ParamWriteCompletionCallback(i));
                        else if (param.getParamValue() == 1)
                            getDjiAircraft().getFlightController().setConnectionFailSafeBehavior(ConnectionFailSafeBehavior.LANDING, new ParamWriteCompletionCallback(i));
                        else if (param.getParamValue() == 2)
                            getDjiAircraft().getFlightController().setConnectionFailSafeBehavior(ConnectionFailSafeBehavior.GO_HOME, new ParamWriteCompletionCallback(i));
                        else if (param.getParamValue() == 255)
                            getDjiAircraft().getFlightController().setConnectionFailSafeBehavior(ConnectionFailSafeBehavior.UNKNOWN, new ParamWriteCompletionCallback(i));
                        break;
                    case "DJI_LOW_BAT":
                        getDjiAircraft().getFlightController().setLowBatteryWarningThreshold(Math.round(param.getParamValue()), new ParamWriteCompletionCallback(i));
                        break;
                    case "DJI_MAX_HEIGHT":
                        getDjiAircraft().getFlightController().setMaxFlightHeight(Math.round(param.getParamValue()), new ParamWriteCompletionCallback(i));
                        break;
                    case "DJI_MAX_RADIUS":
                        getDjiAircraft().getFlightController().setMaxFlightRadius(Math.round(param.getParamValue()), new ParamWriteCompletionCallback(i));
                        break;
                    case "DJI_RLPCH_MODE":
                        if (param.getParamValue() == 0)
                            getDjiAircraft().getFlightController().setRollPitchControlMode(RollPitchControlMode.ANGLE);
                        else if (param.getParamValue() == 1)
                            getDjiAircraft().getFlightController().setRollPitchControlMode(RollPitchControlMode.VELOCITY);
                        break;
                    case "DJI_RTL_HEIGHT":
                        getDjiAircraft().getFlightController().setGoHomeHeightInMeters(Math.round(param.getParamValue()), new ParamWriteCompletionCallback(i));
                        break;
                    case "DJI_SERIOUS_BAT":
                        getDjiAircraft().getFlightController().setSeriousLowBatteryWarningThreshold(Math.round(param.getParamValue()), new ParamWriteCompletionCallback(i));
                        break;
                    case "DJI_SMART_RTH":
                        getDjiAircraft().getFlightController().setSmartReturnToHomeEnabled(param.getParamValue() > 0, new ParamWriteCompletionCallback(i));
                        break;
                    case "DJI_VERT_MODE":
                        if (param.getParamValue() == 0)
                            getDjiAircraft().getFlightController().setVerticalControlMode(VerticalControlMode.VELOCITY);
                        else if (param.getParamValue() == 1)
                            getDjiAircraft().getFlightController().setVerticalControlMode(VerticalControlMode.POSITION);
                        break;
                    case "DJI_YAW_MODE":
                        if (param.getParamValue() == 0)
                            getDjiAircraft().getFlightController().setYawControlMode(YawControlMode.ANGLE);
                        else if (param.getParamValue() == 1)
                            getDjiAircraft().getFlightController().setYawControlMode(YawControlMode.ANGULAR_VELOCITY);
                        break;
                    default:
                        parent.logMessageDJI("Unknown parameter name");

                }
                send_param(i);
                break;
            }
        }
        Log.d(TAG, "Request to set param that doesn't exist");

    }


    void send_mission_count() {
        msg_mission_count msg = new msg_mission_count();
        msg.mission_type = MAV_MISSION_TYPE.MAV_MISSION_TYPE_MISSION;
        sendMessage(msg);
    }

    void send_mission_item(int i) {
        msg_mission_item msg = new msg_mission_item();

        if (i == 0) {
            msg.x = (float) (djiAircraft.getFlightController().getState().getHomeLocation().getLatitude());
            msg.y = (float) (djiAircraft.getFlightController().getState().getHomeLocation().getLongitude());
            msg.z = 0;
        } else {
            Waypoint wp = Objects.requireNonNull(getWaypointMissionOperator().getLoadedMission()).getWaypointList().get(i - 1);
            msg.x = (float) (wp.coordinate.getLatitude());
            msg.y = (float) (wp.coordinate.getLongitude());
            msg.z = wp.altitude;
        }

        msg.seq = i;
        msg.frame = MAV_FRAME.MAV_FRAME_GLOBAL_RELATIVE_ALT;
        sendMessage(msg);
    }

    public void send_mission_item_reached(int seq) {
        msg_mission_item_reached msg = new msg_mission_item_reached();
        msg.seq = seq;
        sendMessage(msg);
    }

    // Send mission accepted back to Mavlink...
    void send_mission_ack(int status) {
        parent.logMessageDJI("Mavlink: "+status);
        msg_mission_ack msg = new msg_mission_ack();
        msg.type = (short)status;
        msg.mission_type = MAV_MISSION_TYPE.MAV_MISSION_TYPE_MISSION;
        sendMessage(msg);
    }

    public void fetch_gcs_mission() {
        request_mission_list();
    }

    private void request_mission_list() {
        msg_mission_request_list msg = new msg_mission_request_list();
        sendMessage(msg);
    }

    void request_mission_item(int seq) {
        msg_mission_request msg = new msg_mission_request();
        msg.seq = seq;
        msg.mission_type = MAV_MISSION_TYPE.MAV_MISSION_TYPE_MISSION;
        sendMessage(msg);
    }

    void startWaypointMission() {
        mAutonomy = false;
        parent.logMessageDJI("start WaypointMission()");
        
        if (getWaypointMissionOperator() == null) {
            parent.logMessageDJI("start WaypointMission() - WaypointMissionOperator null");
            return;
        }
        if (getWaypointMissionOperator().getCurrentState() == WaypointMissionState.READY_TO_EXECUTE) {
            parent.logMessageDJI("Ready to execute mission!\n");
        } else {
            parent.logMessageDJI("Not ready to execute mission\n");
            parent.logMessageDJI(getWaypointMissionOperator().getCurrentState().getName());
            return;
        }
        if (mSafetyEnabled) {
            parent.logMessageDJI("You must turn off the safety to start mission");
        } else {
            getWaypointMissionOperator().startMission(new CommonCallbacks.CompletionCallback() {
                @Override
                public void onResult(DJIError djiError) {
                    if (djiError != null)
                        parent.logMessageDJI("Error: " + djiError.toString());
                    else
                        parent.logMessageDJI("Mission started!");
                }
            });
        }
    }

    public void stopWaypointMission() {
        mAutonomy = false;
        if (getWaypointMissionOperator() == null) {
            parent.logMessageDJI("stopWaypointMission() - mWaypointMissionOperator null");
            return;
        }

        if (getWaypointMissionOperator().getCurrentState() == WaypointMissionState.EXECUTING) {
            parent.logMessageDJI("Stopping mission...\n");
            getWaypointMissionOperator().stopMission(new CommonCallbacks.CompletionCallback() {
                @Override
                public void onResult(DJIError djiError) {
                    if (djiError != null)
                        parent.logMessageDJI("Error: " + djiError.toString());
                    else
                        parent.logMessageDJI("Mission stopped!\n");
                }
            });
        }
    }

    void pauseWaypointMission() {
        mAutonomy = false;
        if (getWaypointMissionOperator() == null) {
            parent.logMessageDJI("pauseWaypointMission() - mWaypointMissionOperator null");
            return;
        }

        if (getWaypointMissionOperator().getCurrentState() == WaypointMissionState.EXECUTING) {
            parent.logMessageDJI("Pausing mission...\n");
            getWaypointMissionOperator().pauseMission(new CommonCallbacks.CompletionCallback() {
                @Override
                public void onResult(DJIError djiError) {
                    if (djiError != null)
                        parent.logMessageDJI("Error: " + djiError.toString());
                    else
                        parent.logMessageDJI("Mission paused!\n");
                }
            });
        }
    }

    void resumeWaypointMission() {
        mAutonomy = false;
        if (isSafetyEnabled()) {
            parent.logMessageDJI(parent.getResources().getString(R.string.safety_launch));
            send_command_ack(MAV_CMD_NAV_TAKEOFF, MAV_RESULT.MAV_RESULT_DENIED);
            return;
        }

        if (getWaypointMissionOperator() == null) {
            parent.logMessageDJI("resumeWaypointMission() - mWaypointMissionOperator null");
            return;
        }

        if (getWaypointMissionOperator().getCurrentState() == WaypointMissionState.EXECUTION_PAUSED) {
            parent.logMessageDJI("Resuming mission...\n");
            getWaypointMissionOperator().resumeMission(new CommonCallbacks.CompletionCallback() {
                @Override
                public void onResult(DJIError djiError) {
                    if (djiError != null)
                        parent.logMessageDJI("Error: " + djiError.toString());
                    else {
                        parent.logMessageDJI("Mission resumed!\n");
                        mGCSCommandedMode = NOT_USING_GCS_COMMANDED_MODE;
                    }
                }
            });
        }
    }

    void do_takeoff() {
        mAutonomy = false;
        if (mSafetyEnabled) {
            parent.logMessageDJI(parent.getResources().getString(R.string.safety_launch));
            send_command_ack(MAV_CMD_NAV_TAKEOFF, MAV_RESULT.MAV_RESULT_DENIED);
            return;
        }

        if (getWaypointMissionOperator().getCurrentState() == WaypointMissionState.READY_TO_EXECUTE) {
            startWaypointMission();
            send_command_ack(MAV_CMD_NAV_TAKEOFF, MAV_RESULT.MAV_RESULT_ACCEPTED);
        } else {
            parent.logMessageDJI("Initiating takeoff");
            djiAircraft.getFlightController().startTakeoff(new CommonCallbacks.CompletionCallback() {
                @Override
                public void onResult(DJIError djiError) {
                    if (djiError != null) {
                        parent.logMessageDJI("Error: " + djiError.toString());
                        send_command_ack(MAV_CMD_NAV_TAKEOFF, MAV_RESULT.MAV_RESULT_FAILED);
                    } else {
                        parent.logMessageDJI("Takeoff successful!\n");
                        send_command_ack(MAV_CMD_NAV_TAKEOFF, MAV_RESULT.MAV_RESULT_ACCEPTED);
                    }
                    mGCSCommandedMode = NOT_USING_GCS_COMMANDED_MODE;
                }
            });
        }
    }

    void do_land() {
        mAutonomy = false;
        parent.logMessageDJI("Initiating landing");
        djiAircraft.getFlightController().startLanding(new CommonCallbacks.CompletionCallback() {
            @Override
            public void onResult(DJIError djiError) {
                if (djiError != null)
                    parent.logMessageDJI("Error: " + djiError.toString());
                else {
                    parent.logMessageDJI("Landing successful!\n");
                    mMotorsArmed = false;
                }
            }
        });
    }

    void do_go_home() {
        mAutonomy = false;
        parent.logMessageDJI("Initiating Go Home");
        djiAircraft.getFlightController().startGoHome(new CommonCallbacks.CompletionCallback() {
            @Override
            public void onResult(DJIError djiError) {
                if (djiError != null)
                    parent.logMessageDJI("Error: " + djiError.toString());
                else
                    parent.logMessageDJI("Go home successful!\n");
            }
        });
    }

    /********************************************
     * Motion implementation                    *
     ********************************************/


    void do_set_Gimbal(float channel, float value)
    {
        Rotation.Builder builder = new Rotation.Builder().mode(RotationMode.ABSOLUTE_ANGLE).time(2);
        float param = (value-(float)1500.0)/(float)5.5;
        if (channel == 9) {
            builder.pitch(param);
        } else if (channel == 8) {
            builder.yaw(param);
        }
        if (mGimbal == null) {
            return;
        }


        mGimbal.setMode(GimbalMode.FREE,djiError -> {
            if (djiError != null)
                parent.logMessageDJI("Error: " + djiError.toString());
        });

        mGimbal.rotate(builder.build(), djiError -> {
            if (djiError != null)
                parent.logMessageDJI("Error: " + djiError.toString());
        });
        util.safeSleep(500);
    }

    // --------------------------------------------------------------------------------
    public void do_set_motion_absolute(double Lat, double Lon, float alt, float head) {

        m_Destination_Lat = Lat;
        m_Destination_Lon = Lon;
        m_Destination_Alt = alt;
        m_Destination_Yaw = head;

        if(mAutonomy == false) {
            mMoveToDataTask = new MoveTo();
            mMoveToDataTimer = new Timer();
            mMoveToDataTimer.schedule(mMoveToDataTask, 100, 200);
            mAutonomy = true;
        }
    }

    class MoveTo extends TimerTask {
        int detection = 0;  // Wi might fly past the point se we look for consecutive hits...

        @Override
        public void run() {
            while(mAutonomy == true) {
                FlightControllerState coord = djiAircraft.getFlightController().getState();
                double dist = getRangeBetweenWaypoints_m(m_Destination_Lat, m_Destination_Lon, m_Destination_Alt,
                        coord.getAircraftLocation().getLatitude(), coord.getAircraftLocation().getLongitude(), coord.getAircraftLocation().getAltitude());

                // Mavlink uses 0-360 while DJI uses +-180... We use 0-360 for math...
                double yaw = coord.getAttitude().yaw;
                if (yaw < 0) yaw = yaw + 360.0;

                // Make the error +-180 deg. error  + is we need to turn more right...
                double yawerror = (m_Destination_Yaw - yaw);
                if (yawerror > 180.0) yawerror = yawerror - 360.0;
                if (yawerror < -180.0) yawerror = yawerror + 360.0;

                // If we are there...
                if (dist < 0.25 && Math.abs(yawerror) < 5.0) {
                    do_set_motion_velocity(0, 0, 0, 0);
                    // If we got 5 (1 sec) consecutive hits at the right spot....
                    if (++detection > 5) {
                        mAutonomy = false;
                        return;
                    }
                } else {
                    detection = 0;
                }

                // Find the heading difference compared to bearing...
                double brng = getBearingBetweenWaypoints(coord.getAircraftLocation().getLatitude(), coord.getAircraftLocation().getLongitude(),
                        m_Destination_Lat, m_Destination_Lon);
                // Drone heading - Waypoint bearing... to +-180 deg...
                double direction = (yaw - brng);
                if (direction > 180.0) direction = direction - 360.0;
                if (direction < -180.0) direction = direction + 360.0;

                double hypotenus = getRangeBetweenWaypoints_m(coord.getAircraftLocation().getLatitude(), coord.getAircraftLocation().getLongitude(), 0,
                        m_Destination_Lat, m_Destination_Lon, 0);

                // Fnd the X and Y distance...
                double fw_dist = Math.sin(direction) * hypotenus;
                double right_dist = Math.cos(direction) * hypotenus;

                // Make a very simple P controller...
                final double motion_p = 1.0;
                float fwmotion = (float) Math.min(fw_dist*motion_p, 2.0); // Positive is forward...
                float rightmotion = (float) Math.min(right_dist*motion_p, 2.0); // Positive is right ...
                float upmotion = (float) Math.min(m_Destination_Alt - coord.getAircraftLocation().getAltitude()*motion_p, 1.0); // positive is up ...
                float clockmotion = (float) Math.min(yawerror*motion_p, 10); // positive is with clock ...

                do_set_motion_velocity(fwmotion, rightmotion, upmotion, clockmotion);
            }
        }
    }

    // --------------------------------------------------------------------------------
    void do_set_motion_velocity(float x, float y, float z, float yaw) {
        mPitch = y;   mRoll = x;   mYaw = yaw;  mThrottle = z;

        // Only allow velocity movement in P mode...
        if( rcmode != avtivemode) {
            return;
        }

        // If first time...
        if (null == mSendVirtualStickDataTimer) {

            // After a manual mode change, we might loose the JOYSTICK mode...
            if (lastMode != FlightMode.JOYSTICK) {
                mFlightController.setVirtualStickModeEnabled(true, djiError -> {
                        if (djiError != null)
                            parent.logMessageDJI("Velocity Mode not enabled Error: " + djiError.toString());
                    }
                );
            }

            mSendVirtualStickDataTask = new SendVelocityDataTask();
            mSendVirtualStickDataTimer = new Timer();
            mSendVirtualStickDataTimer.schedule(mSendVirtualStickDataTask, 100, 150);
        }else{
            mSendVirtualStickDataTask.repeat = 14;
        }
    }

    // Run the velocity command for 2 seconds...
    class SendVelocityDataTask extends TimerTask {
        public int repeat = 14;

        @Override
        public void run() {
            if (mFlightController != null) {
                if(--repeat <= 0){
                    mSendVirtualStickDataTimer.cancel();
                    mSendVirtualStickDataTimer.purge();
                    mSendVirtualStickDataTimer = null;

                    // After a manual mode change, we might loose the JOYSTICK mode...
                    mFlightController.setVirtualStickModeEnabled(false, djiError -> {
                                if (djiError != null)
                                    parent.logMessageDJI("Velocity Mode not disabled Error: " + djiError.toString());
                            }
                    );
                    lastMode = FlightMode.GPS_ATTI;
      //              parent.logMessageDJI("Motion done!\n");
                    return;
                }
//                parent.logMessageDJI("X: " + String.valueOf(mPitch) + " Y: " + String.valueOf(mRoll) + " Z: " + String.valueOf(mYaw) + " T: " + String.valueOf(mThrottle));
                mFlightController.sendVirtualStickFlightControlData(
                        new FlightControlData(mPitch, mRoll, mYaw, mThrottle)
                        , djiError -> {
                            if (djiError != null)
                                parent.logMessageDJI("Motion Error: " + djiError.toString());
                        }
                );
            }
        }
    }

    // Follow me is not used by Mavlink for now, in the DJI implementation it for a
    // limit to a few meters from current location.
    public void startSimpleFollowMe()
    {
        if(fmmo == null){
            fmmo = DJISDKManager.getInstance().getMissionControl().getFollowMeMissionOperator();
        }
        final FollowMeMissionOperator followMeMissionOperator  = fmmo;
        if (followMeMissionOperator.getCurrentState().equals(MissionState.READY_TO_EXECUTE)){
            followMeMissionOperator.startMission(new FollowMeMission(FollowMeHeading.TOWARD_FOLLOW_POSITION,m_Latitude , m_Longitude, m_alt)
                    , djiError -> {
                        if(djiError != null){
                            parent.logMessageDJI(djiError.getDescription());
                        } else {
                            parent.logMessageDJI("Mission Start: Successfully");
                        }
                    });
        }
    }

    public void updateSimpleFollowMe(){
        if(fmmo == null){
            fmmo = DJISDKManager.getInstance().getMissionControl().getFollowMeMissionOperator();
        }
        final FollowMeMissionOperator followMeMissionOperator  = fmmo;
        if(followMeMissionOperator.getCurrentState().equals(FollowMeMissionState.EXECUTING)) {
            followMeMissionOperator.updateFollowingTarget(new LocationCoordinate2D(m_Latitude , m_Longitude),
                    error -> {
                        if (error != null) {
                            parent.logMessageDJI(followMeMissionOperator.getCurrentState().getName() + " " + error.getDescription());
                        } else {
                            parent.logMessageDJI("Mission Update Successfully");
                        }
                    });
        }
    }
/*
    void follow_me() {

        FollowMeMissionOperator missionOperator = new FollowMeMissionOperator();
        FollowMeMission followMeInitSettings = new FollowMeMission(FollowMeHeading.TOWARD_FOLLOW_POSITION, locationCoordinate3D.getLatitude(), locationCoordinate3D.getLongitude(), locationCoordinate3D.getAltitude());

        missionOperator.startMission(followMeInitSettings, new CommonCallbacks.CompletionCallback() {
            @Override
            public void onResult(DJIError djiError) {

                if (djiError == null) {
                    Thread locationUpdateThread = new Thread(new Runnable() {
                        @Override
                        public void run() {
                            while (!Thread.currentThread().isInterrupted()) {
                                final Location newLocation = highAccuracyLocationTracker.getLocation();
                                missionOperator.updateFollowingTarget(new LocationCoordinate2D(newLocation.getLatitude(), newLocation.getLongitude()), new CommonCallbacks.CompletionCallback() {
                                    @Override
                                    public void onResult(DJIError djiError) {
                                        if (djiError != null) {
                                            parent.logMessageDJI("Follow.updateTarget failed: " + djiError.getDescription());
                                        }
                                    }
                                });

                                try {
                                    Thread.sleep(100);
                                } catch (InterruptedException e) {
                                    // The exception clears the interrupt flag so I'll refresh the flag otherwise the thread keeps running
                                    Thread.currentThread().interrupt();
                                }
                            }
                        }
                    });

                    locationUpdateThread.start();
                }
            }
        });
    }

 */

//    public void set_flight_mode(FlightControlState djiMode) {
//        // TODO
//        return;
//    }

    void takePhoto() {
        SettingsDefinitions.ShootPhotoMode photoMode = SettingsDefinitions.ShootPhotoMode.SINGLE;
        djiAircraft.getCamera().startShootPhoto(djiError -> {
            if (djiError == null) {
                parent.logMessageDJI("Took photo");
                send_command_ack(MAV_CMD_DO_DIGICAM_CONTROL, MAV_RESULT.MAV_RESULT_ACCEPTED);
            } else {
                parent.logMessageDJI("Error taking photo: " + djiError.toString());
                send_command_ack(MAV_CMD_DO_DIGICAM_CONTROL, MAV_RESULT.MAV_RESULT_FAILED);
            }
        });
    }

    /********************************************
     * CompletionCallback implementation        *
     ********************************************/

    @Override
    public void onResult(DJIError djiError) {

    }

    public void echoLoadedMission() {
        getWaypointMissionOperator().downloadMission(
                djiError -> {
                    if (djiError == null) {
                        parent.logMessageDJI("Waypoint mission successfully downloaded");
                    } else {
                        parent.logMessageDJI("Error downloading: " + djiError.getDescription());
                    }
                });
        WaypointMission wm = getWaypointMissionOperator().getLoadedMission();
        if (wm == null) {
            parent.logMessageDJI("No mission loaded");
            return;
        }
        parent.logMessageDJI("Waypoint count: " + wm.getWaypointCount());
        for (Waypoint w : wm.getWaypointList())
            parent.logMessageDJI(w.coordinate.toString());
        parent.logMessageDJI("State: " + getWaypointMissionOperator().getCurrentState().getName());
    }

    /********************************************
     * Parameter callbacks                      *
     ********************************************/

    public class ParamIntegerCallback implements CommonCallbacks.CompletionCallbackWith<Integer> {
        private int paramIndex;

        ParamIntegerCallback(int paramIndex) {
            this.paramIndex = paramIndex;
        }

        @Override
        public void onSuccess(Integer integer) {
            getParams().get(paramIndex).setParamValue((float) integer);
//            parent.logMessageDJI("Fetched param from DJI: " + getParams().get(paramIndex).getParamName() + "=" + String.valueOf(integer));
        }

        @Override
        public void onFailure(DJIError djiError) {
            getParams().get(paramIndex).setParamValue(-99.0f);
//            parent.logMessageDJI("Param fetch fail: " + getParams().get(paramIndex).getParamName());
        }
    }

    public class ParamBooleanCallback implements CommonCallbacks.CompletionCallbackWith<Boolean> {
        private int paramIndex;

        ParamBooleanCallback(int paramIndex) {
            this.paramIndex = paramIndex;
        }

        @Override
        public void onSuccess(Boolean aBoolean) {
            getParams().get(paramIndex).setParamValue(aBoolean ? 1.0f : 0.0f);
//            parent.logMessageDJI("Fetched param from DJI: " + getParams().get(paramIndex).getParamName() + "=" + String.valueOf(aBoolean));
        }

        @Override
        public void onFailure(DJIError djiError) {
            getParams().get(paramIndex).setParamValue(-99.0f);
//            parent.logMessageDJI("Param fetch fail: " + getParams().get(paramIndex).getParamName());
        }
    }

    public class ParamControlModeCallback implements CommonCallbacks.CompletionCallbackWith<ControlMode> {
        private int paramIndex;

        ParamControlModeCallback(int paramIndex) {
            this.paramIndex = paramIndex;
        }

        @Override
        public void onSuccess(ControlMode cMode) {
            if (cMode == ControlMode.MANUAL)
                getParams().get(paramIndex).setParamValue(0f);
            else if (cMode == ControlMode.SMART)
                getParams().get(paramIndex).setParamValue(2f);
            else if (cMode == ControlMode.UNKNOWN)
                getParams().get(paramIndex).setParamValue(255f);

//            parent.logMessageDJI("Fetched param from DJI: " + getParams().get(paramIndex).getParamName() + "=" + String.valueOf(cMode));
        }

        @Override
        public void onFailure(DJIError djiError) {
            getParams().get(paramIndex).setParamValue(-99.0f);
//            parent.logMessageDJI("Param fetch fail: " + getParams().get(paramIndex).getParamName());
        }
    }

    public class ParamConnectionFailSafeBehaviorCallback implements CommonCallbacks.CompletionCallbackWith<ConnectionFailSafeBehavior> {
        private int paramIndex;

        ParamConnectionFailSafeBehaviorCallback(int paramIndex) {
            this.paramIndex = paramIndex;
        }

        @Override
        public void onSuccess(ConnectionFailSafeBehavior behavior) {
            if (behavior == ConnectionFailSafeBehavior.HOVER)
                getParams().get(paramIndex).setParamValue(0f);
            else if (behavior == ConnectionFailSafeBehavior.LANDING)
                getParams().get(paramIndex).setParamValue(1f);
            else if (behavior == ConnectionFailSafeBehavior.GO_HOME)
                getParams().get(paramIndex).setParamValue(2f);
            else if (behavior == ConnectionFailSafeBehavior.UNKNOWN)
                getParams().get(paramIndex).setParamValue(255f);

            parent.logMessageDJI("Fetched param from DJI: " + getParams().get(paramIndex).getParamName() + "=" + behavior);
        }

        @Override
        public void onFailure(DJIError djiError) {
            getParams().get(paramIndex).setParamValue(-99.0f);
            parent.logMessageDJI("Param fetch fail: " + getParams().get(paramIndex).getParamName());
        }
    }

    public class ParamWriteCompletionCallback implements CommonCallbacks.CompletionCallback {

        private int paramIndex;

        ParamWriteCompletionCallback(int paramIndex) {
            this.paramIndex = paramIndex;
        }

        @Override
        public void onResult(DJIError djiError) {
            if (djiError == null)
                parent.logMessageDJI(("Wrote param to DJI: " + getParams().get(paramIndex).getParamName()));
            else
                parent.logMessageDJI(("Error writing param to DJI: " + getParams().get(paramIndex).getParamName()));
        }
    }

    public class CellVoltageCompletionCallback implements CommonCallbacks.CompletionCallbackWith<Integer[]> {

        @Override
        public void onSuccess(Integer[] integer) {
            for (int i = 0; i < integer.length; i++)
                mCellVoltages[i] = integer[i];
            Log.d(TAG, "got cell voltages, v[0] =" + mCellVoltages[0]);
        }

        @Override
        public void onFailure(DJIError djiError) {

        }

    }

    /********************************************
     * Start Stop  callbacks                      *
     ********************************************/

    void startRecordingVideo() {
        djiAircraft.getCamera().startRecordVideo(djiError -> {
            if (djiError == null) {
                parent.logMessageDJI("Started recording video");
                send_command_ack(MAV_CMD_VIDEO_START_CAPTURE, MAV_RESULT.MAV_RESULT_ACCEPTED);
            } else {
                parent.logMessageDJI("Error starting video recording: " + djiError.toString());
                send_command_ack(MAV_CMD_VIDEO_START_CAPTURE, MAV_RESULT.MAV_RESULT_FAILED);
            }
        });
    }

    void stopRecordingVideo() {
        djiAircraft.getCamera().stopRecordVideo(djiError -> {
            if (djiError == null) {
                parent.logMessageDJI("Stopped recording video");
                send_command_ack(MAV_CMD_VIDEO_STOP_CAPTURE, MAV_RESULT.MAV_RESULT_ACCEPTED);
            } else {
                parent.logMessageDJI("Error stopping video recording: " + djiError.toString());
                send_command_ack(MAV_CMD_VIDEO_STOP_CAPTURE, MAV_RESULT.MAV_RESULT_FAILED);
            }
        });
    }

    double getBearingBetweenWaypoints(double lat1,double lon1, double lat2,double lon2) {
        // (all angles in radians)
        double rad = Math.PI / 180.0;
        lat1 = lat1*rad;
        lat2 = lat2*rad;
        lon1 = lon1*rad;
        lon2 = lon2*rad;
        double y = Math.sin(lat2 - lat1) * Math.cos(lon2);
        double x = Math.cos(lon1) * Math.sin(lon2) -
                Math.sin(lon1) * Math.cos(lon2) * Math.cos(lat2 - lat1);
        double z = Math.atan2(y, x);
        double brng = (z * 180 / Math.PI + 360) % 360; // in degrees
        return brng;
    }

    // --------------------------------------------------------------------------------
    double getRangeBetweenWaypoints_m( double lat1,double lon1, float el1,double lat2,double lon2,float el2) {
        final int R = 6371; // Radius of the earth
        double latDistance = Math.toRadians(lat2 - lat1);
        double lonDistance = Math.toRadians(lon2 - lon1);
        double a = Math.sin(latDistance / 2) * Math.sin(latDistance / 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                * Math.sin(lonDistance / 2) * Math.sin(lonDistance / 2);
        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
        double distance = R * c * 1000; // convert to meters
        double height = el1 - el2;
        distance = Math.pow(distance, 2) + Math.pow(height, 2);
        return Math.sqrt(distance);
    }

}