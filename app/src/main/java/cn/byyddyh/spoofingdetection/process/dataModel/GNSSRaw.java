package cn.byyddyh.spoofingdetection.process.dataModel;

import java.util.ArrayList;
import java.util.List;

public class GNSSRaw {
    public List<Double> ElapsedRealtimeMillis;
    public List<Long> TimeNanos;
    public List<Double> LeapSecond;
    public List<Double> TimeUncertaintyNanos;
    public List<Long> FullBiasNanos;
    public List<Double> BiasNanos;
    public List<Double> BiasUncertaintyNanos;
    public List<Double> DriftNanosPerSecond;
    public List<Double> DriftUncertaintyNanosPerSecond;
    public List<Double> HardwareClockDiscontinuityCount;
    public List<Double> Svid;
    public List<Double> TimeOffsetNanos;
    public List<Long> State;
    public List<Long> ReceivedSvTimeNanos;
    public List<Long> ReceivedSvTimeUncertaintyNanos;
    public List<Double> Cn0DbHz;
    public List<Double> PseudorangeRateMetersPerSecond;
    public List<Double> PseudorangeRateUncertaintyMetersPerSecond;
    public List<Double> AccumulatedDeltaRangeState;
    public List<Double> AccumulatedDeltaRangeMeters;
    public List<Double> AccumulatedDeltaRangeUncertaintyMeters;
    public List<Double> CarrierFrequencyHz;
    public List<Long> CarrierCycles;
    public List<Double> MultipathIndicator;
    public List<Long> ConstellationType;
    public List<Double> AgcDb;
    public List<Long> allRxMillis;

    public GNSSRaw() {
        ElapsedRealtimeMillis = new ArrayList<>();
        TimeNanos = new ArrayList<>();
        LeapSecond = new ArrayList<>();
        TimeUncertaintyNanos = new ArrayList<>();
        FullBiasNanos = new ArrayList<>();
        BiasNanos = new ArrayList<>();
        BiasUncertaintyNanos = new ArrayList<>();
        DriftNanosPerSecond = new ArrayList<>();
        DriftUncertaintyNanosPerSecond = new ArrayList<>();
        HardwareClockDiscontinuityCount = new ArrayList<>();
        Svid = new ArrayList<>();
        TimeOffsetNanos = new ArrayList<>();
        State = new ArrayList<>();
        ReceivedSvTimeNanos = new ArrayList<>();
        ReceivedSvTimeUncertaintyNanos = new ArrayList<>();
        Cn0DbHz = new ArrayList<>();
        PseudorangeRateMetersPerSecond = new ArrayList<>();
        PseudorangeRateUncertaintyMetersPerSecond = new ArrayList<>();
        AccumulatedDeltaRangeState = new ArrayList<>();
        AccumulatedDeltaRangeMeters = new ArrayList<>();
        AccumulatedDeltaRangeUncertaintyMeters = new ArrayList<>();
        CarrierFrequencyHz = new ArrayList<>();
        CarrierCycles = new ArrayList<>();
        MultipathIndicator = new ArrayList<>();
        ConstellationType = new ArrayList<>();
        AgcDb = new ArrayList<>();
        allRxMillis = new ArrayList<>();
    }

    public GNSSRaw(List<Double> elapsedRealtimeMillis, List<Long> timeNanos, List<Double> leapSecond,
                   List<Double> timeUncertaintyNanos, List<Long> fullBiasNanos, List<Double> biasNanos,
                   List<Double> biasUncertaintyNanos, List<Double> driftNanosPerSecond, List<Double> driftUncertaintyNanosPerSecond,
                   List<Double> hardwareClockDiscontinuityCount, List<Double> svid, List<Double> timeOffsetNanos, List<Long> state,
                   List<Long> receivedSvTimeNanos, List<Long> receivedSvTimeUncertaintyNanos, List<Double> cn0DbHz,
                   List<Double> pseudorangeRateMetersPerSecond, List<Double> pseudorangeRateUncertaintyMetersPerSecond, List<Double> accumulatedDeltaRangeState,
                   List<Double> accumulatedDeltaRangeMeters, List<Double> accumulatedDeltaRangeUncertaintyMeters, List<Double> carrierFrequencyHz,
                   List<Long> carrierCycles, List<Double> multipathIndicator, List<Long> constellationType, List<Double> agcDb, List<Long> allRxMillis) {
        this.ElapsedRealtimeMillis = elapsedRealtimeMillis;
        this.TimeNanos = timeNanos;
        this.LeapSecond = leapSecond;
        this.TimeUncertaintyNanos = timeUncertaintyNanos;
        this.FullBiasNanos = fullBiasNanos;
        this.BiasNanos = biasNanos;
        this.BiasUncertaintyNanos = biasUncertaintyNanos;
        this.DriftNanosPerSecond = driftNanosPerSecond;
        this.DriftUncertaintyNanosPerSecond = driftUncertaintyNanosPerSecond;
        this.HardwareClockDiscontinuityCount = hardwareClockDiscontinuityCount;
        this.Svid = svid;
        this.TimeOffsetNanos = timeOffsetNanos;
        this.State = state;
        this.ReceivedSvTimeNanos = receivedSvTimeNanos;
        this.ReceivedSvTimeUncertaintyNanos = receivedSvTimeUncertaintyNanos;
        this.Cn0DbHz = cn0DbHz;
        this.PseudorangeRateMetersPerSecond = pseudorangeRateMetersPerSecond;
        this.PseudorangeRateUncertaintyMetersPerSecond = pseudorangeRateUncertaintyMetersPerSecond;
        this.AccumulatedDeltaRangeState = accumulatedDeltaRangeState;
        this.AccumulatedDeltaRangeMeters = accumulatedDeltaRangeMeters;
        this.AccumulatedDeltaRangeUncertaintyMeters = accumulatedDeltaRangeUncertaintyMeters;
        this.CarrierFrequencyHz = carrierFrequencyHz;
        this.CarrierCycles = carrierCycles;
        this.MultipathIndicator = multipathIndicator;
        this.ConstellationType = constellationType;
        this.AgcDb = agcDb;
        this.allRxMillis = allRxMillis;
    }
}
