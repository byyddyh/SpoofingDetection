package cn.byyddyh.spoofingdetection.pseudorange;

import java.util.ArrayList;

public class DopplerSmoother {
    private static final int MAX_SMOOTH_WINDOW_SIZE = 50;
    private static final double MAX_SMOOTH_TIME_DIFFERENCE = 1.0; // seconds
    private static final double SPEED_OF_LIGHT = 299792458.0; // m/s

    private ArrayList<Double> pseudoranges;
    private ArrayList<Double> smoothedPseudoranges;

    public DopplerSmoother(ArrayList<Double> pseudoranges) {
        this.pseudoranges = pseudoranges;
        this.smoothedPseudoranges = new ArrayList<Double>();
    }

    public ArrayList<Double> smoothPseudoranges() {
        int n = pseudoranges.size();
        double[] deltaTs = calculateTimeDeltas();

        for (int i = 0; i < n; i++) {
            double sum = 0;
            int count = 0;
            for (int j = i; j < n; j++) {
                if (deltaTs[j] > MAX_SMOOTH_TIME_DIFFERENCE) {
                    break;
                }
                double weight = calculateWeight(deltaTs[j]);
                sum += weight * pseudoranges.get(j);
                count += weight;
            }
            double smoothedPseudorange = sum / count;
            smoothedPseudoranges.add(smoothedPseudorange);
        }

        return smoothedPseudoranges;
    }

    private double[] calculateTimeDeltas() {
        int n = pseudoranges.size();
        double[] deltaTs = new double[n];
        deltaTs[0] = 0;
        for (int i = 1; i < n; i++) {
            deltaTs[i] = (pseudoranges.get(i) - pseudoranges.get(i-1)) / SPEED_OF_LIGHT;
        }
        return deltaTs;
    }

    private double calculateWeight(double deltaT) {
        return Math.exp(-0.5 * Math.pow(deltaT / MAX_SMOOTH_WINDOW_SIZE, 2));
    }
}
