package zones.application;

import zones.model.*;
import zones.processing.*;

/** Orchestrates the authorized preprocessing; the existing numerical engine stays unchanged. */
public final class AnalysisProcessor {
    public AnalysisResult process(ElectropherogramData raw,DetectorChannel detector,AnalysisParameters parameters) {
        var internal=parameters.baseline().toInternal(raw.samplingIntervalSeconds());
        var preprocessed=new SignalPreprocessor().process(raw,parameters.spikeRemoval(),parameters.recalculateCharge());
        var baseline=new BaselineProcessor().process(preprocessed.cleaned(),detector,internal);
        return new AnalysisResult(preprocessed,baseline,parameters);
    }
}
