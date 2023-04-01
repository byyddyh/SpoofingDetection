package cn.byyddyh.spoofingdetection;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.Intent;
import android.location.GnssClock;
import android.location.GnssMeasurement;
import android.location.GnssMeasurementsEvent;
import android.location.GnssNavigationMessage;
import android.location.Location;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.os.SystemClock;
import android.util.Log;
import android.widget.Toast;

import androidx.core.content.FileProvider;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileFilter;
import java.io.FileWriter;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.Objects;

public class FileLogger {

    private static final String TAG = "FileLogger";
    private static final String FILE_PREFIX = "gnss_log";
    private static final String ERROR_WRITING_FILE = "Problem writing to file.";
    private static final String COMMENT_START = "# ";
    private static final char RECORD_DELIMITER = ',';
    private static final String VERSION_TAG = "Version: 1.4.0.0, Platform: HUAWEI MATE30";

    private static final int MAX_FILES_STORED = 100;
    private static final int MINIMUM_USABLE_FILE_SIZE_BYTES = 1000;

    private final Context mContext;

    private final Object mFileLock = new Object();
    private BufferedWriter mFileWriter;
    private File mFile;

    public static final String MeasurementProviderTAG = "MeasurementProvider";

    private LogFragment.UIFragmentComponent mUiComponent;

    public synchronized LogFragment.UIFragmentComponent getUiComponent() {
        return mUiComponent;
    }

    public synchronized void setUiComponent(LogFragment.UIFragmentComponent value) {
        mUiComponent = value;
    }

    public FileLogger(Context context) {
        this.mContext = context;
    }

