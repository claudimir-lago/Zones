package zones.model;
public record PeakComponent(PeakCandidate candidate,int parentWindowId,double magnitude,
        double fittedCenterSeconds,double fittedApexSeconds,double fittedApexSignal,
        double sigmaSeconds,double alpha,double signedArea,double areaWithinWindow,
        double capturedFraction,boolean fitReliable,boolean parameterAtBound,
        double a3Seconds,double effectiveVarianceSeconds2,double fwhmSeconds,
        double effectivePlates,double gaussianPlates) {
    public double absoluteArea(){return Math.abs(signedArea);}
    public double centerMinusObservedSeconds(){return fittedCenterSeconds-candidate.observedApexSeconds();}
    public double fittedApexMinusObservedSeconds(){return fittedApexSeconds-candidate.observedApexSeconds();}
    public double fittedApexMinusCenterSeconds(){return fittedApexSeconds-fittedCenterSeconds;}
    /** HVL aliases retained without breaking older UI/export code: center=a1, sigma=a2, alpha=eta. */
    public double a1Seconds(){return fittedCenterSeconds;}
    public double a2Seconds(){return sigmaSeconds;}
    public double eta(){return alpha;}
}
