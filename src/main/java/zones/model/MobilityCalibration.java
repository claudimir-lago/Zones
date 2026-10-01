package zones.model;

/**
 * Two-standard effective-mobility calibration following Eq. 14 of da Costa et al.
 * Input migration charge is treated as magnitude |q| in mC and mobility in Tiselius (Ti); k is therefore Ti*mC.
 */
public record MobilityCalibration(double charge1MilliCoulombs,double mobility1Ti,
        double charge2MilliCoulombs,double mobility2Ti,double kTiMilliCoulombs,double eofMobilityTi) {

    public static MobilityCalibration fromTwoStandards(double q1,double mu1,double q2,double mu2) {
        double[] values={q1,mu1,q2,mu2};
        for(double v:values)if(!Double.isFinite(v))throw new IllegalArgumentException("Calibration values must be finite.");
        q1=Math.abs(q1);q2=Math.abs(q2);
        if(q1==0 || q2==0)throw new IllegalArgumentException("Reference migration charges must be non-zero.");
        if(q1==q2)throw new IllegalArgumentException("Reference migration-charge magnitudes must be different.");
        double denominator=1.0/q1-1.0/q2;
        if(denominator==0)throw new IllegalArgumentException("Invalid reference charges.");
        double k=(mu1-mu2)/denominator;
        if(!Double.isFinite(k)||k==0)throw new IllegalArgumentException("The two standards do not define a valid mobility scale.");
        double eof=k/q1-mu1;
        if(!Double.isFinite(eof))throw new IllegalArgumentException("Invalid EOF mobility from calibration.");
        return new MobilityCalibration(q1,mu1,q2,mu2,k,eof);
    }

    /** Effective electrophoretic mobility in Ti. */
    public double effectiveMobilityTi(double chargeMilliCoulombs) {
        if(!Double.isFinite(chargeMilliCoulombs)||chargeMilliCoulombs==0)return Double.NaN;
        double value=kTiMilliCoulombs/chargeMilliCoulombs-eofMobilityTi;
        return Double.isFinite(value)?value:Double.NaN;
    }

    public double singularityGuardMilliCoulombs(){
        return Math.max(1e-12,Math.min(Math.abs(charge1MilliCoulombs),Math.abs(charge2MilliCoulombs))*1e-4);
    }
}
