/*
 * Copyright (C) 2017 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package cn.byyddyh.spoofingdetection.pseudorange;

import static java.lang.Double.NaN;

import android.annotation.SuppressLint;
import android.location.cts.nano.Ephemeris.GpsEphemerisProto;
import android.location.cts.nano.Ephemeris.GpsNavMessageProto;
import android.util.Log;

import androidx.annotation.VisibleForTesting;
import androidx.core.util.Preconditions;

import com.google.firebase.crashlytics.buildtools.reloc.com.google.common.collect.Lists;

import org.apache.commons.math3.linear.Array2DRowRealMatrix;
import org.apache.commons.math3.linear.LUDecomposition;
import org.apache.commons.math3.linear.QRDecomposition;
import org.apache.commons.math3.linear.RealMatrix;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import cn.byyddyh.spoofingdetection.LogFragment;
import cn.byyddyh.spoofingdetection.MainActivity;

/**
 * Computes an iterative least square receiver position solution given the pseudorange (meters) and
 * accumulated delta range (meters) measurements, receiver time of week, week number and the
 * navigation message.
 */
class UserPositionVelocityWeightedLeastSquare {
    private static final double SPEED_OF_LIGHT_MPS = 299792458.0;
    private static final int SECONDS_IN_WEEK = 604800;
    private static final double LEAST_SQUARE_TOLERANCE_METERS = 4.0e-8;
    /**
     * Position correction threshold below which atmospheric correction will be applied
     */
    private static final double ATMOSPHERIC_CORRECTIONS_THRESHOLD_METERS = 1000.0;
    private static final int MINIMUM_NUMBER_OF_SATELLITES = 4;
    private static final double RESIDUAL_TO_REPEAT_LEAST_SQUARE_METERS = 20.0;
    private static final int MAXIMUM_NUMBER_OF_LEAST_SQUARE_ITERATIONS = 100;
    /**
     * GPS C/A code chip width Tc = 1 microseconds
     */
    private static final double GPS_CHIP_WIDTH_T_C_SEC = 1.0e-6;
    /**
     * Narrow correlator with spacing d = 0.1 chip
     */
    private static final double GPS_CORRELATOR_SPACING_IN_CHIPS = 0.1;
    /**
     * Average time of DLL correlator T of 20 milliseconds
     */
    private static final double GPS_DLL_AVERAGING_TIME_SEC = 20.0e-3;
    /**
     * Average signal travel time from GPS satellite and earth
     */
    private static final double AVERAGE_TRAVEL_TIME_SECONDS = 70.0e-3;
    private static final double SECONDS_PER_NANO = 1.0e-9;
    private static final double DOUBLE_ROUND_OFF_TOLERANCE = 0.0000000001;

    private final PseudorangeSmoother pseudorangeSmoother;
    private double geoidHeightMeters;
    private ElevationApiHelper elevationApiHelper;
    private boolean calculateGeoidMeters = true;
    private RealMatrix geometryMatrix;
    private double[] truthLocationForCorrectedResidualComputationEcef = null;

    /**
     * Constructor
     */
    public UserPositionVelocityWeightedLeastSquare(PseudorangeSmoother pseudorangeSmoother) {
        this.pseudorangeSmoother = pseudorangeSmoother;
    }

    /**
     * Constructor with Google Elevation API Key
     */
    public UserPositionVelocityWeightedLeastSquare(PseudorangeSmoother pseudorangeSmoother,
                                                   String elevationApiKey) {
        this.pseudorangeSmoother = pseudorangeSmoother;
        this.elevationApiHelper = new ElevationApiHelper(elevationApiKey);
    }

    /**
     * Sets the reference ground truth for pseudorange residual correction calculation. If no ground
     * truth is set, no corrected pseudorange residual will be calculated.
     */
    public void setTruthLocationForCorrectedResidualComputationEcef
    (double[] groundTruthForResidualCorrectionEcef) {
        this.truthLocationForCorrectedResidualComputationEcef = groundTruthForResidualCorrectionEcef;
    }

    private double receiverClockBias;                                              // 接收机时钟误差
    private double receiverClockBiasRate;                                           // 接收机时钟误差率
    private int initCount = 0;                                                      // 保证初始化完成
    private int initLen = 10;                                                       // 系统初始化时间
    private int errorPseLimit = 200;                                                // 最大伪距误差

