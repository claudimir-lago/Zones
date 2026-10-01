package zones.processing;

import java.util.*;
import zones.model.*;

/** Local extrema, bounded prominence and half-prominence width, in that order.
 * Distance suppression uses oriented height before prominence, matching scipy.find_peaks. */
public final class PeakDetector {
    public List<PeakCandidate> detect(double[] timeSeconds,double[] signal,boolean[] baselineMask,double dt,PeakParameters p){
        validate(timeSeconds,signal,baselineMask,dt);
        List<PeakCandidate> pool=new ArrayList<>();
        int distance=points(p.minimumDistanceSeconds(),dt,1),half=oddPoints(p.prominenceWindowSeconds(),dt,3)/2;
        for(int sign:new int[]{1,-1}){
            List<Integer> extrema=new ArrayList<>();
            for(int i=1;i<signal.length-1;i++)if(sign*signal[i]>sign*signal[i-1]){
                int end=i;while(end+1<signal.length&&signal[end+1]==signal[i])end++;
                if(end<signal.length-1&&sign*signal[end]>sign*signal[end+1])extrema.add((i+end)/2);
                i=end;
            }
            extrema.sort(Comparator.<Integer>comparingDouble(i->sign*signal[i]).reversed().thenComparing(Comparator.reverseOrder()));
            boolean[] suppressed=new boolean[signal.length];List<Integer> kept=new ArrayList<>();
            for(int i:extrema)if(!suppressed[i]){
                kept.add(i);Arrays.fill(suppressed,Math.max(0,i-distance+1),Math.min(signal.length,i+distance),true);
            }
            Collections.sort(kept);
            for(int apex:kept){
                double height=sign*signal[apex],leftMin=height,rightMin=height;int left=apex,right=apex;
                for(int i=apex;i>=Math.max(0,apex-half)&&sign*signal[i]<=height;i--)if(sign*signal[i]<leftMin){leftMin=sign*signal[i];left=i;}
                for(int i=apex;i<=Math.min(signal.length-1,apex+half)&&sign*signal[i]<=height;i++)if(sign*signal[i]<rightMin){rightMin=sign*signal[i];right=i;}
                double prominence=height-Math.max(leftMin,rightMin);if(prominence<p.poolProminence())continue;
                double level=height-.5*prominence;int li=apex,ri=apex;
                while(li>left&&sign*signal[li]>level)li--;
                while(ri<right&&sign*signal[ri]>level)ri++;
                double lip=li,rip=ri;
                if(sign*signal[li]<level)lip+=(level-sign*signal[li])/(sign*(signal[li+1]-signal[li]));
                if(sign*signal[ri]<level)rip-=(level-sign*signal[ri])/(sign*(signal[ri-1]-signal[ri]));
                double width=(rip-lip)*dt;
                int noiseHalf=points(p.noiseHalfWindowSeconds(),dt,1),guard=points(p.noiseGuardW50()*width,dt,1);
                double[] samples=new double[Math.min(signal.length,2*noiseHalf+1)];int n=0;
                for(int i=Math.max(0,apex-noiseHalf);i<=Math.min(signal.length-1,apex+noiseHalf);i++)if(baselineMask[i]&&Math.abs(i-apex)>guard)samples[n++]=signal[i];
                double sigma=n>=p.minimumNoisePoints()?1.4826*Statistics.mad(Arrays.copyOf(samples,n)):Double.NaN;
                double lpnr=Double.isFinite(sigma)&&sigma>0?prominence/sigma:Double.NaN;
                double bes=Double.isFinite(sigma)&&sigma>0?sign*signal[apex]/sigma:Double.NaN;
                pool.add(new PeakCandidate(0,0,apex,sign,timeSeconds[apex],signal[apex],prominence,left,right,width,sigma,n,lpnr,bes));
            }
        }
        pool.sort(Comparator.comparingDouble(PeakCandidate::localProminence).reversed());
        Map<Integer,Integer> prominenceRanks=new HashMap<>();for(int i=0;i<pool.size();i++)prominenceRanks.put(pool.get(i).index(),i+1);
        pool.sort(Comparator.comparingDouble((PeakCandidate c)->Double.isFinite(c.lpnr())?c.lpnr():Double.NEGATIVE_INFINITY).reversed());
        List<PeakCandidate> ranked=new ArrayList<>();
        for(int i=0;i<pool.size();i++){var c=pool.get(i);ranked.add(new PeakCandidate(i+1,prominenceRanks.get(c.index()),c.index(),c.polarity(),c.observedApexSeconds(),c.observedApexSignal(),c.localProminence(),c.leftBase(),c.rightBase(),c.w50Seconds(),c.localNoiseSigma(),c.noisePointCount(),c.lpnr(),c.bes()));}
        return List.copyOf(ranked);
    }
    static int points(double seconds,double dt,int minimum){return Math.max(minimum,(int)Math.rint(seconds/dt));}
    static int oddPoints(double seconds,double dt,int minimum){int n=points(seconds,dt,minimum);return n%2==0?n+1:n;}
    static double clip(double value,double low,double high){return Math.max(low,Math.min(high,value));}
    static void validate(double[] t,double[] y,boolean[] mask,double dt){
        if(t.length!=y.length||y.length!=mask.length||y.length<5||!Double.isFinite(dt)||dt<=0)throw new IllegalArgumentException("Insufficient or incompatible data for peak analysis.");
        for(int i=0;i<y.length;i++)if(!Double.isFinite(t[i])||!Double.isFinite(y[i])||(i>0&&t[i]<=t[i-1]))throw new IllegalArgumentException("Peak samples must be finite with increasing time.");
    }
}
