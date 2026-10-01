package zones.model;
import java.util.List;
public record PeakFit(PeakWindow window,List<PeakComponent> components,double offset,
        boolean success,String message,int evaluations,double r2,double rms,
        double experimentalAlgebraicArea,double experimentalAbsoluteArea,
        double fittedAlgebraicArea,double absoluteComponentSum) {
    public PeakFit {components=List.copyOf(components);}
    /** Numerical reliability is not chemical identification or proof of a unique optimum. */
    public boolean reliable(){return success&&components.stream().allMatch(PeakComponent::fitReliable);}
    public double isolatedAreaDifferencePercent(){return window.type()==PeakWindow.Type.ISOLATED&&experimentalAlgebraicArea!=0?100*(fittedAlgebraicArea-experimentalAlgebraicArea)/experimentalAlgebraicArea:Double.NaN;}
}