    /**
     * Start a new file logging process.
     */
    public void startNewLog() {
        synchronized (mFileLock) {
            /* 新建存储文件 */
            File baseDirectory;
            String state = Environment.getExternalStorageState();
            if (Environment.MEDIA_MOUNTED.equals(state)) {
                baseDirectory = new File(Environment.getExternalStorageDirectory(), FILE_PREFIX);
                baseDirectory.mkdirs();
            } else if (Environment.MEDIA_MOUNTED_READ_ONLY.equals(state)) {
                logError("Cannot write to external storage.");
                return;
            } else {
                logError("Cannot read external storage.");
                return;
            }

            @SuppressLint("SimpleDateFormat") SimpleDateFormat formatter = new SimpleDateFormat("yyy_MM_dd_HH_mm_ss");
            Date now = new Date();
            String fileName = String.format("%s_%s.txt", FILE_PREFIX, formatter.format(now));
            File currentFile = new File(baseDirectory, fileName);
            String currentFilePath = currentFile.getAbsolutePath();
            BufferedWriter currentFileWriter;
            try {
                currentFileWriter = new BufferedWriter(new FileWriter(currentFile));
            } catch (IOException e) {
                logException("Could not open file: " + currentFilePath, e);
                return;
            }

            // initialize the contents of the file
            // 初始化文件内容
            try {
                currentFileWriter.write(COMMENT_START);
                currentFileWriter.newLine();
                currentFileWriter.write(COMMENT_START);
                currentFileWriter.write("Header Description:");
                currentFileWriter.newLine();
                currentFileWriter.write(COMMENT_START);
                currentFileWriter.newLine();
                currentFileWriter.write(COMMENT_START);
                currentFileWriter.write(VERSION_TAG);
                currentFileWriter.newLine();
                currentFileWriter.write(COMMENT_START);
                currentFileWriter.newLine();
                currentFileWriter.write(COMMENT_START);
                currentFileWriter.write(
                        "Raw,ElapsedRealtimeMillis,TimeNanos,LeapSecond,TimeUncertaintyNanos,FullBiasNanos,"
                                + "BiasNanos,BiasUncertaintyNanos,DriftNanosPerSecond,DriftUncertaintyNanosPerSecond,"
                                + "HardwareClockDiscontinuityCount,Svid,TimeOffsetNanos,State,ReceivedSvTimeNanos,"
                                + "ReceivedSvTimeUncertaintyNanos,Cn0DbHz,PseudorangeRateMetersPerSecond,"
                                + "PseudorangeRateUncertaintyMetersPerSecond,"
                                + "AccumulatedDeltaRangeState,AccumulatedDeltaRangeMeters,"
                                + "AccumulatedDeltaRangeUncertaintyMeters,CarrierFrequencyHz,CarrierCycles,"
                                + "CarrierPhase,CarrierPhaseUncertainty,MultipathIndicator,SnrInDb,"
                                + "ConstellationType,AgcDb");
                currentFileWriter.newLine();
                currentFileWriter.write(COMMENT_START);
                currentFileWriter.newLine();
                currentFileWriter.write(COMMENT_START);
                currentFileWriter.write(
                        "Fix,Provider,Latitude,Longitude,Altitude,Speed,Accuracy,(UTC)TimeInMs");
                currentFileWriter.newLine();
                currentFileWriter.write(COMMENT_START);
                currentFileWriter.newLine();
                currentFileWriter.write(COMMENT_START);
                currentFileWriter.write("Nav,Svid,Type,Status,MessageId,Sub-messageId,Data(Bytes)");
                currentFileWriter.newLine();
                currentFileWriter.write(COMMENT_START);
                currentFileWriter.newLine();
            } catch (IOException e) {
                logException("Count not initialize file: " + currentFilePath, e);
                return;
            }

            if (mFileWriter != null) {
                try {
                    mFileWriter.close();
                } catch (IOException e) {
                    logException("Unable to close all file streams.", e);
                    return;
                }
            }

            mFile = currentFile;
            mFileWriter = currentFileWriter;
            Toast.makeText(mContext, "File opened: " + currentFilePath, Toast.LENGTH_SHORT).show();

            // To make sure that files do not fill up the external storage:
            // - Remove all empty files
            FileFilter filter = new FileToDeleteFilter(mFile);
            for (File existingFile : Objects.requireNonNull(baseDirectory.listFiles(filter))) {
                existingFile.delete();
            }
            // - Trim the number of files with data
            File[] existingFiles = baseDirectory.listFiles();
            int filesToDeleteCount = existingFiles.length - MAX_FILES_STORED;
            if (filesToDeleteCount > 0) {
                Arrays.sort(existingFiles);
                for (int i = 0; i < filesToDeleteCount; ++i) {
                    existingFiles[i].delete();
                }
            }
        }
    }

