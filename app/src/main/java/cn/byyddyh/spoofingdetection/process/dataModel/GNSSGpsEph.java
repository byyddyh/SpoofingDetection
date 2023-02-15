package cn.byyddyh.spoofingdetection.process.dataModel;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import cn.byyddyh.spoofingdetection.process.utils.GpsConstants;
import cn.byyddyh.spoofingdetection.process.utils.MathUtils;

public class GNSSGpsEph {

    public List<Integer> PRN;               /*SV PRN number*/
    public List<Integer> Toc;               /*Time of clock (seconds)*/
    public List<BigDecimal> af0;            /*SV clock bias (seconds)*/
    public List<BigDecimal> af1;            /*SV clock drift (sec/sec)*/
    public List<BigDecimal> af2;            /*SV clock drift rate (sec/sec2)*/
    public List<Integer> IODE;              /*Issue of data, ephemeris / 发布数据、星历表*/
    public List<Double> Crs;                /*Sine harmonic correction to orbit radius (meters) / 轨道半径正弦谐波校正（米）*/
    public List<BigDecimal> Delta_n;        /*Mean motion difference from computed value (radians/sec) / 与计算值的平均运动差（弧度/秒）*/
    public List<Double> M0;                 /*Mean anomaly at reference time (radians) / 参考时间的平均异常（弧度）*/
    public List<BigDecimal> Cuc;            /*Cosine harmonic correction to argument of lat (radians) / lat参数的余弦谐波校正（弧度）*/
    public List<Double> e;                  /*Eccentricity (dimensionless) / 偏心度（无量纲）*/
    public List<BigDecimal> Cus;            /*Sine harmonic correction to argument of latitude (radians) / 纬度（弧度）参数的正弦谐波校正*/
    public List<BigDecimal> Asqrt;          /*Square root of semi-major axis (meters^1/2) / 半长轴的平方根（米^1/2）*/
    public List<Integer> Toe;               /*Reference time of ephemeris (seconds) / 星历表参考时间（秒）*/
    public List<BigDecimal> Cic;            /*Cosine harmonic correction to angle of inclination (radians) / 倾角余弦谐波校正（弧度）*/
    public List<Double> OMEGA;              /*Longitude of ascending node at weekly epoch (radians) / 周历元上升节点的经度（弧度）*/
    public List<BigDecimal> Cis;            /*Sine harmonic correction to angle of inclination (radians) / 倾斜角度的正弦谐波校正（弧度）*/
    public List<Double> i0;                 /*Inclination angle at reference time (radians) / 参考时间的倾角（弧度）*/
    public List<Double> Crc;                /*Cosine harmonic correction to the orbit radius (meters) / 轨道半径的余弦谐波校正（米）*/
    public List<Double> omega;              /*Argument of perigee (radians) / 近地点参数（弧度）*/
    public List<BigDecimal> OMEGA_DOT;      /*Rate of right ascension (radians/sec) / 赤经速率（弧度/秒）*/
    public List<BigDecimal> IDOT;           /*Rate of inclination angle (radians/sec) / 倾角速率（弧度/秒）*/
    public List<Integer> codeL2;            /*codes on L2 channel*/
    public List<Integer> GPS_Week;          /*GPS week (to go with Toe), (NOT Mod 1024) / GPS周，（非1024型）*/
    public List<Integer> L2Pdata;           /*L2 P data flag / L2 P数据标志*/
    public List<Double> accuracy;           /*SV user range accuracy (meters) / SV用户范围精度（米）*/
    public List<Integer> health;            /*Satellite health*/
    public List<BigDecimal> TGD;            /*Group delay (seconds)*/
    public List<Integer> IODC;              /*Issue of Data, Clock */
    public List<Integer> ttx;               /*Transmission time of message (seconds)*/
    public List<Integer> Fit_interval;      /*fit interval (hours), zero if not known*/

    public GNSSGpsEph() {
        this.PRN = new ArrayList<>();
        Toc = new ArrayList<>();
        this.af0 = new ArrayList<>();
        this.af1 = new ArrayList<>();
        this.af2 = new ArrayList<>();
        this.IODE = new ArrayList<>();
        Crs = new ArrayList<>();
        Delta_n = new ArrayList<>();
        M0 = new ArrayList<>();
        Cuc = new ArrayList<>();
        this.e = new ArrayList<>();
        Cus = new ArrayList<>();
        Asqrt = new ArrayList<>();
        Toe = new ArrayList<>();
        Cic = new ArrayList<>();
        this.OMEGA = new ArrayList<>();
        Cis = new ArrayList<>();
        this.i0 = new ArrayList<>();
        Crc = new ArrayList<>();
        this.omega = new ArrayList<>();
        this.OMEGA_DOT = new ArrayList<>();
        this.IDOT = new ArrayList<>();
        this.codeL2 = new ArrayList<>();
        this.GPS_Week = new ArrayList<>();
        L2Pdata = new ArrayList<>();
        this.accuracy = new ArrayList<>();
        this.health = new ArrayList<>();
        this.TGD = new ArrayList<>();
        this.IODC = new ArrayList<>();
        this.ttx = new ArrayList<>();
        Fit_interval = new ArrayList<>();
    }

