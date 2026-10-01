package zones.model;

/** X-axis representation. Scientific preprocessing and peak detection remain in time domain. */
public enum ElectropherogramDomain {
    TIME("Time", "Migration time (min)"),
    CHARGE("Charge", "Charge (mC)"),
    MOBILITY("Mobility", "Mobility (Ti)");
    private final String displayName; private final String axisLabel;
    ElectropherogramDomain(String displayName,String axisLabel){this.displayName=displayName;this.axisLabel=axisLabel;}
    public String displayName(){return displayName;} public String axisLabel(){return axisLabel;}
    @Override public String toString(){return displayName;}
}
