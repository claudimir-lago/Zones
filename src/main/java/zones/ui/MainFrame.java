package zones.ui;

import java.awt.BorderLayout;
import java.nio.file.Path;
import java.util.Locale;
import java.util.concurrent.ExecutionException;
import javax.swing.*;
import javax.swing.filechooser.FileNameExtensionFilter;
import zones.chart.ElectropherogramChart;
import zones.io.AutoDetectDatReader;
import zones.model.*;
import zones.processing.*;
import zones.application.*;

/** NetBeans-editable shell. Event handlers delegate scientific work to the engine. */
public class MainFrame extends JFrame {
    private final ElectropherogramChart chart=new ElectropherogramChart();
    private ElectropherogramData data;
    private BaselineResult result;
    private AnalysisResult analysisResult;
    private AnalysisEvents events=AnalysisEvents.NONE;
    private boolean loading;
    private JDialog peaksDialog;
    private PeakAnalysisPanel peaksPanel;
    private PeakFit highlightedFit;
    private long processingGeneration=0;
    private ElectropherogramDomain activeDomain=ElectropherogramDomain.TIME;
    private MobilityCalibration mobilityCalibration;
    private final JToggleButton timeDomainButton=new JToggleButton("Time");
    private final JToggleButton chargeDomainButton=new JToggleButton("Charge");
    private final JToggleButton mobilityDomainButton=new JToggleButton("Mobility");
    private final JButton mobilityCalibrationButton=new JButton("Mobility calibration…");
    public MainFrame() {
        initComponents();
        installDomainControls();
        chartHostPanel.add(chart,BorderLayout.CENTER);
        chart.onCursor(cursorLabel::setText);
        chart.onAnalysisRangeChanged(()->invalidateCalculatedState("Analysis range changed — recalculate the baseline before peak analysis."));
        detectorCombo.setModel(new DefaultComboBoxModel<>(DetectorChannel.values()));
        detectorCombo.setSelectedIndex(-1);
        openButton.addActionListener(e->chooseFile());
        processButton.addActionListener(e->process());
        peaksButton.addActionListener(e->openPeaks());
        peaksButton.setEnabled(false);
        detectorCombo.addActionListener(e->{if(!loading)selectDetector();});
        highCheck.addActionListener(e->updateOverlays());correctedCheck.addActionListener(e->updateOverlays());originalCheck.addActionListener(e->updateOverlays());
        resetZoomButton.addActionListener(e->chart.resetZoom());
        parametersCheck.addActionListener(e->{parameterPanel.setVisible(parametersCheck.isSelected());revalidate();});
        parameterPanel.onApply(this::process);
        parameterPanel.onChanged(()->{events.parametersEdited();invalidateCalculatedState("Parameters changed — recalculate the baseline before peak analysis.");});
        processButton.setEnabled(false);detectorCombo.setEnabled(false);
        updateDomainAvailability();
        applyDomain(ElectropherogramDomain.TIME,false);
        setLocationRelativeTo(null);
    }
    private DetectorChannel selectedDetector(){return (DetectorChannel)detectorCombo.getSelectedItem();}
    private void installDomainControls(){
        var group=new ButtonGroup();group.add(timeDomainButton);group.add(chargeDomainButton);group.add(mobilityDomainButton);
        for(var b:new JToggleButton[]{timeDomainButton,chargeDomainButton,mobilityDomainButton})b.putClientProperty("zones.domainButton",Boolean.TRUE);
        timeDomainButton.setSelected(true);
        domainPanel.add(new JLabel("Domain:"));
        domainPanel.add(timeDomainButton);domainPanel.add(chargeDomainButton);domainPanel.add(mobilityDomainButton);domainPanel.add(mobilityCalibrationButton);
        timeDomainButton.addActionListener(e->applyDomain(ElectropherogramDomain.TIME,true));
        chargeDomainButton.addActionListener(e->applyDomain(ElectropherogramDomain.CHARGE,true));
        mobilityDomainButton.addActionListener(e->{if(mobilityCalibration==null&&!editMobilityCalibration()){selectDomainButton(activeDomain);return;}applyDomain(ElectropherogramDomain.MOBILITY,true);});
        mobilityCalibrationButton.addActionListener(e->{if(editMobilityCalibration()&&activeDomain==ElectropherogramDomain.MOBILITY)applyDomain(activeDomain,true);});
    }
    private boolean hasChargeSource(){return data!=null&&(data.currentMicroamps().isPresent()||data.chargeMilliCoulombs().isPresent());}
    private void updateDomainAvailability(){boolean available=hasChargeSource();chargeDomainButton.setEnabled(available);mobilityDomainButton.setEnabled(available);mobilityCalibrationButton.setEnabled(available);if(!available&&activeDomain!=ElectropherogramDomain.TIME)applyDomain(ElectropherogramDomain.TIME,false);}
    private void selectDomainButton(ElectropherogramDomain domain){timeDomainButton.setSelected(domain==ElectropherogramDomain.TIME);chargeDomainButton.setSelected(domain==ElectropherogramDomain.CHARGE);mobilityDomainButton.setSelected(domain==ElectropherogramDomain.MOBILITY);}
    private void applyDomain(ElectropherogramDomain domain,boolean announce){
        if(domain!=ElectropherogramDomain.TIME&&!hasChargeSource())domain=ElectropherogramDomain.TIME;
        if(domain==ElectropherogramDomain.MOBILITY&&mobilityCalibration==null)domain=ElectropherogramDomain.TIME;
        activeDomain=domain;selectDomainButton(domain);chart.setDomain(domain,mobilityCalibration);rerenderChart();DomainTheme.apply(this,domain);if(peaksDialog!=null)DomainTheme.apply(peaksDialog,domain);if(peaksPanel!=null)peaksPanel.setDomain(domain,mobilityCalibration);
        if(announce){
            String message=domain==ElectropherogramDomain.TIME?"Time domain — baseline and peak detection use the acquisition timescale.":domain==ElectropherogramDomain.CHARGE?"Charge domain — x axis uses |q| in mC; cumulative charge uses trapezoidal integration.":"Mobility domain — x axis uses |mu| in Ti; values above 1000 Ti are excluded.";
            if(domain!=ElectropherogramDomain.TIME&&data!=null){var tr=new DomainTransform(data,mobilityCalibration);if(tr.hasCurrentPolarityReversal(data)||!tr.magnitudeChargeMonotonic())message+=" WARNING: current polarity reversal makes |q| non-monotonic; transformed coordinates may be ambiguous.";}
            statusLabel.setText(message);
        }
    }
    private void rerenderChart(){
        if(data==null||selectedDetector()==null)return;double start=chart.analysisStartMinute(),end=chart.analysisEndMinute();chart.showSignal(data,selectedDetector());chart.setAnalysisRangeMinutes(start,end);if(analysisResult!=null)chart.showAnalysis(analysisResult);else if(result!=null)chart.showResult(result);chart.setStale(false);updateOverlays();if(highlightedFit!=null)updatePeakOverlay();
    }
    private boolean editMobilityCalibration(){
        JTextField q1=new JTextField(mobilityCalibration==null?"":Double.toString(mobilityCalibration.charge1MilliCoulombs()),10);
        JTextField mu1=new JTextField(mobilityCalibration==null?"":Double.toString(mobilityCalibration.mobility1Ti()),10);
        JTextField q2=new JTextField(mobilityCalibration==null?"":Double.toString(mobilityCalibration.charge2MilliCoulombs()),10);
        JTextField mu2=new JTextField(mobilityCalibration==null?"":Double.toString(mobilityCalibration.mobility2Ti()),10);
        JPanel panel=new JPanel(new java.awt.GridLayout(0,2,6,6));panel.add(new JLabel("Standard 1 migration |q| (mC):"));panel.add(q1);panel.add(new JLabel("Standard 1 effective mobility (Ti):"));panel.add(mu1);panel.add(new JLabel("Standard 2 migration |q| (mC):"));panel.add(q2);panel.add(new JLabel("Standard 2 effective mobility (Ti):"));panel.add(mu2);
        while(true){int answer=JOptionPane.showConfirmDialog(this,panel,"Two-standard mobility calibration",JOptionPane.OK_CANCEL_OPTION,JOptionPane.PLAIN_MESSAGE);if(answer!=JOptionPane.OK_OPTION)return false;try{mobilityCalibration=MobilityCalibration.fromTwoStandards(Double.parseDouble(q1.getText().trim()),Double.parseDouble(mu1.getText().trim()),Double.parseDouble(q2.getText().trim()),Double.parseDouble(mu2.getText().trim()));statusLabel.setText(String.format(Locale.ROOT,"Mobility calibration: k = %.8g Ti·mC | EOF = %.8g Ti",mobilityCalibration.kTiMilliCoulombs(),mobilityCalibration.eofMobilityTi()));return true;}catch(Exception ex){JOptionPane.showMessageDialog(this,ex.getMessage(),"Invalid mobility calibration",JOptionPane.ERROR_MESSAGE);}}
    }
    private void chooseFile() {
        JFileChooser chooser=new JFileChooser(data==null?new java.io.File("."):data.source().getParent().toFile());
        chooser.setFileFilter(new FileNameExtensionFilter("minimalistiCE / oldMinimalistiCE (*.dat)","dat"));
        if(chooser.showOpenDialog(this)==JFileChooser.APPROVE_OPTION)loadFile(chooser.getSelectedFile().toPath());
    }
    public void loadFile(Path path) {
        setBusy(true);statusLabel.setText("Reading " + path.getFileName() + "…");
        new SwingWorker<ElectropherogramData,Void>() {
            @Override protected ElectropherogramData doInBackground() throws Exception {return new AutoDetectDatReader().read(path);}
            @Override protected void done() {
                try {
                    clearPeaks();data=get();result=null;analysisResult=null;mobilityCalibration=null;activeDomain=ElectropherogramDomain.TIME;parameterPanel.setSamplingInterval(data.samplingIntervalSeconds());fileLabel.setText(data.source().getFileName().toString());fileLabel.setToolTipText(data.source().toString());
                    loading=true;detectorCombo.setModel(new DefaultComboBoxModel<>(data.detectors().toArray(DetectorChannel[]::new)));
                    DetectorChannel preferred=data.detectors().contains(DetectorChannel.RIGHT)&&!data.isConstant(DetectorChannel.RIGHT)?DetectorChannel.RIGHT:
                        data.detectors().contains(DetectorChannel.LEFT)?DetectorChannel.LEFT:data.detectors().iterator().next();
                    detectorCombo.setSelectedItem(preferred);loading=false;
                    diagnosticsArea.setText(String.format(Locale.ROOT,"%d points | median dt %.5f s | %.3f Hz%nDefault detector: %s.",data.size(),data.samplingIntervalSeconds(),data.samplingRateHz(),preferred));
                    chartHostPanel.removeAll();chartHostPanel.add(chart,BorderLayout.CENTER);chartHostPanel.revalidate();chartHostPanel.repaint();
                    updateDomainAvailability();applyDomain(ElectropherogramDomain.TIME,false);selectDetector();
                    statusLabel.setText("File loaded — ready for baseline calculation.");
                }catch(InterruptedException e){Thread.currentThread().interrupt();showError(e);}catch(ExecutionException e){showError(e.getCause());}
                finally{loading=false;setBusy(false);}
            }
        }.execute();
    }
    private void selectDetector() {
        if(data==null || selectedDetector()==null)return;
        clearPeaks();result=null;analysisResult=null;chartHostPanel.removeAll();chartHostPanel.add(chart,BorderLayout.CENTER);chartHostPanel.revalidate();chartHostPanel.repaint();
        chart.showSignal(data,selectedDetector());updateOverlays();
        boolean constant=data.isConstant(selectedDetector());
        processButton.setEnabled(!constant);
        statusLabel.setText(constant?"Constant channel in this file: select another detector.":"Ready to calculate baseline — " + selectedDetector());
        diagnosticsArea.setText(String.format(Locale.ROOT,"%s | %d points | %.3f Hz%nNo result has been calculated for this selection.",selectedDetector(),data.size(),data.samplingRateHz()));
    }
    private void process() {
        if(data==null || selectedDetector()==null){statusLabel.setText("Open a file and select a detector.");return;}
        final AnalysisParameters parameters;
        try{parameters=parameterPanel.analysisParameters();}catch(IllegalArgumentException e){showError(e);return;}
        ElectropherogramData input;DetectorChannel detector=selectedDetector();
        try{input=data.slice(chart.analysisStartMinute(),chart.analysisEndMinute());}catch(IllegalArgumentException ex){showError(ex);return;}
        clearPeaks();result=null;analysisResult=null;long generation=++processingGeneration;
        setBusy(true);statusLabel.setText("Processing selective MMR + baseline…");
        new SwingWorker<AnalysisResult,Void>() {
            @Override protected AnalysisResult doInBackground(){return new AnalysisProcessor().process(input,detector,parameters);}
            @Override protected void done(){
                try{AnalysisResult r=get();if(generation==processingGeneration)displayAnalysis(r);}catch(InterruptedException e){Thread.currentThread().interrupt();showError(e);}catch(ExecutionException e){showError(e.getCause());}finally{setBusy(false);}
            }
        }.execute();
    }
    /** Also used by the offscreen UI verification harness. */
    public void displayResult(BaselineResult value) {
        analysisResult=null;
        displayBaseline(value,value.data());
    }
    private void displayBaseline(BaselineResult value,ElectropherogramData raw) {
        clearPeaks();
        boolean sameFile=data!=null&&data.source().equals(raw.source());
        boolean sameSelection=sameFile&&selectedDetector()==value.detector();
        if(!sameFile)data=raw;result=value;
        loading=true;detectorCombo.setSelectedItem(value.detector());loading=false;
        fileLabel.setText(data.source().getFileName().toString());
        chartHostPanel.removeAll();chartHostPanel.add(chart,BorderLayout.CENTER);chartHostPanel.revalidate();
        if(!sameSelection)chart.showSignal(data,value.detector());
        chart.showResult(value);chart.setStale(false);updateOverlays();
        int high=Statistics.count(value.highConfidenceBaselineMask()),recovered=Statistics.count(value.recoveredBaselineMask()),total=Statistics.count(value.finalBaselineMask());
        diagnosticsArea.setText(String.format(Locale.ROOT,"%s | %d points | %.3f Hz | noise sigma %.6f a.u.%nFABC anchors: %d | Recovered: %d | Final: %d (%.2f%%) | %d ms%nValid scales: %s%nFailures: %s%n%s%nAlgorithm: %s",
            value.detector(),value.data().size(),value.data().samplingRateHz(),value.noiseEstimate(),high,recovered,total,100.0*total/value.data().size(),value.elapsedMillis(),value.scaleMasks().keySet(),value.failedScales().isEmpty()?"none":value.failedScales(),value.parametersUsed(),BaselineResult.ALGORITHM_VERSION));
        diagnosticsArea.setCaretPosition(0);
        statusLabel.setText(value.failedScales().isEmpty()?"Baseline calculated — " + value.detector():"Calculated with scale failures — see diagnostics.");
        setBusy(false);
    }
    public void displayAnalysis(AnalysisResult value) {
        analysisResult=value;
        displayBaseline(value.baseline(),value.preprocessing().raw());
        chart.showAnalysis(value);chart.setStale(false);updateOverlays();
        var pre=value.preprocessing();
        diagnosticsArea.append("\nPreprocessing: "+(pre.parametersUsed().enabled()?"selective MMR 5 sigma + quadratic 2+2, characteristic duration = "+pre.parametersUsed().durationSeconds()+" s, N = "+pre.halfWindow()+", window = "+pre.windowPoints()+" points":"MMR disabled")
                +" | baseline input: "+(pre.parametersUsed().enabled()?"selectively reconstructed signal":"raw signal")+"\nPhysical controls: "+value.parametersUsed().baseline()+"\nPipeline: "+AnalysisResult.PIPELINE_VERSION);
        if(value.parametersUsed().recalculateCharge()) {
            var charge=pre.recalculatedChargeMilliCoulombs();
            diagnosticsArea.append(charge.isPresent()?String.format(Locale.ROOT,"%nRecalculated charge (initial Q = 0): %.9f mC",charge.get()[charge.get().length-1]):"\nCharge not recalculated: current channel unavailable.");
            pre.raw().chargeMilliCoulombs().ifPresent(stored->diagnosticsArea.append(String.format(Locale.ROOT," | stored final charge: %.9f mC (preserved)",stored[stored.length-1])));
        }
        if(activeDomain!=ElectropherogramDomain.TIME){
            var tr=new DomainTransform(pre.cleaned(),mobilityCalibration);
            diagnosticsArea.append("\nTransformed domains use coordinate magnitude: |q| for charge and |mu| for mobility; signed values are preserved internally.");
            if(tr.hasCurrentPolarityReversal(pre.cleaned())||!tr.magnitudeChargeMonotonic())diagnosticsArea.append("\nWARNING: current polarity reversal/non-monotonic |q| detected. Charge/mobility coordinates can be ambiguous for this run.");
            if(activeDomain==ElectropherogramDomain.MOBILITY)diagnosticsArea.append("\nMobility display/quantitation excludes transformed values above 1000 Ti.");
        }
        diagnosticsArea.setCaretPosition(0);events.analysisCompleted(value);
    }
    public void setAnalysisEvents(AnalysisEvents events){this.events=java.util.Objects.requireNonNull(events);}
    private void invalidateCalculatedState(String message){
        ++processingGeneration;clearPeaks();result=null;analysisResult=null;chart.setStale(true);peaksButton.setEnabled(false);statusLabel.setText(message);
    }
    private void clearPeaks(){
        if(peaksDialog!=null)peaksDialog.dispose();peaksDialog=null;peaksPanel=null;highlightedFit=null;
        peaksButton.setEnabled(false);chart.showPeakFit(null,false,false,false);
    }
    private void openPeaks(){
        if(result==null)return;
        if(peaksDialog==null){
            peaksPanel=new PeakAnalysisPanel();peaksPanel.setInput(result,analysisResult);peaksPanel.setDomain(activeDomain,mobilityCalibration);
            PeakAnalysisPanel owner=peaksPanel;
            peaksPanel.onSelection(fit->{if(peaksPanel==owner){highlightedFit=fit;updatePeakOverlay();}});peaksPanel.onOverlayChanged(this::updatePeakOverlay);
            peaksPanel.onZoomToWindow(fit->{if(peaksPanel==owner&&fit!=null)chart.zoomToWindow(fit.window(),0.1);});
            peaksDialog=new JDialog(this,"Peak analysis — "+result.detector(),false);peaksDialog.setDefaultCloseOperation(JDialog.HIDE_ON_CLOSE);
            peaksDialog.add(peaksPanel);peaksDialog.setSize(1150,540);peaksDialog.setLocationRelativeTo(this);DomainTheme.apply(peaksDialog,activeDomain);peaksPanel.analyze();
        }
        peaksDialog.setVisible(true);
    }
    private void updatePeakOverlay(){if(peaksPanel!=null)chart.showPeakFit(highlightedFit,peaksPanel.componentsVisible(),peaksPanel.sumVisible(),peaksPanel.markersVisible());}
    private void updateOverlays(){chart.overlays(highCheck.isSelected(),false,originalCheck.isSelected(),correctedCheck.isSelected());chart.cleanedVisible(false);}
    private void setBusy(boolean busy){openButton.setEnabled(!busy);peaksButton.setEnabled(!busy&&result!=null);detectorCombo.setEnabled(!busy && data!=null);processButton.setEnabled(!busy && data!=null && selectedDetector()!=null && !data.isConstant(selectedDetector()));parameterPanel.setBusy(busy);progressBar.setIndeterminate(busy);timeDomainButton.setEnabled(!busy);boolean available=!busy&&hasChargeSource();chargeDomainButton.setEnabled(available);mobilityDomainButton.setEnabled(available);mobilityCalibrationButton.setEnabled(available);}
    private void showError(Throwable error){statusLabel.setText("Failure — " + error.getMessage());JOptionPane.showMessageDialog(this,error.getMessage(),"Zones",JOptionPane.ERROR_MESSAGE);}
    @SuppressWarnings("unchecked")
    // <editor-fold defaultstate="collapsed" desc="Generated Code">//GEN-BEGIN:initComponents
    private void initComponents() {
        topPanel = new javax.swing.JPanel();
        filePanel = new javax.swing.JPanel();
        openButton = new javax.swing.JButton();
        fileLabel = new javax.swing.JLabel();
        detectorLabel = new javax.swing.JLabel();
        detectorCombo = new javax.swing.JComboBox<>();
        processButton = new javax.swing.JButton();
        peaksButton = new javax.swing.JButton();
        domainPanel = new javax.swing.JPanel();
        viewPanel = new javax.swing.JPanel();
        highCheck = new javax.swing.JCheckBox();
        originalCheck = new javax.swing.JCheckBox();
        correctedCheck = new javax.swing.JCheckBox();
        resetZoomButton = new javax.swing.JButton();
        parametersCheck = new javax.swing.JCheckBox();
        chartHostPanel = new javax.swing.JPanel();
        parameterPanel = new zones.ui.ParameterPanel();
        bottomPanel = new javax.swing.JPanel();
        diagnosticsScroll = new javax.swing.JScrollPane();
        diagnosticsArea = new javax.swing.JTextArea();
        statusPanel = new javax.swing.JPanel();
        statusLabel = new javax.swing.JLabel();
        progressBar = new javax.swing.JProgressBar();
        cursorLabel = new javax.swing.JLabel();
        setTitle("Zones — CE-C4D");
        setDefaultCloseOperation(3);
        setMinimumSize(new java.awt.Dimension(1000, 680));
        setPreferredSize(new java.awt.Dimension(1360, 880));
        getContentPane().setLayout(new java.awt.BorderLayout(8, 8));
        topPanel.setLayout(new java.awt.BorderLayout(4, 4));
        filePanel.setBorder(javax.swing.BorderFactory.createTitledBorder("Electropherogram"));
        filePanel.setLayout(new java.awt.FlowLayout(0, 8, 6));
        openButton.setText("Open .dat…");
        filePanel.add(openButton);
        fileLabel.setText("No file open");
        filePanel.add(fileLabel);
        detectorLabel.setText("Detector:");
        filePanel.add(detectorLabel);
        filePanel.add(detectorCombo);
        processButton.setText("Calculate baseline");
        filePanel.add(processButton);
        peaksButton.setText("Analyze peaks…");
        filePanel.add(peaksButton);
        topPanel.add(filePanel, java.awt.BorderLayout.NORTH);
        domainPanel.setLayout(new java.awt.FlowLayout(0, 8, 3));
        topPanel.add(domainPanel, java.awt.BorderLayout.CENTER);
        viewPanel.setLayout(new java.awt.FlowLayout(0, 8, 2));
        highCheck.setText("FABC anchors (magenta)");
        highCheck.setSelected(true);
        viewPanel.add(highCheck);
        originalCheck.setText("Original C4D");
        originalCheck.setSelected(true);
        viewPanel.add(originalCheck);
        correctedCheck.setText("Corrected C4D");
        correctedCheck.setSelected(true);
        viewPanel.add(correctedCheck);
        resetZoomButton.setText("Reset zoom");
        viewPanel.add(resetZoomButton);
        parametersCheck.setText("Parameters");
        parametersCheck.setSelected(true);
        viewPanel.add(parametersCheck);
        topPanel.add(viewPanel, java.awt.BorderLayout.SOUTH);
        getContentPane().add(topPanel, java.awt.BorderLayout.NORTH);
        chartHostPanel.setBorder(javax.swing.BorderFactory.createTitledBorder("Signal and baseline"));
        chartHostPanel.setLayout(new java.awt.BorderLayout(0, 0));
        getContentPane().add(chartHostPanel, java.awt.BorderLayout.CENTER);
        getContentPane().add(parameterPanel, java.awt.BorderLayout.EAST);
        bottomPanel.setLayout(new java.awt.BorderLayout(4, 4));
        diagnosticsScroll.setBorder(javax.swing.BorderFactory.createTitledBorder("Diagnostics / result parameters"));
        diagnosticsScroll.setPreferredSize(new java.awt.Dimension(1000, 125));
        diagnosticsArea.setEditable(false);
        diagnosticsArea.setRows(5);
        diagnosticsArea.setColumns(70);
        diagnosticsScroll.setViewportView(diagnosticsArea);
        bottomPanel.add(diagnosticsScroll, java.awt.BorderLayout.CENTER);
        statusPanel.setLayout(new java.awt.BorderLayout(8, 0));
        statusLabel.setText("Open an experimental file.");
        statusPanel.add(statusLabel, java.awt.BorderLayout.CENTER);
        progressBar.setPreferredSize(new java.awt.Dimension(90, 14));
        statusPanel.add(progressBar, java.awt.BorderLayout.EAST);
        bottomPanel.add(statusPanel, java.awt.BorderLayout.SOUTH);
        cursorLabel.setText("Drag to zoom • Ctrl + drag to pan • mouse wheel to zoom • drag Analysis start/end markers to set the analysis range");
        bottomPanel.add(cursorLabel, java.awt.BorderLayout.NORTH);
        getContentPane().add(bottomPanel, java.awt.BorderLayout.SOUTH);
        pack();
    }// </editor-fold>//GEN-END:initComponents
    // Variables declaration - do not modify//GEN-BEGIN:variables
    private javax.swing.JPanel topPanel;
    private javax.swing.JPanel filePanel;
    private javax.swing.JButton openButton;
    private javax.swing.JLabel fileLabel;
    private javax.swing.JLabel detectorLabel;
    private javax.swing.JComboBox<zones.model.DetectorChannel> detectorCombo;
    private javax.swing.JButton processButton;
    private javax.swing.JButton peaksButton;
    private javax.swing.JPanel domainPanel;
    private javax.swing.JPanel viewPanel;
    private javax.swing.JCheckBox highCheck;
    private javax.swing.JCheckBox originalCheck;
    private javax.swing.JCheckBox correctedCheck;
    private javax.swing.JButton resetZoomButton;
    private javax.swing.JCheckBox parametersCheck;
    private javax.swing.JPanel chartHostPanel;
    private zones.ui.ParameterPanel parameterPanel;
    private javax.swing.JPanel bottomPanel;
    private javax.swing.JScrollPane diagnosticsScroll;
    private javax.swing.JTextArea diagnosticsArea;
    private javax.swing.JPanel statusPanel;
    private javax.swing.JLabel statusLabel;
    private javax.swing.JProgressBar progressBar;
    private javax.swing.JLabel cursorLabel;
    // End of variables declaration//GEN-END:variables
}
