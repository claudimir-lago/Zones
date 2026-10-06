package zones.ui;

import java.awt.*;
import javax.swing.*;
import javax.swing.border.TitledBorder;
import zones.model.ElectropherogramDomain;

/** Contextual domain tint: neutral/blue=time, amber=charge, violet=mobility. */
public final class DomainTheme {
    private DomainTheme() {}

    /** Mark an application-owned push button as eligible for domain tinting.
     *  Internal Look & Feel buttons (spinner arrows, combo arrows, scrollbars)
     *  are deliberately left unmarked and therefore untouched. */
    public static <T extends AbstractButton> T themeButton(T button){
        button.putClientProperty("zones.themeButton",Boolean.TRUE);
        return button;
    }

    public static Color background(ElectropherogramDomain domain){return switch(domain){
        case TIME -> new Color(244,246,250);
        case CHARGE -> new Color(255,247,237);
        case MOBILITY -> new Color(250,245,255);
    };}

    public static Color accent(ElectropherogramDomain domain){return switch(domain){
        case TIME -> new Color(37,99,235);
        case CHARGE -> new Color(217,119,6);
        case MOBILITY -> new Color(126,34,206);
    };}

    /** Apply the active-domain cue to the whole Swing window, including FlatLaf title bars. */
    public static void apply(Window window,ElectropherogramDomain domain){
        Color bg=background(domain),accent=accent(domain);
        if(window instanceof RootPaneContainer rpc){
            JRootPane root=rpc.getRootPane();
            root.putClientProperty("JRootPane.titleBarBackground",accent);
            root.putClientProperty("JRootPane.titleBarForeground",Color.WHITE);
            Container content=rpc.getContentPane();
            tint(content,bg,accent);
            content.setBackground(bg);
        }
        window.repaint();
    }

    /** Apply a domain tint to a standalone component tree (e.g. About content). */
    public static void applyToComponent(Component component,ElectropherogramDomain domain){
        tint(component,background(domain),accent(domain));
        component.repaint();
    }

    private static void tint(Component c,Color bg,Color accent){
        if(c instanceof JPanel p)p.setBackground(bg);
        if(c instanceof JViewport viewport)viewport.setBackground(readoutBackground(bg));
        if(c instanceof JTextArea area && !area.isEditable()){
            area.setBackground(readoutBackground(bg));
            area.setCaretColor(accent.darker());
        }
        if(c instanceof JToggleButton b && Boolean.TRUE.equals(b.getClientProperty("zones.domainButton"))){
            b.setBackground(b.isSelected()?accent:bg);
            b.setForeground(b.isSelected()?Color.WHITE:UIManager.getColor("Label.foreground"));
            b.setOpaque(true);
        } else if(c instanceof JButton b && Boolean.TRUE.equals(b.getClientProperty("zones.themeButton"))){
            Color buttonBg=blend(bg,accent,0.22);
            b.setBackground(buttonBg);
            b.setForeground(UIManager.getColor("Button.foreground"));
            b.setOpaque(true);
        }
        if(c instanceof JComponent jc && jc.getBorder() instanceof TitledBorder tb)tb.setTitleColor(accent.darker());
        if(c instanceof Container ct)for(Component child:ct.getComponents())tint(child,bg,accent);
    }

    private static Color blend(Color base,Color accent,double fraction){
        double f=Math.max(0,Math.min(1,fraction));
        return new Color(
                (int)Math.round(base.getRed()*(1-f)+accent.getRed()*f),
                (int)Math.round(base.getGreen()*(1-f)+accent.getGreen()*f),
                (int)Math.round(base.getBlue()*(1-f)+accent.getBlue()*f));
    }

    private static Color readoutBackground(Color bg){
        // Keep diagnostics legible while making the active domain unmistakable.
        return new Color(
                (bg.getRed()*3+255)/4,
                (bg.getGreen()*3+255)/4,
                (bg.getBlue()*3+255)/4);
    }
}
