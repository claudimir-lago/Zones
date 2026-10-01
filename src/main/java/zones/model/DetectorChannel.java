package zones.model;

public enum DetectorChannel {
    LEFT("1st C4D"), RIGHT("2nd C4D");
    private final String label;
    DetectorChannel(String label) { this.label = label; }
    @Override public String toString() { return label; }
}
