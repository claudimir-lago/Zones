package zones.ui;
import java.awt.event.*;
import java.util.Map;
import javax.swing.*;
public final class ParameterHelp {
    private ParameterHelp(){}
    private static final Map<String,String> TEXT=Map.ofEntries(
        Map.entry("spikes","Selective moving-median residual (MMR) outlier detection. The median is used only to detect candidates; non-candidate samples are preserved exactly. Candidate runs above 5 robust sigma are reconstructed by a quadratic fitted to the 2 samples before and 2 after the run. Characteristic duration defines the median half-window."),
        Map.entry("lambda","log(λ), from 5 to 15. A value of 9 means λ = 1e9. Higher values make the baseline stiffer; lower values make it more flexible."),
        Map.entry("numStd","FABC baseline-versus-peak classification. Range 2.0–3.5; default 3.0. Higher values tend to accept more baseline; lower values are more conservative. This is not LOD/LOQ."),
        Map.entry("width","Scale defines the characteristic time width of events considered compatible with a CE peak. The minimum peak width sets the first scale; larger scales progressively represent broader peak-like structures. Internally the algorithm uses samples, but Zones presents the scales in seconds."),
        Map.entry("count","Requested number of time scales spanning the minimum-to-maximum peak-width range. Each scale asks whether structures of that characteristic duration look peak-like; duplicate scales after sampling conversion are removed."),
        Map.entry("votes","K (votes) is the minimum number of scales that must agree that a point is peak-like before it is excluded from the baseline. If fewer than K scales vote peak-like, the point is accepted as baseline. K must be between 1 and the effective number of scales."),
        Map.entry("minLength","Minimum duration of a baseline run in FABC, converted to points using median dt."),
        Map.entry("window","Legacy local-recovery parameter. Local anchor recovery is disabled in the validated pipeline."),
        Map.entry("noise","Legacy local-recovery parameter. Local anchor recovery is disabled in the validated pipeline."),
        Map.entry("slope","Legacy local-recovery parameter. Local anchor recovery is disabled in the validated pipeline."),
        Map.entry("run","Legacy local-recovery parameter. Local anchor recovery is disabled in the validated pipeline."),
        Map.entry("charge","Recalculate accumulated charge from the current channel after selective outlier processing using trapezoidal integration at the actual sample times. Stored charge is preserved."));
    public static void install(JComponent c,String key){String text=TEXT.get(key);if(text==null)return;c.setToolTipText(text);}
}
