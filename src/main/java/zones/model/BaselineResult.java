package zones.model;

import java.util.*;

/** Immutable scientific output; arrays are copied on construction and access. */
public final class BaselineResult {
    public static final String ALGORITHM_VERSION="fabc-multiscale-no-local-refinement-v2";
    private final ElectropherogramData data;
    private final DetectorChannel detector;
    private final BaselineParameters parameters;
    private final double[] originalBaseline, baseline, localNoise, localSlope;
    private final int[] votes;
    private final boolean[] high, recovered, combined;
    private final Map<Integer,boolean[]> scaleMasks;
    private final Map<Integer,String> failedScales;
    private final double noiseEstimate;
    private final long elapsedMillis;
    public BaselineResult(ElectropherogramData data, DetectorChannel detector, BaselineParameters parameters,
            double[] originalBaseline, double[] baseline, double[] localNoise, double[] localSlope,
            int[] votes, boolean[] high, boolean[] recovered, boolean[] combined,
            Map<Integer,boolean[]> scaleMasks, Map<Integer,String> failedScales, double noiseEstimate,long elapsedMillis) {
        this.data=data;this.detector=detector;this.parameters=parameters;
        this.originalBaseline=originalBaseline.clone();this.baseline=baseline.clone();
        this.localNoise=localNoise.clone();this.localSlope=localSlope.clone();this.votes=votes.clone();
        this.high=high.clone();this.recovered=recovered.clone();this.combined=combined.clone();
        this.scaleMasks=new LinkedHashMap<>();scaleMasks.forEach((k,v)->this.scaleMasks.put(k,v.clone()));
        this.failedScales=Collections.unmodifiableMap(new LinkedHashMap<>(failedScales));
        this.noiseEstimate=noiseEstimate;this.elapsedMillis=elapsedMillis;
    }
    public ElectropherogramData data(){return data;}
    public DetectorChannel detector(){return detector;}
    public BaselineParameters parametersUsed(){return parameters;}
    public double[] rawSignal(){return data.signal(detector);}
    public double[] baseline(){return baseline.clone();}
    public double[] originalBaseline(){return originalBaseline.clone();}
    public double[] correctedSignal(){double[] y=rawSignal();for(int i=0;i<y.length;i++)y[i]-=baseline[i];return y;}
    public double[] localNoise(){return localNoise.clone();}
    public double[] localSlope(){return localSlope.clone();}
    public int[] voteCount(){return votes.clone();}
    public boolean[] highConfidenceBaselineMask(){return high.clone();}
    public boolean[] recoveredBaselineMask(){return recovered.clone();}
    public boolean[] finalBaselineMask(){return combined.clone();}
    public Map<Integer,boolean[]> scaleMasks(){Map<Integer,boolean[]> copy=new LinkedHashMap<>();scaleMasks.forEach((k,v)->copy.put(k,v.clone()));return Collections.unmodifiableMap(copy);}
    public Map<Integer,String> failedScales(){return failedScales;}
    public double noiseEstimate(){return noiseEstimate;}
    public long elapsedMillis(){return elapsedMillis;}
}
