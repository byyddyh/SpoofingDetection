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

public class SettingsFragment extends Fragment {

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
    EditText neigh1;
    EditText neigh2;
    public static double latitude;
    public static double longitude;
    private String nasa_uri = "https://cddis.nasa.gov/archive/gnss/data/hourly/";
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

        ipGenerate.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                try {
                    String ipAddress = NetworkUtils.getLocalIPAddress();
                    Toast.makeText(requireActivity().getApplicationContext(), "ip:\t" + ipAddress, Toast.LENGTH_SHORT).show();
                } catch (SocketException e) {
                    throw new RuntimeException(e);
                }
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
