package zones.core;

import java.util.Arrays;
import zones.application.AnalysisProcessor;
import zones.application.PeakAnalysisProcessor;
import zones.model.*;
import zones.processing.DomainPeakCalculator;

/**
 * UI-independent façade over the scientific processing pipeline.
 * The Swing frontend and future CLI/batch frontend should use this class rather than
 * instantiate processing steps directly.
 */
public final class ZonesEngine {
    private final AnalysisProcessor analysisProcessor = new AnalysisProcessor();
    private final PeakAnalysisProcessor peakProcessor = new PeakAnalysisProcessor();

    public AnalysisResult analyze(ElectropherogramData data, DetectorChannel detector, AnalysisParameters parameters) {
        return analysisProcessor.process(data, detector, parameters);
    }

    public PeakAnalysisResult analyzePeaks(BaselineResult baseline, PeakParameters parameters) {
        return peakProcessor.process(baseline, parameters);
    }

    /** One-call entry point intended for future CLI/batch processing. */
    public RunAnalysis analyzeRun(ElectropherogramData data, DetectorChannel detector, AnalysisParameters analysisParameters, PeakParameters peakParameters) {
        AnalysisResult analysis = analyze(data, detector, analysisParameters);
        PeakAnalysisResult peaks = analyzePeaks(analysis.baseline(), peakParameters);
        return new RunAnalysis(analysis, peaks);
    }

    public PeakAnalysisResult refitWindowComponentCount(BaselineResult baseline, PeakAnalysisResult current, int windowId, int count) {
        double[] times = Arrays.stream(baseline.data().timeMinutes()).map(v -> v * 60.0).toArray();
        return peakProcessor.refitWindowComponentCount(times, baseline.correctedSignal(), current, windowId, count);
    }

    public DomainPeakMetrics peakMetrics(ElectropherogramData data, ElectropherogramDomain domain,
            MobilityCalibration calibration, boolean invertCharge, PeakComponent component) {
        return new DomainPeakCalculator(data, domain, calibration, invertCharge).metrics(component);
    }
}
