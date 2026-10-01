package zones.application;
import java.util.*;
import zones.model.*;
import zones.processing.*;

/** Peak analysis consumes a baseline snapshot; changing its threshold never reruns baseline. */
public final class PeakAnalysisProcessor {
    public PeakAnalysisResult process(BaselineResult baseline,PeakParameters p){
        double[] times=Arrays.stream(baseline.data().timeMinutes()).map(v->v*60).toArray();
        return process(times,baseline.correctedSignal(),baseline.finalBaselineMask(),baseline.data().samplingIntervalSeconds(),p);
    }
    public PeakAnalysisResult process(double[] times,double[] signal,boolean[] mask,double dt,PeakParameters p){
        var candidates=new PeakDetector().detect(times,signal,mask,dt,p);
        return refit(times,signal,dt,candidates,p);
    }
    public PeakAnalysisResult refit(double[] times,double[] signal,double dt,List<PeakCandidate> candidates,PeakParameters p){
        var windows=new PeakWindowBuilder().build(times,signal,dt,candidates,p);
        List<PeakFit> fits=new ArrayList<>();for(var w:windows)fits.add(new PeakFitter().fit(w,times,signal,p));
        return new PeakAnalysisResult(candidates,fits,p);
    }
    /** Refit one existing window with an expert-selected component count. Detection and baseline are unchanged. */
    public PeakAnalysisResult refitWindowComponentCount(double[] times,double[] signal,PeakAnalysisResult current,int windowId,int count){
        if(count<1||count>12)throw new IllegalArgumentException("Component count must be between 1 and 12.");
        double dt=times.length>1?Math.abs(times[1]-times[0]):0.1;
        PeakFit old=current.fits().stream().filter(f->f.window().id()==windowId).findFirst().orElseThrow();
        PeakWindow w=old.window(); List<PeakCandidate> seeds=new ArrayList<>(w.members());
        seeds.sort(Comparator.comparingDouble(PeakCandidate::localProminence).reversed());
        if(seeds.size()>count)seeds=new ArrayList<>(seeds.subList(0,count));
        if(seeds.size()<count){
            double offset=old.offset(); List<Integer> extrema=new ArrayList<>();
            for(int i=Math.max(w.leftIndex()+1,1);i<Math.min(w.rightIndex(),signal.length-1);i++){
                double a=Math.abs(signal[i-1]-offset),b=Math.abs(signal[i]-offset),c=Math.abs(signal[i+1]-offset);
                if(b>=a&&b>=c)extrema.add(i);
            }
            extrema.sort((a,b)->Double.compare(Math.abs(signal[b]-offset),Math.abs(signal[a]-offset)));
            int serial=1;
            for(int idx:extrema){
                if(seeds.size()>=count)break; double ts=times[idx];
                boolean near=seeds.stream().anyMatch(q->Math.abs(q.observedApexSeconds()-ts)<Math.max(2*dt,.08)); if(near)continue;
                PeakCandidate ref=w.members().stream().min(Comparator.comparingDouble(q->Math.abs(q.observedApexSeconds()-ts))).orElse(w.members().get(0));
                int pol=signal[idx]-offset>=0?1:-1; double prom=Math.abs(signal[idx]-offset);
                seeds.add(new PeakCandidate(-1000-serial++,-1000-serial,idx,pol,ts,signal[idx],prom,idx,idx,ref.w50Seconds(),ref.localNoiseSigma(),ref.noisePointCount(),ref.lpnr(),Math.max(ref.bes(),PeakCandidate.BES_THRESHOLD)));
            }
            while(seeds.size()<count){
                int n=seeds.size(),idx=w.leftIndex()+(int)Math.round((w.rightIndex()-w.leftIndex())*(n+1.0)/(count+1.0));
                PeakCandidate ref=w.members().get(0);int pol=signal[idx]-offset>=0?1:-1;double prom=Math.abs(signal[idx]-offset);
                seeds.add(new PeakCandidate(-2000-n,-2000-n,idx,pol,times[idx],signal[idx],prom,idx,idx,ref.w50Seconds(),ref.localNoiseSigma(),ref.noisePointCount(),ref.lpnr(),Math.max(ref.bes(),PeakCandidate.BES_THRESHOLD)));
            }
        }
        seeds.sort(Comparator.comparingDouble(PeakCandidate::observedApexSeconds));
        PeakWindow edited=new PeakWindow(w.id(),w.leftIndex(),w.rightIndex(),w.startSeconds(),w.endSeconds(),PeakWindow.classify(seeds),seeds);
        PeakFit replacement=new PeakFitter().fit(edited,times,signal,current.parametersUsed());
        List<PeakFit> fits=new ArrayList<>();for(PeakFit f:current.fits())fits.add(f.window().id()==windowId?replacement:f);
        return new PeakAnalysisResult(current.candidates(),fits,current.parametersUsed());
    }
}
