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
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.provider.Settings;

import com.amap.api.location.AMapLocationClient;
import com.amap.api.maps.MapsInitializer;
import com.amap.api.services.core.ServiceSettings;
import com.google.android.material.bottomnavigation.BottomNavigationView;

import java.io.IOException;
import java.util.Random;

import cn.byyddyh.spoofingdetection.process.GetEphemeris;
import cn.byyddyh.spoofingdetection.process.dataModel.GNSSGpsEph;
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
    private static final MapFragment mapFragment;

    /**
     * 传感器数据
     */
    public static SensorManager mSensor_Toggle;
    public static SensorManager mSensor_Stream;
    public static LocationManager mLocationmanager;
    private GNSSGpsEph nasaHourlyEphemeris;
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

    private RealTimePositionVelocityCalculator mRealTimePositionVelocityCalculator;

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
//                    // 初始GNSS测量值用于定位解算
//                    try {
//                        // 1. 解析星历数据
//                        if (ephemerisFlag && nasaHourlyEphemeris == null) {
//                            analysisEphemeris();
//                        } else if (ephemerisFlag) {
//                            // 获取原始导航数据
//                            GNSSRaw gnssRaw = ProcessUtils.filterRawData(eventArgs.getMeasurements(), eventArgs.getClock());
//
//                            if (gnssRaw == null || gnssRaw.Svid.size() < 4) {
//                                return;
//                            }
//
//                            // 对数据进行处理，计算伪距值，使用最小二乘法进行计算
//                            GNSSMeas gnssMeas = PseudorangeProcessUtils.processGnssMeas(gnssRaw);
//
//                            // 计算WLS位置和速度
//                            GNSSPosition.gpsWlsPvt(gnssMeas, nasaHourlyEphemeris);
//
//                            LogFragment.logText("text", "导航卫星数据" + eventArgs.getMeasurements().size());
//                        }
//
//                    } catch (Exception e) {
//                        Log.d("exception", e.getMessage());
//                    }
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

    /**
     * 解析星历数据
     */
    private void analysisEphemeris() {
        hasPermission();
        try {
            nasaHourlyEphemeris = GetEphemeris.getNasaHourlyEphemeris();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private boolean isRefuse;

    private void hasPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && !isRefuse) {// android 11  且 不是已经被拒绝
            // 先判断有没有权限
            if (!Environment.isExternalStorageManager()) {
                Intent intent = new Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION);
                intent.setData(Uri.parse("package:" + getPackageName()));
                startActivityForResult(intent, 1024);
            }
        }
    }

    // 带回授权结果
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
    private static int acc_count = 0;
    private static int acc_len = 200;
    private static double delta_timestamp_sec = 0;

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
                logFragment.setAccView(event.values);
                for (int i = 0; i < event.values.length; i++) {
                    mLin_Acc_Buffer[i] = event.values[i];
                }
                delta_timestamp_sec = timestamp_sec - mLin_Acc_Time;
                mLin_Acc_Time = timestamp_sec;

                // 进行位置计算
                if (acc_count < acc_len) {
                    acc_count++;
                } else {
                    for (int i = 0; i < 3; i++) {
                        vel_mea[i] = vel_mea[i] + (mLin_Acc_Buffer[i]) * delta_timestamp_sec;
                        pos_mea[i] = pos_mea[i] + vel_mea[i] * delta_timestamp_sec;
                        logFragment.setVelView(vel_mea);
                        logFragment.setPosView(pos_mea, 1);
                    }
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
                    mSensor_Stream.getDefaultSensor(Sensor.TYPE_LINEAR_ACCELERATION), SensorManager.SENSOR_DELAY_NORMAL);
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
    public static double[] lla_mea = new double[3];        // 接收机位置的平均值
    public static double[] init_radians_mea = new double[3];        // 接收机位置的平均值
    public static double[] init_ecef_Meters;        // 接收机位置的平均值
    public static Ecef2EnuConverter.EnuValues enuValues;        // 接收机位置的平均值

    public static double[] lla_mea_temp = new double[3];

    /** GPS传感器数据读取，用于设定初值 */
    @Override
    public void onLocationChanged(@NonNull Location location) {
        lla_mea_temp[0] = location.getLatitude();
        lla_mea_temp[1] = location.getLongitude();
//        if (mapFragment != null && MapFragment.aMap != null) {
//            mapFragment.addMarker(location.getLatitude(), location.getLongitude());
//        }

        if (init_gps_count >= init_gps_len) {
            int gps_len = 10;
            if (gps_count == gps_len) {
                mRealTimePositionVelocityCalculator.mPseudorangePositionVelocityFromRealTimeEvents.setReferencePosition((int) (location.getLatitude() * 1E7),
                        (int) (location.getLongitude() * 1E7),
                        (int) (location.getAltitude() * 1E7));
                for (int i = 0; i < 3; i++) {
                    vel_mea[i] = 0;
                    pos_mea[i] = 0;
                }
                ++gps_count;
            } else if (gps_count < gps_len){
                ++gps_count;
                lla_mea[0] += location.getLatitude();
                lla_mea[1] += location.getLongitude();
                lla_mea[2] += location.getAltitude();
                if (gps_count == gps_len) {
                    for (int i = 0; i < 3; i++) {
                        lla_mea[i] = lla_mea[i] / gps_len;
                    }
                    init_radians_mea[0] = Math.toRadians(lla_mea[0]);
                    init_radians_mea[1] = Math.toRadians(lla_mea[1]);
                    init_ecef_Meters = Lla2EcefConverter.convertFromLlaToEcefMeters(new Ecef2LlaConverter.GeodeticLlaValues(init_radians_mea[0], init_radians_mea[1], init_radians_mea[2]));
                    enuValues = Ecef2EnuConverter.convertEcefToEnu(init_ecef_Meters[0], init_ecef_Meters[1], init_ecef_Meters[2],
                            init_radians_mea[0], init_radians_mea[1]);
                }
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