    /**
     * Least square solution to calculate the user position given the navigation message, pseudorange
     * and accumulated delta range measurements. Also calculates user velocity non-iteratively from
     * Least square position solution.
     * 给定导航信息、伪距和累积增量距离测量值，计算用户位置的最小二乘解。还根据最小二乘位置解非迭代地计算用户速度。
     *
     * <p>The method fills the user position and velocity in ECEF coordinates and receiver clock
     * offset in meters and clock offset rate in meters per second.
     * 该方法在ECEF坐标中填充用户位置和速度，以米为单位填充接收器时钟偏移，以米/秒为单位填充时钟偏移率。
     *
     * <p>One can choose between no smoothing, using the carrier phase measurements (accumulated delta
     * range) or the doppler measurements (pseudorange rate) for smoothing the pseudorange. The
     * smoothing is applied only if time has changed below a specific threshold since last invocation.
     * 可以选择不平滑，使用载波相位测量（累积增量范围）或多普勒测量（伪距率）来平滑伪距。仅当自上次调用以来时间变化低于特定阈值时，才应用平滑。
     *
     * <p>Source for least squares:
     *
     * <ul>
     *   <li>http://www.u-blox.com/images/downloads/Product_Docs/GPS_Compendium%28GPS-X-02007%29.pdf
     *       page 81 - 85
     *   <li>Parkinson, B.W., Spilker Jr., J.J.: ‘Global positioning system: theory and applications’
     *       page 412 - 414
     * </ul>
     *
     * <p>Sources for smoothing pseudorange with carrier phase measurements:
     *
     * <ul>
     *   <li>Satellite Communications and Navigation Systems book, page 424,
     *   <li>Principles of GNSS, Inertial, and Multisensor Integrated Navigation Systems, page 388,
     *       389.
     * </ul>
     *
     * <p>The function does not modify the smoothed measurement list {@code
     * immutableSmoothedSatellitesToReceiverMeasurements}
     *
     * @param navMessageProto                        parameters of the navigation message                                      导航消息的参数
     * @param usefulSatellitesToReceiverMeasurements Map of useful satellite PRN to {@link
     *                                               GpsMeasurementWithRangeAndUncertainty} containing receiver measurements for computing the    将有用的卫星PRN映射到｛@link GpsMeasurementWithRangeAndUn确定性｝，
     *                                               position solution.                                                                           其中包含用于计算位置解的接收器测量值。
     * @param receiverGPSTowAtReceptionSeconds       Receiver estimate of GPS time of week (seconds)          接收机对GPS每周时间的估计（秒）
     * @param receiverGPSWeek                        Receiver estimate of GPS week (0-1024+)                                   GPS周接收机估计值（0-1024+）
     * @param dayOfYear1To366                        The day of the year between 1 and 366                                     1到366之间的一年中的一天
     * @param positionVelocitySolutionECEF           Solution array of the following format:
     *                                               [0-2] xyz solution of user.
     *                                               [3] clock bias of user.
     *                                               [4-6] velocity of user.
     *                                               [7] clock bias rate of user.
     * @param positionVelocityUncertaintyEnu         Uncertainty of calculated position and velocity solution   以米和mps本地ENU系统计算的位置和速度解的不确定度。
     *                                               in meters and mps local ENU system. Array has the following format:
     *                                               [0-2] Enu uncertainty of position solution in meters
     *                                               [3-5] Enu uncertainty of velocity solution in meters per second.
     * @param pseudorangeResidualMeters              The pseudorange residual corrected by subtracting expected
     *                                               pseudorange calculated with the use clock bias of the highest elevation satellites.          通过减去使用最高仰角卫星的时钟偏差计算的预期伪距来校正伪距残差。
     */
    @SuppressLint({"RestrictedApi", "LongLogTag"})
    public void calculateUserPositionVelocityLeastSquare(
            GpsNavMessageProto navMessageProto,
            List<GpsMeasurementWithRangeAndUncertainty> usefulSatellitesToReceiverMeasurements,
            double receiverGPSTowAtReceptionSeconds,
            int receiverGPSWeek,
            int dayOfYear1To366,
            double[] positionVelocitySolutionECEF,
            double[] positionVelocityUncertaintyEnu,
            double[] pseudorangeResidualMeters)
            throws Exception {

        // Use PseudorangeSmoother to smooth the pseudorange according to: Satellite Communications and
        // Navigation Systems book, page 424 and Principles of GNSS, Inertial, and Multisensor
        // Integrated Navigation Systems, page 388, 389.
        // 根据《卫星通信和导航系统》一书第424页和《全球导航卫星系统、惯性和多传感器综合导航系统原理》第388,389页，使用伪距平滑器平滑伪距。
        double[] deltaPositionMeters;
        List<GpsMeasurementWithRangeAndUncertainty> immutableSmoothedSatellitesToReceiverMeasurements =
                pseudorangeSmoother.updatePseudorangeSmoothingResult(
                        Collections.unmodifiableList(usefulSatellitesToReceiverMeasurements));

        List<GpsMeasurementWithRangeAndUncertainty> mutableSmoothedSatellitesToReceiverMeasurements =
                Lists.newArrayList(immutableSmoothedSatellitesToReceiverMeasurements);

        int numberOfUsefulSatellites =
                getNumberOfUsefulSatellites(mutableSmoothedSatellitesToReceiverMeasurements);

        // Least square position solution is supported only if 4 or more satellites visible
        // 只有当4个或更多卫星可见时，才支持最小二乘位置解
        Preconditions.checkArgument(numberOfUsefulSatellites >= MINIMUM_NUMBER_OF_SATELLITES,
                "At least 4 satellites have to be visible... Only 3D mode is supported...");

        boolean repeatLeastSquare;
        SatellitesPositionPseudorangesResidualAndCovarianceMatrix satPosPseudorangeResidualAndWeight;
        boolean isFirstWLS = true;

        // TODO
        double[] receiverEcefData = Lla2EcefConverter.convertFromLlaToEcefMeters(new Ecef2LlaConverter.GeodeticLlaValues(
                MainActivity.reference_radians_mea[0],
                MainActivity.reference_radians_mea[1],
                MainActivity.reference_radians_mea[2]));
        // 去除星历误差
        SatellitesPositionPseudorangesResidualAndCovarianceMatrix satellitesPositionPseudorangesTemp = calculateSatTruePseudoranges(
                navMessageProto,
                mutableSmoothedSatellitesToReceiverMeasurements,
                receiverGPSTowAtReceptionSeconds,
                receiverGPSWeek,
                dayOfYear1To366,
                new double[]{
                        receiverEcefData[0], receiverEcefData[1], receiverEcefData[2], receiverClockBias,
                        0, 0, 0, receiverClockBiasRate
                },
                true);

        // 计算卫星位置,保存伪距的测量值
        List<Double> receiverMeasurementPseudorangeMeters = new ArrayList<>();
        List<Double> receiverMeasurementSvid = new ArrayList<>();
        List<double[]> satPosEcefData = new ArrayList<>();
        List<Double> pseErrorData = new ArrayList<>();
        double[] referencePseData = new double[GpsNavigationMessageStore.MAX_NUMBER_OF_SATELLITES];
        int intCountSat = 0;

        for (int i = 0; i < GpsNavigationMessageStore.MAX_NUMBER_OF_SATELLITES; i++) {
            if (mutableSmoothedSatellitesToReceiverMeasurements.get(i) != null) {
                GpsEphemerisProto ephemeridesProto = getEphemerisForSatellite(navMessageProto, i + 1);

                // 伪距测量值
                double pseudorangeMeasurementMeters =
                        mutableSmoothedSatellitesToReceiverMeasurements.get(i).pseudorangeMeters;
                // 获取矫正后的接收时间
                GpsTimeOfWeekAndWeekNumber correctedTowAndWeek =
                        calculateCorrectedTransmitTowAndWeek(ephemeridesProto, receiverGPSTowAtReceptionSeconds,
                                receiverGPSWeek, pseudorangeMeasurementMeters);
                // Calculate satellite velocity
                SatellitePositionCalculator.PositionAndVelocity satPosECEFMetersVelocityMPS = SatellitePositionCalculator
                        .calculateSatellitePositionAndVelocityFromEphemeris(
                                ephemeridesProto,
                                correctedTowAndWeek.gpsTimeOfWeekSeconds,
                                correctedTowAndWeek.weekNumber,
                                positionVelocitySolutionECEF[0],
                                positionVelocitySolutionECEF[1],
                                positionVelocitySolutionECEF[2]);
                // 卫星位置satPosECEFMetersVelocityMPS.position(X/Y/Z)Meters
                satPosEcefData.add(new double[]{satPosECEFMetersVelocityMPS.positionXMeters, satPosECEFMetersVelocityMPS.positionYMeters, satPosECEFMetersVelocityMPS.positionZMeters});
                referencePseData[i] = Math.sqrt(Math.pow(receiverEcefData[0] - satPosECEFMetersVelocityMPS.positionXMeters, 2) + Math.pow(receiverEcefData[1] - satPosECEFMetersVelocityMPS.positionYMeters, 2) + Math.pow(receiverEcefData[2] - satPosECEFMetersVelocityMPS.positionZMeters, 2));

                // 经过校正后的伪距测量值
                receiverMeasurementPseudorangeMeters.add(satellitesPositionPseudorangesTemp.pseudorangeResidualsMeters[intCountSat]);
                // Svid号
                receiverMeasurementSvid.add((double) satellitesPositionPseudorangesTemp.satellitePRNs[intCountSat]);
                // 计算伪距误差
                double errorPse = satellitesPositionPseudorangesTemp.pseudorangeResidualsMeters[intCountSat++] - referencePseData[i];

                // single算法
                if (MainActivity.isUsedAntiSpoof && initCount >= initLen && errorPse > errorPseLimit) {
                    // 可以对数据进行有效的滤除
                    mutableSmoothedSatellitesToReceiverMeasurements.set(i, null);
                }
                pseErrorData.add(errorPse);
            } else {
                referencePseData[i] = 0;
            }
        }

        Log.d("GNSS pseudorange Meters", String.valueOf(receiverMeasurementPseudorangeMeters));
        if (LogFragment.writableFlag && initCount >= initLen) {
            LogFragment.fileLogger.storeListData("GNSS pseudorange residual Meters", pseErrorData);
            LogFragment.fileLogger.storeListData("GNSS Measurement pseudorange Meters", receiverMeasurementPseudorangeMeters);
            LogFragment.fileLogger.storeListData("GNSS Measurement Svid", receiverMeasurementSvid);
            LogFragment.fileLogger.storeArrayData("receiverClockBias", new double[]{receiverClockBias, receiverClockBiasRate});
            LogFragment.fileLogger.storeArrayData("GNSS Estimate Pse Data", referencePseData);
            LogFragment.fileLogger.storeArrayData("GNSS Receiver Ecef Data", receiverEcefData);
            for (int i = 0; i < satPosEcefData.size(); i++) {
                LogFragment.fileLogger.storeArrayData("GNSS Satellite Position Ecef Data", satPosEcefData.get(i));
            }
        }

        // Least square position solution is supported only if 4 or more satellites visible
        // 只有当4个或更多卫星可见时，才支持最小二乘位置解
        Preconditions.checkArgument(numberOfUsefulSatellites >= MINIMUM_NUMBER_OF_SATELLITES,
                "At least 4 satellites have to be visible... Only 3D mode is supported...");

        do {
            // Calculate satellites' positions, measurement residuals per visible satellite and
            // weight matrix for the iterative least square
            // 计算卫星位置、每个可见卫星的测量残差和迭代最小二乘的权重矩阵
            boolean doAtmosphericCorrections = false;
            satPosPseudorangeResidualAndWeight =
                    calculateSatPosAndPseudorangeResidual(
                            navMessageProto,
                            mutableSmoothedSatellitesToReceiverMeasurements,
                            receiverGPSTowAtReceptionSeconds,
                            receiverGPSWeek,
                            dayOfYear1To366,
                            positionVelocitySolutionECEF,
                            doAtmosphericCorrections);

            // Calculate the geometry matrix according to "Global Positioning System: Theory and
            // Applications", Parkinson and Spilker page 413
            RealMatrix covarianceMatrixM2 =
                    new Array2DRowRealMatrix(satPosPseudorangeResidualAndWeight.covarianceMatrixMetersSquare);

            geometryMatrix = new Array2DRowRealMatrix(calculateGeometryMatrix(
                    satPosPseudorangeResidualAndWeight.satellitesPositionsMeters,
                    positionVelocitySolutionECEF));
            RealMatrix weightedGeometryMatrix;
            RealMatrix weightMatrixMetersMinus2 = null;

            // Apply weighted least square only if the covariance matrix is not singular (has a non-zero
            // determinant), otherwise apply ordinary least square. The reason is to ignore reported
            // signal to noise ratios by the receiver that can lead to such singularities
            // 仅当协方差矩阵不是奇异的（具有非零行列式）时应用加权最小二乘，否则应用普通最小二乘。原因是忽略了接收机报告的可能导致这种奇异性的信噪比
            LUDecomposition ludCovMatrixM2 = new LUDecomposition(covarianceMatrixM2);
            double det = ludCovMatrixM2.getDeterminant();

            if (det <= DOUBLE_ROUND_OFF_TOLERANCE) {
                // Do not weight the geometry matrix if covariance matrix is singular.
                weightedGeometryMatrix = geometryMatrix;
            } else {
                weightMatrixMetersMinus2 = ludCovMatrixM2.getSolver().getInverse();
                RealMatrix hMatrix =
                        calculateHMatrix(weightMatrixMetersMinus2, geometryMatrix);
                weightedGeometryMatrix = hMatrix.multiply(geometryMatrix.transpose())
                        .multiply(weightMatrixMetersMinus2);
            }

            // Equation 9 page 413 from "Global Positioning System: Theory and Applications", Parkinson
            // and Spilker
            deltaPositionMeters =
                    GpsMathOperations.matrixByColVectMultiplication(weightedGeometryMatrix.getData(),
                            satPosPseudorangeResidualAndWeight.pseudorangeResidualsMeters);

            // Apply corrections to the position estimate
            positionVelocitySolutionECEF[0] += deltaPositionMeters[0];
            positionVelocitySolutionECEF[1] += deltaPositionMeters[1];
            positionVelocitySolutionECEF[2] += deltaPositionMeters[2];
            positionVelocitySolutionECEF[3] += deltaPositionMeters[3];
            // Iterate applying corrections to the position solution until correction is below threshold
            // 重复对位置解应用校正，直到校正低于阈值
            satPosPseudorangeResidualAndWeight =
                    applyWeightedLeastSquare(
                            navMessageProto,
                            mutableSmoothedSatellitesToReceiverMeasurements,
                            receiverGPSTowAtReceptionSeconds,
                            receiverGPSWeek,
                            dayOfYear1To366,
                            positionVelocitySolutionECEF,
                            deltaPositionMeters,
                            doAtmosphericCorrections,
                            satPosPseudorangeResidualAndWeight,
                            weightMatrixMetersMinus2);

            // We use the first WLS iteration results and correct them based on the ground truth position
            // and using a clock error computed from high elevation satellites. The first iteration is
            // used before satellite with high residuals being removed.
            // 我们使用第一次WLS迭代结果，并根据地面真实位置和从高海拔卫星计算的时钟误差对其进行校正。在去除高残差的卫星之前使用第一次迭代。
            if (isFirstWLS && truthLocationForCorrectedResidualComputationEcef != null) {
                // Snapshot the information needed before high residual satellites are removed
                System.arraycopy(
                        ResidualCorrectionCalculator.calculateCorrectedResiduals(
                                satPosPseudorangeResidualAndWeight,
                                positionVelocitySolutionECEF.clone(),
                                truthLocationForCorrectedResidualComputationEcef),
                        0 /*source starting pos*/,
                        pseudorangeResidualMeters,
                        0 /*destination starting pos*/,
                        GpsNavigationMessageStore.MAX_NUMBER_OF_SATELLITES /*length of elements*/);
                isFirstWLS = false;
            }
            repeatLeastSquare = false;
            int satsWithResidualBelowThreshold =
                    satPosPseudorangeResidualAndWeight.pseudorangeResidualsMeters.length;

            // remove satellites that have residuals above RESIDUAL_TO_REPEAT_LEAST_SQUARE_METERS as they
            // worsen the position solution accuracy. If any satellite is removed, repeat the least square
            // 移除残差高于RESIDUAL_TO_REPAT_LEAST_SQUARE_METERS的卫星，因为它们会降低位置解的精度。如果移除了任何卫星，重复最小二乘法
            repeatLeastSquare =
                    removeHighResidualSats(
                            mutableSmoothedSatellitesToReceiverMeasurements,
                            repeatLeastSquare,
                            satPosPseudorangeResidualAndWeight,
                            satsWithResidualBelowThreshold);

        } while (repeatLeastSquare);
        calculateGeoidMeters = false;

        // The computed ECEF position will be used next to compute the user velocity.
        // we calculate and fill in the user velocity solutions based on following equation:
        // Weight Matrix * GeometryMatrix * User Velocity Vector
        // = Weight Matrix * deltaPseudoRangeRateWeightedMps
        // Reference: Pratap Misra and Per Enge
        // "Global Positioning System: Signals, Measurements, and Performance" Page 218.

        // Get the number of satellite used in Geometry Matrix
        numberOfUsefulSatellites = geometryMatrix.getRowDimension();

        RealMatrix rangeRateMps = new Array2DRowRealMatrix(numberOfUsefulSatellites, 1);
        RealMatrix deltaPseudoRangeRateMps =
                new Array2DRowRealMatrix(numberOfUsefulSatellites, 1);
        RealMatrix pseudorangeRateWeight
                = new Array2DRowRealMatrix(numberOfUsefulSatellites, numberOfUsefulSatellites);

        // Correct the receiver time of week with the estimated receiver clock bias
        // 使用估计的接收器时钟偏差校正接收器每周的时间
        receiverGPSTowAtReceptionSeconds =
                receiverGPSTowAtReceptionSeconds - positionVelocitySolutionECEF[3] / SPEED_OF_LIGHT_MPS;

        int measurementCount = 0;

        // Calculate range rates
        for (int i = 0; i < GpsNavigationMessageStore.MAX_NUMBER_OF_SATELLITES; i++) {
            if (mutableSmoothedSatellitesToReceiverMeasurements.get(i) != null) {
                GpsEphemerisProto ephemeridesProto = getEphemerisForSatellite(navMessageProto, i + 1);

                double pseudorangeMeasurementMeters =
                        mutableSmoothedSatellitesToReceiverMeasurements.get(i).pseudorangeMeters;
                GpsTimeOfWeekAndWeekNumber correctedTowAndWeek =
                        calculateCorrectedTransmitTowAndWeek(ephemeridesProto, receiverGPSTowAtReceptionSeconds,
                                receiverGPSWeek, pseudorangeMeasurementMeters);

                // Calculate satellite velocity
                SatellitePositionCalculator.PositionAndVelocity satPosECEFMetersVelocityMPS = SatellitePositionCalculator
                        .calculateSatellitePositionAndVelocityFromEphemeris(
                                ephemeridesProto,
                                correctedTowAndWeek.gpsTimeOfWeekSeconds,
                                correctedTowAndWeek.weekNumber,
                                positionVelocitySolutionECEF[0],
                                positionVelocitySolutionECEF[1],
                                positionVelocitySolutionECEF[2]);

                // Calculate satellite clock error rate
                double satelliteClockErrorRateMps = SatelliteClockCorrectionCalculator.
                        calculateSatClockCorrErrorRate(
                                ephemeridesProto,
                                correctedTowAndWeek.gpsTimeOfWeekSeconds,
                                correctedTowAndWeek.weekNumber);

                // Fill in range rates. range rate = satellite velocity (dot product) line-of-sight vector
                rangeRateMps.setEntry(measurementCount, 0, -1 * (
                        satPosECEFMetersVelocityMPS.velocityXMetersPerSec
                                * geometryMatrix.getEntry(measurementCount, 0)
                                + satPosECEFMetersVelocityMPS.velocityYMetersPerSec
                                * geometryMatrix.getEntry(measurementCount, 1)
                                + satPosECEFMetersVelocityMPS.velocityZMetersPerSec
                                * geometryMatrix.getEntry(measurementCount, 2)));

                deltaPseudoRangeRateMps.setEntry(measurementCount, 0,
                        mutableSmoothedSatellitesToReceiverMeasurements.get(i).pseudorangeRateMps
                                - rangeRateMps.getEntry(measurementCount, 0) + satelliteClockErrorRateMps
                                - positionVelocitySolutionECEF[7]);

                // Calculate the velocity weight matrix by using 1 / square(PseudorangeRate Uncertainty)
                // along the diagonal
                pseudorangeRateWeight.setEntry(measurementCount, measurementCount,
                        1 / (mutableSmoothedSatellitesToReceiverMeasurements
                                .get(i).pseudorangeRateUncertaintyMps
                                * mutableSmoothedSatellitesToReceiverMeasurements
                                .get(i).pseudorangeRateUncertaintyMps));
                measurementCount++;
            }
        }

        RealMatrix weightedGeoMatrix = pseudorangeRateWeight.multiply(geometryMatrix);
        RealMatrix deltaPseudoRangeRateWeightedMps =
                pseudorangeRateWeight.multiply(deltaPseudoRangeRateMps);
        QRDecomposition qrdWeightedGeoMatrix = new QRDecomposition(weightedGeoMatrix);
        RealMatrix velocityMps
                = qrdWeightedGeoMatrix.getSolver().solve(deltaPseudoRangeRateWeightedMps);
        positionVelocitySolutionECEF[4] = velocityMps.getEntry(0, 0);
        positionVelocitySolutionECEF[5] = velocityMps.getEntry(1, 0);
        positionVelocitySolutionECEF[6] = velocityMps.getEntry(2, 0);
        positionVelocitySolutionECEF[7] = velocityMps.getEntry(3, 0);

        RealMatrix pseudorangeWeight
                = new LUDecomposition(
                new Array2DRowRealMatrix(satPosPseudorangeResidualAndWeight.covarianceMatrixMetersSquare
                )
        ).getSolver().getInverse();

        // Calculate and store the uncertainties of position and velocity in local ENU system in meters
        // and meters per second.
        double[] pvUncertainty =
                calculatePositionVelocityUncertaintyEnu(pseudorangeRateWeight, pseudorangeWeight,
                        positionVelocitySolutionECEF);
        System.arraycopy(pvUncertainty,
                0 /*source starting pos*/,
                positionVelocityUncertaintyEnu,
                0 /*destination starting pos*/,
                6 /*length of elements*/);

        receiverClockBias = positionVelocitySolutionECEF[3];
        receiverClockBiasRate = positionVelocitySolutionECEF[7];

        if (positionVelocitySolutionECEF[0] == NaN) {
            Log.d("Error", "position");
        }

        if (initCount < initLen) {
            initCount++;
        }
    }

