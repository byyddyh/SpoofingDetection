package cn.byyddyh.spoofingdetection;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.text.Editable;
import android.text.SpannableStringBuilder;
import android.text.style.ForegroundColorSpan;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

public class LogFragment extends Fragment {

    private ScrollView scrollView;
    private TextView logView;
    private static UIFragmentComponent uiFragmentComponent;
    private Button buttonStart;
    private Button buttonEnd;
    private static final int USED_COLOR = Color.rgb(0x4a, 0x5f, 0x70);
    @SuppressLint("StaticFieldLeak")
    public static FileLogger fileLogger;

    public static void logText(String tag, String text) {
        if (uiFragmentComponent != null) {
            uiFragmentComponent.logTextFragment(tag, text, USED_COLOR);
        }
    }

    /**
     * 传感器对应的视图
     */
    public static TextView[] mAcc = new TextView[3];
    public static TextView[] mVel = new TextView[3];
    public static TextView[] mPos = new TextView[3];

    /**
     * 文件写入标志位
     */
    public static boolean writableFlag = false;

    int count = 0;
    @SuppressLint("MissingInflatedId")
    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View newView = inflater.inflate(R.layout.log_fragment, container, false /* attachToRoot */);

        logView = newView.findViewById(R.id.log_view);
        scrollView = newView.findViewById(R.id.log_scroll);
        buttonStart = newView.findViewById(R.id.button_start);
        buttonEnd = newView.findViewById(R.id.button_end);

        // 将视图view进行绑定
        mAcc[0] = newView.findViewById(R.id.X_Acc);
        mAcc[1] = newView.findViewById(R.id.Y_Acc);
        mAcc[2] = newView.findViewById(R.id.Z_Acc);

        mVel[0] = newView.findViewById(R.id.X_Vel);
        mVel[1] = newView.findViewById(R.id.Y_Vel);
        mVel[2] = newView.findViewById(R.id.Z_Vel);

        mPos[0] = newView.findViewById(R.id.X_Pos);
        mPos[1] = newView.findViewById(R.id.Y_Pos);
        mPos[2] = newView.findViewById(R.id.Z_Pos);

        uiFragmentComponent = new UIFragmentComponent();
        fileLogger.setUiComponent(uiFragmentComponent);

        buttonStart.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                writableFlag = true;
                fileLogger.startNewLog();
            }
        });

        buttonEnd.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                writableFlag = false;
                fileLogger.send();
            }
        });

        // 开启读取传感器
        MainActivity.start_Software_Sensors();

        return newView;
    }
    public class UIFragmentComponent {
        private static final int MAX_LENGTH = 42000;
        private static final int LOWER_THRESHOLD = (int) (MAX_LENGTH * 0.5);

        public synchronized void logTextFragment(final String tag, final String text, int color) {
            final SpannableStringBuilder builder = new SpannableStringBuilder();
            builder.append(tag).append(" | ").append(text).append("\n");
            builder.setSpan(
                    new ForegroundColorSpan(color),
                    0 /* start */,
                    builder.length(),
                    SpannableStringBuilder.SPAN_INCLUSIVE_EXCLUSIVE);

            Activity activity = getActivity();
            if (activity == null) {
                return;
            }
            activity.runOnUiThread(
                    () -> {
                        logView.append(builder);
                        SharedPreferences sharedPreferences = PreferenceManager.
                                getDefaultSharedPreferences(getActivity());
                        Editable editable = logView.getEditableText();
                        int length = editable.length();
                        if (length > MAX_LENGTH) {
                            editable.delete(0, length - LOWER_THRESHOLD);
                        }
                        // 设置ScrollView滚动到底部
                        scrollView.post(() -> scrollView.fullScroll(View.FOCUS_DOWN));
                    });
        }

        public void startActivity(Intent intent) {
            getActivity().startActivity(intent);
        }
    }

    public void setFileLogger(FileLogger value) {
        fileLogger = value;
    }

    @SuppressLint("DefaultLocale")
    public void setAccView(float[] values) {
        for (int i = 0; i < values.length; i++) {
            mAcc[i].setText(String.format("%6.3f", values[i]));
        }
    }

    @SuppressLint("DefaultLocale")
    public void setVelView(double[] vel_mea) {
        for (int i = 0; i < vel_mea.length; i++) {
            mVel[i].setText(String.format("%6.3f", vel_mea[i]));
        }
    }

    @SuppressLint("DefaultLocale")
    public void setPosView(double[] pos_mea) {
        for (int i = 0; i < pos_mea.length; i++) {
            mPos[i].setText(String.format("%6.3f", pos_mea[i]));
        }
    }
}
