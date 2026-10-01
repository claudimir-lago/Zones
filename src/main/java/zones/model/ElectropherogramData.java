package zones.model;

import java.nio.file.Path;
import java.util.*;

/** Immutable data, with original time in minutes and explicit channel identity. */
public final class ElectropherogramData {
    private final Path source;
    private final double[] timeMinutes, currentMicroamps, chargeMilliCoulombs;
    private final EnumMap<DetectorChannel, double[]> channels = new EnumMap<>(DetectorChannel.class);
    private final double samplingIntervalSeconds;
    public ElectropherogramData(Path source, double[] timeMinutes, Map<DetectorChannel,double[]> channels,
            double[] currentMicroamps, double[] chargeMilliCoulombs) {
        this.source = source.toAbsolutePath();
        if (timeMinutes.length < 3) throw new IllegalArgumentException("At least three points are required.");
        this.timeMinutes = checked(timeMinutes, timeMinutes.length);
        double[] intervals = new double[timeMinutes.length - 1];
        for (int i=1; i<timeMinutes.length; i++) {
            intervals[i-1] = (timeMinutes[i]-timeMinutes[i-1])*60;
            if (!(intervals[i-1] > 0)) throw new IllegalArgumentException("Time must be strictly increasing.");
        }
        Arrays.sort(intervals);
        int m=intervals.length/2;
        samplingIntervalSeconds = intervals.length%2==0 ? (intervals[m-1]+intervals[m])/2 : intervals[m];
        channels.forEach((key,value) -> this.channels.put(key, checked(value, timeMinutes.length)));
        if (channels.isEmpty()) throw new IllegalArgumentException("No C4D channel was found.");
        this.currentMicroamps = currentMicroamps == null ? null : checked(currentMicroamps, timeMinutes.length);
        this.chargeMilliCoulombs = chargeMilliCoulombs == null ? null : checked(chargeMilliCoulombs, timeMinutes.length);
    }
    private static double[] checked(double[] a,int n) {
        if (a.length!=n || Arrays.stream(a).anyMatch(v -> !Double.isFinite(v)))
            throw new IllegalArgumentException("Column has an invalid length or non-finite values.");
        return a.clone();
    }
    public Path source() { return source; }
    public int size() { return timeMinutes.length; }
    public double[] timeMinutes() { return timeMinutes.clone(); }
    public Set<DetectorChannel> detectors() { return Collections.unmodifiableSet(channels.keySet()); }
    public double[] signal(DetectorChannel channel) {
        if (!channels.containsKey(channel)) throw new IllegalArgumentException("Detector is unavailable.");
        return channels.get(channel).clone();
    }
    public boolean isConstant(DetectorChannel channel) {
        double[] y=channels.get(channel);
        return Arrays.stream(y).allMatch(v -> v==y[0]);
    }
    public double samplingIntervalSeconds() { return samplingIntervalSeconds; }
    public double samplingRateHz() { return 1/samplingIntervalSeconds; }
    public Optional<double[]> currentMicroamps() { return Optional.ofNullable(currentMicroamps).map(double[]::clone); }
    public ElectropherogramData slice(double startMinute,double endMinute){
        if(!Double.isFinite(startMinute)||!Double.isFinite(endMinute)||endMinute<=startMinute)throw new IllegalArgumentException("Invalid analysis range.");
        int lo=0,hi=timeMinutes.length-1;while(lo<timeMinutes.length&&timeMinutes[lo]<startMinute)lo++;while(hi>=0&&timeMinutes[hi]>endMinute)hi--;
        if(hi-lo+1<5)throw new IllegalArgumentException("Analysis range must contain at least five samples.");
        double[] tt=Arrays.copyOfRange(timeMinutes,lo,hi+1);var cc=new EnumMap<DetectorChannel,double[]>(DetectorChannel.class);for(var e:channels.entrySet())cc.put(e.getKey(),Arrays.copyOfRange(e.getValue(),lo,hi+1));
        double[] cur=currentMicroamps==null?null:Arrays.copyOfRange(currentMicroamps,lo,hi+1),chg=chargeMilliCoulombs==null?null:Arrays.copyOfRange(chargeMilliCoulombs,lo,hi+1);
        return new ElectropherogramData(source,tt,cc,cur,chg);
    }
    public Optional<double[]> chargeMilliCoulombs() { return Optional.ofNullable(chargeMilliCoulombs).map(double[]::clone); }
}
