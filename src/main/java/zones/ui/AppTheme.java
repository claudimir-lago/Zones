package zones.ui;
import com.formdev.flatlaf.FlatLaf;
import com.formdev.flatlaf.FlatLightLaf;
import javax.swing.JDialog;
import javax.swing.JFrame;

public final class AppTheme {
    private AppTheme() {}
    public static void setup() {
        // Swing/FlatLaf decorations allow the domain color to include the title bar.
        JFrame.setDefaultLookAndFeelDecorated(true);
        JDialog.setDefaultLookAndFeelDecorated(true);
        FlatLaf.registerCustomDefaultsSource("zones.theme");
        if(!FlatLightLaf.setup())throw new IllegalStateException("Could not initialize FlatLaf.");
    }
}
