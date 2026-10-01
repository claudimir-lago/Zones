package zones.model;

/** Extension point for later analysis of the corrected signal, without changing BaselineResult. */
public record AnalysisResult(PreprocessedData preprocessing,BaselineResult baseline,AnalysisParameters parametersUsed) {
    public static final String PIPELINE_VERSION="mmr-5sigma-quadratic22+fabc-multiscale+hvl-v2";
    public double[] correctedSignal(){return baseline.correctedSignal();}
}
