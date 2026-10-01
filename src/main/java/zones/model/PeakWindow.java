package zones.model;
import java.util.List;
public record PeakWindow(int id,int leftIndex,int rightIndex,double startSeconds,double endSeconds,
        Type type,List<PeakCandidate> members) {
    public enum Type {ISOLATED,SAME_POLARITY_OVERLAP,BIPOLAR_COMPOSITE}
    public PeakWindow {members=List.copyOf(members);}
    public static Type classify(List<PeakCandidate> members){
        if(members.size()==1)return Type.ISOLATED;
        return members.stream().map(PeakCandidate::polarity).distinct().count()==1?Type.SAME_POLARITY_OVERLAP:Type.BIPOLAR_COMPOSITE;
    }
}
