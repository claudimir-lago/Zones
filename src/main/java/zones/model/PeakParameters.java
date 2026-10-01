package zones.model;

/** Physical settings of the Test50 analysis; independent of baseline parameters. */
public record PeakParameters(double poolProminence, double minimumDistanceSeconds,
        double prominenceWindowSeconds, double lpnrThreshold, double noiseHalfWindowSeconds,
        double noiseGuardW50, int minimumNoisePoints, double geometrySmoothSeconds,
        double geometryStartW50, double geometrySlopeFraction, double geometryStableSeconds,
        double geometryRelativeHeight, double geometryBaselineSigma, double geometrySearchW50,
        double minimumSearchSeconds, double maximumSearchSeconds, double paddingW50,
        double minimumPaddingSeconds, double maximumPaddingSeconds, double joinGapSeconds,
        double alphaMax, double centerShiftW50, double minimumCenterShiftSeconds,
        double maximumCenterShiftSeconds, int maximumEvaluations) {
    public static final double FWHM_TO_SIGMA=2.354820045;
    public static final int FIT_EDGE_MINIMUM=2,FIT_EDGE_MAXIMUM=8,FIT_EDGE_DIVISOR=5;
    public static final double FIT_OFFSET_NOISE_FACTOR=5,FIT_INITIAL_SIGMA_FLOOR_SECONDS=.10,
        FIT_SIGMA_FLOOR_SECONDS=.08,FIT_SIGMA_LOWER_FACTOR=.20,FIT_SIGMA_UPPER_FACTOR=5,
        FIT_SIGMA_MINIMUM_BOUND_RATIO=1.5,FIT_MAGNITUDE_INITIAL_FACTOR=8,
        FIT_MAGNITUDE_SIGNAL_FACTOR=5,FIT_MAGNITUDE_NOISE_FACTOR=10,FIT_NOISE_FLOOR=1;
    public PeakParameters {
        double[] positive={poolProminence,minimumDistanceSeconds,prominenceWindowSeconds,lpnrThreshold,
            noiseHalfWindowSeconds,noiseGuardW50,geometrySmoothSeconds,geometryStartW50,
            geometrySlopeFraction,geometryStableSeconds,geometryRelativeHeight,geometryBaselineSigma,
            geometrySearchW50,minimumSearchSeconds,maximumSearchSeconds,paddingW50,
            minimumPaddingSeconds,maximumPaddingSeconds,alphaMax,centerShiftW50,
            minimumCenterShiftSeconds,maximumCenterShiftSeconds};
        for(double v:positive)if(!Double.isFinite(v)||v<=0)throw new IllegalArgumentException("Peak parameters must be finite and positive.");
        if(!Double.isFinite(joinGapSeconds)||joinGapSeconds<0||minimumNoisePoints<2||maximumEvaluations<1
                ||maximumSearchSeconds<minimumSearchSeconds||maximumPaddingSeconds<minimumPaddingSeconds
                ||maximumCenterShiftSeconds<minimumCenterShiftSeconds)throw new IllegalArgumentException("Invalid peak-analysis limits.");
    }
    public static PeakParameters defaults(){return new PeakParameters(30,.5,20,10,60,2,20,.55,.35,.12,.55,.22,2.5,4,3,35,.5,.5,4,1,40,.75,.5,4,16000);}
    public PeakParameters withThreshold(double threshold){return new PeakParameters(poolProminence,minimumDistanceSeconds,prominenceWindowSeconds,threshold,noiseHalfWindowSeconds,noiseGuardW50,minimumNoisePoints,geometrySmoothSeconds,geometryStartW50,geometrySlopeFraction,geometryStableSeconds,geometryRelativeHeight,geometryBaselineSigma,geometrySearchW50,minimumSearchSeconds,maximumSearchSeconds,paddingW50,minimumPaddingSeconds,maximumPaddingSeconds,joinGapSeconds,alphaMax,centerShiftW50,minimumCenterShiftSeconds,maximumCenterShiftSeconds,maximumEvaluations);}
}
