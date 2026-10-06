package zones.model;

import zones.parameters.*;

/** User intent in seconds; conversions use the median interval of the loaded file. */
public record PhysicalBaselineParameters(double numStd,double minimumLengthSeconds,int voteThresholdK,
        double peakWidthMinimumSeconds,double peakWidthMaximumSeconds,int numberOfScales,
        double logLambda,double localWindowSeconds,double noiseFactor,double slopeFactor,double minimumRunSeconds) {
    public static final double REFERENCE_INTERVAL_SECONDS=0.07620;
    public PhysicalBaselineParameters {
        if(!Double.isFinite(numStd) || numStd<2 || numStd>3.5)throw new IllegalArgumentException("FABC threshold (σ) must be between 2.0 and 3.5.");
        PhysicalUnits.lambda(logLambda);
        for(double duration:new double[]{minimumLengthSeconds,localWindowSeconds,minimumRunSeconds,peakWidthMinimumSeconds,peakWidthMaximumSeconds})
            if(!Double.isFinite(duration) || duration<=0)throw new IllegalArgumentException("Durations must be positive and finite.");
        if(peakWidthMinimumSeconds>peakWidthMaximumSeconds || numberOfScales<1 || numberOfScales>64 || voteThresholdK<1)
            throw new IllegalArgumentException("Check the width range, number of scales, and K.");
        if(!Double.isFinite(noiseFactor) || noiseFactor<0 || !Double.isFinite(slopeFactor) || slopeFactor<0)
            throw new IllegalArgumentException("Local factors must be finite and non-negative.");
    }
    public static PhysicalBaselineParameters defaults() {
        var p=BaselineParameters.defaults();double dt=REFERENCE_INTERVAL_SECONDS;int[] s=p.scales();
        return new PhysicalBaselineParameters(p.numStd(),p.minLength()*dt,p.voteThresholdK(),s[0]*dt,s[s.length-1]*dt,
                s.length,Math.log10(p.lambda()),p.localWindow()*dt,p.noiseFactor(),p.slopeFactor(),p.minimumRun()*dt);
    }
    public BaselineParameters toInternal(double dtSeconds) {
        int[] scales=new ScaleGenerator().generate(peakWidthMinimumSeconds,peakWidthMaximumSeconds,numberOfScales,dtSeconds);
        if(voteThresholdK>scales.length)throw new IllegalArgumentException("K exceeds the "+scales.length+" unique scales after rounding.");
        return new BaselineParameters(numStd,PhysicalUnits.points(minimumLengthSeconds,dtSeconds,1,false),voteThresholdK,scales,0,
                PhysicalUnits.lambda(logLambda),PhysicalUnits.points(localWindowSeconds,dtSeconds,3,true),noiseFactor,slopeFactor,
                PhysicalUnits.points(minimumRunSeconds,dtSeconds,1,false));
    }
}
