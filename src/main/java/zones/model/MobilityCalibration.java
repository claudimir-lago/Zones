package zones.model;

import zones.processing.WaterViscosity;

/** Converts either migration charge or migration time to apparent/effective mobility at 25 °C. */
public final class MobilityCalibration {
    public enum Kind {
        APPARENT_CHARGE_INSTRUMENTAL,
        APPARENT_VOLTAGE_INSTRUMENTAL,
        EFFECTIVE_TWO_STANDARDS_CHARGE,
        EFFECTIVE_TWO_STANDARDS_TIME,
        EFFECTIVE_CHARGE_INSTRUMENTAL_REFERENCE,
        EFFECTIVE_VOLTAGE_INSTRUMENTAL_REFERENCE
    }
    public enum Basis { CHARGE, TIME }

    private final Kind kind;
    private final Basis basis;
    /** k in Ti*mC for CHARGE basis or Ti*min for TIME basis. */
    private final double k;
    private final double eof;
    private final double x1,mu1,x2,mu2;
    private final double referenceTemperatureC;

    private MobilityCalibration(Kind kind,Basis basis,double k,double eof,double x1,double mu1,double x2,double mu2,double referenceTemperatureC){
        this.kind=kind;this.basis=basis;this.k=k;this.eof=eof;this.x1=x1;this.mu1=mu1;this.x2=x2;this.mu2=mu2;this.referenceTemperatureC=referenceTemperatureC;
    }

    public static MobilityCalibration fromTwoStandards(double q1,double mu1,double q2,double mu2){
        return fromTwoStandardsCoordinate(Basis.CHARGE,Kind.EFFECTIVE_TWO_STANDARDS_CHARGE,q1,mu1,q2,mu2);
    }
    public static MobilityCalibration fromTwoStandardsByTime(double t1Minutes,double mu1,double t2Minutes,double mu2){
        return fromTwoStandardsCoordinate(Basis.TIME,Kind.EFFECTIVE_TWO_STANDARDS_TIME,t1Minutes,mu1,t2Minutes,mu2);
    }
    private static MobilityCalibration fromTwoStandardsCoordinate(Basis basis,Kind kind,double x1,double mu1,double x2,double mu2){
        finite(x1,mu1,x2,mu2);if(x1==0||x2==0||x1==x2)throw new IllegalArgumentException("Reference coordinates must be non-zero and different.");
        double den=1.0/x1-1.0/x2,k=(mu1-mu2)/den,eof=k/x1-mu1;
        if(!Double.isFinite(k)||k==0||!Double.isFinite(eof))throw new IllegalArgumentException("The two standards do not define a valid mobility scale.");
        return new MobilityCalibration(kind,basis,k,eof,x1,mu1,x2,mu2,25.0);
    }

    public static MobilityCalibration fromInstrument(double diameterMicrometers,double conductivitySPerM,double distanceMeters){
        validateChargeInstrument(diameterMicrometers,conductivitySPerM,distanceMeters);
        double r=diameterMicrometers*1e-6/2.0,A=Math.PI*r*r;double k=distanceMeters*conductivitySPerM*A*1e12;
        return new MobilityCalibration(Kind.APPARENT_CHARGE_INSTRUMENTAL,Basis.CHARGE,k,0,Double.NaN,Double.NaN,Double.NaN,Double.NaN,25.0);
    }
    public static MobilityCalibration fromVoltage(double voltageKilovolts,double totalLengthMeters,double detectorDistanceMeters,double runTemperatureC){
        finite(voltageKilovolts,totalLengthMeters,detectorDistanceMeters,runTemperatureC);
        if(voltageKilovolts==0||totalLengthMeters<=0||detectorDistanceMeters<=0)throw new IllegalArgumentException("Voltage must be non-zero and capillary/detector lengths must be positive.");
        WaterViscosity.viscosityMicroPaS(runTemperatureC);
        double voltageV=voltageKilovolts*1000.0;
        // mu(T) = LD*LT/(V*t_seconds); with t in minutes, k has Ti*min.
        double kAtRunT=detectorDistanceMeters*totalLengthMeters/voltageV*1e9/60.0;
        double k25=WaterViscosity.normalizeTo25C(kAtRunT,runTemperatureC);
        return new MobilityCalibration(Kind.APPARENT_VOLTAGE_INSTRUMENTAL,Basis.TIME,k25,0,Double.NaN,Double.NaN,Double.NaN,Double.NaN,25.0);
    }

    public static MobilityCalibration fromInstrumentWithReference(double diameterMicrometers,double conductivitySPerM,double distanceMeters,double qRef,double muEffectiveRef){
        finite(qRef,muEffectiveRef);if(qRef==0)throw new IllegalArgumentException("Reference charge must be finite and non-zero.");
        MobilityCalibration a=fromInstrument(diameterMicrometers,conductivitySPerM,distanceMeters);double eof=a.k/qRef-muEffectiveRef;
        return new MobilityCalibration(Kind.EFFECTIVE_CHARGE_INSTRUMENTAL_REFERENCE,Basis.CHARGE,a.k,eof,qRef,muEffectiveRef,Double.NaN,Double.NaN,25.0);
    }
    public static MobilityCalibration fromVoltageWithReference(double voltageKilovolts,double totalLengthMeters,double detectorDistanceMeters,double runTemperatureC,double tRefMinutes,double muEffectiveRef25){
        finite(tRefMinutes,muEffectiveRef25);if(tRefMinutes<=0)throw new IllegalArgumentException("Reference migration time must be positive.");
        MobilityCalibration a=fromVoltage(voltageKilovolts,totalLengthMeters,detectorDistanceMeters,runTemperatureC);double eof=a.k/tRefMinutes-muEffectiveRef25;
        return new MobilityCalibration(Kind.EFFECTIVE_VOLTAGE_INSTRUMENTAL_REFERENCE,Basis.TIME,a.k,eof,tRefMinutes,muEffectiveRef25,Double.NaN,Double.NaN,25.0);
    }

