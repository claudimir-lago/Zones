package zones.processing;

/** Cumulative charge only, not peak integration. Uses actual intervals and starts at zero. */
public final class ChargeCalculator {
    public double[] fromCurrent(double[] timeMinutes,double[] currentMicroamps) {
        if(timeMinutes.length!=currentMicroamps.length || timeMinutes.length<2)
            throw new IllegalArgumentException("Time and current must have equal lengths and at least two points.");
        double[] charge=new double[timeMinutes.length];
        for(int i=0;i<charge.length;i++) {
            if(!Double.isFinite(timeMinutes[i]) || !Double.isFinite(currentMicroamps[i]))throw new IllegalArgumentException("Non-finite time or current value.");
            if(i==0)continue;
            double dt=(timeMinutes[i]-timeMinutes[i-1])*60;
            if(dt<=0)throw new IllegalArgumentException("Time must be increasing to recalculate charge.");
            // microampere * second = microcoulomb; /1000 converts to millicoulomb.
            charge[i]=charge[i-1]+(currentMicroamps[i-1]+currentMicroamps[i])*0.5*dt/1000;
            if(!Double.isFinite(charge[i]))throw new IllegalArgumentException("Recalculated charge is non-finite.");
        }
        return charge;
    }
}
