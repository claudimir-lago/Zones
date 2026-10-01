package zones;
import javax.swing.SwingUtilities;
import zones.ui.*;
public final class Main {
    private Main() {}
    public static void main(String[] args) {
        SwingUtilities.invokeLater(()->{
            AppTheme.setup();MainFrame frame=new MainFrame();frame.setVisible(true);
            if(args.length>0)frame.loadFile(java.nio.file.Path.of(args[0]));
        });
    }
}
