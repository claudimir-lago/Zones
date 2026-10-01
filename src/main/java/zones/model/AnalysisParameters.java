package zones.model;
import java.util.Objects;
public record AnalysisParameters(PhysicalBaselineParameters baseline,SpikeRemovalParameters spikeRemoval,boolean recalculateCharge) {
    public AnalysisParameters{Objects.requireNonNull(baseline);Objects.requireNonNull(spikeRemoval);}
    public static AnalysisParameters defaults(){return new AnalysisParameters(PhysicalBaselineParameters.defaults(),SpikeRemovalParameters.defaults(),false);}
}
