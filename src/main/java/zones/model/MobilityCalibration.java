package zones.model;

/** Converts signed migration charge (mC) to apparent or effective mobility (Ti). */
public final class MobilityCalibration {
    public enum Kind { APPARENT_INSTRUMENTAL, EFFECTIVE_TWO_STANDARDS, EFFECTIVE_INSTRUMENTAL_REFERENCE }
    private final Kind kind; private final double k; private final double eof;
    private final double q1,mu1,q2,mu2;
    private MobilityCalibration(Kind kind,double k,double eof,double q1,double mu1,double q2,double mu2){this.kind=kind;this.k=k;this.eof=eof;this.q1=q1;this.mu1=mu1;this.q2=q2;this.mu2=mu2;}
    public static MobilityCalibration fromTwoStandards(double q1,double mu1,double q2,double mu2){
        finite(q1,mu1,q2,mu2); if(q1==0||q2==0||q1==q2)throw new IllegalArgumentException("Reference migration charges must be non-zero and different.");
        double den=1.0/q1-1.0/q2,k=(mu1-mu2)/den,eof=k/q1-mu1;
        if(!Double.isFinite(k)||k==0||!Double.isFinite(eof))throw new IllegalArgumentException("The two standards do not define a valid mobility scale.");
        return new MobilityCalibration(Kind.EFFECTIVE_TWO_STANDARDS,k,eof,q1,mu1,q2,mu2);
    }
    public static MobilityCalibration fromInstrument(double diameterMicrometers,double conductivitySPerM,double distanceMeters){
        validateInstrument(diameterMicrometers,conductivitySPerM,distanceMeters); double r=diameterMicrometers*1e-6/2.0,A=Math.PI*r*r; double k=distanceMeters*conductivitySPerM*A*1e12;
        return new MobilityCalibration(Kind.APPARENT_INSTRUMENTAL,k,0,Double.NaN,Double.NaN,Double.NaN,Double.NaN);
    }
    public static MobilityCalibration fromInstrumentWithReference(double diameterMicrometers,double conductivitySPerM,double distanceMeters,double qRef,double muEffectiveRef){
        if(qRef==0||!Double.isFinite(qRef)||!Double.isFinite(muEffectiveRef))throw new IllegalArgumentException("Reference charge must be finite and non-zero.");
        MobilityCalibration a=fromInstrument(diameterMicrometers,conductivitySPerM,distanceMeters); double eof=a.k/qRef-muEffectiveRef;
        return new MobilityCalibration(Kind.EFFECTIVE_INSTRUMENTAL_REFERENCE,a.k,eof,qRef,muEffectiveRef,Double.NaN,Double.NaN);
    }
    private static void validateInstrument(double d,double c,double l){finite(d,c,l);if(d<=0||c<=0||l<=0)throw new IllegalArgumentException("Diameter, conductivity and detector distance must be positive.");}
    private static void finite(double...v){for(double x:v)if(!Double.isFinite(x))throw new IllegalArgumentException("Calibration values must be finite.");}
    public double mobilityTi(double chargeMilliCoulombs){if(!Double.isFinite(chargeMilliCoulombs)||chargeMilliCoulombs==0)return Double.NaN;double v=k/chargeMilliCoulombs-(isEffective()?eof:0);return Double.isFinite(v)?v:Double.NaN;}
    public double effectiveMobilityTi(double q){return mobilityTi(q);}
    public Kind kind(){return kind;} public boolean isEffective(){return kind!=Kind.APPARENT_INSTRUMENTAL;} public String mobilityName(){return isEffective()?"Effective mobility":"Apparent mobility";}
    public double kTiMilliCoulombs(){return k;} public double eofMobilityTi(){return eof;}
    public double charge1MilliCoulombs(){return q1;} public double mobility1Ti(){return mu1;} public double charge2MilliCoulombs(){return q2;} public double mobility2Ti(){return mu2;}
    public double singularityGuardMilliCoulombs(){double q=Double.isFinite(q1)?Math.abs(q1):1; if(Double.isFinite(q2))q=Math.min(q,Math.abs(q2)); return Math.max(1e-12,q*1e-4);}
}
