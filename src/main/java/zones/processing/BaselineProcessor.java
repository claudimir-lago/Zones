package zones.processing;

import java.util.*;
import zones.model.*;

/** Deterministic synchronous multiscale FABC pipeline. Local anchor recovery is intentionally disabled. */
public final class BaselineProcessor {
    public BaselineResult process(ElectropherogramData data,DetectorChannel channel,BaselineParameters p) {
        long start=System.nanoTime();
        double[] y=data.signal(channel);int n=y.length;
        if(p.minLength()>n)throw new IllegalArgumentException("Minimum baseline length exceeds the signal length.");
        if(data.isConstant(channel))throw new IllegalArgumentException("The selected channel is constant; there is no varying signal to classify.");
        Map<Integer,boolean[]> masks=new LinkedHashMap<>();Map<Integer,String> failures=new LinkedHashMap<>();
        FabcClassifier classifier=new FabcClassifier();WhittakerSmoother smoother=new WhittakerSmoother();
        for(int scale:p.scales()) {
            if(Thread.currentThread().isInterrupted())throw new java.util.concurrent.CancellationException();
            try {
                boolean[] mask=classifier.classify(y,scale,p.numStd(),p.minLength());
                smoother.smooth(y,mask,1e6);
                masks.put(scale,mask);
            } catch(IllegalArgumentException|IllegalStateException e) {failures.put(scale,e.getMessage());}
        }
        if(masks.isEmpty())throw new IllegalStateException("All FABC scales failed: " + failures);
        int[] votes=new int[n];for(boolean[] mask:masks.values())for(int i=0;i<n;i++)if(!mask[i])votes[i]++;
        boolean[] high=new boolean[n];for(int i=0;i<n;i++)high[i]=votes[i]<p.voteThresholdK();
        double[] baseline=smoother.smooth(y,high,p.lambda());
        double sigma=Statistics.noise(y); // diagnostic only; no local green-anchor refinement is applied.
        boolean[] recovered=new boolean[n];
        double[] zero=new double[n];
        return new BaselineResult(data,channel,p,baseline,baseline,zero,zero,votes,
                high,recovered,high,masks,failures,sigma,(System.nanoTime()-start)/1000000);
    }
}
