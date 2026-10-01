package zones.model;

import java.util.Arrays;

/** Scientific defaults from test23.py; index-based scales, without time adaptation. */
public record BaselineParameters(double numStd, int minLength, int voteThresholdK,
        int[] scales, int dilation, double lambda, int localWindow,
        double noiseFactor, double slopeFactor, int minimumRun) {
    public BaselineParameters {
        if (scales == null || scales.length == 0) throw new IllegalArgumentException("Provide at least one scale.");
        scales = scales.clone();
        if (Arrays.stream(scales).anyMatch(s -> s < 1 || s > 100000)
                || Arrays.stream(scales).distinct().count() != scales.length)
            throw new IllegalArgumentException("Scales must be distinct integers between 1 and 100000.");
        if (!Double.isFinite(numStd) || numStd <= 0 || minLength < 1
                || voteThresholdK < 1 || voteThresholdK > scales.length
                || !Double.isFinite(lambda) || lambda <= 0
                || localWindow < 3 || localWindow % 2 == 0 || minimumRun < 1
                || !Double.isFinite(noiseFactor) || noiseFactor < 0
                || !Double.isFinite(slopeFactor) || slopeFactor < 0)
            throw new IllegalArgumentException("Invalid parameters: odd window >= 3, finite factors, and K between 1 and the number of scales.");
        if (dilation != 0) throw new IllegalArgumentException("The current reference requires dilation = 0.");
    }
    @Override public int[] scales() { return scales.clone(); }
    public static BaselineParameters defaults() {
        return new BaselineParameters(3.0, 10, 2, new int[]{24,47,71,94,132,188,264,377}, 0, 1e9, 21, 1.5, 1.5, 5);
    }
    @Override public String toString() {
        return "numStd=" + numStd + ", minLength=" + minLength + ", K=" + voteThresholdK
                + ", scales=" + Arrays.toString(scales) + ", dilation=" + dilation + ", lambda=" + lambda
                + ", window=" + localWindow + ", noiseFactor=" + noiseFactor
                + ", slopeFactor=" + slopeFactor + ", minimumRun=" + minimumRun;
    }
}