    private double smoothingFactor = 0.5; // 平滑因子
    private double[] lastSmoothedPseudorange = new double[32]; // 上一个平滑伪距值
    /**
     * 计算平滑伪距
     * */
    public List<Double> calculateSmoothedPseudoranges(List<GpsMeasurementWithRangeAndUncertainty> copySatellitesToReceiverMeasurements) {
        List<Double> pseError = new ArrayList<>();

        for (int i = 0; i < copySatellitesToReceiverMeasurements.size(); i++) {
            if (copySatellitesToReceiverMeasurements.get(i) != null) {
                double pseudorange = copySatellitesToReceiverMeasurements.get(i).pseudorangeMeters;
                double pseudorangeRate = copySatellitesToReceiverMeasurements.get(i).pseudorangeRateMps;

                double smoothedPseudorange;
                if (i == 0) {
                    // 第一个平滑伪距值等于第一个伪距值
                    smoothedPseudorange = pseudorange;
                    lastSmoothedPseudorange[i] = pseudorange;
                } else {
                    // 计算平滑伪距
                    smoothedPseudorange = smoothingFactor * pseudorange
                            + (1 - smoothingFactor) * (lastSmoothedPseudorange[i] + pseudorangeRate);
                }

                lastSmoothedPseudorange[i] = smoothedPseudorange;
                pseError.add(smoothedPseudorange - pseudorange);
            }
        }

        return pseError;
    }

