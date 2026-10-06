package zones.core;

import zones.model.AnalysisResult;
import zones.model.PeakAnalysisResult;

/** Complete processing result for one electrophoretic run, independent of any UI. */
public record RunAnalysis(AnalysisResult analysis, PeakAnalysisResult peaks) {}
