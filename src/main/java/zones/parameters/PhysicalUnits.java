package zones.parameters;

/** Display-unit conversion only; no change to the index-based baseline engine. */
public final class PhysicalUnits {
    private PhysicalUnits() {}
    public static int points(double seconds,double samplingIntervalSeconds,int minimum,boolean odd) {
        if(!Double.isFinite(seconds) || seconds<=0 || !Double.isFinite(samplingIntervalSeconds) || samplingIntervalSeconds<=0)
            throw new IllegalArgumentException("Duration and sampling interval must be positive and finite.");
        double ratio=seconds/samplingIntervalSeconds;
        if(ratio>100000)throw new IllegalArgumentException("Duration exceeds the 100000-sample limit.");
        int result=Math.max(minimum,(int)Math.round(ratio));
        if(odd && result%2==0)result++;
        return result;
    }
    public static double lambda(double logLambda) {
        if(!Double.isFinite(logLambda) || logLambda<5 || logLambda>15)
            throw new IllegalArgumentException("log(λ) must be between 5 and 15.");
        return Math.pow(10,logLambda);
    }
}
