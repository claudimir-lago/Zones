package zones.model;
import zones.parameters.PhysicalUnits;
public record SpikeRemovalParameters(boolean enabled,double durationSeconds) {
    public SpikeRemovalParameters {
        if(!Double.isFinite(durationSeconds) || durationSeconds<=0)throw new IllegalArgumentException("The characteristic outlier duration must be positive.");
    }
    public static SpikeRemovalParameters defaults(){return new SpikeRemovalParameters(true,0.20);}
    public int halfWindow(double dtSeconds){return PhysicalUnits.points(durationSeconds,dtSeconds,1,false);}
    public int windowPoints(double dtSeconds){return 2*halfWindow(dtSeconds)+1;}
}
