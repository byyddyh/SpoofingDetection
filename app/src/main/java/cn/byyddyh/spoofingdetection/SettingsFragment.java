package cn.byyddyh.spoofingdetection;

import android.annotation.SuppressLint;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import java.util.Calendar;
import java.util.Date;

import cn.byyddyh.spoofingdetection.process.GetEphemeris;

public class SettingsFragment extends Fragment {

    Button startSettings;
    Button endSettings;
    Button downloadSettings;

    private String nasa_uri = "https://cddis.nasa.gov/archive/gnss/data/hourly/";

    @SuppressLint("MissingInflatedId")
    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View inflate = inflater.inflate(R.layout.settings_fragment, container, false);

        startSettings = inflate.findViewById(R.id.start_settings);
        endSettings = inflate.findViewById(R.id.end_settings);
        downloadSettings = inflate.findViewById(R.id.download_settings);

        startSettings.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                MainActivity.ephemerisFlag = true;
            }
        });

        endSettings.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                MainActivity.ephemerisFlag = false;
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
