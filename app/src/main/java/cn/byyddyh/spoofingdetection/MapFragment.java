package cn.byyddyh.spoofingdetection;

import android.annotation.SuppressLint;
import android.graphics.Color;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.amap.api.location.AMapLocation;
import com.amap.api.location.AMapLocationClient;
import com.amap.api.location.AMapLocationClientOption;
import com.amap.api.location.AMapLocationListener;
import com.amap.api.maps.AMap;
import com.amap.api.maps.LocationSource;
import com.amap.api.maps.MapView;
import com.amap.api.maps.UiSettings;
import com.amap.api.maps.model.BitmapDescriptorFactory;
import com.amap.api.maps.model.LatLng;
import com.amap.api.maps.model.Marker;
import com.amap.api.maps.model.MarkerOptions;
import com.amap.api.maps.model.MyLocationStyle;

import java.util.Random;

import pub.devrel.easypermissions.AfterPermissionGranted;
import pub.devrel.easypermissions.EasyPermissions;

public class MapFragment extends Fragment implements AMapLocationListener, LocationSource {

    //请求权限码
    private static final int REQUEST_PERMISSIONS = 9527;

    //内容
    private MapView mapView;
    //地图控制器
    public static AMap aMap = null;
    //位置更改监听
    private LocationSource.OnLocationChangedListener mListener;
    //定位样式
    private MyLocationStyle myLocationStyle = new MyLocationStyle();
    //定义一个UiSettings对象
    private UiSettings mUiSettings;

    private RealTimePositionVelocityCalculator mRealTimePositionVelocityCalculator;

    @SuppressLint("MissingInflatedId")
    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View inflate = inflater.inflate(R.layout.map_fragment, container, false);

        mapView = inflate.findViewById(R.id.map_view);
        mapView.onCreate(savedInstanceState);

        // 初始化定位
        initLocation();

        // 初始化地图
        initMap(inflate, savedInstanceState);

        // 检查Android版本
        checkingAndroidVersion();

