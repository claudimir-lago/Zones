package zones.processing;

import java.util.*;
import zones.model.*;

/** Selective moving-median residual (MMR) outlier filter validated in TestMMR05. */
public final class SignalPreprocessor {
    public static final double MMR_SIGMA_THRESHOLD=5.0;

    public PreprocessedData process(ElectropherogramData raw,SpikeRemovalParameters p,boolean recalculateCharge) {
        int halfWindow=p.enabled()?p.halfWindow(raw.samplingIntervalSeconds()):0;
        var channels=new EnumMap<DetectorChannel,double[]>(DetectorChannel.class);
        for(var channel:raw.detectors()) {
            double[] signal=raw.signal(channel);
            channels.put(channel,p.enabled()?selectiveMmr(signal,halfWindow,raw.timeMinutes()):signal.clone());
        }
        double[] current=raw.currentMicroamps().orElse(null);
        if(current!=null && p.enabled())current=selectiveMmr(current,halfWindow,raw.timeMinutes());
        var cleaned=new ElectropherogramData(raw.source(),raw.timeMinutes(),channels,current,raw.chargeMilliCoulombs().orElse(null));
        double[] charge=recalculateCharge && current!=null?new ChargeCalculator().fromCurrent(raw.timeMinutes(),current):null;
        return new PreprocessedData(raw,cleaned,p,halfWindow,charge);
    }

    /** Median is detector-only: samples not classified as outliers remain bit-for-bit unchanged. */
    static double[] selectiveMmr(double[] signal,int halfWindow,double[] timeMinutes){
        if(signal.length<5 || halfWindow<1)return signal.clone();
        double[] median=new MovingMedian().apply(signal,halfWindow);
        double[] residual=new double[signal.length];
        for(int i=0;i<signal.length;i++)residual[i]=signal[i]-median[i];
        double sigma=1.4826*Statistics.mad(residual);
        if(!(sigma>0) || !Double.isFinite(sigma))return signal.clone();
        double threshold=MMR_SIGMA_THRESHOLD*sigma;
        boolean[] suspicious=new boolean[signal.length];
        for(int i=0;i<signal.length;i++)suspicious[i]=Math.abs(residual[i])>threshold;
        double[] cleaned=signal.clone();
        for(int i=0;i<signal.length;){
            if(!suspicious[i]){i++;continue;}
            int first=i;while(i+1<signal.length&&suspicious[i+1])i++;int last=i++;
            quadratic22(signal,cleaned,timeMinutes,first,last);
        }
        return cleaned;
    }

    /** Reconstruct a suspicious run from the two immediately preceding and following valid samples. */
    private static void quadratic22(double[] source,double[] target,double[] timeMinutes,int first,int last){
        if(first<2 || last+2>=source.length){
            // At file edges, retain the conservative median replacement rather than extrapolating a parabola.
            int half=Math.min(3,Math.min(first,source.length-1-last));
            if(half<1)return;
            double[] local=Arrays.copyOfRange(source,Math.max(0,first-half),Math.min(source.length,last+half+1));
            double replacement=Statistics.median(local);
            for(int j=first;j<=last;j++)target[j]=replacement;
            return;
        }
        double center=.5*(timeMinutes[first]+timeMinutes[last]);
        int[] ix={first-2,first-1,last+1,last+2};
        // Normal equations for y = a + b*x + c*x^2, with x centered near the reconstructed run.
        double s0=4,s1=0,s2=0,s3=0,s4=0,sy=0,sxy=0,sx2y=0;
        for(int q:ix){double x=(timeMinutes[q]-center)*60.0,y=source[q],x2=x*x;s1+=x;s2+=x2;s3+=x2*x;s4+=x2*x2;sy+=y;sxy+=x*y;sx2y+=x2*y;}
        double[][] a={{s0,s1,s2,sy},{s1,s2,s3,sxy},{s2,s3,s4,sx2y}};
        for(int col=0;col<3;col++){
            int pivot=col;for(int r=col+1;r<3;r++)if(Math.abs(a[r][col])>Math.abs(a[pivot][col]))pivot=r;
            double[] tmp=a[col];a[col]=a[pivot];a[pivot]=tmp;
            if(Math.abs(a[col][col])<1e-14)return;
            double d=a[col][col];for(int c=col;c<4;c++)a[col][c]/=d;
            for(int r=0;r<3;r++)if(r!=col){double f=a[r][col];for(int c=col;c<4;c++)a[r][c]-=f*a[col][c];}
        }
        double aa=a[0][3],bb=a[1][3],cc=a[2][3];
        for(int q=first;q<=last;q++){double x=(timeMinutes[q]-center)*60.0;target[q]=aa+bb*x+cc*x*x;}
    }
}
