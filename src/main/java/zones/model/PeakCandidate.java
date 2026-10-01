package zones.model;

/** Rank is descending LPNR; prominenceRank preserves the local-prominence report identifier. */
public record PeakCandidate(int rank, int prominenceRank, int index, int polarity,
        double observedApexSeconds, double observedApexSignal, double localProminence,
        int leftBase, int rightBase, double w50Seconds, double localNoiseSigma,
        int noisePointCount, double lpnr, double bes) {
    public static final double BES_THRESHOLD=3.0;
    public boolean selected(double lpnrThreshold){return Double.isFinite(lpnr)&&lpnr>=lpnrThreshold&&Double.isFinite(bes)&&bes>=BES_THRESHOLD;}
}
