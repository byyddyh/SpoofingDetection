package cn.byyddyh.spoofingdetection.process.dataProcess;

import android.os.Build;

import java.io.*;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

import cn.byyddyh.spoofingdetection.process.dataModel.GNSSAnalysis;
import cn.byyddyh.spoofingdetection.process.dataModel.GNSSRaw;
import cn.byyddyh.spoofingdetection.process.utils.MathUtils;

public class ReadGnssLogger {
    private static final GNSSRaw gnssRaw;

    private static GNSSAnalysis gnssAnalysis;

    private static String[] header;

    private static DataFilter dataFilter;

    private static final List<String> allowFiles = Arrays.asList(".txt", ".csv");

    static  {
        gnssRaw = new GNSSRaw();
        gnssAnalysis = new GNSSAnalysis();
        gnssAnalysis.setGnssClockErrors("GnssClock Errors.");
        gnssAnalysis.setGnssMeasurementErrors("GnssMeasurement Errors.");

        dataFilter = new DataFilter();
    }

    public static GNSSRaw ReadGnssLogger(String dirName, String fileName) throws Exception {
        if (!checkFileType(fileName)) {
            throw new Exception("Expecting file name of the form \"*.txt\", or \"*.csv");
        }

        // 将日志文件读入数字矩阵 S 和单元格数组 header
        String rawCsvFile = makeCsv(dirName, fileName);
        GNSSRaw rawCsv = readRawCsv(rawCsvFile);

        // 应用 dataFilter
        filterData(rawCsv);

        // 将数据打包到gnssRaw结构中
        // TODO: 可能后续需要进行优化

        // 检查时钟和测量值
        CheckGnssClock();

        // 报告缺失值
        ReportMissingFields();
        System.out.println(gnssAnalysis.getApiPassFail());

        return gnssRaw;
    }

    private static boolean checkFileType(String fileName) {

        String fileNameType = fileName.substring(fileName.length() - 4);
        for (String str : allowFiles) {
            if (str.equals(fileNameType)) {
                return true;
            }
        }

        return false;
    }

    /**
     * %% make csv file, if necessary.
     */
    private static String makeCsv(String dirName, String fileName) throws IOException {
        if (!("" + dirName.charAt(dirName.length() - 1)).equals(File.separator)) {
            dirName += File.separator;
        }

        String csvFileName = dirName + "raw.csv";

        if (fileName.substring(fileName.length() - 4).equals(".csv")) {
            // input file is a csv file, nothing more to do here
            return csvFileName;
        }

        String extendedFileName = dirName + fileName;
        System.out.println("ReadGnssLogger \t\textendedFileName:" + extendedFileName);

        // read version
        File file = new File(extendedFileName);
        if (!file.canRead()) {
            throw new Error("file " + extendedFileName + " not found");
        }

        // 一行一行读，来获取版本信息
        boolean versionFLag = false;
        String line = "";
        List<String> allLines = null;
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            allLines = Files.readAllLines(Paths.get(extendedFileName), StandardCharsets.UTF_8);
        }
        int index = 0;
        for (; index < allLines.size(); ++index) {
            if (allLines.get(index).toLowerCase(Locale.ROOT).contains("version")) {
                versionFLag = true;
                line = allLines.get(index);
                break;
            }
        }
        if (!versionFLag) {
            System.out.println("ReadGnssLogger \t\tCould not find Version" + extendedFileName + " in input file");
            return csvFileName;
        }

        // look for the beginning of the version number, e.g. 1.4.0.0
        // 遍历 line 找到前四个数字
        int count = 0;
        List<Integer> version = new ArrayList<>();
        for (int i = 0; i < line.length(); i++) {
            if (Character.isDigit(line.charAt(i))) {
                version.add(line.charAt(i) - '0');
                ++count;
                if (count >= 4) {
                    break;
                }
            }
        }

        while (version.size() > 4) {
            version.add(0);
        }

        // Now extract the platform
        int k = line.indexOf("Platform:");
        String sPlatform = "";
        if (k >= 0) {
            sPlatform = line.substring(k + 9);
        }

        if (!sPlatform.contains("N")) {
            System.out.println("This version of ReadGnssLogger supports Android");
            System.out.println("WARNING: did not find \"Platform\" type in log file, expected \"Platform: N\"");
            System.out.println("Please Update GnssLogger");
            sPlatform = "N";
        }

        List<Integer> versionCom = Arrays.asList(1, 4, 0, 0);
        String result = CompareVersions(version, versionCom);