        return inflate;
    }

    public void destroy() {
        //销毁定位客户端，同时销毁本地定位服务。
        mLocationClient.onDestroy();
        // 在activity执行onDestroy时执行mMapView.onDestroy()，销毁地图
        mapView.onDestroy();
    }

    @Override
    public void onResume() {
        super.onResume();
        //在activity执行onResume时执行mMapView.onResume ()，重新绘制加载地图
        mapView.onResume();
    }

    @Override
    public void onPause() {
        super.onPause();
        //在activity执行onPause时执行mMapView.onPause ()，暂停地图的绘制
        mapView.onPause();
    }

    @Override
    public void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        //在activity执行onSaveInstanceState时执行mMapView.onSaveInstanceState (outState)，保存地图当前的状态
        mapView.onSaveInstanceState(outState);
    }

    /**
     * 检查Android版本
     */
    private void checkingAndroidVersion() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            //Android6.0及以上先获取权限再定位
            requestPermission();
        } else {
            //Android6.0以下直接定位
            mLocationClient.startLocation();
        }
    }


    /**
     * 动态请求权限
     */
    @AfterPermissionGranted(REQUEST_PERMISSIONS)
    private void requestPermission() {
        String[] permissions = {
                android.Manifest.permission.ACCESS_COARSE_LOCATION,
                android.Manifest.permission.ACCESS_FINE_LOCATION,
                android.Manifest.permission.READ_PHONE_STATE,
                android.Manifest.permission.WRITE_EXTERNAL_STORAGE
        };

        if (EasyPermissions.hasPermissions(requireActivity().getApplicationContext(), permissions)) {
            //true 有权限 开始定位
            showMsg("已获得权限，可以定位啦！");

            mLocationClient.startLocation();
        } else {
            //false 无权限
            EasyPermissions.requestPermissions(this, "需要权限", REQUEST_PERMISSIONS, permissions);
        }
    }

    /**
     * 请求权限结果
     */
    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        //设置权限请求结果
        EasyPermissions.onRequestPermissionsResult(requestCode, permissions, grantResults, this);
    }

    /**
     * Toast提示
     */
    private void showMsg(String msg) {
        Toast.makeText(requireActivity().getApplicationContext(), msg, Toast.LENGTH_SHORT).show();
    }

    //声明AMapLocationClient类对象
    public AMapLocationClient mLocationClient = null;
    //声明AMapLocationClientOption对象
    public AMapLocationClientOption mLocationOption = null;

    /**
     * 初始化定位
     */
    private void initLocation() {
        //初始化定位
        try {
            mLocationClient = new AMapLocationClient(requireActivity().getApplicationContext());
        } catch (Exception e) {
            e.printStackTrace();
        }
        if (mLocationClient != null) {
            //设置定位回调监听
            mLocationClient.setLocationListener(this);
            //初始化AMapLocationClientOption对象
            mLocationOption = new AMapLocationClientOption();
            //设置定位模式为AMapLocationMode.Hight_Accuracy，高精度模式。
            mLocationOption.setLocationMode(AMapLocationClientOption.AMapLocationMode.Hight_Accuracy);
            //获取最近3s内精度最高的一次定位结果：
            //设置setOnceLocationLatest(boolean b)接口为true，启动定位时SDK会返回最近3s内精度最高的一次定位结果。如果设置其为true，setOnceLocation(boolean b)接口也会被设置为true，反之不会，默认为false。
            mLocationOption.setOnceLocationLatest(true);
            //设置是否返回地址信息（默认返回地址信息）
            mLocationOption.setNeedAddress(true);
            //设置定位请求超时时间，单位是毫秒，默认30000毫秒，建议超时时间不要低于8000毫秒。
            mLocationOption.setHttpTimeOut(20000);
            //关闭缓存机制，高精度定位会产生缓存。
            mLocationOption.setLocationCacheEnable(false);
            //给定位客户端对象设置定位参数
            mLocationClient.setLocationOption(mLocationOption);
        }
    }

    /**
     * 接收异步返回的定位结果
     */
    @Override
    public void onLocationChanged(AMapLocation aMapLocation) {
        if (aMapLocation != null) {
            if (aMapLocation.getErrorCode() == 0) {
                aMapLocation.setLatitude(MainActivity.lla_mea_temp[0]);
                aMapLocation.setLongitude(MainActivity.lla_mea_temp[1]);

                StringBuffer stringBuffer = new StringBuffer();
                stringBuffer.append("纬度:").append(aMapLocation.getLatitude()).append("\n");
                stringBuffer.append("经度:").append(aMapLocation.getLongitude()).append("\n");

                showMsg(stringBuffer.toString());

                // 显示地图定位结果
                if (mListener != null) {
                    // 显示系统图标
                    mListener.onLocationChanged(aMapLocation);
                }
            } else {
                //定位失败时，可通过ErrCode（错误码）信息来确定失败的原因，errInfo是错误信息，详见错误码表。
                Log.e("AmapError", "location Error, ErrCode:"
                        + aMapLocation.getErrorCode() + ", errInfo:"
                        + aMapLocation.getErrorInfo());
            }
        }
    }

    /**
     * 初始化地图
     */
    private void initMap(View inflate, Bundle savedInstanceState) {
        mapView = inflate.findViewById(R.id.map_view);
        //在activity执行onCreate时执行mMapView.onCreate(savedInstanceState)，创建地图
        mapView.onCreate(savedInstanceState);
        //初始化地图控制器对象
        aMap = mapView.getMap();

        //设置最小缩放等级为16 ，缩放级别范围为[3, 20]
        aMap.setMinZoomLevel(18);
        // 自定义定位蓝点图标
        myLocationStyle.myLocationIcon(BitmapDescriptorFactory.fromResource(R.drawable.gps_point));
        // 自定义精度范围的圆形边框颜色  都为0则透明
        myLocationStyle.strokeColor(Color.argb(0, 0, 0, 0));
        // 自定义精度范围的圆形边框宽度  0 无宽度
        myLocationStyle.strokeWidth(0);
        // 设置圆形的填充颜色  都为0则透明
        myLocationStyle.radiusFillColor(Color.argb(0, 0, 0, 0));

        //设置定位蓝点的Style
        aMap.setMyLocationStyle(myLocationStyle);

        // 设置定位监听
        aMap.setLocationSource(this);
        // 设置为true表示显示定位层并可触发定位，false表示隐藏定位层并不可触发定位，默认是false
        aMap.setMyLocationEnabled(true);

        //实例化UiSettings类对象
        mUiSettings = aMap.getUiSettings();
        //隐藏缩放按钮
        mUiSettings.setZoomControlsEnabled(false);
        //显示比例尺 默认不显示
        mUiSettings.setScaleControlsEnabled(true);
    }

    /**
     * 激活定位
     */
    @Override
    public void activate(OnLocationChangedListener onLocationChangedListener) {
        mListener = onLocationChangedListener;
        if (mLocationClient != null) {
            mLocationClient.startLocation();//启动定位
        }
    }

    /**
     * 停止定位
     */
    @Override
    public void deactivate() {
        mListener = null;
        if (mLocationClient != null) {
            mLocationClient.stopLocation();
            mLocationClient.onDestroy();
        }
        mLocationClient = null;
    }

    /**
     * 去除标记点
     */
    public void clearMarker(double latDegRaw, double lngDegRaw) {
        LatLng latLng = new LatLng(latDegRaw, lngDegRaw);
        Marker marker = aMap.addMarker(new MarkerOptions().position(latLng).snippet("DefaultMarker"));
        marker.remove();
        marker.remove();
    }


    private int markerCount = 0;
    private final int markerSize = 50;
    private boolean removeFlag = false;
    private final Marker[] markers = new Marker[markerSize];

    /**
     * 添加标记点
     */
    public void addMarker(double latDegRaw, double lngDegRaw) {
        if (markerCount == markerSize) {
            markerCount = 0;
            removeFlag = true;
        }

        if (removeFlag) {
            markers[markerCount].remove();
        }

        LatLng latLng = new LatLng(latDegRaw, lngDegRaw);
        MarkerOptions markerOptions = new MarkerOptions().position(latLng).snippet("DefaultMarker");
        Marker marker = aMap.addMarker(markerOptions);
        markers[markerCount] = marker;
        markerCount++;
    }

    public void setPositionVelocityCalculator(RealTimePositionVelocityCalculator mRealTimePositionVelocityCalculator) {
        this.mRealTimePositionVelocityCalculator = mRealTimePositionVelocityCalculator;
    }
}