    /**
     * Calculates the position uncertainty in meters and the velocity uncertainty
     * in meters per second solution in local ENU system.
     *
     * <p> Reference: Global Positioning System: Signals, Measurements, and Performance
     * by Pratap Misra, Per Enge, Page 206 - 209.
     *
     * @param velocityWeightMatrix     the velocity weight matrix
     * @param positionWeightMatrix     the position weight matrix
     * @param positionVelocitySolution the position and velocity solution in ECEF
     * @return an array containing the position and velocity uncertainties in ENU coordinate system.
     * [0-2] Enu uncertainty of position solution in meters.
     * [3-5] Enu uncertainty of velocity solution in meters per second.
     */
    public double[] calculatePositionVelocityUncertaintyEnu(
            RealMatrix velocityWeightMatrix, RealMatrix positionWeightMatrix,
            double[] positionVelocitySolution) {

        if (geometryMatrix == null) {
            return null;
        }

        RealMatrix velocityH = calculateHMatrix(velocityWeightMatrix, geometryMatrix);
        RealMatrix positionH = calculateHMatrix(positionWeightMatrix, geometryMatrix);

        // Calculate the rotation Matrix to convert to local ENU system.
        RealMatrix rotationMatrix = new Array2DRowRealMatrix(4, 4);
        Ecef2LlaConverter.GeodeticLlaValues llaValues = Ecef2LlaConverter.convertECEFToLLACloseForm
                (positionVelocitySolution[0], positionVelocitySolution[1], positionVelocitySolution[2]);
        rotationMatrix.setSubMatrix(
                Ecef2EnuConverter.getRotationMatrix(llaValues.longitudeRadians,
                        llaValues.latitudeRadians).getData(), 0, 0);
        rotationMatrix.setEntry(3, 3, 1);

        // Convert to local ENU by pre-multiply rotation matrix and multiply rotation matrix transposed
        velocityH = rotationMatrix.multiply(velocityH).multiply(rotationMatrix.transpose());
        positionH = rotationMatrix.multiply(positionH).multiply(rotationMatrix.transpose());

        // Return the square root of diagonal entries
        return new double[]{
                Math.sqrt(positionH.getEntry(0, 0)), Math.sqrt(positionH.getEntry(1, 1)),
                Math.sqrt(positionH.getEntry(2, 2)), Math.sqrt(velocityH.getEntry(0, 0)),
                Math.sqrt(velocityH.getEntry(1, 1)), Math.sqrt(velocityH.getEntry(2, 2))};
    }

    /**
     * Calculates the measurement connection matrix H as a function of weightMatrix and
     * geometryMatrix.
     *
     * <p> H = (geometryMatrixTransposed * Weight * geometryMatrix) ^ -1
     *
     * <p> Reference: Global Positioning System: Signals, Measurements, and Performance, P207
     *
     * @param weightMatrix Weights for computing H Matrix
     * @return H Matrix
     */
    private RealMatrix calculateHMatrix
    (RealMatrix weightMatrix, RealMatrix geometryMatrix) {

        RealMatrix tempH = geometryMatrix.transpose().multiply(weightMatrix).multiply(geometryMatrix);
        return new LUDecomposition(tempH).getSolver().getInverse();
    }

    /**
     * Applies weighted least square iterations and corrects to the position solution until correction
     * is below threshold. An exception is thrown if the maximum number of iterations:
     * {@value #MAXIMUM_NUMBER_OF_LEAST_SQUARE_ITERATIONS} is reached without convergence.
     */
    @SuppressLint("RestrictedApi")
    private SatellitesPositionPseudorangesResidualAndCovarianceMatrix applyWeightedLeastSquare(
            GpsNavMessageProto navMessageProto,
            List<GpsMeasurementWithRangeAndUncertainty> usefulSatellitesToReceiverMeasurements,
            double receiverGPSTowAtReceptionSeconds,
            int receiverGPSWeek,
            int dayOfYear1To366,
            double[] positionSolutionECEF,
            double[] deltaPositionMeters,
            boolean doAtmosphericCorrections,
            SatellitesPositionPseudorangesResidualAndCovarianceMatrix satPosPseudorangeResidualAndWeight,
            RealMatrix weightMatrixMetersMinus2)
            throws Exception {
        RealMatrix weightedGeometryMatrix;
        int numberOfIterations = 0;

        while ((Math.abs(deltaPositionMeters[0]) + Math.abs(deltaPositionMeters[1])
                + Math.abs(deltaPositionMeters[2])) >= LEAST_SQUARE_TOLERANCE_METERS) {
            // Apply ionospheric and tropospheric corrections only if the applied correction to
            // position is below a specific threshold
            if ((Math.abs(deltaPositionMeters[0]) + Math.abs(deltaPositionMeters[1])
                    + Math.abs(deltaPositionMeters[2])) < ATMOSPHERIC_CORRECTIONS_THRESHOLD_METERS) {
                doAtmosphericCorrections = true;
            }
            // Calculate satellites' positions, measurement residual per visible satellite and
            // weight matrix for the iterative least square
            satPosPseudorangeResidualAndWeight =
                    calculateSatPosAndPseudorangeResidual(
                            navMessageProto,
                            usefulSatellitesToReceiverMeasurements,
                            receiverGPSTowAtReceptionSeconds,
                            receiverGPSWeek,
                            dayOfYear1To366,
                            positionSolutionECEF,
                            doAtmosphericCorrections);

            // Calculate the geometry matrix according to "Global Positioning System: Theory and
            // Applications", Parkinson and Spilker page 413
            geometryMatrix = new Array2DRowRealMatrix(calculateGeometryMatrix(
                    satPosPseudorangeResidualAndWeight.satellitesPositionsMeters, positionSolutionECEF));
            // Apply weighted least square only if the covariance matrix is
            // not singular (has a non-zero determinant), otherwise apply ordinary least square.
            // The reason is to ignore reported signal to noise ratios by the receiver that can
            // lead to such singularities
            if (weightMatrixMetersMinus2 == null) {
                weightedGeometryMatrix = geometryMatrix;
            } else {
                RealMatrix hMatrix =
                        calculateHMatrix(weightMatrixMetersMinus2, geometryMatrix);
                weightedGeometryMatrix = hMatrix.multiply(geometryMatrix.transpose())
                        .multiply(weightMatrixMetersMinus2);
            }

            // Equation 9 page 413 from "Global Positioning System: Theory and Applications",
            // Parkinson and Spilker
            deltaPositionMeters =
                    GpsMathOperations.matrixByColVectMultiplication(
                            weightedGeometryMatrix.getData(),
                            satPosPseudorangeResidualAndWeight.pseudorangeResidualsMeters);

            // Apply corrections to the position estimate
            positionSolutionECEF[0] += deltaPositionMeters[0];
            positionSolutionECEF[1] += deltaPositionMeters[1];
            positionSolutionECEF[2] += deltaPositionMeters[2];
            positionSolutionECEF[3] += deltaPositionMeters[3];
            numberOfIterations++;
            Preconditions.checkArgument(numberOfIterations <= MAXIMUM_NUMBER_OF_LEAST_SQUARE_ITERATIONS,
                    "Maximum number of least square iterations reached without convergence...");
        }
        return satPosPseudorangeResidualAndWeight;
    }

