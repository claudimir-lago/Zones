package zones.model;
/** A peak selected by the user as a mobility calibration reference. */
public record MobilityReference(double migrationChargeMilliCoulombs,double migrationTimeMinutes,double effectiveMobilityTi,String label,double sourceTemperatureC) {
    public MobilityReference(double migrationChargeMilliCoulombs,double effectiveMobilityTi,String label,double sourceTemperatureC){this(migrationChargeMilliCoulombs,Double.NaN,effectiveMobilityTi,label,sourceTemperatureC);}
    public MobilityReference(double migrationChargeMilliCoulombs,double effectiveMobilityTi,String label){this(migrationChargeMilliCoulombs,Double.NaN,effectiveMobilityTi,label,25.0);}
}