        if ("before".equals(result)) {
            System.out.println("This version of ReadGnssLogger supports v1.4.0.0 onwards");
            throw new Error("Found " + line + " in log file");
        }

        // write csv file with header and numbers
        /*
        * We could use grep and sed to make a csv file
        % fclose(txtfileID);
        % system(['grep -e ''Raw,'' ',extendedFileName,...
        %     ' | sed -e ''s/true/1/'' -e ''s/false/0/'' -e ''s/# //'' ',...
        %     ' -e ''s/Raw,//'' ',... %replace "Raw," with nothing
        %     '-e ''s/(//g'' -e ''s/)//g'' > ',csvFileName]);
        %
        % On versions from v1.4.0.0 N:
        % grep on "Raw," replace alpha characters amongst the numbers,
        % remove parentheses in the header,
        % note use of /g for "global" so sed acts on every occurrence in each line
        % csv file "prs.csv" now contains a header row followed by numerical data
        %
        % But we'll do the same thing using Matlab, so people don't need grep/sed:
        * */
        try {
            BufferedWriter out = null;
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                out = new BufferedWriter(new OutputStreamWriter(Files.newOutputStream(Paths.get(csvFileName)), StandardCharsets.UTF_8));
            }
            for (; index < allLines.size(); ++index) {
                line = allLines.get(index);
                if (!line.contains("Raw,")) {
                    continue;
                }

                line = line.replace("Raw,", "").replace("#", "").replace(" ", "");
                out.write(line);
                out.newLine();
            }
            out.flush();
            out.close();
        } catch (Exception e) {
            e.printStackTrace();
        }

        return csvFileName;
    }

    /**
     * 读取原始的星历数据
     */
    private static GNSSRaw readRawCsv(String rawCsvFile) {
        // 读Header
        File csv = new File(rawCsvFile);
        csv.setReadable(true);
        csv.setWritable(true);
        InputStreamReader isr = null;
        BufferedReader br = null;
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                isr = new InputStreamReader(Files.newInputStream(csv.toPath()), StandardCharsets.UTF_8);
            }
            br = new BufferedReader(isr);
        } catch (Exception e) {
            e.printStackTrace();
            throw new Error("file " + rawCsvFile + " not found");
        }
        String line = "";
        GNSSRaw gnssRaw = new GNSSRaw();
        try {
            // 处理标题头
            line = br.readLine();
            System.out.println("Header \t\t" + line);
            if (!line.contains("TimeNanos")) {
                throw new Error("\"TimeNanos\" string not found in file ");
            }
            header = line.split(",");

            // 处理内容 我们将TimeNanos和FullBiasNanos作为int64，将其他值作为double，将空值作为null
            String preLine = "";
            int i = 0;
            while ((line = br.readLine()) != null) {
                ++i;
                if (i == 8133) {
                    System.out.println("");
                }
                String[] strings = line.split(",");
                gnssRaw.ElapsedRealtimeMillis.add("".equals(strings[0])? null: Double.parseDouble(strings[0]));
                gnssRaw.TimeNanos.add("".equals(strings[1])? null: MathUtils.bigDecimalToLong(strings[1]));
                gnssRaw.LeapSecond.add("".equals(strings[2])? null: Double.parseDouble(strings[2]));
                gnssRaw.TimeUncertaintyNanos.add("".equals(strings[3])? null: Double.parseDouble(strings[3]));
                gnssRaw.FullBiasNanos.add("".equals(strings[4])? null: MathUtils.bigDecimalToLong(strings[4]));
                gnssRaw.BiasNanos.add("".equals(strings[5])? null: Double.parseDouble(strings[5]));
                gnssRaw.BiasUncertaintyNanos.add("".equals(strings[6])? null: Double.parseDouble(strings[6]));
                gnssRaw.DriftNanosPerSecond.add("".equals(strings[7])? null: Double.parseDouble(strings[7]));
                gnssRaw.DriftUncertaintyNanosPerSecond.add("".equals(strings[8])? null: Double.parseDouble(strings[8]));
                gnssRaw.HardwareClockDiscontinuityCount.add("".equals(strings[9])? null: Double.parseDouble(strings[9]));
                gnssRaw.Svid.add("".equals(strings[10])? null: Double.parseDouble(strings[10]));
                gnssRaw.TimeOffsetNanos.add("".equals(strings[11])? null: Double.parseDouble(strings[11]));
                gnssRaw.State.add("".equals(strings[12])? null: MathUtils.bigDecimalToLong(strings[12]));
                gnssRaw.ReceivedSvTimeNanos.add("".equals(strings[13])? null: MathUtils.bigDecimalToLong(strings[13]));
                gnssRaw.ReceivedSvTimeUncertaintyNanos.add("".equals(strings[14])? null: MathUtils.bigDecimalToLong(strings[14]));
                gnssRaw.Cn0DbHz.add("".equals(strings[15])? null: Double.parseDouble(strings[15]));
                gnssRaw.PseudorangeRateMetersPerSecond.add("".equals(strings[16])? null: Double.parseDouble(strings[16]));
                gnssRaw.PseudorangeRateUncertaintyMetersPerSecond.add("".equals(strings[17])? null: Double.parseDouble(strings[17]));
                gnssRaw.AccumulatedDeltaRangeState.add("".equals(strings[18])? null: Double.parseDouble(strings[18]));
                gnssRaw.AccumulatedDeltaRangeMeters.add("".equals(strings[19])? null: Double.parseDouble(strings[19]));
                gnssRaw.AccumulatedDeltaRangeUncertaintyMeters.add("".equals(strings[20])? null: Double.parseDouble(strings[20]));
                gnssRaw.CarrierFrequencyHz.add("".equals(strings[21])? null: Double.parseDouble(strings[21]));
                gnssRaw.CarrierCycles.add("".equals(strings[22])? null: MathUtils.bigDecimalToLong(strings[22]));
//                gnssRaw.CarrierPhase.add("".equals(strings[23])? null: Double.parseDouble(strings[23]));
//                gnssRaw.CarrierPhaseUncertainty.add("".equals(strings[24])? null: Double.parseDouble(strings[24]));
                gnssRaw.MultipathIndicator.add("".equals(strings[25])? null: Double.parseDouble(strings[25]));
//                gnssRaw.SnrInDb.add("".equals(strings[26])? null: Double.parseDouble(strings[26]));
                gnssRaw.ConstellationType.add("".equals(strings[27])? null: MathUtils.bigDecimalToLong(strings[27]));
                gnssRaw.AgcDb.add("".equals(strings[28])? null: Double.parseDouble(strings[28]));
            }
            System.out.println(preLine);
        } catch (IOException e) {
            e.printStackTrace();
        }

        return gnssRaw;
    }

    /**
     * 对输入的原始数据进行完整性检验
     */
    private static void filterData(GNSSRaw rawCsv) {
        List<String> needData = Arrays.asList("FullBiasNanos", "ConstellationType", "State");

        // 校验 header 中是否包含指定字段，因为我们的目的就是为了校验这些数值
        for (String str :needData) {
            boolean existFlag = false;
            for (String headerVal :header) {
                if (str.equals(headerVal)) {
                    existFlag = true;
                    break;
                }
            }

            if (!existFlag) {
                throw new Error("str not found in header");
            }
        }

        for (int i = 0; i < rawCsv.State.size(); i++) {
            boolean bOK = true;
            // 校验 FullBiasNanos
            if (!dataFilter.nanosCheck(rawCsv.FullBiasNanos.get(i))) {
                bOK = false;
            }

            // 校验 ConstellationType
            if (!dataFilter.ConstellationTypeCheck(rawCsv.ConstellationType.get(i))) {
                bOK = false;
            }

            // 校验 State
            if (!dataFilter.stateCheck(rawCsv.State.get(i))) {
                bOK = false;
            }

            if (bOK) {
                gnssRaw.ElapsedRealtimeMillis.add(rawCsv.ElapsedRealtimeMillis.get(i));
                gnssRaw.TimeNanos.add(rawCsv.TimeNanos.get(i));
                gnssRaw.LeapSecond.add(rawCsv.LeapSecond.get(i));
                gnssRaw.TimeUncertaintyNanos.add(rawCsv.TimeUncertaintyNanos.get(i));
                gnssRaw.FullBiasNanos.add(rawCsv.FullBiasNanos.get(i));
                gnssRaw.BiasNanos.add(rawCsv.BiasNanos.get(i));
                gnssRaw.BiasUncertaintyNanos.add(rawCsv.BiasUncertaintyNanos.get(i));
                gnssRaw.DriftNanosPerSecond.add(rawCsv.DriftNanosPerSecond.get(i));
                gnssRaw.DriftUncertaintyNanosPerSecond.add(rawCsv.DriftUncertaintyNanosPerSecond.get(i));
                gnssRaw.HardwareClockDiscontinuityCount.add(rawCsv.HardwareClockDiscontinuityCount.get(i));
                gnssRaw.Svid.add(rawCsv.Svid.get(i));
                gnssRaw.TimeOffsetNanos.add(rawCsv.TimeOffsetNanos.get(i));
                gnssRaw.State.add(rawCsv.State.get(i));
                gnssRaw.ReceivedSvTimeNanos.add(rawCsv.ReceivedSvTimeNanos.get(i));
                gnssRaw.ReceivedSvTimeUncertaintyNanos.add(rawCsv.ReceivedSvTimeUncertaintyNanos.get(i));
                gnssRaw.Cn0DbHz.add(rawCsv.Cn0DbHz.get(i));
                gnssRaw.PseudorangeRateMetersPerSecond.add(rawCsv.PseudorangeRateMetersPerSecond.get(i));
                gnssRaw.PseudorangeRateUncertaintyMetersPerSecond.add(rawCsv.PseudorangeRateUncertaintyMetersPerSecond.get(i));
                gnssRaw.AccumulatedDeltaRangeState.add(rawCsv.AccumulatedDeltaRangeState.get(i));
                gnssRaw.AccumulatedDeltaRangeMeters.add(rawCsv.AccumulatedDeltaRangeMeters.get(i));
                gnssRaw.AccumulatedDeltaRangeUncertaintyMeters.add(rawCsv.AccumulatedDeltaRangeUncertaintyMeters.get(i));
                gnssRaw.CarrierFrequencyHz.add(rawCsv.CarrierFrequencyHz.get(i));
                gnssRaw.CarrierCycles.add(rawCsv.CarrierCycles.get(i));
//                gnssRaw.CarrierPhase.add(rawCsv.CarrierPhase.get(i));
//                gnssRaw.CarrierPhaseUncertainty.add(rawCsv.CarrierPhaseUncertainty.get(i));
                gnssRaw.MultipathIndicator.add(rawCsv.MultipathIndicator.get(i));
//                gnssRaw.SnrInDb.add(rawCsv.SnrInDb.get(i));
                gnssRaw.ConstellationType.add(rawCsv.ConstellationType.get(i));
                gnssRaw.AgcDb.add(rawCsv.AgcDb.get(i));
            }
        }

        System.out.println(gnssRaw.AgcDb.size());
        if (gnssRaw.State.size() == 0) {
            throw new Error("All measurements removed. Specify dataFilter less strictly");
        }
    }

    /**
     * 校验时钟信息
     */
    private static void CheckGnssClock() {
        // 检查gnssRaw中的时钟值
        boolean bOK = true;

        // 初始化字符串以记录失败消息
        StringBuilder sFail = new StringBuilder();
        int N = gnssRaw.ReceivedSvTimeNanos.size();

        // 校验 TimeNanos
        boolean failFlag = true;
        for (String str :header) {
            if ("TimeNanos".equals(str)) {
                failFlag = false;
                break;
            }
        }
        if (failFlag) {
            sFail.append(" TimeNanos  missing from GnssLogger File.");
            System.out.println("WARNING: TimeNanos  missing from GnssLogger File.");
            bOK = false;
        }

        // 校验 FullBiasNanos
        failFlag = true;
        for (String str :header) {
            if ("FullBiasNanos".equals(str)) {
                failFlag = false;
                break;
            }
        }
        if (failFlag) {
            sFail.append(" FullBiasNanos missing from GnssLogger file.");
            System.out.println("WARNING: FullBiasNanos missing from GnssLogger file.");
            bOK = false;
        }

        // 校验 BiasNanos
        failFlag = true;
        for (String str :header) {
            if ("BiasNanos".equals(str)) {
                failFlag = false;
                break;
            }
        }
        if (failFlag) {
            gnssRaw.BiasNanos = new ArrayList<>(gnssRaw.FullBiasNanos.size());
        }

        // 校验 HardwareClockDiscontinuityCount
        failFlag = true;
        for (String str :header) {
            if ("HardwareClockDiscontinuityCount".equals(str)) {
                failFlag = false;
                break;
            }
        }
        if (failFlag) {
            gnssRaw.HardwareClockDiscontinuityCount = new ArrayList<>(gnssRaw.FullBiasNanos.size());
            System.out.println("WARNING: Added HardwareClockDiscontinuityCount=0 because it is missing from GNSS Logger file");
        }

        // check FullBiasNanos, it should be negative values
        failFlag = false;
        for (Long data: gnssRaw.FullBiasNanos) {
            if (data > 0) {
                failFlag = true;
                break;
            }
        }
        if (failFlag) {
            throw new Error("FullBiasNanos changes sign within log file, this should never happen");
        }

        // 计算测量的全周期时间，以毫秒为单位
        for (int i = 0; i < gnssRaw.TimeNanos.size(); i++) {
//            gnssRaw.allRxMillis.add((gnssRaw.TimeNanos.get(i) - gnssRaw.FullBiasNanos.get(i)) / 1000000);
            gnssRaw.allRxMillis.add(
                    new BigDecimal(gnssRaw.TimeNanos.get(i))
                            .subtract(new BigDecimal(gnssRaw.FullBiasNanos.get(i)))
                            .divide(new BigDecimal(1000000))
                            .add(new BigDecimal("0.5"))
                            .longValue()
            );
        }

        if (!bOK){
            gnssAnalysis.setApiPassFail("FAIL " + sFail);
        }
    }

    /**
     * 报告缺失字段
     */
    private static void ReportMissingFields() {
        boolean bOk = true;
        boolean failFlag = false;

        // report missing clock fields
        List<String> clockFields = Arrays.asList("TimeNanos",
                "TimeUncertaintyNanos",
                "LeapSecond",
                "FullBiasNanos",
                "BiasUncertaintyNanos",
                "DriftNanosPerSecond",
                "DriftUncertaintyNanosPerSecond",
                "HardwareClockDiscontinuityCount",
                "BiasNanos");
        for (String clockField :clockFields) {
            bOk = true;
            for (String headerData :header) {
                if (clockField.equals(headerData)) {
                    bOk = false;
                    break;
                }
            }

            if (bOk) {
                failFlag = true;
                System.out.println(gnssAnalysis.getGnssClockErrors() + "\t Missing Fields:" + clockField);
            }
        }

        // report missing measurement fields
        List<String> measurementFields = Arrays.asList("Cn0DbHz",
                "ConstellationType",
                "MultipathIndicator",
                "PseudorangeRateMetersPerSecond",
                "PseudorangeRateUncertaintyMetersPerSecond",
                "ReceivedSvTimeNanos",
                "ReceivedSvTimeUncertaintyNanos",
                "State",
                "Svid",
                "AccumulatedDeltaRangeMeters",
                "AccumulatedDeltaRangeUncertaintyMeters");
        for (String measurementField :measurementFields) {
            bOk = true;
            for (String headerData :header) {
                if (measurementField.equals(headerData)) {
                    bOk = false;
                    break;
                }
            }

            if (bOk) {
                failFlag = true;
                System.out.println(gnssAnalysis.getGnssMeasurementErrors() + "\t Missing Fields:" + measurementField);
            }
        }

        // assign pass/fail
        if (gnssAnalysis.getApiPassFail() == null || !gnssAnalysis.getApiPassFail().contains("FAIL")) {
            if (failFlag) {
                gnssAnalysis.setApiPassFail("FAIL BECAUSE OF MISSING FIELDS");
            } else {
                gnssAnalysis.setApiPassFail("PASS");
            }
        }
    }

    private static String CompareVersions(List<Integer> version, List<Integer> versionCom) {
        if (version.size() != versionCom.size()) {
            throw new Error("The two inputs must be scalars or vectors of the same length");
        }

        List<Integer> result = MathUtils.listSub(version, versionCom);
        for (Integer in : result) {
            if (in > 0) {
                return "after";
            } else if (in < 0) {
                return "before";
            }
        }

        return "equal";
    }

    public static void main(String[] args) {
        File file = new File("D:\\Programs\\pseudoranges_log_2021_08_23_16_21_01.txt");
        System.out.println(file);
        System.out.println(file.canRead());


        String str = "502470145,1025486443774,18,0.0,-1313741200513292546,0.0,333.564095198152,225.03089506113173,0.0,3,193,0.0,0,0,7,0.0,439.17582375926764,0.05,16,0.0,0.1,1.57542003E9,,,,0,,4,3.0,1.57542003E9";
        String[] strings = str.split(",");
        System.out.println("length:" + strings.length);
        for (int i = 0; i < strings.length; i++) {
            System.out.println("index:" + i + " record:" + strings[i] + ".");
            if ("".equals(strings[i])) {
                System.out.println("index:" + i);
            }
        }

        Long data = 16399L;
        System.out.println(dataFilter.stateCheck(data));

        data = 1L;
        System.out.println(dataFilter.ConstellationTypeCheck(data));

        data = -1313741200513326082L;
        System.out.println(dataFilter.nanosCheck(data));

        System.out.println("===========================================================================");
        String dataStr = "-1313741200513292546";
        BigDecimal bigDecimal = new BigDecimal(dataStr);
        System.out.println("bigDecimal:" + bigDecimal);
        BigDecimal decimal = bigDecimal.add(new BigDecimal("0.5"));
        long value = decimal.longValue();
        System.out.println("value:" + value);
    }
}
