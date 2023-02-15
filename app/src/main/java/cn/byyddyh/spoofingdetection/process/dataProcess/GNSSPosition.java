package cn.byyddyh.spoofingdetection.process.dataProcess;

import android.util.Log;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;

import cn.byyddyh.spoofingdetection.LogFragment;
import cn.byyddyh.spoofingdetection.process.dataModel.GNSSGpsEph;
import cn.byyddyh.spoofingdetection.process.dataModel.GNSSMeas;
import cn.byyddyh.spoofingdetection.process.dataModel.GpsPvt;
import cn.byyddyh.spoofingdetection.process.dataModel.WlsVal;
import cn.byyddyh.spoofingdetection.process.utils.GNSSThresholds;
import cn.byyddyh.spoofingdetection.process.utils.GpsConstants;
import cn.byyddyh.spoofingdetection.process.utils.MathUtils;

/**
 * 从gnssMeas计算PVT(位置、速度和时间(UTC时间)。)
 */
public class GNSSPosition {

    private static HashMap<Integer, List<Integer>> gpsEphToSvid = new HashMap<>();

    // index of columns
    private static final int jWk = 0, jSec = 1, jSv = 2, jPr = 3, jPrSig = 4, jPrr = 5, jPrrSig = 6;

    /**
     * initial state: [center of the Earth, bc=0, velocities = 0]'
     */
    private static double[] xo = new double[8];

    public static void gpsWlsPvt(GNSSMeas gnssMeas, GNSSGpsEph allGpsEph) {
        int N = gnssMeas.FctSeconds.size();
        long weekNum = gnssMeas.FctSeconds.get(0).longValue() / GpsConstants.WEEKSEC;

        // 这里的周展期待定检查（在ProcessGnssMeas中进行了检查，但该功能应该是独立的，因此我们应该再次检查，并在必要时将tRxSeconds调整为+-一周）
        // 顺便问一下，为什么不随身携带fct，而不用担心周数的麻烦以及相关周展期问题？
        // A、 因为当你把fct放进一个double时，你不能得到超过1000ns（1微秒）的预测。这将在距离残差计算中造成约800m/s*1us（卫星距离速率*时间误差）~1mm的误差。
        // 那么呢？好吧，如果你用载波相位开始处理，这些误差可能会累积。
        GpsPvt gpsPvt = new GpsPvt();

        // 确定Svid和index之间的关系
        loadMap(allGpsEph);

        gpsPvt.FctSeconds = gnssMeas.FctSeconds;
        Double[] tmp = new Double[3];

        for (int i = 0; i < N; i++) {
            List<Integer> svid = new ArrayList<>();

            // 根据 gnssMeas.PrM 选出有效的svid
            for (int j = 0; j < gnssMeas.PrM.get(i).length; j++) {
                if (gnssMeas.PrM.get(i)[j] != null) {
                    svid.add(gnssMeas.Svid.get(j));
                }
            }

            // 找到最接近最新鲜的星历数据
            GNSSGpsEph gpsEph = closestGpsEph(allGpsEph, svid, gnssMeas.FctSeconds.get(i));
            // svid for which we have ephemeris
            List<Integer> idList = gpsEph.PRN;
            int size = idList.size();
            if (size < 4) {
                // skip to next epoch
                continue;
            }

            gpsPvt.numSvs.add(size);

            /* WLS PVT */
            // for those svIds with valid ephemeris, pack prs matrix for WlsNav
            List<List<Double>> prs = new ArrayList<>();
            HashMap<Integer, Integer> idToSvidIndexMap = new HashMap<>();
            for (int k = 0; k < svid.size(); ++k) {
                idToSvidIndexMap.put(svid.get(k), k);
            }

            // 封装prs
            for (Integer is : idList) {
                Integer index = idToSvidIndexMap.get(is);
                List<Double> data = new ArrayList<>();
                data.add((double) weekNum);
                data.add(gnssMeas.tRxSeconds.get(i)[index].doubleValue());
                data.add(Double.valueOf(is));
                data.add(gnssMeas.PrM.get(i)[index].doubleValue());
                data.add(gnssMeas.PrSigmaM.get(i)[index]);
                data.add(gnssMeas.PrrMps.get(i)[index]);
                data.add(gnssMeas.PrrSigmaMps.get(i)[index]);
                prs.add(data);
            }


            // initialize speed to zero
            for (int j = 4; j < 7; j++) {
                xo[j] = 0;
            }

            // compute WLS solution
            WlsVal wlsVal = wlsPvt(prs, gpsEph, xo);
            for (int j = 0; j < xo.length; j++) {
                xo[j] += wlsVal.xHat[j];
            }

            // extract position states
            for (int j = 0; j < tmp.length; j++) {
                tmp[j] = xo[j];
            }
            Double[] llaDegDegM = PositionTrans.Xyz2Lla(tmp);
            Log.d("llaDegDegM", Arrays.toString(llaDegDegM));
            gpsPvt.allLlaDegDegM.add(llaDegDegM);
            gpsPvt.allBcMeters.add(xo[4]);

            // extract velocity states
            Double[][] RE2N = PositionTrans.RotEcef2Ned(llaDegDegM[0], llaDegDegM[1]);

            for (int j = 0; j < tmp.length; j++) {
                tmp[j] = xo[4 + j];
            }
            Double[] vNed = MathUtils.matrixMultipleArray(RE2N, tmp);
            gpsPvt.allVelMps.add(vNed);
            gpsPvt.allBcDotMps.add(xo[7]);
        }

        return;
    }

