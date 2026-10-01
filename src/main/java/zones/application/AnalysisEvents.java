package zones.application;
import zones.model.AnalysisResult;

/** UI/session notifications only. Edits and completed computations are not acceptance or ML labels.
 * Future recording must add explicit opt-in and acceptance, outside the numerical engine. */
public interface AnalysisEvents {
    AnalysisEvents NONE=new AnalysisEvents() {};
    default void parametersEdited() {}
    default void analysisCompleted(AnalysisResult result) {}
}
