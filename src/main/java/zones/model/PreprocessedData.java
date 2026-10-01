package zones.model;
import java.util.Optional;

/** Original imported data and a distinct cleaned representation; stored charge is never replaced. */
public final class PreprocessedData {
    private final ElectropherogramData raw,cleaned;
    private final SpikeRemovalParameters parameters;
    private final int halfWindow;
    private final double[] recalculatedCharge;
    public PreprocessedData(ElectropherogramData raw,ElectropherogramData cleaned,SpikeRemovalParameters parameters,int halfWindow,double[] recalculatedCharge) {
        this.raw=raw;this.cleaned=cleaned;this.parameters=parameters;this.halfWindow=halfWindow;
        this.recalculatedCharge=recalculatedCharge==null?null:recalculatedCharge.clone();
    }
    public ElectropherogramData raw(){return raw;}
    public ElectropherogramData cleaned(){return cleaned;}
    public SpikeRemovalParameters parametersUsed(){return parameters;}
    public int halfWindow(){return halfWindow;}
    public int windowPoints(){return parameters.enabled()?2*halfWindow+1:1;}
    public Optional<double[]> recalculatedChargeMilliCoulombs(){return Optional.ofNullable(recalculatedCharge).map(double[]::clone);}
}