    public static MobilityCalibration fromTwoStandardsAtTemperatures(double q1,double mu1,double temp1C,double q2,double mu2,double temp2C){
        return fromTwoStandards(q1,WaterViscosity.normalizeTo25C(mu1,temp1C),q2,WaterViscosity.normalizeTo25C(mu2,temp2C));
    }
    public static MobilityCalibration fromTwoStandardsByTimeAtTemperatures(double t1Minutes,double mu1,double temp1C,double t2Minutes,double mu2,double temp2C){
        return fromTwoStandardsByTime(t1Minutes,WaterViscosity.normalizeTo25C(mu1,temp1C),t2Minutes,WaterViscosity.normalizeTo25C(mu2,temp2C));
    }
    public static MobilityCalibration fromInstrumentAtTemperature(double diameterMicrometers,double conductivitySPerM,double conductivityTemperatureC,double distanceMeters){
        return fromInstrument(diameterMicrometers,WaterViscosity.normalizeTo25C(conductivitySPerM,conductivityTemperatureC),distanceMeters);
    }
    public static MobilityCalibration fromInstrumentWithReferenceAtTemperatures(double diameterMicrometers,double conductivitySPerM,double conductivityTemperatureC,double distanceMeters,double qRef,double muEffectiveRef,double referenceTemperatureC){
        double kappa25=WaterViscosity.normalizeTo25C(conductivitySPerM,conductivityTemperatureC);
        double mu25=WaterViscosity.normalizeTo25C(muEffectiveRef,referenceTemperatureC);
        return fromInstrumentWithReference(diameterMicrometers,kappa25,distanceMeters,qRef,mu25);
    }
    public static MobilityCalibration fromVoltageWithReferenceAtTemperatures(double voltageKilovolts,double totalLengthMeters,double detectorDistanceMeters,double runTemperatureC,double tRefMinutes,double muEffectiveRef,double referenceTemperatureC){
        double mu25=WaterViscosity.normalizeTo25C(muEffectiveRef,referenceTemperatureC);
        return fromVoltageWithReference(voltageKilovolts,totalLengthMeters,detectorDistanceMeters,runTemperatureC,tRefMinutes,mu25);
    }

    private static void validateChargeInstrument(double d,double c,double l){finite(d,c,l);if(d<=0||c<=0||l<=0)throw new IllegalArgumentException("Diameter, conductivity and detector distance must be positive.");}
    private static void finite(double...v){for(double x:v)if(!Double.isFinite(x))throw new IllegalArgumentException("Calibration values must be finite.");}

    public double mobilityTi(double chargeMilliCoulombs){
        if(basis!=Basis.CHARGE||!Double.isFinite(chargeMilliCoulombs)||chargeMilliCoulombs==0)return Double.NaN;
        return mobilityFromCoordinate(chargeMilliCoulombs);
    }
    public double mobilityTiFromTimeMinutes(double timeMinutes){
        if(basis!=Basis.TIME||!Double.isFinite(timeMinutes)||timeMinutes<=0)return Double.NaN;
        return mobilityFromCoordinate(timeMinutes);
    }
    private double mobilityFromCoordinate(double x){double v=k/x-(isEffective()?eof:0);return Double.isFinite(v)?v:Double.NaN;}
    public double effectiveMobilityTi(double q){return mobilityTi(q);}

    public Kind kind(){return kind;} public Basis basis(){return basis;} public boolean usesCharge(){return basis==Basis.CHARGE;} public boolean usesTime(){return basis==Basis.TIME;}
    public boolean isEffective(){return switch(kind){case APPARENT_CHARGE_INSTRUMENTAL,APPARENT_VOLTAGE_INSTRUMENTAL->false;default->true;};}
    public String mobilityName(){return isEffective()?"Effective mobility":"Apparent mobility";}
    public String basisName(){return switch(kind){
        case EFFECTIVE_TWO_STANDARDS_CHARGE -> "Two standards / migration charge";
        case EFFECTIVE_TWO_STANDARDS_TIME -> "Two standards / migration time";
        default -> basis==Basis.CHARGE?"Charge / conductivity":"Voltage / capillary length";
    };}
    public double kTiMilliCoulombs(){return basis==Basis.CHARGE?k:Double.NaN;} public double kTiMinutes(){return basis==Basis.TIME?k:Double.NaN;} public double eofMobilityTi(){return eof;}
    public double charge1MilliCoulombs(){return basis==Basis.CHARGE?x1:Double.NaN;} public double mobility1Ti(){return mu1;} public double charge2MilliCoulombs(){return basis==Basis.CHARGE?x2:Double.NaN;} public double mobility2Ti(){return mu2;} public double referenceTemperatureC(){return referenceTemperatureC;}
    public double coordinate1(){return x1;} public double coordinate2(){return x2;}
    public double singularityGuardMilliCoulombs(){if(basis!=Basis.CHARGE)return 0;double q=Double.isFinite(x1)?Math.abs(x1):1;if(Double.isFinite(x2))q=Math.min(q,Math.abs(x2));return Math.max(1e-12,q*1e-4);}
    public double singularityGuardMinutes(){if(basis!=Basis.TIME)return 0;double t=Double.isFinite(x1)?Math.abs(x1):1;if(Double.isFinite(x2))t=Math.min(t,Math.abs(x2));return Math.max(1e-12,t*1e-4);}
}
