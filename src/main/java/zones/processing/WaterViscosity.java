package zones.processing;

/** Water viscosity correlation used for thermal normalization of aqueous CE data. */
public final class WaterViscosity {
    public static final double STANDARD_TEMPERATURE_C = 25.0;
    private static final double[] A = {280.68, 511.45, 61.131, 0.459}; // microPa s
    private static final double[] B = {-1.9, -7.7, -19.6, -40.0};
    private WaterViscosity() {}

    /** Dynamic viscosity of water at 0.1 MPa, in microPa s. */
    public static double viscosityMicroPaS(double temperatureC) {
        if (!Double.isFinite(temperatureC) || temperatureC < 0 || temperatureC > 100)
            throw new IllegalArgumentException("Water-viscosity normalization supports 0 to 100 °C.");
        double t = temperatureC + 273.15;
        double x = t / 300.0;
        double eta = 0.0;
        for (int i = 0; i < A.length; i++) eta += A[i] * Math.pow(x, B[i]);
        return eta;
    }

    /** For properties approximately proportional to inverse viscosity (mobility, aqueous conductivity). */
    public static double normalizeInverseViscosityProperty(double value, double sourceTemperatureC, double targetTemperatureC) {
        if (!Double.isFinite(value)) throw new IllegalArgumentException("Value must be finite.");
        return value * viscosityMicroPaS(sourceTemperatureC) / viscosityMicroPaS(targetTemperatureC);
    }

    public static double normalizeTo25C(double value, double sourceTemperatureC) {
        return normalizeInverseViscosityProperty(value, sourceTemperatureC, STANDARD_TEMPERATURE_C);
    }
}
