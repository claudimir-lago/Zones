package zones.model;
/** A peak selected by the user as a mobility calibration reference. */
public record MobilityReference(double migrationChargeMilliCoulombs,double effectiveMobilityTi,String label) {}