    /**
     * Removes satellites that have residuals above {@value #RESIDUAL_TO_REPEAT_LEAST_SQUARE_METERS}
     * from the {@code usefulSatellitesToReceiverMeasurements} list. Returns true if any satellite is
     * removed.
     */
    private boolean removeHighResidualSats(
            List<GpsMeasurementWithRangeAndUncertainty> usefulSatellitesToReceiverMeasurements,
            boolean repeatLeastSquare,
            SatellitesPositionPseudorangesResidualAndCovarianceMatrix satPosPseudorangeResidualAndWeight,
            int satsWithResidualBelowThreshold) {

        for (int i = 0; i < satPosPseudorangeResidualAndWeight.pseudorangeResidualsMeters.length; i++) {
            if (satsWithResidualBelowThreshold > MINIMUM_NUMBER_OF_SATELLITES) {
                if (Math.abs(satPosPseudorangeResidualAndWeight.pseudorangeResidualsMeters[i])
                        > RESIDUAL_TO_REPEAT_LEAST_SQUARE_METERS) {
                    int prn = satPosPseudorangeResidualAndWeight.satellitePRNs[i];
                    usefulSatellitesToReceiverMeasurements.set(prn - 1, null);
                    satsWithResidualBelowThreshold--;
                    repeatLeastSquare = true;
                }
            }
        }
        return repeatLeastSquare;
    }

    /**
     * Calculates position of all visible satellites and pseudorange measurement residual
     * (difference of measured to predicted pseudoranges) needed for the least square computation. The
     * result is stored in an instance of {@link
     * SatellitesPositionPseudorangesResidualAndCovarianceMatrix}
     *
     * @param navMessageProto                        parameters of the navigation message
     * @param usefulSatellitesToReceiverMeasurements Map of useful satellite PRN to {@link
     *                                               GpsMeasurementWithRangeAndUncertainty} containing receiver measurements for computing the
     *                                               position solution
     * @param receiverGPSTowAtReceptionSeconds       Receiver estimate of GPS time of week (seconds)
     * @param receiverGpsWeek                        Receiver estimate of GPS week (0-1024+)
     * @param dayOfYear1To366                        The day of the year between 1 and 366
     * @param userPositionECEFMeters                 receiver ECEF position in meters
     * @param doAtmosphericCorrections               boolean indicating if atmospheric range corrections should be
     *                                               applied
     * @return SatellitesPositionPseudorangesResidualAndCovarianceMatrix Object containing satellite
     * prns, satellite positions in ECEF, pseudorange residuals and covariance matrix.
     */
    public SatellitesPositionPseudorangesResidualAndCovarianceMatrix
    calculateSatPosAndPseudorangeResidual(
            GpsNavMessageProto navMessageProto,
            List<GpsMeasurementWithRangeAndUncertainty> usefulSatellitesToReceiverMeasurements,
            double receiverGPSTowAtReceptionSeconds,
            int receiverGpsWeek,
            int dayOfYear1To366,
            double[] userPositionECEFMeters,
            boolean doAtmosphericCorrections)
            throws Exception {
        int numberOfUsefulSatellites =
                getNumberOfUsefulSatellites(usefulSatellitesToReceiverMeasurements);
        // deltaPseudorange is the pseudorange measurement residual
        // deltaPseudorange是伪距测量残差
        double[] deltaPseudorangesMeters = new double[numberOfUsefulSatellites];
        double[][] satellitesPositionsECEFMeters = new double[numberOfUsefulSatellites][3];

        // satellite PRNs
        int[] satellitePRNs = new int[numberOfUsefulSatellites];

        // Ionospheric model parameters
        // 电离层模型参数
        double[] alpha =
                {navMessageProto.iono.alpha[0], navMessageProto.iono.alpha[1],
                        navMessageProto.iono.alpha[2], navMessageProto.iono.alpha[3]};
        double[] beta = {navMessageProto.iono.beta[0], navMessageProto.iono.beta[1],
                navMessageProto.iono.beta[2], navMessageProto.iono.beta[3]};
        // Weight matrix for the weighted least square
        RealMatrix covarianceMatrixMetersSquare =
                new Array2DRowRealMatrix(numberOfUsefulSatellites, numberOfUsefulSatellites);
        calculateSatPosAndResiduals(
                navMessageProto,
                usefulSatellitesToReceiverMeasurements,
                receiverGPSTowAtReceptionSeconds,
                receiverGpsWeek,
                dayOfYear1To366,
                userPositionECEFMeters,
                doAtmosphericCorrections,
                deltaPseudorangesMeters,
                satellitesPositionsECEFMeters,
                satellitePRNs,
                alpha,
                beta,
                covarianceMatrixMetersSquare);

        return new SatellitesPositionPseudorangesResidualAndCovarianceMatrix(satellitePRNs,
                satellitesPositionsECEFMeters, deltaPseudorangesMeters,
                covarianceMatrixMetersSquare.getData());
    }

    public SatellitesPositionPseudorangesResidualAndCovarianceMatrix
    calculateSatTruePseudoranges(
            GpsNavMessageProto navMessageProto,
            List<GpsMeasurementWithRangeAndUncertainty> usefulSatellitesToReceiverMeasurements,
            double receiverGPSTowAtReceptionSeconds,
            int receiverGpsWeek,
            int dayOfYear1To366,
            double[] userPositionECEFMeters,
            boolean doAtmosphericCorrections)
            throws Exception {
        int numberOfUsefulSatellites =
                getNumberOfUsefulSatellites(usefulSatellitesToReceiverMeasurements);
        // deltaPseudorange是伪距测量残差
        double[] deltaPseudorangesMeters = new double[numberOfUsefulSatellites];
        double[][] satellitesPositionsECEFMeters = new double[numberOfUsefulSatellites][3];

        // satellite PRNs
        int[] satellitePRNs = new int[numberOfUsefulSatellites];

        // 电离层模型参数
        double[] alpha =
                {navMessageProto.iono.alpha[0], navMessageProto.iono.alpha[1],
                        navMessageProto.iono.alpha[2], navMessageProto.iono.alpha[3]};
        double[] beta = {navMessageProto.iono.beta[0], navMessageProto.iono.beta[1],
                navMessageProto.iono.beta[2], navMessageProto.iono.beta[3]};
        // Weight matrix for the weighted least square
        RealMatrix covarianceMatrixMetersSquare =
                new Array2DRowRealMatrix(numberOfUsefulSatellites, numberOfUsefulSatellites);
        calculateSatPosTruePseudoranges(
                navMessageProto,
                usefulSatellitesToReceiverMeasurements,
                receiverGPSTowAtReceptionSeconds,
                receiverGpsWeek,
                dayOfYear1To366,
                userPositionECEFMeters,
                doAtmosphericCorrections,
                deltaPseudorangesMeters,
                satellitesPositionsECEFMeters,
                satellitePRNs,
                alpha,
                beta,
                covarianceMatrixMetersSquare);

        return new SatellitesPositionPseudorangesResidualAndCovarianceMatrix(satellitePRNs,
                satellitesPositionsECEFMeters, deltaPseudorangesMeters,
                covarianceMatrixMetersSquare.getData());
    }

