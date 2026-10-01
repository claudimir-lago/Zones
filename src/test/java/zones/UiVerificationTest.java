package zones;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.nio.file.*;
import java.lang.reflect.Field;
import java.util.*;
import javax.imageio.ImageIO;
import javax.swing.*;
import javax.xml.parsers.DocumentBuilderFactory;
import zones.ui.*;
import zones.io.*;
import zones.model.*;
import zones.processing.*;
import zones.application.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class UiVerificationTest {
    @Test void netbeansFormsDescribeEveryDeclaredControl() throws Exception {
        for(Class<?> type:new Class<?>[]{MainFrame.class,ParameterPanel.class,PeakAnalysisPanel.class}) {
            var factory=DocumentBuilderFactory.newInstance();factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl",true);
            var document=factory.newDocumentBuilder().parse(Path.of("src/main/java/zones/ui/"+type.getSimpleName()+".form").toFile());
            var all=document.getElementsByTagName("*");Set<String> names=new HashSet<>();
            for(int i=0;i<all.getLength();i++) {
                var item=all.item(i);if(!item.getNodeName().equals("Component") && !item.getNodeName().equals("Container"))continue;
                String name=item.getAttributes().getNamedItem("name").getNodeValue();
                assertTrue(names.add(name));Field field=type.getDeclaredField(name);
                assertEquals(item.getAttributes().getNamedItem("class").getNodeValue(),field.getType().getName());
            }
            assertFalse(names.isEmpty());
            String java=Files.readString(Path.of("src/main/java/zones/ui/"+type.getSimpleName()+".java"));
            assertTrue(java.contains("//GEN-BEGIN:initComponents"));assertTrue(java.contains("//GEN-BEGIN:variables"));
        }
    }
    @Test void renderAndExerciseUi() throws Exception {
        // Opt-in: instantiate hidden windows for visual QA, never capture the user's desktop.
        org.junit.jupiter.api.Assumptions.assumeTrue(Boolean.getBoolean("zones.verifyUi"));
        var data=new MinimalisticeDatReader().read(Path.of("BAH112026.09.15_13h13m57s.dat"));
        var result=new AnalysisProcessor().process(data,DetectorChannel.RIGHT,AnalysisParameters.defaults());
        SwingUtilities.invokeAndWait(()->{
            AppTheme.setup();MainFrame frame=new MainFrame();
            try {
                frame.displayAnalysis(result);frame.setSize(1360,880);frame.validate();
                save(frame,"preview.png");
                JCheckBox high=find(frame,"highCheck",JCheckBox.class);high.doClick();assertFalse(high.isSelected());high.doClick();
                JCheckBox corrected=find(frame,"correctedCheck",JCheckBox.class);corrected.doClick();assertFalse(corrected.isSelected());corrected.doClick();
                find(frame,"resetZoomButton",JButton.class).doClick();
                var chart=find(frame,"chart",zones.chart.ElectropherogramChart.class);
                var axis=find(chart,"timeAxis",org.jfree.chart.axis.NumberAxis.class);
                axis.setRange(2,4);frame.displayAnalysis(result);assertEquals(2,axis.getLowerBound());assertEquals(4,axis.getUpperBound());
                find(frame,"resetZoomButton",JButton.class).doClick();
                find(frame,"parametersCheck",JCheckBox.class).doClick();assertFalse(find(frame,"parameterPanel",ParameterPanel.class).isVisible());
                frame.validate();save(frame,"preview-expanded.png");
                JComboBox<?> detectors=find(frame,"detectorCombo",JComboBox.class);detectors.setSelectedItem(DetectorChannel.LEFT);
                assertFalse(find(frame,"processButton",JButton.class).isEnabled());
                detectors.setSelectedItem(DetectorChannel.RIGHT);assertTrue(find(frame,"processButton",JButton.class).isEnabled());
                assertEquals(3.0,find(frame,"parameterPanel",ParameterPanel.class).parameters().numStd());
            }finally{frame.dispose();}
        });
    }
    @Test void boundedControlsRegenerateScalesAndSynchronizeLambda() throws Exception {
        SwingUtilities.invokeAndWait(()->{
            ParameterPanel parameters=new ParameterPanel();parameters.setSamplingInterval(0.1);
            find(parameters,"logLambdaSpinner",JSpinner.class).setValue(10.2);
            assertEquals(102,find(parameters,"lambdaSlider",JSlider.class).getValue());
            find(parameters,"lambdaSlider",JSlider.class).setValue(88);
            assertEquals(8.8,((Number)find(parameters,"logLambdaSpinner",JSpinner.class).getValue()).doubleValue());
            find(parameters,"widthMinimumSpinner",JSpinner.class).setValue(0.01);
            find(parameters,"widthMaximumSpinner",JSpinner.class).setValue(0.04);
            assertEquals(1,parameters.parameters().scales().length);assertEquals(1,parameters.parameters().voteThresholdK());
            assertEquals(1,((SpinnerNumberModel)find(parameters,"votesSpinner",JSpinner.class).getModel()).getMaximum());
            assertTrue(find(parameters,"conversionStatus",JLabel.class).getText().contains("K adjusted"));
            find(parameters,"resetButton",JButton.class).doClick();
            assertEquals(2,parameters.parameters().voteThresholdK());assertEquals(1e9,parameters.parameters().lambda());
            assertTrue(parameters.analysisParameters().spikeRemoval().enabled());
            assertFalse(parameters.analysisParameters().recalculateCharge());
        });
    }
    @Test void fileSelectionAndRecalculationRunThroughWorkers() throws Exception {
        org.junit.jupiter.api.Assumptions.assumeTrue(Boolean.getBoolean("zones.verifyUi"));
        MainFrame[] holder=new MainFrame[1];
        SwingUtilities.invokeAndWait(()->{AppTheme.setup();holder[0]=new MainFrame();holder[0].loadFile(Path.of("BAH112026.09.15_13h13m57s.dat"));});
        MainFrame frame=holder[0];
        try {
            awaitEdt(()->find(frame,"detectorCombo",JComboBox.class).isEnabled());
            SwingUtilities.invokeAndWait(()->{
                assertNull(find(frame,"detectorCombo",JComboBox.class).getSelectedItem());
                find(frame,"detectorCombo",JComboBox.class).setSelectedItem(DetectorChannel.RIGHT);
                find(find(frame,"parameterPanel",ParameterPanel.class),"medianCheck",JCheckBox.class).doClick();
                find(frame,"processButton",JButton.class).doClick();
                assertFalse(find(frame,"detectorCombo",JComboBox.class).isEnabled());
            });
            awaitEdt(()->find(frame,"result",BaselineResult.class)!=null);
            SwingUtilities.invokeAndWait(()->{
                var result=find(frame,"result",BaselineResult.class);assertEquals(6503,Statistics.count(result.finalBaselineMask()));
                var parameters=find(frame,"parameterPanel",ParameterPanel.class);
                find(parameters,"runSpinner",JSpinner.class).setValue(6*PhysicalBaselineParameters.REFERENCE_INTERVAL_SECONDS);
                find(parameters,"medianCheck",JCheckBox.class).doClick();
                find(parameters,"chargeCheck",JCheckBox.class).doClick();
                assertTrue(find(frame,"statusLabel",JLabel.class).getText().contains("Parameters edited"));
                find(parameters,"applyButton",JButton.class).doClick();
            });
            awaitEdt(()->find(frame,"result",BaselineResult.class).parametersUsed().minimumRun()==6);
            SwingUtilities.invokeAndWait(()->{
                assertEquals(DetectorChannel.RIGHT,find(frame,"result",BaselineResult.class).detector());
                var analysis=find(frame,"analysisResult",AnalysisResult.class);
                assertTrue(analysis.preprocessing().parametersUsed().enabled());
                assertTrue(analysis.preprocessing().recalculatedChargeMilliCoulombs().isPresent());
            });
        }finally{SwingUtilities.invokeAndWait(frame::dispose);}
    }
    private static void awaitEdt(java.util.function.BooleanSupplier ready) throws Exception {
        java.util.concurrent.CompletableFuture<Void> completion=new java.util.concurrent.CompletableFuture<>();
        SwingUtilities.invokeAndWait(()->{
            long deadline=System.nanoTime()+java.util.concurrent.TimeUnit.SECONDS.toNanos(15);
            javax.swing.Timer timer=new javax.swing.Timer(25,null);
            timer.addActionListener(e->{
                try{if(ready.getAsBoolean()){timer.stop();completion.complete(null);}else if(System.nanoTime()>deadline){timer.stop();completion.completeExceptionally(new AssertionError("Worker timeout"));}}
                catch(Throwable error){timer.stop();completion.completeExceptionally(error);}
            });timer.start();
        });
        completion.get(20,java.util.concurrent.TimeUnit.SECONDS);
    }
    @Test void peakViewRendersAndThresholdRefitsWithoutBaseline()throws Exception{
        org.junit.jupiter.api.Assumptions.assumeTrue(Boolean.getBoolean("zones.verifyUi"));
        var analysis=PeakAnalysisTest.test50Analysis();
        var result=new PeakAnalysisProcessor().process(analysis.baseline(),PeakParameters.defaults());
        PeakAnalysisPanel[] holder=new PeakAnalysisPanel[1];JFrame[] window=new JFrame[1];
        SwingUtilities.invokeAndWait(()->{
            AppTheme.setup();var panel=new PeakAnalysisPanel();holder[0]=panel;panel.setInput(analysis.baseline(),analysis);panel.display(result);
            JFrame frame=new JFrame();window[0]=frame;frame.add(panel);frame.setSize(1150,540);frame.addNotify();frame.validate();
            assertEquals(8,find(panel,"resultsTable",JTable.class).getRowCount());
            assertEquals(151,find(panel,"candidatesTable",JTable.class).getRowCount());
            var image=new BufferedImage(1150,540,BufferedImage.TYPE_INT_RGB);var g=image.createGraphics();frame.getContentPane().printAll(g);g.dispose();
            try{ImageIO.write(image,"png",Path.of("target/peak-results.png").toFile());}catch(Exception e){throw new AssertionError(e);}
            var tabs=find(panel,"tabs",JTabbedPane.class);tabs.setSelectedIndex(3);frame.validate();
            g=image.createGraphics();frame.getContentPane().printAll(g);g.dispose();
            try{ImageIO.write(image,"png",Path.of("target/peak-ranking.png").toFile());}catch(Exception e){throw new AssertionError(e);}
            var chart=new zones.chart.ElectropherogramChart();chart.showSignal(analysis.preprocessing().raw(),DetectorChannel.RIGHT);chart.showAnalysis(analysis);chart.showPeakFit(result.fits().get(2),true,true,true);
            assertTrue(find(chart,"correctedPlot",org.jfree.chart.plot.XYPlot.class).getDatasetCount()>1);
            chart.showPeakFit(null,false,false,false);assertNull(find(chart,"correctedPlot",org.jfree.chart.plot.XYPlot.class).getDataset(1));
            find(panel,"thresholdSpinner",JSpinner.class).setValue(1000000.0);
        });
        try{awaitEdt(()->holder[0].result().parametersUsed().lpnrThreshold()==1000000.0);
            SwingUtilities.invokeAndWait(()->{assertEquals(0,holder[0].result().selectedCount());assertEquals(result.candidates(),holder[0].result().candidates());});
        }finally{SwingUtilities.invokeAndWait(window[0]::dispose);}
    }
    private static <T> T find(Object object,String name,Class<T> type) {
        try{Field field=object.getClass().getDeclaredField(name);field.setAccessible(true);return type.cast(field.get(object));}
        catch(ReflectiveOperationException e){throw new AssertionError(e);}
    }
    private static void save(MainFrame frame,String name) {
        var content=frame.getContentPane();BufferedImage image=new BufferedImage(content.getWidth(),content.getHeight(),BufferedImage.TYPE_INT_RGB);
        Graphics2D g=image.createGraphics();content.printAll(g);g.dispose();
        try{ImageIO.write(image,"png",Path.of("target",name).toFile());}catch(java.io.IOException e){throw new AssertionError(e);}
    }
}