    public GNSSGpsEph(List<Integer> PRN, List<Integer> toc, List<BigDecimal> af0, List<BigDecimal> af1,
                      List<BigDecimal> af2, List<Integer> IODE, List<Double> crs, List<BigDecimal> delta_n,
                      List<Double> m0, List<BigDecimal> cuc, List<Double> e, List<BigDecimal> cus, List<BigDecimal> asqrt,
                      List<Integer> toe, List<BigDecimal> cic, List<Double> OMEGA, List<BigDecimal> cis, List<Double> i0,
                      List<Double> crc, List<Double> omega, List<BigDecimal> OMEGA_DOT, List<BigDecimal> IDOT, List<Integer> codeL2,
                      List<Integer> GPS_Week, List<Integer> l2Pdata, List<Double> accuracy, List<Integer> health, List<BigDecimal> TGD,
                      List<Integer> IODC, List<Integer> ttx, List<Integer> fix_interval) {
        this.PRN = PRN;
        Toc = toc;
        this.af0 = af0;
        this.af1 = af1;
        this.af2 = af2;
        this.IODE = IODE;
        Crs = crs;
        Delta_n = delta_n;
        M0 = m0;
        Cuc = cuc;
        this.e = e;
        Cus = cus;
        Asqrt = asqrt;
        Toe = toe;
        Cic = cic;
        this.OMEGA = OMEGA;
        Cis = cis;
        this.i0 = i0;
        Crc = crc;
        this.omega = omega;
        this.OMEGA_DOT = OMEGA_DOT;
        this.IDOT = IDOT;
        this.codeL2 = codeL2;
        this.GPS_Week = GPS_Week;
        L2Pdata = l2Pdata;
        this.accuracy = accuracy;
        this.health = health;
        this.TGD = TGD;
        this.IODC = IODC;
        this.ttx = ttx;
        Fit_interval = fix_interval;
    }

    @Override
    public String toString() {
        return "GnssGpsEph{" +
                "PRN=" + PRN +
                ",\n Toc=" + Toc +
                ",\n af0=" + af0 +
                ",\n af1=" + af1 +
                ",\n af2=" + af2 +
                ",\n IODE=" + IODE +
                ",\n Crs=" + Crs +
                ",\n Delta_n=" + Delta_n +
                ",\n M0=" + M0 +
                ",\n Cuc=" + Cuc +
                ",\n e=" + e +
                ",\n Cus=" + Cus +
                ",\n Asqrt=" + Asqrt +
                ",\n Toe=" + Toe +
                ",\n Cic=" + Cic +
                ",\n OMEGA=" + OMEGA +
                ",\n Cis=" + Cis +
                ",\n i0=" + i0 +
                ",\n Crc=" + Crc +
                ",\n omega=" + omega +
                ",\n OMEGA_DOT=" + OMEGA_DOT +
                ",\n IDOT=" + IDOT +
                ",\n codeL2=" + codeL2 +
                ",\n GPS_Week=" + GPS_Week +
                ",\n L2Pdata=" + L2Pdata +
                ",\n accuracy=" + accuracy +
                ",\n health=" + health +
                ",\n TGD=" + TGD +
                ",\n IODC=" + IODC +
                ",\n ttx=" + ttx +
                ",\n Fix_interval=" + Fit_interval +
                '}';
    }

    /**
     * Calculate satellite clock bias
     */
    public static double[] gpsEph2Dtsv(GNSSGpsEph gpsEph, List<Double> tS) {
        int pt = tS.size();
        int p = gpsEph.PRN.size();
        if (p > 1 && pt != p) {
            throw new Error("If gpsEph is a vector tS must be a vector with #rows = length(gpsEph)");
        }

        // Calculate dependent variables
        List<Double> MK = new ArrayList<>();
        for (int i = 0; i < tS.size(); i++) {
            double tk = tS.get(i) - gpsEph.Toe.get(i);
            if (tk > 302400.0) {
                tk = tk - GpsConstants.WEEKSEC;
            } else if (tk < -302400.0) {
                tk = tk + GpsConstants.WEEKSEC;
            }

            double no = Math.sqrt( GpsConstants.mu / gpsEph.Asqrt.get(i).pow(6).doubleValue());
            double n = no + gpsEph.Delta_n.get(i).doubleValue();
            MK.add(gpsEph.M0.get(i) + n * tk);
        }
        Double[] Ek = MathUtils.Kepler(MK, gpsEph.e);

        // Calculate satellite clock bias (See ICD-GPS-200 20.3.3.3.3.1)
        double[] dtsvS = new double[tS.size()];
        for (int i = 0; i < tS.size(); i++) {
            double dt = tS.get(i) - gpsEph.Toc.get(i);
            if (dt > 302400.00) {
                dt = dt - GpsConstants.WEEKSEC;
            } else if (dt < -302400.0){
                dt = dt + GpsConstants.WEEKSEC;
            }

            dtsvS[i] = gpsEph.af0.get(i).doubleValue() + gpsEph.af1.get(i).doubleValue() * dt + gpsEph.af2.get(i).doubleValue() * dt * dt +
                    GpsConstants.FREL * gpsEph.e.get(i) * gpsEph.Asqrt.get(i).doubleValue() * Math.sin(Ek[i]) - gpsEph.TGD.get(i).doubleValue();
        }
        return dtsvS;
    }

    public static void main(String[] args) {
        List<Double> mk = Arrays.asList(1.712257411040443, -2.860017312175392, 0.798477904040757, 0.449325337745797, -0.543676957533292, 0.287169903774706, -1.294070701791078, -0.520631742787847, 1.378303757967611);
        List<Double> e = Arrays.asList(0.006097595789470, 0.006854743580330, 0.005299657816070, 0.013526534428800, 0.001851130160500, 0.005524867330680, 0.001584041747270, 0.011399894137900, 0.001972229802050);
        System.out.println(Arrays.toString(MathUtils.Kepler(mk, e)));
    }
}