    public void writeGnssMeasurementData(GnssMeasurementsEvent event) {
        synchronized (mFileLock) {
            if (mFileWriter == null) {
                return;
            }
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.N) {
                GnssClock gnssClock  = event.getClock();

                for (GnssMeasurement measurement : event.getMeasurements()) {
                    try {
                        /*写入到文件中*/
                        writeGnssMeasurementToFile(gnssClock, measurement);
                    } catch (IOException e) {
                        logException(ERROR_WRITING_FILE, e);
                    }
                }
            }
        }
    }

    /**
     * 将GNSS测量值记录到日志文件中
     */
    private void writeGnssMeasurementToFile(GnssClock clock, GnssMeasurement measurement)
            throws IOException {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.N) {
            String clockStream =
                    String.format(
                            "Raw,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s",
                            SystemClock.elapsedRealtime(),
                            clock.getTimeNanos(),
                            clock.hasLeapSecond() ? clock.getLeapSecond() : "",
                            clock.hasTimeUncertaintyNanos() ? clock.getTimeUncertaintyNanos() : "",
                            clock.getFullBiasNanos(),
                            clock.hasBiasNanos() ? clock.getBiasNanos() : "",
                            clock.hasBiasUncertaintyNanos() ? clock.getBiasUncertaintyNanos() : "",
                            clock.hasDriftNanosPerSecond() ? clock.getDriftNanosPerSecond() : "",
                            clock.hasDriftUncertaintyNanosPerSecond()
                                    ? clock.getDriftUncertaintyNanosPerSecond()
                                    : "",
                            clock.getHardwareClockDiscontinuityCount() + ",");
            mFileWriter.write(clockStream);

            String measurementStream =
                    String.format(
                            "%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s",
                            measurement.getSvid(),
                            measurement.getTimeOffsetNanos(),
                            measurement.getState(),
                            measurement.getReceivedSvTimeNanos(),
                            measurement.getReceivedSvTimeUncertaintyNanos(),
                            measurement.getCn0DbHz(),
                            measurement.getPseudorangeRateMetersPerSecond(),
                            measurement.getPseudorangeRateUncertaintyMetersPerSecond(),
                            measurement.getAccumulatedDeltaRangeState(),
                            measurement.getAccumulatedDeltaRangeMeters(),
                            measurement.getAccumulatedDeltaRangeUncertaintyMeters(),
                            measurement.hasCarrierFrequencyHz() ? measurement.getCarrierFrequencyHz() : "",
                            measurement.hasCarrierCycles() ? measurement.getCarrierCycles() : "",
                            measurement.hasCarrierPhase() ? measurement.getCarrierPhase() : "",
                            measurement.hasCarrierPhaseUncertainty()
                                    ? measurement.getCarrierPhaseUncertainty()
                                    : "",
                            measurement.getMultipathIndicator(),
                            measurement.hasSnrInDb() ? measurement.getSnrInDb() : "",
                            measurement.getConstellationType(),
                            Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
                                    && measurement.hasAutomaticGainControlLevelDb()
                                    ? measurement.getAutomaticGainControlLevelDb()
                                    : "");
            mFileWriter.write(measurementStream);
            mFileWriter.newLine();
        }
    }

    /**
     * Send the current log via email or other options selected from a pop menu shown to the user. A
     * new log is started when calling this function.
     * 通过电子邮件或从显示给用户的弹出菜单中选择的其他选项发送当前日志。 调用此函数时会启动一个新日志。
     */
    public void send() {
        if (mFile == null) {
            return;
        }

        Intent emailIntent = new Intent(Intent.ACTION_SEND);
        emailIntent.setType("*/*");
        emailIntent.putExtra(Intent.EXTRA_SUBJECT, "SensorLog");
        emailIntent.putExtra(Intent.EXTRA_TEXT, "");
        // attach the file
        Uri fileURI =
                FileProvider.getUriForFile(mContext, BuildConfig.APPLICATION_ID + ".provider", mFile);
        emailIntent.putExtra(Intent.EXTRA_STREAM, fileURI);
        getUiComponent().startActivity(Intent.createChooser(emailIntent, "Send log.."));
        if (mFileWriter != null) {
            try {
                mFileWriter.flush();
                mFileWriter.close();
                mFileWriter = null;
            } catch (IOException e) {
                logException("Unable to close all file streams.", e);
                return;
            }
        }
    }

    public void onLocationReceived(Location location) {
        synchronized (mFileLock) {
            if (mFileWriter == null) {
                return;
            }
            StringBuilder builder = new StringBuilder("GNSS");
            builder.append(RECORD_DELIMITER);
            builder.append(location.getLatitude());
            builder.append(RECORD_DELIMITER);
            builder.append(location.getLongitude());
            builder.append(RECORD_DELIMITER);
            builder.append(location.getAltitude());
            builder.append(RECORD_DELIMITER);

            try {
                mFileWriter.write(builder.toString());
                mFileWriter.newLine();
            } catch (IOException e) {
                logException(ERROR_WRITING_FILE, e);
            }
        }
    }

    public void storeData(String str, double[] data) {
        synchronized (mFileLock) {
            if (mFileWriter == null) {
                return;
            }
            StringBuilder builder = new StringBuilder(str);
            builder.append(RECORD_DELIMITER);
            builder.append(data[0]);
            builder.append(RECORD_DELIMITER);
            builder.append(data[1]);
            builder.append(RECORD_DELIMITER);
            builder.append(data[2]);

            try {
                mFileWriter.write(builder.toString());
                mFileWriter.newLine();
            } catch (IOException e) {
                logException(ERROR_WRITING_FILE, e);
            }
        }
    }

    public void storeListData(String str, List<Double> data) {
        synchronized (mFileLock) {
            if (mFileWriter == null) {
                return;
            }
            StringBuilder builder = new StringBuilder(str);

            for (double datum : data) {
                builder.append(RECORD_DELIMITER);
                builder.append(datum);
            }

            try {
                mFileWriter.write(builder.toString());
                mFileWriter.newLine();
            } catch (IOException e) {
                logException(ERROR_WRITING_FILE, e);
            }
        }
    }

    public void storeArrayData(String str, double[] data) {
        synchronized (mFileLock) {
            if (mFileWriter == null) {
                return;
            }

            // 写标题
            StringBuilder builder = new StringBuilder(str);

            // 写数据
            for (double datum : data) {
                builder.append(RECORD_DELIMITER);
                builder.append(datum);
            }

            try {
                mFileWriter.write(builder.toString());
                mFileWriter.newLine();
            } catch (IOException e) {
                logException(ERROR_WRITING_FILE, e);
            }
        }
    }

    /**
     * 保存IMU速度信息
     * @param values    加速度
     * @param vel_mea   速度
     * @param pos_mea   位置
     * @param delta_timestamp_sec 时间间隔
     */
    public void storeIMUData(double[] values, double[] vel_mea, double[] pos_mea, double delta_timestamp_sec) {
        synchronized (mFileLock) {
            if (mFileWriter == null) {
                return;
            }

            // 写标题头
            StringBuilder builder = new StringBuilder("Imu_data");
            // 写加速度
            for (double datum : values) {
                builder.append(RECORD_DELIMITER);
                builder.append(datum);
            }
            // 写速度
            for (double datum : vel_mea) {
                builder.append(RECORD_DELIMITER);
                builder.append(datum);
            }
            // 写位置
            for (double datum : pos_mea) {
                builder.append(RECORD_DELIMITER);
                builder.append(datum);
            }
            builder.append(RECORD_DELIMITER);
            builder.append(delta_timestamp_sec);

            try {
                mFileWriter.write(builder.toString());
                mFileWriter.newLine();
            } catch (IOException e) {
                logException(ERROR_WRITING_FILE, e);
            }
        }
    }

    @SuppressLint("LongLogTag")
    private void logError(String errorMessage) {
        Log.e(MeasurementProviderTAG + TAG, errorMessage);
        Toast.makeText(mContext, errorMessage, Toast.LENGTH_LONG).show();
    }

    @SuppressLint("LongLogTag")
    private void logException(String errorMessage, Exception e) {
        Log.e(MeasurementProviderTAG + TAG, errorMessage, e);
        Toast.makeText(mContext, errorMessage, Toast.LENGTH_LONG).show();
    }

    /**
     * Implements a {@link FileFilter} to delete files that are not in the
     * {@link FileToDeleteFilter#mRetainedFiles}.
     */
    private static class FileToDeleteFilter implements FileFilter {
        private final List<File> mRetainedFiles;

        public FileToDeleteFilter(File... retainedFiles) {
            this.mRetainedFiles = Arrays.asList(retainedFiles);
        }

        /**
         * Returns {@code true} to delete the file, and {@code false} to keep the file.
         *
         * <p>Files are deleted if they are not in the {@link FileToDeleteFilter#mRetainedFiles} list.
         */
        @Override
        public boolean accept(File pathname) {
            if (pathname == null || !pathname.exists()) {
                return false;
            }
            if (mRetainedFiles.contains(pathname)) {
                return false;
            }
            return pathname.length() < MINIMUM_USABLE_FILE_SIZE_BYTES;
        }
    }
}
