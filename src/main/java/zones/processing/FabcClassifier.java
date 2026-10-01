package zones.processing;

import java.util.Arrays;

/** Port of pybaselines 1.2.1 Haar CWT, iterative threshold and mask refinement.
 * See THIRD-PARTY-NOTICES.md and docs/NUMERICAL_REFERENCE.md. */
public final class FabcClassifier {
    public boolean[] classify(double[] y,int scale,double numStd,int minLength) {
        int pad=2*scale, n=y.length;
        double[] padded=new double[n+2*pad];
        System.arraycopy(y,0,padded,pad,n);
        extrapolate(y,padded,pad,false); extrapolate(y,padded,pad,true);
        int length=Math.min(10*scale,padded.length);
        if(length%2!=scale%2)length++;
        double[] kernel=new double[length];
        for(int j=0;j<length;j++) {
            double x=j-(length-1)/2.0;
            if(scale%2==0) {if(x>=-scale/2.0 && x<0)kernel[j]=1; else if(x>=0 && x<scale/2.0)kernel[j]=-1;}
            else {if(x>-scale/2.0 && x<0)kernel[j]=1; else if(x>0 && x<scale/2.0)kernel[j]=-1;}
            kernel[j]/=Math.sqrt(scale);
        }
        double[] power=new double[n];
        // scipy.signal.convolve(data, haar[::-1], mode='same'), centered crop.
        int offset=(length-1)/2;
        for(int i=0;i<n;i++) {
            double value=0;
            for(int j=0;j<length;j++) {
                int index=i+pad+offset-j;
                if(index>=0 && index<padded.length && kernel[length-1-j]!=0)
                    value+=padded[index]*kernel[length-1-j];
            }
            power[i]=value*value;
            if(!Double.isFinite(power[i]))throw new IllegalArgumentException("Non-finite CWT power.");
        }
        boolean[] mask=new boolean[n]; Arrays.fill(mask,true);
        while(true) {
            int count=Statistics.count(mask);
            if(count<2)break;
            double mean=0;for(int i=0;i<n;i++)if(mask[i])mean+=power[i]; mean/=count;
            double variance=0;for(int i=0;i<n;i++)if(mask[i])variance+=(power[i]-mean)*(power[i]-mean);
            double threshold=mean+numStd*Math.sqrt(variance/(count-1));
            boolean[] next=new boolean[n];for(int i=0;i<n;i++)next[i]=power[i]<threshold;
            if(Arrays.equals(mask,next))break;
            mask=next;
        }
        return refineMask(mask,minLength);
    }
    private static void extrapolate(double[] y,double[] padded,int pad,boolean right) {
        int count=Math.min(pad,y.length), start=right?y.length-count:0;
        double center=(count-1)/2.0,mean=0;
        for(int j=0;j<count;j++)mean+=y[start+j];mean/=count;
        double covariance=0,variance=0;
        for(int j=0;j<count;j++){double x=j-center;covariance+=x*(y[start+j]-mean);variance+=x*x;}
        double slope=variance==0?0:covariance/variance;
        for(int j=0;j<pad;j++) {
            int signalIndex=right?y.length+j:j-pad;
            padded[right?pad+y.length+j:j]=mean+slope*(signalIndex-start-center);
        }
    }
    public static boolean[] refineMask(boolean[] input,int minimum) {
        int h=minimum/2,n=input.length;
        boolean[] padded=new boolean[n+2*h]; Arrays.fill(padded,true);
        System.arraycopy(input,0,padded,h,n);
        // Opening with an all-true flat element removes short runs; explicit true padding
        // preserves the asymmetric boundary behavior of scipy.ndimage.binary_opening.
        boolean[] opened=Statistics.removeShortRuns(padded,minimum);
        boolean[] result=Arrays.copyOfRange(opened,h,h+n), previous=result.clone();
        for(int i=1;i<n-1;i++)result[i]|=previous[i-1] && previous[i+1];
        return result;
    }
}
