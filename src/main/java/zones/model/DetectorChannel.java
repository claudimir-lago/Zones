package zones.model;

public enum DetectorChannel {
    LEFT("C4D left"), RIGHT("C4D right");
    private final String label;
    DetectorChannel(String label) { this.label = label; }
    @Override public String toString() { return label; }
}