    /**
     * Calculates and fill the position of all visible satellites:
     * {@code satellitesPositionsECEFMeters}, pseudorange measurement residual (difference of
     * measured to predicted pseudoranges): {@code deltaPseudorangesMeters} and covariance matrix from
     * the weighted least square: {@code covarianceMatrixMetersSquare}. An array of the satellite PRNs
     * {@code satellitePRNs} is as well filled.
     * 计算并填充所有可见卫星的位置：｛@code satellitesPositionsECEFMeters｝、
     * 伪距测量残差（测量的伪距与预测的伪距之差）：｛:code deltaPseudorangesMeters｝
     * 和加权最小二乘的协方差矩阵：｛@code协变矩阵MetersSquare｝。
     * 卫星PRN｛@code satellitePRN｝的阵列也被填满。
     */
    @SuppressLint("LongLogTag")
    private void calculateSatPosAndResiduals(
            GpsNavMessageProto navMessageProto,
            List<GpsMeasurementWithRangeAndUncertainty> usefulSatellitesToReceiverMeasurements,
            double receiverGPSTowAtReceptionSeconds,
            int receiverGpsWeek,
            int dayOfYear1To366,
            double[] userPositionECEFMeters,
            boolean doAtmosphericCorrections,
            double[] deltaPseudorangesMeters,
            double[][] satellitesPositionsECEFMeters,
            int[] satellitePRNs,
            double[] alpha,
            double[] beta,
            RealMatrix covarianceMatrixMetersSquare)
            throws Exception {
        // user position without the clock estimate
        // 没有时钟估计的用户位置
        double[] userPositionTempECEFMeters =
                {userPositionECEFMeters[0], userPositionECEFMeters[1], userPositionECEFMeters[2]};
        int satsCounter = 0;

        for (int i = 0; i < GpsNavigationMessageStore.MAX_NUMBER_OF_SATELLITES; i++) {
            if (usefulSatellitesToReceiverMeasurements.get(i) != null) {
                GpsEphemerisProto ephemeridesProto = getEphemerisForSatellite(navMessageProto, i + 1);
                // Correct the receiver time of week with the estimated receiver clock bias
                // 使用估计的接收器时钟偏差校正接收器每周的时间
                receiverGPSTowAtReceptionSeconds =
                        receiverGPSTowAtReceptionSeconds - userPositionECEFMeters[3] / SPEED_OF_LIGHT_MPS;

                double pseudorangeMeasurementMeters =
                        usefulSatellitesToReceiverMeasurements.get(i).pseudorangeMeters;
                double pseudorangeUncertaintyMeters =
                        usefulSatellitesToReceiverMeasurements.get(i).pseudorangeUncertaintyMeters;

                // Assuming uncorrelated pseudorange measurements, the covariance matrix will be diagonal as
                // follows
                // 假设不相关的伪距测量，协方差矩阵将是对角的，如下所示
                covarianceMatrixMetersSquare.setEntry(satsCounter, satsCounter,
                        pseudorangeUncertaintyMeters * pseudorangeUncertaintyMeters);

                // Calculate time of week at transmission time corrected with the satellite clock drift
                // 计算用卫星时钟漂移校正的传输时间的每周时间
                GpsTimeOfWeekAndWeekNumber correctedTowAndWeek =
                        calculateCorrectedTransmitTowAndWeek(ephemeridesProto, receiverGPSTowAtReceptionSeconds,
                                receiverGpsWeek, pseudorangeMeasurementMeters);

                // calculate satellite position and velocity
                SatellitePositionCalculator.PositionAndVelocity satPosECEFMetersVelocityMPS = SatellitePositionCalculator
                        .calculateSatellitePositionAndVelocityFromEphemeris(ephemeridesProto,
                                correctedTowAndWeek.gpsTimeOfWeekSeconds, correctedTowAndWeek.weekNumber,
                                userPositionECEFMeters[0], userPositionECEFMeters[1], userPositionECEFMeters[2]);

                satellitesPositionsECEFMeters[satsCounter][0] = satPosECEFMetersVelocityMPS.positionXMeters;
                satellitesPositionsECEFMeters[satsCounter][1] = satPosECEFMetersVelocityMPS.positionYMeters;
                satellitesPositionsECEFMeters[satsCounter][2] = satPosECEFMetersVelocityMPS.positionZMeters;

                // Calculate ionospheric and tropospheric corrections
                // 计算电离层和对流层改正
                double ionosphericCorrectionMeters;
                double troposphericCorrectionMeters;
                if (doAtmosphericCorrections) {
                    ionosphericCorrectionMeters =
                            IonosphericModel.ionoKlobucharCorrectionSeconds(
                                    userPositionTempECEFMeters,
                                    satellitesPositionsECEFMeters[satsCounter],
                                    correctedTowAndWeek.gpsTimeOfWeekSeconds,
                                    alpha,
                                    beta,
                                    IonosphericModel.L1_FREQ_HZ)
                                    * SPEED_OF_LIGHT_MPS;

                    troposphericCorrectionMeters =
                            calculateTroposphericCorrectionMeters(
                                    dayOfYear1To366,
                                    satellitesPositionsECEFMeters,
                                    userPositionTempECEFMeters,
                                    satsCounter);
                } else {
                    troposphericCorrectionMeters = 0.0;
                    ionosphericCorrectionMeters = 0.0;
                }
                double predictedPseudorangeMeters =
                        calculatePredictedPseudorange(userPositionECEFMeters, satellitesPositionsECEFMeters,
                                userPositionTempECEFMeters, satsCounter, ephemeridesProto, correctedTowAndWeek,
                                ionosphericCorrectionMeters, troposphericCorrectionMeters);

                // Pseudorange residual (difference of measured to predicted pseudoranges)
                // 伪距残差（测量伪距与预测伪距之差）
                deltaPseudorangesMeters[satsCounter] =
                        pseudorangeMeasurementMeters - predictedPseudorangeMeters;

                // Satellite PRNs
                satellitePRNs[satsCounter] = i + 1;
                satsCounter++;
            }
        }
    }

    @SuppressLint("LongLogTag")
    private void calculateSatPosTruePseudoranges(
            GpsNavMessageProto navMessageProto,
            List<GpsMeasurementWithRangeAndUncertainty> usefulSatellitesToReceiverMeasurements,
            double receiverGPSTowAtReceptionSeconds,
            int receiverGpsWeek,
            int dayOfYear1To366,
            double[] userPositionECEFMeters,
            boolean doAtmosphericCorrections,
            double[] deltaPseudorangesMeters,
            double[][] satellitesPositionsECEFMeters,
            int[] satellitePRNs,
            double[] alpha,
            double[] beta,
            RealMatrix covarianceMatrixMetersSquare)
            throws Exception {
        // user position without the clock estimate
        // 没有时钟估计的用户位置
        double[] userPositionTempECEFMeters =
                {userPositionECEFMeters[0], userPositionECEFMeters[1], userPositionECEFMeters[2]};
        int satsCounter = 0;

        for (int i = 0; i < GpsNavigationMessageStore.MAX_NUMBER_OF_SATELLITES; i++) {
            if (usefulSatellitesToReceiverMeasurements.get(i) != null) {
                GpsEphemerisProto ephemeridesProto = getEphemerisForSatellite(navMessageProto, i + 1);
                // Correct the receiver time of week with the estimated receiver clock bias
                // 使用估计的接收器时钟偏差校正接收器每周的时间
                receiverGPSTowAtReceptionSeconds =
                        receiverGPSTowAtReceptionSeconds - userPositionECEFMeters[3] / SPEED_OF_LIGHT_MPS;

                double pseudorangeMeasurementMeters =
                        usefulSatellitesToReceiverMeasurements.get(i).pseudorangeMeters;
                double pseudorangeUncertaintyMeters =
                        usefulSatellitesToReceiverMeasurements.get(i).pseudorangeUncertaintyMeters;

                // Assuming uncorrelated pseudorange measurements, the covariance matrix will be diagonal as
                // follows
                // 假设不相关的伪距测量，协方差矩阵将是对角的，如下所示
                covarianceMatrixMetersSquare.setEntry(satsCounter, satsCounter,
                        pseudorangeUncertaintyMeters * pseudorangeUncertaintyMeters);

                // Calculate time of week at transmission time corrected with the satellite clock drift
                // 计算用卫星时钟漂移校正的传输时间的每周时间
                GpsTimeOfWeekAndWeekNumber correctedTowAndWeek =
                        calculateCorrectedTransmitTowAndWeek(ephemeridesProto, receiverGPSTowAtReceptionSeconds,
                                receiverGpsWeek, pseudorangeMeasurementMeters);

                // calculate satellite position and velocity
                SatellitePositionCalculator.PositionAndVelocity satPosECEFMetersVelocityMPS = SatellitePositionCalculator
                        .calculateSatellitePositionAndVelocityFromEphemeris(ephemeridesProto,
                                correctedTowAndWeek.gpsTimeOfWeekSeconds, correctedTowAndWeek.weekNumber,
                                userPositionECEFMeters[0], userPositionECEFMeters[1], userPositionECEFMeters[2]);

                satellitesPositionsECEFMeters[satsCounter][0] = satPosECEFMetersVelocityMPS.positionXMeters;
                satellitesPositionsECEFMeters[satsCounter][1] = satPosECEFMetersVelocityMPS.positionYMeters;
                satellitesPositionsECEFMeters[satsCounter][2] = satPosECEFMetersVelocityMPS.positionZMeters;

                // Calculate ionospheric and tropospheric corrections
                // 计算电离层和对流层改正
                double ionosphericCorrectionMeters;
                double troposphericCorrectionMeters;
                if (doAtmosphericCorrections) {
                    ionosphericCorrectionMeters =
                            IonosphericModel.ionoKlobucharCorrectionSeconds(
                                    userPositionTempECEFMeters,
                                    satellitesPositionsECEFMeters[satsCounter],
                                    correctedTowAndWeek.gpsTimeOfWeekSeconds,
                                    alpha,
                                    beta,
                                    IonosphericModel.L1_FREQ_HZ)
                                    * SPEED_OF_LIGHT_MPS;

                    troposphericCorrectionMeters =
                            calculateTroposphericCorrectionMeters(
                                    dayOfYear1To366,
                                    satellitesPositionsECEFMeters,
                                    userPositionTempECEFMeters,
                                    satsCounter);
                } else {
                    troposphericCorrectionMeters = 0.0;
                    ionosphericCorrectionMeters = 0.0;
                }

                double predictedPseudorangeMeters =
                        calculateMeasurementPseudorange(pseudorangeMeasurementMeters, userPositionECEFMeters,
                                ephemeridesProto, correctedTowAndWeek,
                                ionosphericCorrectionMeters, troposphericCorrectionMeters);

                // Pseudorange residual (difference of measured to predicted pseudoranges)
                // 伪距残差（测量伪距与预测伪距之差）
                deltaPseudorangesMeters[satsCounter] = predictedPseudorangeMeters;

                // Satellite PRNs
                satellitePRNs[satsCounter] = i + 1;
                satsCounter++;
            }
        }
    }

