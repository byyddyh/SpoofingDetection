package cn.byyddyh.spoofingdetection;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.fragment.app.Fragment;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.location.GnssMeasurementsEvent;
import android.location.GnssNavigationMessage;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.provider.Settings;
import android.util.Log;

import com.amap.api.location.AMapLocationClient;
import com.amap.api.maps.MapsInitializer;
import com.amap.api.services.core.ServiceSettings;
import com.google.android.material.bottomnavigation.BottomNavigationView;

import java.util.Random;

import cn.byyddyh.spoofingdetection.pseudorange.Ecef2EnuConverter;
import cn.byyddyh.spoofingdetection.pseudorange.Ecef2LlaConverter;
import cn.byyddyh.spoofingdetection.pseudorange.Lla2EcefConverter;

public class MainActivity extends AppCompatActivity implements LocationListener {

    /**
     * 视图数据
     */
    private BottomNavigationView bottomNavigationView;
    @SuppressLint("StaticFieldLeak")
    private static final SettingsFragment settingsFragment;
    @SuppressLint("StaticFieldLeak")
    private static final LogFragment logFragment;
    public static final MapFragment mapFragment;
    /**
     * 判断是否使用抗GPS欺骗策略
     */
    public static boolean isUsedAntiSpoof;

    /**
     * 传感器数据
     */
    public static SensorManager mSensor_Toggle;
    public static SensorManager mSensor_Stream;
    public static LocationManager mLocationmanager;
    public static boolean ephemerisFlag = true;
    private GnssMeasurementsEvent.Callback gnssMeasurementsEventListener;
    private GnssNavigationMessage.Callback gnssNavigationMessageListener;

    static {
        settingsFragment = new SettingsFragment();
        logFragment = new LogFragment();
        mapFragment = new MapFragment();
    }

    @SuppressLint("StaticFieldLeak")
    public static FileLogger fileLogger;

    public static RealTimePositionVelocityCalculator mRealTimePositionVelocityCalculator;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        Context context = this;
        //定位隐私政策同意
        AMapLocationClient.updatePrivacyShow(context, true, true);
        AMapLocationClient.updatePrivacyAgree(context, true);
        //地图隐私政策同意
        MapsInitializer.updatePrivacyShow(context, true, true);
        MapsInitializer.updatePrivacyAgree(context, true);
        //搜索隐私政策同意
        ServiceSettings.updatePrivacyShow(context, true, true);
        ServiceSettings.updatePrivacyAgree(context, true);

        fileLogger = new FileLogger(getApplicationContext());
        logFragment.setFileLogger(fileLogger);

        bottomNavigationView = findViewById(R.id.bottomNav);

        if (savedInstanceState == null) {
            getSupportFragmentManager().beginTransaction().replace(R.id.fragmentContainer, settingsFragment).commit();
        }

        bottomNavigationView.setOnNavigationItemSelectedListener(item -> {
            Fragment fragment = null;

            switch (item.getItemId()) {
                case R.id.settings:
                    fragment = settingsFragment;
                    break;
                case R.id.logger:
                    fragment = logFragment;
                    break;
                case R.id.map:
                    fragment = mapFragment;
                    break;
            }

            getSupportFragmentManager().beginTransaction().replace(R.id.fragmentContainer, fragment).commit();

            return true;
        });

        Intent intent = new Intent(this, MyService.class);
        startService(intent);

        // 获取传感器对象
        mSensor_Toggle = (SensorManager) getSystemService(SENSOR_SERVICE);
        mSensor_Stream = (SensorManager) getSystemService(SENSOR_SERVICE);
        mLocationmanager = (LocationManager) getSystemService(LOCATION_SERVICE);

        // 初始化RealTimePositionVelocityCalculator
        initCalculator();

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            gnssNavigationMessageListener = new GnssNavigationMessage.Callback() {
                @Override
                public void onGnssNavigationMessageReceived(GnssNavigationMessage event) {
                    mRealTimePositionVelocityCalculator.onGnssNavigationMessageReceived(event);
                    LogFragment.logText("text", "接收到了导航信息数据" + event.getSvid());
                }
            };

