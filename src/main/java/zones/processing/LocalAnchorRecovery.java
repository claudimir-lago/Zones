package zones.processing;
import java.util.Arrays;
import zones.model.BaselineParameters;
public final class LocalAnchorRecovery {
    public record Recovery(double[] noise, double[] slope, boolean[] mask) {}
    public Recovery recover(double[] y, boolean[] highConfidence, double sigma, BaselineParameters p) {
        int n=y.length, h=p.localWindow()/2;
        double[] noise=new double[n], slope=new double[n];
        Arrays.fill(noise,Double.NaN); Arrays.fill(slope,Double.NaN);
        boolean[] candidate=new boolean[n];
        for(int i=h;i<n-h;i++) {
            double[] region=Arrays.copyOfRange(y,i-h,i+h+1);
            noise[i]=Statistics.mad(region)/0.6745;
            double left=0,right=0;
            for(int j=0;j<h;j++) {left+=region[j];right+=region[h+1+j];}
            slope[i]=right/h-left/h;
            candidate[i]=!highConfidence[i] && Double.isFinite(noise[i]) && Double.isFinite(slope[i])
                    && noise[i]<=p.noiseFactor()*sigma && Math.abs(slope[i])<=p.slopeFactor()*sigma;
        }
        return new Recovery(noise,slope,Statistics.removeShortRuns(candidate,p.minimumRun()));
    }
}
