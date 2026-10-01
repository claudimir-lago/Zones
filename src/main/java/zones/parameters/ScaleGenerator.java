package zones.parameters;

import java.util.TreeSet;
import zones.model.BaselineParameters;

/** Log-space interpolation of the validated scale profile, preserving its nonuniform spacing. */
public final class ScaleGenerator {
    public int[] generate(double minimumSeconds,double maximumSeconds,int count,double dtSeconds) {
        if(!Double.isFinite(minimumSeconds) || !Double.isFinite(maximumSeconds)
                || minimumSeconds<=0 || maximumSeconds<minimumSeconds || count<1 || count>64)
            throw new IllegalArgumentException("Invalid width range; the number of scales must be between 1 and 64.");
        PhysicalUnits.points(maximumSeconds,dtSeconds,1,false);
        int[] reference=BaselineParameters.defaults().scales();
        double span=Math.log((double)reference[reference.length-1]/reference[0]);
        double widthSpan=Math.log(maximumSeconds/minimumSeconds);
        TreeSet<Integer> unique=new TreeSet<>();
        for(int i=0;i<count;i++) {
            double rank=count==1?0:(double)i*(reference.length-1)/(count-1);
            int left=(int)Math.floor(rank),right=Math.min(left+1,reference.length-1);
            double position=(Math.log((double)reference[left]/reference[0])*(1-(rank-left))
                    +Math.log((double)reference[right]/reference[0])*(rank-left))/span;
            double seconds=minimumSeconds*Math.exp(position*widthSpan);
            // Avoid round-off just above the validated maximum at the last sample.
            if(i==count-1 && count>1)seconds=maximumSeconds;
            unique.add(PhysicalUnits.points(seconds,dtSeconds,1,false));
        }
        return unique.stream().mapToInt(Integer::intValue).toArray();
    }
}