            gnssMeasurementsEventListener = new GnssMeasurementsEvent.Callback() {
                @SuppressLint("LongLogTag")
                @Override
                public void onGnssMeasurementsReceived(GnssMeasurementsEvent eventArgs) {
                    mRealTimePositionVelocityCalculator.onGnssMeasurementsReceived(eventArgs);
                }
            };
        }

        // 开始定位传感器
        start_GPS_Sensors();
    }

    private void initCalculator() {
        mRealTimePositionVelocityCalculator = new RealTimePositionVelocityCalculator();
        mRealTimePositionVelocityCalculator.setMainActivity(this);
        mRealTimePositionVelocityCalculator.setResidualPlotMode(
                RealTimePositionVelocityCalculator.RESIDUAL_MODE_DISABLED, null /* fixedGroundTruth */);

        mapFragment.setPositionVelocityCalculator(mRealTimePositionVelocityCalculator);
        mRealTimePositionVelocityCalculator.setMapFragment(mapFragment);
    }

    @Override
    protected void onStop() {
        super.onStop();

        if (mOriBufferReady) {
            stop_Software_Sensors();
        }

        stop_GPS_Sensors();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();

        mapFragment.destroy();
    }

    private boolean isRefuse;

    /**
     * 带回授权结果
     */
    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == 1024 && Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            // 检查是否有权限
            if (Environment.isExternalStorageManager()) {
                isRefuse = false;
                // 授权成功
            } else {
                isRefuse = true;
                // 授权失败
            }
        }
    }

    private static boolean mOriBufferReady = false;
    private static double mLin_Acc_Time = 0;
    private static final double NS2S = 1.0f / 1000000000.0f; // nanosec to sec

    public static double[] mLin_Acc_Buffer = new double[3];
    public static double[] pos_mea = new double[3];
    public static double[] vel_mea = new double[3];
    private static final int acc_len = 200;
    public static double[][] acc_mea = new double[acc_len][3];
    public static double[] acc_mea_temp = new double[3];
    private static int acc_count = 0;
    private static int display_count = 0;

    /**
     * 软件传感器监听器
     */
    public static class My_Software_SensorListener implements SensorEventListener {
        @Override
        public void onAccuracyChanged(Sensor arg0, int arg1) {
        }

        @Override
        public void onSensorChanged(SensorEvent event) {
            double timestamp_sec = event.timestamp * NS2S;

            if (event.sensor.getType() == Sensor.TYPE_LINEAR_ACCELERATION) {
                for (int i = 0; i < event.values.length; i++) {
                    mLin_Acc_Buffer[i] = event.values[i];
                }

                double delta_timestamp_sec = timestamp_sec - mLin_Acc_Time;
                mLin_Acc_Time = timestamp_sec;

                if (acc_count < acc_len) {
                    // 记录数据
                    System.arraycopy(mLin_Acc_Buffer, 0, acc_mea[acc_count], 0, 3);
                    acc_count++;

                    // 计算加速度均值
                    if (acc_count == acc_len - 1) {
                        for (int i = 100; i < acc_len; i++) {
                            for (int j = 0; j < 3; j++) {
                                acc_mea_temp[j] = acc_mea_temp[j] + acc_mea[i][j] / (acc_len - 100);
                            }
                        }

                        acc_mea_temp[0] = 0;
                        acc_mea_temp[1] = 0;
                    }
                } else {
                    for (int i = 0; i < 3; i++) {
                        if (i == 2) {
                            mLin_Acc_Buffer[i] = (mLin_Acc_Buffer[i] - acc_mea_temp[i]) / 10;
                        }
                        vel_mea[i] = vel_mea[i] + mLin_Acc_Buffer[i] * delta_timestamp_sec;
                        pos_mea[i] = pos_mea[i] + vel_mea[i] * delta_timestamp_sec;
                    }

                    display_count++;
                    if (display_count == 10) {
                        display_count = 0;
                        logFragment.setAccView(mLin_Acc_Buffer);
                        logFragment.setVelView(vel_mea);
                        logFragment.setPosView(pos_mea, 1);
                    }
                    fileLogger.storeIMUData(mLin_Acc_Buffer, vel_mea, pos_mea, delta_timestamp_sec);
                }
            }
        }
    }

    public static My_Software_SensorListener mySoftwareSensorListener = new My_Software_SensorListener();

    public static void start_Software_Sensors() {
        try {
            mSensor_Stream.registerListener(mySoftwareSensorListener,
                    mSensor_Stream.getDefaultSensor(Sensor.TYPE_ORIENTATION), SensorManager.SENSOR_DELAY_NORMAL);
        } catch (Exception e) {
            e.printStackTrace();
            return;
        }

        try {
            mSensor_Stream.registerListener(mySoftwareSensorListener,
                    mSensor_Stream.getDefaultSensor(Sensor.TYPE_LINEAR_ACCELERATION), SensorManager.SENSOR_DELAY_FASTEST);
        } catch (Exception e) {
            e.printStackTrace();
            return;
        }

        try {
            mSensor_Stream.registerListener(mySoftwareSensorListener,
                    mSensor_Stream.getDefaultSensor(Sensor.TYPE_GRAVITY), SensorManager.SENSOR_DELAY_NORMAL);
        } catch (Exception e) {
            e.printStackTrace();
            return;
        }
        try {
            mSensor_Stream.registerListener(mySoftwareSensorListener,
                    mSensor_Stream.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR), SensorManager.SENSOR_DELAY_NORMAL);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static void stop_Software_Sensors() {

        mSensor_Stream.unregisterListener(mySoftwareSensorListener);

        mOriBufferReady = false;
    }

    private int init_gps_count = 0;
    private int init_gps_len = 5;
    private int gps_count = 0;
    public static double[] reference_radians_mea = new double[3];        // 接收机位置的平均值
    public static double[] init_ecef_Meters;        // 接收机位置的平均值
    public static Ecef2EnuConverter.EnuValues enuValues;        // 接收机位置的平均值
    private boolean isPosSettings = false;

    public static double[] llaMeasure = new double[3];

    /** GPS传感器数据读取，用于设定初值 */
    @Override
    public void onLocationChanged(@NonNull Location location) {
        llaMeasure[0] = location.getLatitude();
        llaMeasure[1] = location.getLongitude();
//        mapFragment.addMarker(location.getLatitude() , location.getLongitude());

        if (init_gps_count >= init_gps_len) {
            int gps_len = 10;
            if (gps_count >= gps_len) {
                if (!isPosSettings) {
                    isPosSettings = true;
                    reference_radians_mea[0] = Math.toRadians(location.getLatitude());
                    reference_radians_mea[1] = Math.toRadians(location.getLongitude());
                    reference_radians_mea[2] = location.getAltitude();
                    init_ecef_Meters = Lla2EcefConverter.convertFromLlaToEcefMeters(new Ecef2LlaConverter.GeodeticLlaValues(reference_radians_mea[0], reference_radians_mea[1], reference_radians_mea[2]));
                    enuValues = Ecef2EnuConverter.convertEcefToEnu(init_ecef_Meters[0], init_ecef_Meters[1], init_ecef_Meters[2],
                            reference_radians_mea[0], reference_radians_mea[1]);
                    mRealTimePositionVelocityCalculator.mPseudorangePositionVelocityFromRealTimeEvents.setReferencePosition(
                            (int) (location.getLatitude() * 1E7),
                            (int) (location.getLongitude() * 1E7),
                            (int) (location.getAltitude() * 1E7));
                    for (int i = 0; i < 3; i++) {
                        vel_mea[i] = 0;
                        pos_mea[i] = 0;
                    }
                }
                ++gps_count;
            } else {
                ++gps_count;
            }
        } else {
            ++init_gps_count;
        }
    }

    public void start_GPS_Sensors() {
        boolean GPS_enabled = mLocationmanager.isProviderEnabled(LocationManager.GPS_PROVIDER);

        if (!GPS_enabled) {
            Intent intent = new Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS);
            startActivity(intent);
        }

        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            return;
        }

        mLocationmanager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 0, 0, this);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            mLocationmanager.registerGnssMeasurementsCallback(gnssMeasurementsEventListener);
        }
    }

    public void stop_GPS_Sensors() {
        mLocationmanager.removeUpdates(this);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            mLocationmanager.unregisterGnssMeasurementsCallback(gnssMeasurementsEventListener);
        }
    }

}