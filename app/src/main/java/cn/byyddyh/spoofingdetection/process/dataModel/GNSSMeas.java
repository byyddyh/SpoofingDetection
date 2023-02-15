package cn.byyddyh.spoofingdetection.process.dataModel;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * 伪距测量值
 */
public class GNSSMeas {
    public List<BigDecimal> FctSeconds;                     /*全周期时间标签*/
    public List<Integer> ClkDCount;                         /*硬件时钟不连续计数*/
    public List<Integer> HwDscDelS;                         /*硬件时钟在每个不连续性期间的变化（秒）*/
    public List<Integer> Svid;                              /*在gnssRaw中找到的所有卫星id*/
    public List<BigDecimal> AzDeg;                          /*上一有效历元的方位角（度）*/
    public List<BigDecimal> ElDeg;                          /*上一有效历元的高程*/
    public List<BigDecimal[]> tRxSeconds;               /*接收时间，gps周秒数*/
    public List<BigDecimal[]> tTxSeconds;               /*传输时间，gps周秒数*/
    public List<BigDecimal[]> PrM;                      /*伪距，行i对应于FctSeconds（i）*/
    public List<Double[]> PrSigmaM;                     /*伪距误差估计（1-sigma）*/
    public List<Double[]> DelPrM;                       /*时钟连续时伪距的变化*/
    public List<Double[]> PrrMps;                       /*伪距率*/
    public List<Double[]> PrrSigmaMps;                  /**/
    public List<Double[]> AdrM;                         /**/
    public List<Double[]> AdrSigmaM;                    /**/
    public List<Double[]> AdrState;                    /**/
    public List<Double[]> Cn0DbHz;                      /*载噪比密度*/

    public GNSSMeas() {
        FctSeconds = new ArrayList<>();
        ClkDCount = new ArrayList<>();
        HwDscDelS = new ArrayList<>();
        Svid = new ArrayList<>();
        AzDeg = new ArrayList<>();
        ElDeg = new ArrayList<>();
        tRxSeconds = new ArrayList<>();
        tTxSeconds = new ArrayList<>();
        PrM = new ArrayList<>();
        PrSigmaM = new ArrayList<>();
        DelPrM = new ArrayList<Double[]>();
        PrrMps = new ArrayList<>();
        PrrSigmaMps = new ArrayList<>();
        AdrM = new ArrayList<>();
        AdrSigmaM = new ArrayList<>();
        AdrState = new ArrayList<>();
        Cn0DbHz = new ArrayList<>();
    }

    public GNSSMeas(List<BigDecimal> fctSeconds, List<Integer> clkDCount, List<Integer> hwDscDelS, List<Integer> svid,
                    List<BigDecimal> azDeg, List<BigDecimal> elDeg, List<BigDecimal[]> tRxSeconds, List<BigDecimal[]> tTxSeconds,
                    List<BigDecimal[]> prM, List<Double[]> prSigmaM, List<Double[]> delPrM, List<Double[]> prrMps, List<Double[]> prrSigmaMps,
                    List<Double[]> adrM, List<Double[]> adrSigmaM, List<Double[]> adrState, List<Double[]> cn0DbHz) {
        FctSeconds = fctSeconds;
        ClkDCount = clkDCount;
        HwDscDelS = hwDscDelS;
        Svid = svid;
        AzDeg = azDeg;
        ElDeg = elDeg;
        this.tRxSeconds = tRxSeconds;
        this.tTxSeconds = tTxSeconds;
        PrM = prM;
        PrSigmaM = prSigmaM;
        DelPrM = delPrM;
        PrrMps = prrMps;
        PrrSigmaMps = prrSigmaMps;
        AdrM = adrM;
        AdrSigmaM = adrSigmaM;
        AdrState = adrState;
        Cn0DbHz = cn0DbHz;
    }

    @Override
    public String toString() {
        return "GNSSMeas{" +
                "\nFctSeconds=" + FctSeconds +
                ",\n ClkDCount=" + ClkDCount +
                ",\n HwDscDelS=" + HwDscDelS +
                ",\n Svid=" + Svid +
                ",\n AzDeg=" + AzDeg +
                ",\n ElDeg=" + ElDeg +
                ",\n tRxSeconds=" + tRxSeconds +
                ",\n tTxSeconds=" + tTxSeconds +
                ",\n PrM=" + PrM +
                ",\n PrSigmaM=" + PrSigmaM +
                ",\n DelPrM=" + DelPrM +
                ",\n PrrMps=" + PrrMps +
                ",\n PrrSigmaMps=" + PrrSigmaMps +
                ",\n AdrM=" + AdrM +
                ",\n AdrSigmaM=" + AdrSigmaM +
                ",\n AdrState=" + AdrState +
                ",\n Cn0DbHz=" + Cn0DbHz +
                '}';
    }
}
