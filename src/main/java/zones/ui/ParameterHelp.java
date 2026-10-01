package zones.ui;
import java.awt.event.*;
import java.util.Map;
import javax.swing.*;
public final class ParameterHelp {
    private ParameterHelp(){}
    private static final Map<String,String> TEXT=Map.ofEntries(
        Map.entry("spikes","Selective moving-median residual (MMR) outlier detection. The median is used only to detect candidates; non-candidate samples are preserved exactly. Candidate runs above 5 robust sigma are reconstructed by a quadratic fitted to the 2 samples before and 2 after the run. Characteristic duration defines the median half-window."),
        Map.entry("lambda","log10(lambda), from 5 to 15. A value of 9 means lambda = 1e9. Higher values make the baseline stiffer; lower values make it more flexible."),
        Map.entry("numStd","FABC baseline-versus-peak classification. Range 2.0–3.5; default 3.0. Higher values tend to accept more baseline; lower values are more conservative. This is not LOD/LOQ."),
        Map.entry("width","Characteristic peak-width range in seconds used to generate multiscale FABC scales. Median dt is used for conversion to points."),
        Map.entry("count","Requested number of scales across the width range. Duplicate scales after rounding are removed."),
        Map.entry("votes","K: number of scales that must vote for a point to be considered a peak region. Must be between 1 and the effective number of scales."),
        Map.entry("minLength","Minimum duration of a baseline run in FABC, converted to points using median dt."),
        Map.entry("window","Legacy local-recovery parameter. Local anchor recovery is disabled in the validated pipeline."),
        Map.entry("noise","Legacy local-recovery parameter. Local anchor recovery is disabled in the validated pipeline."),
        Map.entry("slope","Legacy local-recovery parameter. Local anchor recovery is disabled in the validated pipeline."),
        Map.entry("run","Legacy local-recovery parameter. Local anchor recovery is disabled in the validated pipeline."),
        Map.entry("charge","Recalculate accumulated charge from the current channel after selective outlier processing using trapezoidal integration at the actual sample times. Stored charge is preserved."));
    public static void install(JComponent c,String key){String text=TEXT.get(key);if(text==null)return;c.setToolTipText(text);}
}