    /**
     * Searches ephemerides list for the ephemeris associated with current satellite in process
     */
    private GpsEphemerisProto getEphemerisForSatellite(GpsNavMessageProto navMessageProto,
                                                       int satPrn) {
        List<GpsEphemerisProto> ephemeridesList
                = new ArrayList<GpsEphemerisProto>(Arrays.asList(navMessageProto.ephemerids));
        GpsEphemerisProto ephemeridesProto = null;
        int ephemerisPrn = 0;
        for (GpsEphemerisProto ephProtoFromList : ephemeridesList) {
            ephemerisPrn = ephProtoFromList.prn;
            if (ephemerisPrn == satPrn) {
                ephemeridesProto = ephProtoFromList;
                break;
            }
        }
        return ephemeridesProto;
    }

    /**
     * Calculates predicted pseudorange in meters
     */
    private double calculatePredictedPseudorange(
            double[] userPositionECEFMeters,
            double[][] satellitesPositionsECEFMeters,
            double[] userPositionNoClockECEFMeters,
            int satsCounter,
            GpsEphemerisProto ephemeridesProto,
            GpsTimeOfWeekAndWeekNumber correctedTowAndWeek,
            double ionosphericCorrectionMeters,
            double troposphericCorrectionMeters)
            throws Exception {
        // Calculate the satellite clock drift
        double satelliteClockCorrectionMeters =
                SatelliteClockCorrectionCalculator.calculateSatClockCorrAndEccAnomAndTkIteratively(
                        ephemeridesProto,
                        correctedTowAndWeek.gpsTimeOfWeekSeconds,
                        correctedTowAndWeek.weekNumber)
                        .satelliteClockCorrectionMeters;

        double satelliteToUserDistanceMeters =
                GpsMathOperations.vectorNorm(GpsMathOperations.subtractTwoVectors(
                        satellitesPositionsECEFMeters[satsCounter], userPositionNoClockECEFMeters));
        // Predicted pseudorange
        double predictedPseudorangeMeters =
                satelliteToUserDistanceMeters - satelliteClockCorrectionMeters + ionosphericCorrectionMeters
                        + troposphericCorrectionMeters + userPositionECEFMeters[3];
        return predictedPseudorangeMeters;
    }

    /**
     * Calculates predicted pseudorange in meters
     */
    private double calculateMeasurementPseudorange(
            double pseudorangeMeasurementMeters,
            double[] userPositionECEFMeters,
            GpsEphemerisProto ephemeridesProto,
            GpsTimeOfWeekAndWeekNumber correctedTowAndWeek,
            double ionosphericCorrectionMeters,
            double troposphericCorrectionMeters)
            throws Exception {
        // Calculate the satellite clock drift
        double satelliteClockCorrectionMeters =
                SatelliteClockCorrectionCalculator.calculateSatClockCorrAndEccAnomAndTkIteratively(
                        ephemeridesProto,
                        correctedTowAndWeek.gpsTimeOfWeekSeconds,
                        correctedTowAndWeek.weekNumber)
                        .satelliteClockCorrectionMeters;

        // Predicted pseudorange
        return pseudorangeMeasurementMeters + satelliteClockCorrectionMeters - ionosphericCorrectionMeters
                - troposphericCorrectionMeters - userPositionECEFMeters[3];
    }

    /**
     * Calculates the Gps tropospheric correction in meters
     */
    private double calculateTroposphericCorrectionMeters(int dayOfYear1To366,
                                                         double[][] satellitesPositionsECEFMeters, double[] userPositionTempECEFMeters,
                                                         int satsCounter) {
        double troposphericCorrectionMeters;
        EcefToTopocentricConverter.TopocentricAEDValues elevationAzimuthDist =
                EcefToTopocentricConverter.convertCartesianToTopocentricRadMeters(
                        userPositionTempECEFMeters, GpsMathOperations.subtractTwoVectors(
                                satellitesPositionsECEFMeters[satsCounter], userPositionTempECEFMeters));

        Ecef2LlaConverter.GeodeticLlaValues lla =
                Ecef2LlaConverter.convertECEFToLLACloseForm(userPositionTempECEFMeters[0],
                        userPositionTempECEFMeters[1], userPositionTempECEFMeters[2]);

        // Geoid of the area where the receiver is located is calculated once and used for the
        // rest of the dataset as it change very slowly over wide area. This to save the delay
        // associated with accessing Google Elevation API. We assume this very first iteration of WLS
        // will compute the correct altitude above the ellipsoid of the ground at the latitude and
        // longitude
        if (calculateGeoidMeters) {
            double elevationAboveSeaLevelMeters = 0;
            if (elevationApiHelper == null) {
                System.out.println("No Google API key is set. Elevation above sea level is set to "
                        + "default 0 meters. This may cause inaccuracy in tropospheric correction.");
            } else {
                try {
                    elevationAboveSeaLevelMeters = elevationApiHelper
                            .getElevationAboveSeaLevelMeters(
                                    Math.toDegrees(lla.latitudeRadians), Math.toDegrees(lla.longitudeRadians)
                            );
                } catch (Exception e) {
                    e.printStackTrace();
                    System.out.println("Error when getting elevation from Google Server. "
                            + "Could be wrong Api key or network error. Elevation above sea level is set to "
                            + "default 0 meters. This may cause inaccuracy in tropospheric correction.");
                }
            }

            geoidHeightMeters = ElevationApiHelper.calculateGeoidHeightMeters(
                    lla.altitudeMeters,
                    elevationAboveSeaLevelMeters
            );
            troposphericCorrectionMeters = TroposphericModelEgnos.calculateTropoCorrectionMeters(
                    elevationAzimuthDist.elevationRadians, lla.latitudeRadians, elevationAboveSeaLevelMeters,
                    dayOfYear1To366);
        } else {
            troposphericCorrectionMeters = TroposphericModelEgnos.calculateTropoCorrectionMeters(
                    elevationAzimuthDist.elevationRadians, lla.latitudeRadians,
                    lla.altitudeMeters - geoidHeightMeters, dayOfYear1To366);
        }
        return troposphericCorrectionMeters;
    }

    /**
     * Gets the number of useful satellites from a list of
     * {@link GpsMeasurementWithRangeAndUncertainty}.
     */
    private int getNumberOfUsefulSatellites(
            List<GpsMeasurementWithRangeAndUncertainty> usefulSatellitesToReceiverMeasurements) {
        // calculate the number of useful satellites
        int numberOfUsefulSatellites = 0;
        for (int i = 0; i < usefulSatellitesToReceiverMeasurements.size(); i++) {
            if (usefulSatellitesToReceiverMeasurements.get(i) != null) {
                numberOfUsefulSatellites++;
            }
        }
        return numberOfUsefulSatellites;
    }

