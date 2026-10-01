package zones.model;

/** Quantitative peak descriptors expressed in the currently selected x-domain. */
public record DomainPeakMetrics(double position,double observedPosition,double fittedApexPosition,
        double fwhm,double signedArea,String positionUnit,String widthUnit,String areaUnit,boolean available) {
    public static DomainPeakMetrics unavailable(String positionUnit,String widthUnit,String areaUnit){
        return new DomainPeakMetrics(Double.NaN,Double.NaN,Double.NaN,Double.NaN,Double.NaN,positionUnit,widthUnit,areaUnit,false);
    }
}
