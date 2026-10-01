package zones.processing;

import java.util.*;
import zones.model.*;
import org.ejml.data.DMatrixRMaj;
import org.ejml.dense.row.CommonOps_DDRM;

/** Geometry is used only for supports; it never modifies the signal used in fitting. */
public final class PeakWindowBuilder {
    public List<PeakWindow> build(double[] times,double[] signal,double dt,List<PeakCandidate> candidates,PeakParameters p){
        double[] smooth=smoothGeometry(signal,PeakDetector.oddPoints(p.geometrySmoothSeconds(),dt,5));
        double[] derivative=new double[signal.length];
        derivative[0]=(smooth[1]-smooth[0])/dt;derivative[signal.length-1]=(smooth[signal.length-1]-smooth[signal.length-2])/dt;
        for(int i=1;i<signal.length-1;i++)derivative[i]=(smooth[i+1]-smooth[i-1])/(2*dt);
        record Support(int left,int right,PeakCandidate candidate){}
        List<Support> supports=new ArrayList<>();
        for(var c:candidates)if(c.selected(p.lpnrThreshold())){
            int pad=PeakDetector.points(PeakDetector.clip(p.paddingW50()*c.w50Seconds(),p.minimumPaddingSeconds(),p.maximumPaddingSeconds()),dt,1);
            supports.add(new Support(Math.max(0,boundary(c,-1,smooth,derivative,dt,p)-pad),Math.min(signal.length-1,boundary(c,1,smooth,derivative,dt,p)+pad),c));
        }
        // Sorting supports, rather than ranks, also handles future very broad overlapping events.
        supports.sort(Comparator.comparingInt(Support::left));
        List<PeakWindow> result=new ArrayList<>();int left=-1,right=-1;List<PeakCandidate> members=new ArrayList<>();
        int gap=PeakDetector.points(p.joinGapSeconds(),dt,0);
        for(var s:supports){
            if(left>=0&&s.left()>right+gap){add(result,left,right,times,members);members=new ArrayList<>();left=-1;}
            if(left<0)left=s.left();right=Math.max(right,s.right());members.add(s.candidate());
        }
        if(left>=0)add(result,left,right,times,members);
        return List.copyOf(result);
    }
    private static void add(List<PeakWindow> result,int left,int right,double[] times,List<PeakCandidate> members){
        members.sort(Comparator.comparingInt(PeakCandidate::index));
        result.add(new PeakWindow(result.size()+1,left,right,times[left],times[right],PeakWindow.classify(members),members));
    }
    private int boundary(PeakCandidate c,int direction,double[] y,double[] dy,double dt,PeakParameters p){
        int stable=PeakDetector.points(p.geometryStableSeconds(),dt,2),start=PeakDetector.points(p.geometryStartW50()*c.w50Seconds(),dt,1);
        int reach=Math.max(start+stable+2,PeakDetector.points(PeakDetector.clip(p.geometrySearchW50()*c.w50Seconds(),p.minimumSearchSeconds(),p.maximumSearchSeconds()),dt,1));
        int stop=Math.max(0,Math.min(y.length-1,c.index()+direction*reach));
        double[] slopes=new double[Math.abs(stop-c.index())+1];int n=0;
        for(int i=Math.min(stop,c.index());i<=Math.max(stop,c.index());i++){double decay=-direction*c.polarity()*dy[i];if(decay>0)slopes[n++]=decay;}
        if(n<3)return stop;
        Arrays.sort(slopes,0,n);double position=.9*(n-1);int k=(int)position;
        double limit=p.geometrySlopeFraction()*(slopes[k]+(position-k)*(slopes[Math.min(n-1,k+1)]-slopes[k]));
        int run=0;
        for(int i=c.index()+direction*start;i>=0&&i<y.length&&(direction<0?i>=stop:i<=stop);i+=direction){
            double oriented=c.polarity()*y[i];boolean low=Math.max(oriented,0)/c.localProminence()<=p.geometryRelativeHeight()
                ||(Double.isFinite(c.localNoiseSigma())&&c.localNoiseSigma()>0&&oriented<=p.geometryBaselineSigma()*c.localNoiseSigma());
            if(low&&Math.max(-direction*c.polarity()*dy[i],0)<=limit){if(++run>=stable)return i-direction*(stable-1);}else run=0;
        }
        return stop;
    }
    /** Savitzky-Golay cubic least-squares with polynomial interpolation at edges. */
    public static double[] smoothGeometry(double[] values,int requested){
        int window=Math.min(requested,values.length%2==0?values.length-1:values.length),degree=Math.min(3,window-2),half=window/2;
        DMatrixRMaj design=new DMatrixRMaj(window,degree+1);
        for(int i=0;i<window;i++)for(int j=0;j<=degree;j++)design.set(i,j,Math.pow(i-half,j));
        DMatrixRMaj inverse=new DMatrixRMaj(degree+1,window);CommonOps_DDRM.pinv(design,inverse);
        double[] result=new double[values.length];
        for(int i=0;i<values.length;i++){
            int start=Math.max(0,Math.min(values.length-window,i-half));double x=i-start-half;
            for(int j=0;j<window;j++){double weight=0;for(int d=0;d<=degree;d++)weight+=Math.pow(x,d)*inverse.get(d,j);result[i]+=weight*values[start+j];}
        }
        return result;
    }
}
