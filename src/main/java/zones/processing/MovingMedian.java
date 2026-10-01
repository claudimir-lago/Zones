package zones.processing;

/** Centered 2N+1 window with endpoint replication. Never shifts or shortens the trace. */
public final class MovingMedian {
    public double[] apply(double[] signal,int halfWindow) {
        if(halfWindow<1 || 2L*halfWindow+1>signal.length)
            throw new IllegalArgumentException("The median window must contain 2N+1 points, N >= 1, and fit within the signal.");
        double[] result=new double[signal.length],window=new double[2*halfWindow+1];
        for(double value:signal)if(!Double.isFinite(value))throw new IllegalArgumentException("Non-finite signal in moving median.");
        for(int i=0;i<signal.length;i++) {
            if(Thread.currentThread().isInterrupted())throw new java.util.concurrent.CancellationException();
            for(int j=-halfWindow;j<=halfWindow;j++)window[j+halfWindow]=signal[Math.max(0,Math.min(signal.length-1,i+j))];
            result[i]=Statistics.median(window);
        }
        return result;
    }
}