    private static void loadMap(GNSSGpsEph allGpsEph) {
        for (int i = 0; i < allGpsEph.PRN.size(); i++) {
            List<Integer> indexList = null;
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.N) {
                indexList = gpsEphToSvid.computeIfAbsent(allGpsEph.PRN.get(i), k -> new ArrayList<>());
                indexList.add(i);
            }
        }
    }

    /**
     * find ephemeris in a GPS ephemeris structure allGpsEph for all svIds listed
     * return gpsEph = unique ephemeris for svIds, with fctToe closest to fctSeconds
     */
    private static GNSSGpsEph closestGpsEph(GNSSGpsEph allGpsEph, List<Integer> svIds, BigDecimal fctSeconds) {
        GNSSGpsEph gpsEph = new GNSSGpsEph();

        for (int i = 0; i < svIds.size(); i++) {
            List<Integer> indexList = gpsEphToSvid.get(svIds.get(i));
            if (indexList.size() > 0) {
                double minVal = Double.MAX_VALUE;
                int minIndex = 0;
                Integer fitInterval = 0;
                // 遍历 indexList
                for (int j = 0; j < indexList.size(); j++) {
                    Integer index = indexList.get(j);
                    // find Toe within fit interval, set fit interval
                    Integer fitIntervalHours = allGpsEph.Fit_interval.get(index);

                    // Rinex says "Zero if not known", so adjust for zeros
                    if (fitIntervalHours == 0) {
                        fitIntervalHours = 4;
                    }

                    // full cycle time of ephemeris Toe
                    long fctToe = allGpsEph.GPS_Week.get(index) * GpsConstants.WEEKSEC + allGpsEph.Toe.get(index);

                    // find freshest Toe
                    double min = Math.abs(fctToe - fctSeconds.doubleValue());
                    if (minVal > min) {
                        fitInterval = fitIntervalHours;
                        minVal = min;
                        minIndex = j;
                    }
                }

                // 更新gpsEph
                gpsEph.PRN.add(allGpsEph.PRN.get(indexList.get(minIndex)));
                gpsEph.Toc.add(allGpsEph.Toc.get(indexList.get(minIndex)));
                gpsEph.af0.add(allGpsEph.af0.get(indexList.get(minIndex)));
                gpsEph.af1.add(allGpsEph.af1.get(indexList.get(minIndex)));
                gpsEph.af2.add(allGpsEph.af2.get(indexList.get(minIndex)));
                gpsEph.IODE.add(allGpsEph.IODE.get(indexList.get(minIndex)));
                gpsEph.Crs.add(allGpsEph.Crs.get(indexList.get(minIndex)));
                gpsEph.Delta_n.add(allGpsEph.Delta_n.get(indexList.get(minIndex)));
                gpsEph.M0.add(allGpsEph.M0.get(indexList.get(minIndex)));
                gpsEph.Cuc.add(allGpsEph.Cuc.get(indexList.get(minIndex)));
                gpsEph.e.add(allGpsEph.e.get(indexList.get(minIndex)));
                gpsEph.Cus.add(allGpsEph.Cus.get(indexList.get(minIndex)));
                gpsEph.Asqrt.add(allGpsEph.Asqrt.get(indexList.get(minIndex)));
                gpsEph.Toe.add(allGpsEph.Toe.get(indexList.get(minIndex)));
                gpsEph.Cic.add(allGpsEph.Cic.get(indexList.get(minIndex)));
                gpsEph.OMEGA.add(allGpsEph.OMEGA.get(indexList.get(minIndex)));
                gpsEph.Cis.add(allGpsEph.Cis.get(indexList.get(minIndex)));
                gpsEph.i0.add(allGpsEph.i0.get(indexList.get(minIndex)));
                gpsEph.Crc.add(allGpsEph.Crc.get(indexList.get(minIndex)));
                gpsEph.omega.add(allGpsEph.omega.get(indexList.get(minIndex)));
                gpsEph.OMEGA_DOT.add(allGpsEph.OMEGA_DOT.get(indexList.get(minIndex)));
                gpsEph.IDOT.add(allGpsEph.IDOT.get(indexList.get(minIndex)));
                gpsEph.codeL2.add(allGpsEph.codeL2.get(indexList.get(minIndex)));
                gpsEph.GPS_Week.add(allGpsEph.GPS_Week.get(indexList.get(minIndex)));
                gpsEph.L2Pdata.add(allGpsEph.L2Pdata.get(indexList.get(minIndex)));
                gpsEph.accuracy.add(allGpsEph.accuracy.get(indexList.get(minIndex)));
                gpsEph.health.add(allGpsEph.health.get(indexList.get(minIndex)));
                gpsEph.TGD.add(allGpsEph.TGD.get(indexList.get(minIndex)));
                gpsEph.IODC.add(allGpsEph.IODC.get(indexList.get(minIndex)));
                gpsEph.ttx.add(allGpsEph.ttx.get(indexList.get(minIndex)));
                gpsEph.Fit_interval.add(allGpsEph.Fit_interval.get(indexList.get(minIndex)));
            }
        }

        return gpsEph;
    }

    /**
     * calculate a weighted least squares PVT solution, xHat given pseudoranges, pr rates, and initial state
     */
    private static WlsVal wlsPvt(List<List<Double>> prs, GNSSGpsEph gpsEph, double[] xo) {
        if (!checkInputs(prs, gpsEph)) {
            throw new Error("inputs not right size, or not properly aligned with each other");
        }

        List<Double> ttxSeconds = new ArrayList<>();
        List<Double> ttxWeek = new ArrayList<>();
        for (List<Double> pr : prs) {
            // week of tx. Note - we could get a rollover, when ttx_sv goes negative, and it is handled in GpsEph2Pvt, where we work with fct
            // 注意：当ttx_sv变为负值时，我们可能会发生滚动，它在GpsEph2Pvt中处理，我们在这里使用fct
            ttxWeek.add(pr.get(jWk));
            // ttx by sv clock, this is accurate satellite time of tx, because we use actual pseudo-ranges
            // 这是精确的tx卫星时间，因为我们在这里使用实际的伪距离，而不是校正的距离。
            ttxSeconds.add(pr.get(jSec) - pr.get(jPr) / GpsConstants.LIGHTSPEED);
        }
        // 写下伪距离的等式，以看到rx时钟误差精确抵消，从而获得精确的GPS时间：我们从sv时间中减去卫星时钟误差，如下所述：
        double[] dtsvS = GNSSGpsEph.gpsEph2Dtsv(gpsEph, ttxSeconds);
        int numVal = dtsvS.length;

        // 从sv时间中减去dtsv得到真实的gps时间
        List<Double> ttx = new ArrayList<>();
        for (int i = 0; i < numVal; i++) {
            ttx.add(ttxSeconds.get(i) - dtsvS[i]);
        }

        // calculate satellite position at ttx
        XyzNode xyzNode = gpsEph2Pvt(gpsEph, ttxWeek, ttx);
        List<Double[]> svXyzTtx = xyzNode.xyzM;
        List<Double> dtsv = xyzNode.dtsvS;
        List<Double[]> svXyzDot = xyzNode.vMps;
        List<Double> dtsvDot = xyzNode.dtsvSDot;

        Double[][] svXyzTrx = new Double[numVal][svXyzTtx.get(0).length];
        for (int i = 0; i < numVal; i++) {
            System.arraycopy(svXyzTtx.get(i), 0, svXyzTrx[i], 0, svXyzTtx.get(i).length);
        }

        // Compute weights
        Double[][] Wpr = MathUtils.generateDiag(1.0, prs, jPrSig);
        Double[][] Wrr = MathUtils.generateDiag(1.0, prs, jPrrSig);

        // iterate on this next part till change in pos & line of sight vectors converge
        Double[] xHat = {0.0, 0.0, 0.0, 0.0};
        Double[] dx = {Double.MAX_VALUE, Double.MAX_VALUE, Double.MAX_VALUE, Double.MAX_VALUE};
        int whileCount = 0, maxWhileCount = 100;

        // 负责向量
        Double[] xyz0 = new Double[]{xo[0], xo[1], xo[2]};

        // we expect the while loop to converge in < 10 iterations, even with initial
        // position on other side of the Earth (see Stanford course AA272C "Intro to GPS")
        for (int i = 4; i < 6; i++) {
            xo[i] = 0;
        }
        double bc = xo[3];
        double dtflight;
        double prHat;
        Double[] ones = new Double[numVal];
        Arrays.fill(ones, 1.0);
        Double[] range = new Double[svXyzTrx.length];
        Double[] zPr = new Double[svXyzTrx.length];
        Double[][] H = new Double[svXyzTrx.length][4];
        Double[][] v = new Double[3][svXyzTrx.length];

        while (MathUtils.norm(dx) > GNSSThresholds.MAXDELPOSFORNAVM) {
            whileCount++;
            if (whileCount >= maxWhileCount) {
                throw new Error("while loop did not converge after " + whileCount + " iterations");
            }

            for (int i = 0; i < gpsEph.Toe.size(); i++) {
                // calculate tflight from, bc and dtsv
                dtflight = (prs.get(i).get(jPr) - bc) / GpsConstants.LIGHTSPEED + dtsv.get(i);
                // Use of bc: bc>0 <=> pr too big <=> tflight too big.
                //        i.e. trx = trxu - bc/GpsConstants.LIGHTSPEED
                // Use of dtsv: dtsv>0 <=> pr too small <=> tflight too small.
                //        i.e ttx = ttxsv - dtsv
                svXyzTrx[i] = MathUtils.flightTimeCorrection(svXyzTtx.get(i), dtflight);
            }

            // calculate line of sight vectors and ranges from satellite to xo
            v = MathUtils.arrayMultipleArray(xyz0, ones);
            MathUtils.matrixSub(v, svXyzTrx);

            // 计算平均值
            for (int i = 0; i < v[0].length; i++) {
                range[i] = 0.0;
                for (int j = 0; j < v.length; j++) {
                    range[i] += Math.pow(v[j][i], 2);
                }

                range[i] = Math.sqrt(range[i]);
            }

            // 将range扩展成矩阵
            for (int i = 0; i < v.length; i++) {
                for (int j = 0; j < v[0].length; j++) {
                    v[i][j] = v[i][j] / range[j];
                }
            }

            // calculate the a-priori range residual
            for (int i = 0; i < zPr.length; i++) {
                prHat = range[i] + bc - GpsConstants.LIGHTSPEED * dtsv.get(i);
                zPr[i] = prs.get(i).get(jPr) - prHat;
            }

            for (int i = 0; i < H.length; i++) {
                H[i][0] = v[0][i];
                H[i][1] = v[1][i];
                H[i][2] = v[2][i];
                H[i][3] = 1.0;
            }

            // z = Hx, premultiply by W: Wz = WHx, and solve for x
            dx = solveX(Wpr, H, zPr);

            // update xo, xhat and bc
            for (int i = 0; i < xHat.length; i++) {
                xHat[i] = xHat[i] + dx[i];
                if (i < 3) {
                    xyz0[i] = xyz0[i] + dx[i];
                }
            }
            bc += dx[3];

            // Now calculate the a-posteriori range residual
            Double[] array = MathUtils.matrixMultipleArray(H, dx);
            for (int i = 0; i < array.length; i++) {
                zPr[i] -= array[i];
            }
        }

        // Compute velocities
        double rrMps;
        double prrHat;
        Double[] zPrr = new Double[numVal];
        for (int i = 0; i < dtsv.size(); i++) {
            rrMps = 0.0;
            for (int j = 0; j < svXyzDot.get(i).length; j++) {
                rrMps -= svXyzDot.get(i)[j] * v[j][i];
            }

            prrHat = rrMps + xo[7] - GpsConstants.LIGHTSPEED * dtsvDot.get(i);
            zPrr[i] = prs.get(i).get(jPrr) - prrHat;
        }

        // z = Hx, premultiply by W: Wz = WHx, and solve for x
        Double[] vHat = solveX(Wrr, H, zPrr);

        // 拼接xHat
        Double[] arr = new Double[xHat.length + vHat.length];
        int index = 0;
        for (Double aDouble : xHat) {
            arr[index++] = aDouble;
        }
        for (Double aDouble : vHat) {
            arr[index++] = aDouble;
        }

        WlsVal wlsVal = new WlsVal();
        wlsVal.xHat = arr;
        wlsVal.H = H;
        wlsVal.Wpr = Wpr;
        wlsVal.Wrr = Wrr;

        return wlsVal;
    }

    /**
     * Calculate sv coordinates, in ECEF frame, sv clock bias, and sv velocity
     * 计算ECEF帧中的sv坐标、sv时钟偏差和sv速度
     */
    private static XyzNode gpsEph2Pvt(GNSSGpsEph gpsEph, List<Double> ttxWeek, List<Double> ttx) {
        XyzNode xyzNode = gpsEph2Xyz(gpsEph, ttxWeek, ttx);
        xyzNode.vMps = new ArrayList<>();
        xyzNode.dtsvSDot = new ArrayList<>();

        // compute velocity from delta position & dtsvS at (t+0.5) - (t-0.5)
        // This is better than differentiating, because both the orbital and relativity
        // terms have nonlinearities that are not easily differentiable
        // 根据（t+0.5）-（t-0.5）处的增量位置和dtsvS计算速度，这比微分要好，因为轨道项和相对论项都具有不易微分的非线性

        // time + 0.5 seconds
        List<Double> t1 = new ArrayList<>();
        List<Double> t2 = new ArrayList<>();
        for (int i = 0; i < ttx.size(); i++) {
            t1.add(ttx.get(i) + 0.5);
            t2.add(ttx.get(i) - 0.5);
        }
        XyzNode xyzNodePlus = gpsEph2Xyz(gpsEph, ttxWeek, t1);

        // time - 0.5 seconds
        XyzNode xyzNodeMinus = gpsEph2Xyz(gpsEph, ttxWeek, t2);

        for (int i = 0; i < xyzNodePlus.xyzM.size(); i++) {
            Double[] xyz = new Double[3];
            for (int j = 0; j < 3; j++) {
                xyz[j] = xyzNodePlus.xyzM.get(i)[j] - xyzNodeMinus.xyzM.get(i)[j];
            }

            xyzNode.vMps.add(xyz);
            xyzNode.dtsvSDot.add(xyzNodePlus.dtsvS.get(i) - xyzNodeMinus.dtsvS.get(i));
        }
        return xyzNode;
    }

    static class XyzNode {
        List<Double> dtsvS;

        List<Double[]> xyzM;

        List<Double[]> vMps;

        List<Double> dtsvSDot;
    }

    /**
     * Calculate sv coordinates, in ECEF frame, at ttx = gpsTime
     */
    private static XyzNode gpsEph2Xyz(GNSSGpsEph gpsEph, List<Double> ttxWeek, List<Double> ttx) {
        int p = gpsEph.TGD.size();
        if (ttxWeek.size() != p || ttx.size() != p) {
            throw new Error("gpsTime must be px2 [gpsWeek, gpsSec], where p =length(gpsEph)");
        }

        List<Double> MK = new ArrayList<>();
        double[] A = new double[gpsEph.Fit_interval.size()];
        double[] tk = new double[gpsEph.Fit_interval.size()];
        for (int i = 0; i < gpsEph.Fit_interval.size(); i++) {
            // set fitIntervalSeconds
            double fitIntervalHours = gpsEph.Fit_interval.get(i);
            if (fitIntervalHours == 0) {
                fitIntervalHours = 2;
            }
            double fitIntervalSeconds = fitIntervalHours * 3600;

            // Calculate dependent variables -------------------------------------------------
            // Time since time of applicability accounting for weeks and therefore rollovers
            // subtract weeks first, to avoid precision errors:
            tk[i] = (ttxWeek.get(i) - gpsEph.GPS_Week.get(i)) * GpsConstants.WEEKSEC + (ttx.get(i) - gpsEph.Toe.get(i));
            if (Math.abs(tk[i]) > fitIntervalSeconds) {
                System.out.println("WARNING in GpsEph2Xyz.m, times outside fit interval");
            }

            // semi-major axis of orbit
            A[i] = Math.pow(gpsEph.Asqrt.get(i).doubleValue(), 2);
            // Computed mean motion (rad/sec)
            double no = Math.sqrt(GpsConstants.mu / Math.pow(A[i], 3));

            // Corrected Mean Motion
            double n = no + gpsEph.Delta_n.get(i).doubleValue();
            double h = Math.sqrt(A[i] * (1 - Math.pow(gpsEph.e.get(i), 2)) * GpsConstants.mu);

            // Mean Anomaly
            MK.add(gpsEph.M0.get(i) + n * tk[i]);
        }

        // Solve Kepler's equation for eccentric anomaly
        Double[] Ek = MathUtils.Kepler(MK, gpsEph.e);

        // Calculate satellite clock bias (See ICD-GPS-200 20.3.3.3.3.1)
        // subtract weeks first, to avoid precision errors:
        List<Double> dtsvS = new ArrayList<>();
        List<Double[]> xyzM = new ArrayList<>();
        for (int i = 0; i < ttx.size(); i++) {
            double dt = (ttxWeek.get(i) - gpsEph.GPS_Week.get(i)) * GpsConstants.WEEKSEC + (ttx.get(i) - gpsEph.Toc.get(i));

            // Calculate satellite clock bias
            double sin_EK = Math.sin(Ek[i]);
            double cos_EK = Math.cos(Ek[i]);
            dtsvS.add( gpsEph.af0.get(i).doubleValue() + gpsEph.af1.get(i).doubleValue() * dt + gpsEph.af2.get(i).doubleValue() * dt * dt
                    + GpsConstants.FREL * gpsEph.e.get(i) * gpsEph.Asqrt.get(i).doubleValue() * sin_EK - gpsEph.TGD.get(i).doubleValue() );

            // true anomaly
            double vk = Math.atan2(Math.sqrt(1 - Math.pow(gpsEph.e.get(i), 2)) * sin_EK / (1 - gpsEph.e.get(i) * cos_EK),
                    (cos_EK - gpsEph.e.get(i)) / (1 - gpsEph.e.get(i) * cos_EK));
            // Argument of latitude
            double Phik = vk + gpsEph.omega.get(i);

            double sin_2Phik = Math.sin(2 * Phik);
            double cos_2Phik = Math.cos(2 * Phik);
            // The next three terms are the second harmonic perturbations
            double duk = gpsEph.Cus.get(i).doubleValue() * sin_2Phik + gpsEph.Cuc.get(i).doubleValue() * cos_2Phik;         // Argument of latitude correction
            double drk = gpsEph.Crc.get(i) * cos_2Phik + gpsEph.Crs.get(i) * sin_2Phik;                                     // Radius Correction
            double dik = gpsEph.Cic.get(i).doubleValue() * cos_2Phik + gpsEph.Cis.get(i).doubleValue() * sin_2Phik;         // Correction to Inclination

            double uk = Phik + duk;                                                                                 // Corrected argument of latitude
            double rk = A[i] * ((1 - Math.pow(gpsEph.e.get(i), 2)) / (1 + gpsEph.e.get(i) * Math.cos(vk))) + drk;                // Corrected radius
            double ik = gpsEph.i0.get(i) + gpsEph.IDOT.get(i).doubleValue() * tk[i] + dik;                          // Corrected inclination

            double sin_uk = Math.sin(uk);
            double cos_uk = Math.cos(uk);
            double xkp = rk * cos_uk;           // Position in orbital plane
            double ykp = rk * sin_uk;

            // Wk = corrected longitude of ascending node
            double Wk = gpsEph.OMEGA.get(i) + (gpsEph.OMEGA_DOT.get(i).doubleValue() - GpsConstants.WE) * tk[i] - GpsConstants.WE * gpsEph.Toe.get(i);

            // for dtflight, see FlightTimeCorrection.m
            double sin_Wk = Math.sin(Wk);
            double cos_Wk = Math.cos(Wk);
            double sin_ik = Math.sin(ik);
            double cos_ik = Math.cos(ik);

            Double[] xyz = new Double[]{
                    xkp * cos_Wk - ykp * cos_ik * sin_Wk,
                    xkp * sin_Wk + ykp * cos_ik * cos_Wk,
                    ykp * sin_ik
            };
            xyzM.add(xyz);
        }

        XyzNode node = new XyzNode();
        node.dtsvS = dtsvS;
        node.xyzM = xyzM;
        return node;
    }

    /**
     * utility function for WlsPvt
     */
    private static boolean checkInputs(List<List<Double>> prs, GNSSGpsEph gpsEph) {
        if (prs.size() != gpsEph.PRN.size()) {
            return false;
        }

        double max = Double.MIN_VALUE;
        double min = Double.MAX_VALUE;
        for (int i = 0; i < prs.size(); i++) {
            max = Math.max(max, prs.get(i).get(jSec));
            min = Math.min(min, prs.get(i).get(jSec));

            if (prs.get(i).get(jSv).intValue() != gpsEph.PRN.get(i)) {
                return false;
            }

            if (prs.get(i).size() != 7) {
                return false;
            }
        }

        return !(max - min > 0);
    }

    /**
     * solve for x
     */
    private static Double[] solveX(Double[][] Wpr, Double[][] H, Double[] zPr) {
        double[][] matrix = MathUtils.matrixMultipleArr(Wpr, H);
        Matrix A = new Matrix(matrix);//构造矩阵
        SingularValueDecomposition SVD = A.svd();
        Double[][] X = svd(SVD.getU().getArray(), SVD.getS().getArray(), SVD.getV().getArray());

        Double[][] tmp = MathUtils.matrixMultiple(X, Wpr);

        return MathUtils.matrixMultipleArray(tmp, zPr);
    }

    public static Double[][] svd(double[][] U, double[][] S, double[][] V) {
        // 获取维度
        double[] s = new double[S.length];
        double valSum = 0.0;
        for (int i = 0; i < S.length; i++) {
            s[i] = S[i][i];
            valSum += Math.pow(S[i][i], 2);
        }

        // 计算有效索引
        int r1 = 0;
        int q = Math.max(S.length, S[0].length);
        for (int i = 0; i < S.length; i++) {
            if (s[i] > Double.MIN_NORMAL * q) {
                r1++;
            }
        }

        // 计算结果
        Double[][] tmp = new Double[V.length][r1];
        for (int i = 0; i < V.length; i++) {
            for (int j = 0; j < r1; j++) {
                tmp[i][j] = V[i][j] / s[j];
            }
        }

        Double[][] X = new Double[tmp.length][U.length];
        for (int i = 0; i < tmp.length; i++) {
            for (int j = 0; j < U.length; j++) {
                X[i][j] = 0.0;
                for (int k = 0; k < r1; k++) {
                    X[i][j] += tmp[i][k] * U[j][k];
                }
            }
        }

        return X;
    }

    public static void main(String[] args) {
        long start = System.currentTimeMillis();
        double[][] matrix = {
                {0.430325249735182,	-0.118853947505311,	-0.166629405009141,	0.476520135997360},
                {-0.168275933543814,	-0.113093219180739,	-0.157259070926247,	0.256587765537040},
                {0.217982442189230,	-0.00499079183749675,	-0.299704773644194,	0.370626772442391},
                {0.207105567064031,	-0.198987351233085,	-0.380240395212383,	0.476520135997360},
                {0.0379800686476560,	-0.542992443928676,	-0.385715883317411,	0.667128190396304},
                {0.663269338715613,	-0.0274285770636774,	0.0661927481032638,	0.667128190396304},
                {-0.114151023635788,	-0.310312663925697,	-0.446928466787621,	0.555940158663587},
                {0.379737139728944,	-0.535998157312412,	-0.116471895707189,	0.667128190396304},
                {-0.00257690633557882,	-0.0279948566261915,	0.0134714218325025,	0.0311742145045002},
        };
        Matrix A = new Matrix(matrix);//构造矩阵
        SingularValueDecomposition SVD = A.svd();
        double[][] S = SVD.getS().getArray();
        double[][] V = SVD.getV().getArray();
        double[][] U = SVD.getU().getArray();


        svd(U, S, V);
        long end = System.currentTimeMillis();
        System.out.println("cost:" + (end - start));
    }
}
