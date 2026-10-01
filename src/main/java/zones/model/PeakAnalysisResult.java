package zones.model;
import java.util.List;
public record PeakAnalysisResult(List<PeakCandidate> candidates,List<PeakFit> fits,PeakParameters parametersUsed) {
    public static final String ALGORITHM_VERSION="local-prominence-lpnr-hvl-apex-multistart-v2";
    public PeakAnalysisResult {candidates=List.copyOf(candidates);fits=List.copyOf(fits);}
    public long selectedCount(){return candidates.stream().filter(c->c.selected(parametersUsed.lpnrThreshold())).count();}
}
