package cn.byyddyh.spoofingdetection;

import static cn.byyddyh.spoofingdetection.MainActivity.mRealTimePositionVelocityCalculator;

import android.annotation.SuppressLint;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import java.net.SocketException;

import cn.byyddyh.spoofingdetection.sockets.NetworkUtils;
import cn.byyddyh.spoofingdetection.sockets.SocketClient;
import cn.byyddyh.spoofingdetection.sockets.SocketServer;

public class SettingsFragment extends Fragment {
    // 判断是否建立链接
    public boolean isConnected = false;
    Button singleSettings;
    Button multipleSettings;
    Button endSettings;
    Button downloadSettings;
    Button binding;
    Button ipGenerate;
    Button ipBinding;
    EditText lat;
    EditText lon;
    EditText alt;
    EditText serverIp;
    public SocketServer server;
    public SocketClient client;
    public static double latitude;
    public static double longitude;
    private final String nasa_uri = "https://cddis.nasa.gov/archive/gnss/data/hourly/";

    @SuppressLint("MissingInflatedId")
    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View inflate = inflater.inflate(R.layout.settings_fragment, container, false);

        singleSettings = inflate.findViewById(R.id.single_settings);
        multipleSettings = inflate.findViewById(R.id.multiple_settings);
        endSettings = inflate.findViewById(R.id.end_settings);
        downloadSettings = inflate.findViewById(R.id.download_settings);
        binding = inflate.findViewById(R.id.binding);
        lat = inflate.findViewById(R.id.Lat);
        lon = inflate.findViewById(R.id.Lon);
        alt = inflate.findViewById(R.id.Alt);
        ipGenerate = inflate.findViewById(R.id.ipGenerate);
        ipBinding = inflate.findViewById(R.id.ip_binding);
        serverIp = inflate.findViewById(R.id.serverIp);

        singleSettings.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                MainActivity.isUsedAntiSpoof = true;
            }
        });

        endSettings.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                MainActivity.isUsedAntiSpoof = false;
            }
        });

        binding.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                double latVal = Double.parseDouble(lat.getText().toString());
                double lonVal = Double.parseDouble(lon.getText().toString());
                double altVal = Double.parseDouble(alt.getText().toString());
                if (latVal >= 0 && latVal <= 180 && lonVal >= 0 && lonVal <= 180) {
                    mRealTimePositionVelocityCalculator.mPseudorangePositionVelocityFromRealTimeEvents.setReferencePosition((int) (latVal * 1E7),
                            (int) (lonVal * 1E7),
                            (int) (altVal * 1E7));
                }
                latitude = Math.toRadians(latVal);
                longitude = Math.toRadians(lonVal);
            }
        });

        ipGenerate.setOnClickListener(v -> {
            try {
                // 注册服务器
                String ipAddress = NetworkUtils.getLocalIPAddress();
                server = new SocketServer(8899);
                /*socket服务端开始监听*/
                server.beginListen();
                Toast.makeText(requireActivity().getApplicationContext(), "ip:\t" + ipAddress + "\t, 服务器开启", Toast.LENGTH_SHORT).show();
                isConnected = true;
            } catch (SocketException e) {
                throw new RuntimeException(e);
            }
        });

        ipBinding.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                String serverIpAddress = serverIp.getText().toString();
                client = new SocketClient();
                //服务端的IP地址和端口号
                client.clintValue(requireActivity(), serverIpAddress, 8899);
                //开启客户端接收消息线程
                client.openClientThread();
                Toast.makeText(requireActivity().getApplicationContext(), "链接已建立", Toast.LENGTH_SHORT).show();
                isConnected = true;
            }
        });

        downloadSettings.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                // 跳转至浏览器下载界面
                Uri uri = Uri.parse(nasa_uri);
                Intent intent = new Intent(Intent.ACTION_VIEW, uri);
                startActivity(intent);
            }
        });
        return inflate;
    }
}
