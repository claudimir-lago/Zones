import java.nio.file.*;
import java.util.*;
import javax.swing.*;
import java.awt.image.BufferedImage;
import javax.imageio.ImageIO;
import zones.io.*;
import zones.model.*;
import zones.processing.*;
import zones.application.*;
import zones.ui.*;

/** Distribution check: runs with the packaged runtime, not the build JDK. */
public final class PackageSmoke {
    public static void main(String[] args) throws Exception {
        Path output = Path.of(args[0]);
        var text = new StringBuilder();
        for (int i=0; i<2400; i++) {
            double t=i*0.1, y=100+0.05*t+0.1*Math.sin(i*1.71)+200*Math.exp(-0.5*Math.pow((t-100)/3,2));
            text.append(String.format(Locale.ROOT,"%.9f 0 %.9f 10 %.9f%n",t/60,y,t*0.01));
        }
        Path file=output.resolve("synthetic.dat"); Files.writeString(file,text);
        var data=new AutoDetectDatReader().read(file);
        var analysis=new AnalysisProcessor().process(data,DetectorChannel.RIGHT,AnalysisParameters.defaults());
        for(double v:analysis.baseline().correctedSignal()) if(!Double.isFinite(v)) throw new AssertionError("Non-finite baseline result");
        var peaks=new PeakAnalysisProcessor().process(analysis.baseline(),PeakParameters.defaults());
        if(peaks.fits().isEmpty()) throw new AssertionError("Synthetic peak was not fitted");
        var calibration=MobilityCalibration.fromTwoStandards(0.5,100,1,50);
        var transform=new DomainTransform(data,calibration);
        if(!Double.isFinite(transform.xAtTime(ElectropherogramDomain.MOBILITY,1))) throw new AssertionError("Mobility conversion failed");
        new PeakAnalysisExporter().write(output.resolve("results.zip"),analysis.baseline(),analysis,peaks);
        SwingUtilities.invokeAndWait(()->{
            AppTheme.setup(); var frame=new MainFrame();
            try {
                frame.displayAnalysis(analysis); frame.setSize(1360,880); frame.addNotify(); frame.validate();
                var panel=new PeakAnalysisPanel(); panel.setInput(analysis.baseline(),analysis); panel.display(peaks);
                panel.setDomain(ElectropherogramDomain.CHARGE,calibration); panel.setDomain(ElectropherogramDomain.MOBILITY,calibration);
                var image=new BufferedImage(1360,880,BufferedImage.TYPE_INT_RGB); var graphics=image.createGraphics();
                frame.getRootPane().printAll(graphics); graphics.dispose();
                var colors=new HashSet<Integer>();
                for(int x=0;x<1360;x+=10)for(int y=0;y<880;y+=10)colors.add(image.getRGB(x,y));
                if(colors.size()<10)throw new AssertionError("Swing rendering produced an empty image");
                ImageIO.write(image,"png",output.resolve("application-preview.png").toFile());
            } catch(Exception ex) {throw new RuntimeException(ex);} finally {frame.dispose();}
        });
        System.out.println("Packaged runtime passed: input, baseline, HVL fitting, mobility, ZIP export and Swing rendering.");
        System.exit(0);
    }
}