    /**
     * Computes the GPS time of week at the time of transmission and as well the corrected GPS week
     * taking into consideration week rollover. The returned GPS time of week is corrected by the
     * computed satellite clock drift. The result is stored in an instance of
     * {@link GpsTimeOfWeekAndWeekNumber}
     *
     * @param ephemerisProto                   parameters of the navigation message
     * @param receiverGpsTowAtReceptionSeconds Receiver estimate of GPS time of week when signal was
     *                                         received (seconds)
     * @param receiverGpsWeek                  Receiver estimate of GPS week (0-1024+)
     * @param pseudorangeMeters                Measured pseudorange in meters
     * @return GpsTimeOfWeekAndWeekNumber Object containing Gps time of week and week number.
     */
    private static GpsTimeOfWeekAndWeekNumber calculateCorrectedTransmitTowAndWeek(
            GpsEphemerisProto ephemerisProto, double receiverGpsTowAtReceptionSeconds,
            int receiverGpsWeek, double pseudorangeMeters) throws Exception {
        // GPS time of week at time of transmission: Gps time corrected for transit time (page 98 ICD
        // GPS 200)
        double receiverGpsTowAtTimeOfTransmission =
                receiverGpsTowAtReceptionSeconds - pseudorangeMeters / SPEED_OF_LIGHT_MPS;

        // Adjust for week rollover
        if (receiverGpsTowAtTimeOfTransmission < 0) {
            receiverGpsTowAtTimeOfTransmission += SECONDS_IN_WEEK;
            receiverGpsWeek -= 1;
        } else if (receiverGpsTowAtTimeOfTransmission > SECONDS_IN_WEEK) {
            receiverGpsTowAtTimeOfTransmission -= SECONDS_IN_WEEK;
            receiverGpsWeek += 1;
        }

        // Compute the satellite clock correction term (Seconds)
        double clockCorrectionSeconds =
                SatelliteClockCorrectionCalculator.calculateSatClockCorrAndEccAnomAndTkIteratively(
                        ephemerisProto, receiverGpsTowAtTimeOfTransmission,
                        receiverGpsWeek).satelliteClockCorrectionMeters / SPEED_OF_LIGHT_MPS;

        // Correct with the satellite clock correction term
        double receiverGpsTowAtTimeOfTransmissionCorrectedSec =
                receiverGpsTowAtTimeOfTransmission + clockCorrectionSeconds;

        // Adjust for week rollover due to satellite clock correction
        if (receiverGpsTowAtTimeOfTransmissionCorrectedSec < 0.0) {
            receiverGpsTowAtTimeOfTransmissionCorrectedSec += SECONDS_IN_WEEK;
            receiverGpsWeek -= 1;
        }
        if (receiverGpsTowAtTimeOfTransmissionCorrectedSec > SECONDS_IN_WEEK) {
            receiverGpsTowAtTimeOfTransmissionCorrectedSec -= SECONDS_IN_WEEK;
            receiverGpsWeek += 1;
        }
        return new GpsTimeOfWeekAndWeekNumber(receiverGpsTowAtTimeOfTransmissionCorrectedSec,
                receiverGpsWeek);
    }

    /**
     * Calculates the Geometry matrix (describing user to satellite geometry) given a list of
     * satellite positions in ECEF coordinates in meters and the user position in ECEF in meters.
     *
     * <p>The geometry matrix has four columns, and rows equal to the number of satellites. For each
     * of the rows (i.e. for each of the satellites used), the columns are filled with the normalized
     * line–of-sight vectors and 1 s for the fourth column.
     *
     * <p>Source: Parkinson, B.W., Spilker Jr., J.J.: ‘Global positioning system: theory and
     * applications’ page 413
     */
    private static double[][] calculateGeometryMatrix(double[][] satellitePositionsECEFMeters,
                                                      double[] userPositionECEFMeters) {

        double[][] geometryMatrix = new double[satellitePositionsECEFMeters.length][4];
        for (int i = 0; i < satellitePositionsECEFMeters.length; i++) {
            geometryMatrix[i][3] = 1;
        }
        // iterate over all satellites
        for (int i = 0; i < satellitePositionsECEFMeters.length; i++) {
            double[] r = {satellitePositionsECEFMeters[i][0] - userPositionECEFMeters[0],
                    satellitePositionsECEFMeters[i][1] - userPositionECEFMeters[1],
                    satellitePositionsECEFMeters[i][2] - userPositionECEFMeters[2]};
            double norm = Math.sqrt(Math.pow(r[0], 2) + Math.pow(r[1], 2) + Math.pow(r[2], 2));
            for (int j = 0; j < 3; j++) {
                geometryMatrix[i][j] =
                        (userPositionECEFMeters[j] - satellitePositionsECEFMeters[i][j]) / norm;
            }
        }
        return geometryMatrix;
    }

    /**
     * Class containing satellites' PRNs, satellites' positions in ECEF meters, the pseudorange
     * residual per visible satellite in meters and the covariance matrix of the
     * pseudoranges in meters square
     */
    protected static class SatellitesPositionPseudorangesResidualAndCovarianceMatrix {

        /**
         * Satellites' PRNs
         */
        protected final int[] satellitePRNs;

        /**
         * ECEF positions (meters) of useful satellites
         */
        protected final double[][] satellitesPositionsMeters;

        /**
         * Pseudorange measurement residuals (difference of measured to predicted pseudoranges)
         */
        protected final double[] pseudorangeResidualsMeters;

        /**
         * Pseudorange covariance Matrix for the weighted least squares (meters square)
         */
        protected final double[][] covarianceMatrixMetersSquare;

        /**
         * Constructor
         */
        private SatellitesPositionPseudorangesResidualAndCovarianceMatrix(int[] satellitePRNs,
                                                                          double[][] satellitesPositionsMeters, double[] pseudorangeResidualsMeters,
                                                                          double[][] covarianceMatrixMetersSquare) {
            this.satellitePRNs = satellitePRNs;
            this.satellitesPositionsMeters = satellitesPositionsMeters;
            this.pseudorangeResidualsMeters = pseudorangeResidualsMeters;
            this.covarianceMatrixMetersSquare = covarianceMatrixMetersSquare;
        }

    }

    /**
     * Class containing GPS time of week in seconds and GPS week number
     */
    private static class GpsTimeOfWeekAndWeekNumber {
        /**
         * GPS time of week in seconds
         */
        private final double gpsTimeOfWeekSeconds;

        /**
         * GPS week number
         */
        private final int weekNumber;

        /**
         * Constructor
         */
        private GpsTimeOfWeekAndWeekNumber(double gpsTimeOfWeekSeconds, int weekNumber) {
            this.gpsTimeOfWeekSeconds = gpsTimeOfWeekSeconds;
            this.weekNumber = weekNumber;
        }
    }

    /**
     * Uses the common reception time approach to calculate pseudoranges from the time of week
     * measurements reported by the receiver according to http://cdn.intechopen.com/pdfs-wm/27712.pdf.
     * As well computes the pseudoranges uncertainties for each input satellite
     * 使用通用接收时间方法根据接收机报告的每周时间测量值计算伪距 http://cdn.intechopen.com/pdfs-wm/27712.pdf.同时计算每个输入卫星的伪距不确定性
     */
    @VisibleForTesting
    static List<GpsMeasurementWithRangeAndUncertainty> computePseudorangeAndUncertainties(
            List<GpsMeasurement> usefulSatellitesToReceiverMeasurements,
            Long[] usefulSatellitesToTOWNs,
            long largestTowNs) {

        List<GpsMeasurementWithRangeAndUncertainty> usefulSatellitesToPseudorangeMeasurements =
                Arrays.asList(
                        new GpsMeasurementWithRangeAndUncertainty
                                [GpsNavigationMessageStore.MAX_NUMBER_OF_SATELLITES]);
        for (int i = 0; i < GpsNavigationMessageStore.MAX_NUMBER_OF_SATELLITES; i++) {
            // 星历数据有效
            if (usefulSatellitesToTOWNs[i] != null) {
                double deltai = largestTowNs - usefulSatellitesToTOWNs[i];
                double pseudorangeMeters =
                        (AVERAGE_TRAVEL_TIME_SECONDS + deltai * SECONDS_PER_NANO) * SPEED_OF_LIGHT_MPS;

                double signalToNoiseRatioLinear =
                        Math.pow(10, usefulSatellitesToReceiverMeasurements.get(i).signalToNoiseRatioDb / 10.0);
                // From Global Positioning System book, Misra and Enge, page 416, the uncertainty of the
                // pseudorange measurement is calculated next.
                // For GPS C/A code chip width Tc = 1 microseconds. Narrow correlator with spacing d = 0.1
                // chip and an average time of DLL correlator T of 20 milliseconds are used.
                double sigmaMeters =
                        SPEED_OF_LIGHT_MPS
                                * GPS_CHIP_WIDTH_T_C_SEC
                                * Math.sqrt(
                                GPS_CORRELATOR_SPACING_IN_CHIPS
                                        / (4 * GPS_DLL_AVERAGING_TIME_SEC * signalToNoiseRatioLinear));
                usefulSatellitesToPseudorangeMeasurements.set(
                        i,
                        new GpsMeasurementWithRangeAndUncertainty(
                                usefulSatellitesToReceiverMeasurements.get(i), pseudorangeMeters, sigmaMeters));
            }
        }
        return usefulSatellitesToPseudorangeMeasurements;
    }

}
