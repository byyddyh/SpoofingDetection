package cn.byyddyh.spoofingdetection.process;

import android.location.GnssClock;
import android.location.GnssMeasurement;
import android.os.Build;

import java.math.BigDecimal;
import java.util.Collection;

import cn.byyddyh.spoofingdetection.LogFragment;
import cn.byyddyh.spoofingdetection.process.dataModel.GNSSRaw;
import cn.byyddyh.spoofingdetection.process.dataProcess.DataFilter;

public class ProcessUtils {
    public static GNSSRaw filterRawData(Collection<GnssMeasurement> inputMeasurement, GnssClock mClock) {
        GNSSRaw gnssRaw = new GNSSRaw();

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            if (mClock.getFullBiasNanos() > 0) {
                return null;
            }

            // 校验 FullBiasNanos
            if (!DataFilter.nanosCheck(mClock.getFullBiasNanos())) {
                return null;
            }

            long allRxMilli = new BigDecimal(mClock.getTimeNanos()).
                    subtract(new BigDecimal(mClock.getFullBiasNanos())).
                    divide(new BigDecimal(1000000))
                    .add(new BigDecimal("0.5"))
                    .longValue();

            for (GnssMeasurement gnssMeasurement : inputMeasurement) {
                boolean bOK = true;

                // 校验 ConstellationType
                if (!DataFilter.ConstellationTypeCheck(gnssMeasurement.getConstellationType())) {
                    bOK = false;
                }

                // 校验 State
                if (!DataFilter.stateCheck(gnssMeasurement.getState())) {
                    bOK = false;
                }

                if (bOK) {
                    LogFragment.logText("text", "state校验通过数据" + gnssMeasurement.getState());

                    gnssRaw.ElapsedRealtimeMillis.add((double) mClock.getElapsedRealtimeNanos());
                    gnssRaw.TimeNanos.add(mClock.getTimeNanos());
                    gnssRaw.LeapSecond.add(mClock.hasLeapSecond() ? (double) mClock.getLeapSecond() : null);
                    gnssRaw.TimeUncertaintyNanos.add(mClock.hasTimeUncertaintyNanos() ? mClock.getTimeUncertaintyNanos() : null);
                    gnssRaw.FullBiasNanos.add(mClock.getFullBiasNanos());
                    gnssRaw.BiasNanos.add(mClock.hasBiasNanos() ? mClock.getBiasNanos() : null);
                    gnssRaw.BiasUncertaintyNanos.add(mClock.hasBiasUncertaintyNanos() ? mClock.getBiasUncertaintyNanos() : null);
                    gnssRaw.DriftNanosPerSecond.add(mClock.hasDriftNanosPerSecond() ? mClock.getDriftNanosPerSecond() : Double.parseDouble(""));
                    gnssRaw.DriftUncertaintyNanosPerSecond.add(mClock.hasDriftUncertaintyNanosPerSecond() ? mClock.getDriftUncertaintyNanosPerSecond() : null);
                    gnssRaw.HardwareClockDiscontinuityCount.add((double) mClock.getHardwareClockDiscontinuityCount());
                    gnssRaw.Svid.add((double) gnssMeasurement.getSvid());
                    gnssRaw.TimeOffsetNanos.add(gnssMeasurement.getTimeOffsetNanos());
                    gnssRaw.State.add((long) gnssMeasurement.getState());
                    gnssRaw.ReceivedSvTimeNanos.add(gnssMeasurement.getReceivedSvTimeNanos());
                    gnssRaw.ReceivedSvTimeUncertaintyNanos.add(gnssMeasurement.getReceivedSvTimeUncertaintyNanos());
                    gnssRaw.Cn0DbHz.add(gnssMeasurement.getCn0DbHz());
                    gnssRaw.PseudorangeRateMetersPerSecond.add(gnssMeasurement.getPseudorangeRateMetersPerSecond());
                    gnssRaw.PseudorangeRateUncertaintyMetersPerSecond.add(gnssMeasurement.getPseudorangeRateUncertaintyMetersPerSecond());
                    gnssRaw.AccumulatedDeltaRangeState.add((double) gnssMeasurement.getAccumulatedDeltaRangeState());
                    gnssRaw.AccumulatedDeltaRangeMeters.add(gnssMeasurement.getAccumulatedDeltaRangeMeters());
                    gnssRaw.AccumulatedDeltaRangeUncertaintyMeters.add(gnssMeasurement.getAccumulatedDeltaRangeUncertaintyMeters());
                    gnssRaw.CarrierFrequencyHz.add(gnssMeasurement.hasCarrierFrequencyHz() ? gnssMeasurement.getCarrierPhaseUncertainty() : null);
                    gnssRaw.CarrierCycles.add(gnssMeasurement.hasCarrierCycles() ? gnssMeasurement.getCarrierCycles() : null);
                    gnssRaw.MultipathIndicator.add((double) gnssMeasurement.getMultipathIndicator());
                    gnssRaw.ConstellationType.add((long) gnssMeasurement.getConstellationType());
                    gnssRaw.AgcDb.add(gnssMeasurement.hasAutomaticGainControlLevelDb() ? gnssMeasurement.getAutomaticGainControlLevelDb() : null);
                    gnssRaw.allRxMillis.add(allRxMilli);
                }
            }
        }

        return gnssRaw;
    }
}
