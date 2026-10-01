package zones.processing;
import java.util.Arrays;
public final class Statistics {
    private Statistics() {}
    public static double median(double[] values) {
        if(values.length==0) throw new IllegalArgumentException("Median requires at least one value.");
        double[] sorted=values.clone(); Arrays.sort(sorted); int m=sorted.length/2;
        return sorted.length%2==0 ? (sorted[m-1]+sorted[m])/2 : sorted[m];
    }
    public static double mad(double[] values) {
        double center=median(values); double[] deviations=new double[values.length];
        for(int i=0;i<values.length;i++) deviations[i]=Math.abs(values[i]-center);
        return median(deviations);
    }
    public static double noise(double[] signal) {
        double[] differences=new double[signal.length-1];
        for(int i=0;i<differences.length;i++) differences[i]=signal[i+1]-signal[i];
        return mad(differences)/(0.6745*Math.sqrt(2));
    }
    public static int count(boolean[] mask) { int n=0; for(boolean b:mask) if(b)n++; return n; }
    public static boolean[] removeShortRuns(boolean[] mask,int minimum) {
        boolean[] result=mask.clone(); int start=-1;
        for(int i=0;i<=mask.length;i++) {
            boolean value=i<mask.length && mask[i];
            if(value && start<0) start=i;
            else if(!value && start>=0) { if(i-start<minimum) Arrays.fill(result,start,i,false); start=-1; }
        }
        return result;
    }
}
