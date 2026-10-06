package zones.ui;

import java.awt.BorderLayout;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Objects;
import java.util.concurrent.ExecutionException;
import java.util.prefs.Preferences;
import javax.swing.*;
import javax.swing.filechooser.FileNameExtensionFilter;
import zones.chart.ElectropherogramChart;
import zones.io.AutoDetectDatReader;
import zones.model.*;
import zones.processing.*;
import zones.application.*;
import zones.core.ZonesEngine;

/** NetBeans-editable shell. Event handlers delegate scientific work to the engine. */
public class MainFrame extends JFrame {
    private final ElectropherogramChart chart=new ElectropherogramChart();
    private final ZonesEngine engine=new ZonesEngine();
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
    private final JButton mobilityCalibrationButton=new JButton("Mobility calibration");
    private final JCheckBox invertChargeCheck=new JCheckBox("Invert charge axis");
    private final JButton aboutButton=new JButton("About");
    private MobilityReference mobilityRef1,mobilityRef2;
    private DetectorChannel mobilityReferenceDetector;
    private static final Preferences MOBILITY_PREFS=Preferences.userNodeForPackage(MainFrame.class).node("mobilityCalibration");
    private String mobilityMode="Two effective-mobility standards";
    private String mobilityInstrumentBasis="Charge / conductivity";
    private double capillaryIdUm=75.0,bgeConductivitySPerM=1.0,bgeConductivityTemperatureC=25.0,leftDistanceCm=13.0,rightDistanceCm=24.0;
    private double appliedVoltageKv=20.0,totalCapillaryLengthCm=50.0,runTemperatureC=25.0;
    public MainFrame() {
        loadMobilityPreferences();
        initComponents();
        markThemeButtons();
        diagnosticsScroll.setVisible(false);
        installDomainControls();
        viewPanel.add(aboutButton);
        aboutButton.addActionListener(e->showAbout());
        chartHostPanel.add(chart,BorderLayout.CENTER);
        chart.onCursor(cursorLabel::setText);
        chart.onAnalysisRangeChanged(()->invalidateCalculatedState("Analysis range changed — recalculate the baseline before peak analysis."));
        chart.onCorrectedClick(this::selectPeakFromChart);
        detectorCombo.setModel(new DefaultComboBoxModel<>(DetectorChannel.values()));
        detectorCombo.setPrototypeDisplayValue(DetectorChannel.RIGHT);
        detectorCombo.setPreferredSize(new java.awt.Dimension(135,28));
        detectorCombo.setMinimumSize(new java.awt.Dimension(135,28));
        disableTooltips(detectorCombo);
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
    private void markThemeButtons(){
        for(AbstractButton b:new AbstractButton[]{openButton,processButton,peaksButton,resetZoomButton,mobilityCalibrationButton,aboutButton})
            DomainTheme.themeButton(b);
    }
    private static void disableTooltips(java.awt.Component component){
        if(component instanceof JComponent jc){jc.setToolTipText(null);ToolTipManager.sharedInstance().unregisterComponent(jc);}
        if(component instanceof java.awt.Container container)
            for(java.awt.Component child:container.getComponents())disableTooltips(child);
    }
    private void showAbout(){
        JTextArea info=new JTextArea(
            "Zones 0.7.4\n"+
            "Capillary electrophoresis data processing in the time, charge, and mobility domains.\n\n"+
            "License: GPL-3.0-or-later\n\n"+
            "Scientific basis:\n"+
            "E. T. da Costa, D. R. Oliveira, C. L. do Lago, Electrophoresis 43 (2022) 2363–2376.\n"+
            "DOI: 10.1002/elps.202200195\n\n"+
            "Thermal normalization:\n"+
            "K. J. M. Francisco, C. L. do Lago, Talanta 185 (2018) 37–41.\n"+
            "Water viscosity: Huber et al., J. Phys. Chem. Ref. Data 38 (2009) 101–125.");
        info.setEditable(false);info.setOpaque(false);info.setLineWrap(true);info.setWrapStyleWord(true);info.setColumns(58);
        JButton github=DomainTheme.themeButton(new JButton("GitHub"));
        JButton manual=DomainTheme.themeButton(new JButton("User manual"));
        JButton paper=DomainTheme.themeButton(new JButton("Scientific paper"));
        github.addActionListener(e->openExternalLink("https://github.com/claudimir-lago/Zones"));
        manual.addActionListener(e->openUserGuide());
        paper.addActionListener(e->openExternalLink("https://doi.org/10.1002/elps.202200195"));
        JPanel links=new JPanel(new java.awt.FlowLayout(java.awt.FlowLayout.CENTER,8,4));links.add(github);links.add(manual);links.add(paper);
        JPanel panel=new JPanel(new BorderLayout(6,8));panel.add(info,BorderLayout.CENTER);panel.add(links,BorderLayout.SOUTH);
        DomainTheme.applyToComponent(panel,activeDomain);
        JOptionPane.showMessageDialog(this,panel,"About Zones",JOptionPane.INFORMATION_MESSAGE);
    }
    private void openUserGuide(){
        try{
            java.nio.file.Path local=java.nio.file.Path.of("USER_GUIDE.html").toAbsolutePath().normalize();
            if(java.nio.file.Files.isRegularFile(local)&&java.awt.Desktop.isDesktopSupported()){java.awt.Desktop.getDesktop().browse(local.toUri());return;}
        }catch(Exception ignored){}
        openExternalLink("https://htmlpreview.github.io/?https://github.com/claudimir-lago/Zones/blob/main/USER_GUIDE.html");
    }
    private void openExternalLink(String url){
        try{
            if(!java.awt.Desktop.isDesktopSupported())throw new UnsupportedOperationException("Desktop browsing is not supported on this system.");
            java.awt.Desktop.getDesktop().browse(java.net.URI.create(url));
        }catch(Exception ex){
            JTextArea text=new JTextArea(url);text.setEditable(false);text.setLineWrap(true);text.setWrapStyleWord(true);
            JOptionPane.showMessageDialog(this,text,"Open this link in your browser",JOptionPane.INFORMATION_MESSAGE);
        }
    }
    private DetectorChannel selectedDetector(){return (DetectorChannel)detectorCombo.getSelectedItem();}
    private void installDomainControls(){
        var group=new ButtonGroup();group.add(timeDomainButton);group.add(chargeDomainButton);group.add(mobilityDomainButton);
        for(var b:new JToggleButton[]{timeDomainButton,chargeDomainButton,mobilityDomainButton})b.putClientProperty("zones.domainButton",Boolean.TRUE);
        timeDomainButton.setSelected(true);
        domainPanel.add(new JLabel("Domain:"));
        domainPanel.add(timeDomainButton);domainPanel.add(chargeDomainButton);domainPanel.add(mobilityDomainButton);domainPanel.add(invertChargeCheck);domainPanel.add(mobilityCalibrationButton);
        timeDomainButton.addActionListener(e->applyDomain(ElectropherogramDomain.TIME,true));
        chargeDomainButton.addActionListener(e->applyDomain(ElectropherogramDomain.CHARGE,true));
        mobilityDomainButton.addActionListener(e->{if(mobilityCalibration==null&&!editMobilityCalibration()){selectDomainButton(activeDomain);return;}applyDomain(ElectropherogramDomain.MOBILITY,true);});
        invertChargeCheck.addActionListener(e->{chart.setDomain(activeDomain,mobilityCalibration,invertChargeCheck.isSelected());rerenderChart();if(peaksPanel!=null)peaksPanel.setDomain(activeDomain,mobilityCalibration,invertChargeCheck.isSelected());if(activeDomain==ElectropherogramDomain.CHARGE)statusLabel.setText(invertChargeCheck.isSelected()?"Charge domain — displaying -Charge (mC).":"Charge domain — displaying signed Charge (mC).");});
        mobilityCalibrationButton.addActionListener(e->{if(editMobilityCalibration()&&activeDomain==ElectropherogramDomain.MOBILITY)applyDomain(activeDomain,true);});
    }
    private boolean hasChargeSource(){return data!=null&&(data.currentMicroamps().isPresent()||data.chargeMilliCoulombs().isPresent());}
    private boolean hasMobilitySource(){return data!=null;}
    private void updateDomainAvailability(){boolean charge=hasChargeSource(),mobility=hasMobilitySource();chargeDomainButton.setEnabled(charge);mobilityDomainButton.setEnabled(mobility);mobilityCalibrationButton.setEnabled(mobility);invertChargeCheck.setEnabled(charge);if(!charge&&activeDomain==ElectropherogramDomain.CHARGE)applyDomain(ElectropherogramDomain.TIME,false);}
    private void selectDomainButton(ElectropherogramDomain domain){timeDomainButton.setSelected(domain==ElectropherogramDomain.TIME);chargeDomainButton.setSelected(domain==ElectropherogramDomain.CHARGE);mobilityDomainButton.setSelected(domain==ElectropherogramDomain.MOBILITY);}
    private void applyDomain(ElectropherogramDomain domain,boolean announce){
        if(domain==ElectropherogramDomain.CHARGE&&!hasChargeSource())domain=ElectropherogramDomain.TIME;
        if(domain==ElectropherogramDomain.MOBILITY&&mobilityCalibration==null)domain=ElectropherogramDomain.TIME;
        activeDomain=domain;selectDomainButton(domain);chart.setDomain(domain,mobilityCalibration,invertChargeCheck.isSelected());rerenderChart();DomainTheme.apply(this,domain);if(peaksDialog!=null)DomainTheme.apply(peaksDialog,domain);if(peaksPanel!=null)peaksPanel.setDomain(domain,mobilityCalibration,invertChargeCheck.isSelected());
        if(announce){
            String message=domain==ElectropherogramDomain.TIME?"Time domain — baseline and peak detection use the acquisition timescale.":domain==ElectropherogramDomain.CHARGE?(invertChargeCheck.isSelected()?"Charge domain — x axis is -Charge (mC).":"Charge domain — x axis is signed Charge (mC)."):(mobilityCalibration==null?"Mobility domain — calibration required.":mobilityCalibration.mobilityName()+" spectrum at 25 °C — signed mobility in Ti; |mobility| > 1000 Ti excluded.");
            if(domain!=ElectropherogramDomain.TIME&&data!=null&&hasChargeSource()&&(domain==ElectropherogramDomain.CHARGE||(mobilityCalibration!=null&&mobilityCalibration.usesCharge()))){var tr=new DomainTransform(data,mobilityCalibration,invertChargeCheck.isSelected());if(tr.hasCurrentPolarityReversal(data)||!tr.signedChargeMonotonic())message+=" WARNING: current polarity reversal/non-monotonic charge detected; transformed coordinates may be ambiguous.";}
            statusLabel.setText(message);
        }
    }
    private void rerenderChart(){
        if(data==null||selectedDetector()==null)return;double start=chart.analysisStartMinute(),end=chart.analysisEndMinute();chart.showSignal(data,selectedDetector());chart.setAnalysisRangeMinutes(start,end);if(analysisResult!=null)chart.showAnalysis(analysisResult);else if(result!=null)chart.showResult(result);chart.setStale(false);updateOverlays();if(highlightedFit!=null)updatePeakOverlay();
    }
    private boolean editMobilityCalibration(){
        JComboBox<String> mode=new JComboBox<>(new String[]{"Instrument parameters only","Two effective-mobility standards","Instrument parameters + one effective-mobility reference"});mode.setSelectedItem(mobilityMode);
        JComboBox<String> basis=new JComboBox<>(new String[]{"Charge / conductivity","Voltage / capillary length"});
        if(!hasChargeSource()&&"Charge / conductivity".equals(mobilityInstrumentBasis))mobilityInstrumentBasis="Voltage / capillary length";
        basis.setSelectedItem(mobilityInstrumentBasis);
        JTextField id=new JTextField(Double.toString(capillaryIdUm),7),cond=new JTextField(Double.toString(bgeConductivitySPerM),7),condTemp=new JTextField(Double.toString(bgeConductivityTemperatureC),7);
        JTextField voltage=new JTextField(Double.toString(appliedVoltageKv),7),totalLength=new JTextField(Double.toString(totalCapillaryLengthCm),7),runTemp=new JTextField(Double.toString(runTemperatureC),7);
        JTextField first=new JTextField(Double.toString(leftDistanceCm),7),second=new JTextField(Double.toString(rightDistanceCm),7);
        JLabel basisLabel=new JLabel("Instrumental basis:");
        JLabel idLabel=new JLabel("Capillary i.d. (um):"),condLabel=new JLabel("BGE conductivity (S/m):"),condTempLabel=new JLabel("Conductivity temperature (°C):");
        JLabel voltageLabel=new JLabel("Applied voltage (kV, signed):"),lengthLabel=new JLabel("Total capillary length (cm):"),runTempLabel=new JLabel("Run temperature (°C):");
        JLabel firstLabel=new JLabel("Injection → 1st C4D (cm):"),secondLabel=new JLabel("Injection → 2nd C4D (cm):");
        JPanel panel=new JPanel(new java.awt.GridBagLayout());
        java.awt.GridBagConstraints gc=new java.awt.GridBagConstraints();gc.insets=new java.awt.Insets(2,4,2,4);gc.anchor=java.awt.GridBagConstraints.WEST;gc.fill=java.awt.GridBagConstraints.HORIZONTAL;
        int[] rowHolder={0};
        java.util.function.BiConsumer<JComponent,JComponent> addRow=(a,b)->{gc.gridx=0;gc.gridy=rowHolder[0];gc.weightx=0;panel.add(a,gc);gc.gridx=1;gc.weightx=1;panel.add(b,gc);rowHolder[0]++;};
        addRow.accept(new JLabel("Calibration mode:"),mode);addRow.accept(basisLabel,basis);
        addRow.accept(idLabel,id);addRow.accept(condLabel,cond);addRow.accept(condTempLabel,condTemp);
        addRow.accept(voltageLabel,voltage);addRow.accept(lengthLabel,totalLength);addRow.accept(runTempLabel,runTemp);
        addRow.accept(firstLabel,first);addRow.accept(secondLabel,second);addRow.accept(new JLabel("Peak references:"),new JLabel(referenceSummary()));
        Runnable update=()->{
            boolean instrumental=!"Two effective-mobility standards".equals(mode.getSelectedItem());
            basis.setEnabled(instrumental);basisLabel.setEnabled(instrumental);
            boolean charge=instrumental&&"Charge / conductivity".equals(basis.getSelectedItem())&&hasChargeSource();
            boolean volts=instrumental&&"Voltage / capillary length".equals(basis.getSelectedItem());
            for(JComponent c:new JComponent[]{id,cond,condTemp,idLabel,condLabel,condTempLabel})c.setEnabled(charge);
            for(JComponent c:new JComponent[]{voltage,totalLength,runTemp,voltageLabel,lengthLabel,runTempLabel})c.setEnabled(volts);
            for(JComponent c:new JComponent[]{first,second,firstLabel,secondLabel})c.setEnabled(instrumental);
            if(instrumental&&!hasChargeSource()&&"Charge / conductivity".equals(basis.getSelectedItem())){basis.setSelectedItem("Voltage / capillary length");}
        };
        mode.addActionListener(e->update.run());basis.addActionListener(e->update.run());update.run();
        int answer=JOptionPane.showConfirmDialog(this,panel,"Mobility calibration",JOptionPane.OK_CANCEL_OPTION,JOptionPane.PLAIN_MESSAGE);if(answer!=JOptionPane.OK_OPTION)return false;
        try{
            mobilityMode=(String)mode.getSelectedItem();mobilityInstrumentBasis=(String)basis.getSelectedItem();
            capillaryIdUm=Double.parseDouble(id.getText().trim());bgeConductivitySPerM=Double.parseDouble(cond.getText().trim());bgeConductivityTemperatureC=Double.parseDouble(condTemp.getText().trim());
            appliedVoltageKv=Double.parseDouble(voltage.getText().trim());totalCapillaryLengthCm=Double.parseDouble(totalLength.getText().trim());runTemperatureC=Double.parseDouble(runTemp.getText().trim());
            leftDistanceCm=Double.parseDouble(first.getText().trim());rightDistanceCm=Double.parseDouble(second.getText().trim());
            saveMobilityPreferences();rebuildMobilityCalibration(false);
            if(peaksPanel!=null){peaksPanel.setMobilityReferences(mobilityRef1,mobilityRef2,mobilityMode);peaksPanel.setExportMetadata(mobilityExportMetadata());}
            if(mobilityCalibration==null&&!"Instrument parameters only".equals(mobilityMode))JOptionPane.showMessageDialog(this,"Calibration mode saved. Assign the required reference peak(s) directly in the Quantitation table of Peak analysis.","Mobility calibration",JOptionPane.INFORMATION_MESSAGE);
            return true;
        }catch(Exception ex){JOptionPane.showMessageDialog(this,ex.getMessage(),"Invalid mobility calibration",JOptionPane.ERROR_MESSAGE);return false;}
    }
    private double currentDetectorDistance(){return (selectedDetector()==DetectorChannel.LEFT?leftDistanceCm:rightDistanceCm)/100.0;}
    private String referenceSummary(){
        return referenceText("Ref1",mobilityRef1)+(mobilityRef2==null?"":"; "+referenceText("Ref2",mobilityRef2));
    }
    private String referenceText(String name,MobilityReference ref){
        if(ref==null)return "none";String q=Double.isFinite(ref.migrationChargeMilliCoulombs())?String.format(Locale.ROOT,", q=%.5g mC",ref.migrationChargeMilliCoulombs()):"";
        return String.format(Locale.ROOT,"%s t=%.6g min%s, mu=%.5g Ti at %.1f °C",name,ref.migrationTimeMinutes(),q,ref.effectiveMobilityTi(),ref.sourceTemperatureC());
    }
    private void editMobilityReference(String role,MobilityReference ref){
        if(ref==null)return;mobilityReferenceDetector=selectedDetector();
        if("—".equals(role)){
            if(sameReference(mobilityRef1,ref))mobilityRef1=null;
            if(sameReference(mobilityRef2,ref))mobilityRef2=null;
        }else if("Two effective-mobility standards".equals(mobilityMode)){
            if(sameReference(mobilityRef1,ref))mobilityRef1=null;if(sameReference(mobilityRef2,ref))mobilityRef2=null;
            if("Ref 1".equals(role))mobilityRef1=ref;else if("Ref 2".equals(role))mobilityRef2=ref;else return;
        }else if("Instrument parameters + one effective-mobility reference".equals(mobilityMode)){
            if(!"Reference".equals(role))return;mobilityRef1=ref;mobilityRef2=null;
        }else return;
        try{rebuildMobilityCalibration(false);statusLabel.setText("Mobility reference updated — "+referenceSummary());if(peaksPanel!=null){peaksPanel.setMobilityReferences(mobilityRef1,mobilityRef2,mobilityMode);peaksPanel.setExportMetadata(mobilityExportMetadata());}if(activeDomain==ElectropherogramDomain.MOBILITY&&mobilityCalibration!=null)applyDomain(activeDomain,false);}catch(Exception ex){statusLabel.setText("Mobility references updated; calibration incomplete: "+ex.getMessage());if(peaksPanel!=null){peaksPanel.setMobilityReferences(mobilityRef1,mobilityRef2,mobilityMode);peaksPanel.setExportMetadata(mobilityExportMetadata());}}
    }
    private boolean sameReference(MobilityReference a,MobilityReference b){if(a==null||b==null)return false;double ta=a.migrationTimeMinutes(),tb=b.migrationTimeMinutes();if(Double.isFinite(ta)&&Double.isFinite(tb)){double tol=Math.max(1e-9,Math.abs(tb)*1e-8);return Math.abs(ta-tb)<=tol;}double qa=a.migrationChargeMilliCoulombs(),qb=b.migrationChargeMilliCoulombs();if(!Double.isFinite(qa)||!Double.isFinite(qb))return false;double tol=Math.max(1e-9,Math.abs(qb)*1e-8);return Math.abs(qa-qb)<=tol;}
    private void rebuildMobilityCalibration(boolean complain){
        double ld=currentDetectorDistance();boolean chargeBasis="Charge / conductivity".equals(mobilityInstrumentBasis);
        if("Instrument parameters only".equals(mobilityMode)){
            if(chargeBasis){if(!hasChargeSource())throw new IllegalArgumentException("Charge/current data are not available; use Voltage / capillary length.");mobilityCalibration=MobilityCalibration.fromInstrumentAtTemperature(capillaryIdUm,bgeConductivitySPerM,bgeConductivityTemperatureC,ld);}
            else mobilityCalibration=MobilityCalibration.fromVoltage(appliedVoltageKv,totalCapillaryLengthCm/100.0,ld,runTemperatureC);
        }else if("Two effective-mobility standards".equals(mobilityMode)){
            if(mobilityRef1==null||mobilityRef2==null){mobilityCalibration=null;if(complain)throw new IllegalArgumentException("Select two peaks in Peak analysis and use them as mobility references.");return;}
            if(hasChargeSource()&&Double.isFinite(mobilityRef1.migrationChargeMilliCoulombs())&&mobilityRef1.migrationChargeMilliCoulombs()!=0&&Double.isFinite(mobilityRef2.migrationChargeMilliCoulombs())&&mobilityRef2.migrationChargeMilliCoulombs()!=0)mobilityCalibration=MobilityCalibration.fromTwoStandardsAtTemperatures(mobilityRef1.migrationChargeMilliCoulombs(),mobilityRef1.effectiveMobilityTi(),mobilityRef1.sourceTemperatureC(),mobilityRef2.migrationChargeMilliCoulombs(),mobilityRef2.effectiveMobilityTi(),mobilityRef2.sourceTemperatureC());
            else mobilityCalibration=MobilityCalibration.fromTwoStandardsByTimeAtTemperatures(mobilityRef1.migrationTimeMinutes(),mobilityRef1.effectiveMobilityTi(),mobilityRef1.sourceTemperatureC(),mobilityRef2.migrationTimeMinutes(),mobilityRef2.effectiveMobilityTi(),mobilityRef2.sourceTemperatureC());
        }else{
            if(mobilityRef1==null){mobilityCalibration=null;if(complain)throw new IllegalArgumentException("Select one peak in Peak analysis and use it as a mobility reference.");return;}
            if(chargeBasis){if(!hasChargeSource())throw new IllegalArgumentException("Charge/current data are not available; use Voltage / capillary length.");mobilityCalibration=MobilityCalibration.fromInstrumentWithReferenceAtTemperatures(capillaryIdUm,bgeConductivitySPerM,bgeConductivityTemperatureC,ld,mobilityRef1.migrationChargeMilliCoulombs(),mobilityRef1.effectiveMobilityTi(),mobilityRef1.sourceTemperatureC());}
            else mobilityCalibration=MobilityCalibration.fromVoltageWithReferenceAtTemperatures(appliedVoltageKv,totalCapillaryLengthCm/100.0,ld,runTemperatureC,mobilityRef1.migrationTimeMinutes(),mobilityRef1.effectiveMobilityTi(),mobilityRef1.sourceTemperatureC());
        }
        String kText=mobilityCalibration.usesCharge()?String.format(Locale.ROOT,"k = %.8g Ti·mC",mobilityCalibration.kTiMilliCoulombs()):String.format(Locale.ROOT,"k = %.8g Ti·min",mobilityCalibration.kTiMinutes());
        statusLabel.setText(String.format(Locale.ROOT,"%s at 25 °C calibration (%s): %s%s",mobilityCalibration.mobilityName(),mobilityCalibration.basisName(),kText,mobilityCalibration.isEffective()?String.format(Locale.ROOT," | EOF = %.8g Ti",mobilityCalibration.eofMobilityTi()):""));
    }
    private java.util.Map<String,String> mobilityExportMetadata(){
        java.util.LinkedHashMap<String,String> m=new java.util.LinkedHashMap<>();
        m.put("mobility.mode",mobilityMode);m.put("mobility.instrumental.basis",mobilityInstrumentBasis);m.put("mobility.reference.temperature.C","25.0");
        m.put("capillary.id.um",Double.toString(capillaryIdUm));m.put("bge.conductivity.S_per_m",Double.toString(bgeConductivitySPerM));m.put("bge.conductivity.temperature.C",Double.toString(bgeConductivityTemperatureC));
        m.put("applied.voltage.kV",Double.toString(appliedVoltageKv));m.put("capillary.total.length.cm",Double.toString(totalCapillaryLengthCm));m.put("run.temperature.C",Double.toString(runTemperatureC));
        m.put("distance.injection_to_1st_C4D.cm",Double.toString(leftDistanceCm));m.put("distance.injection_to_2nd_C4D.cm",Double.toString(rightDistanceCm));
        if(mobilityCalibration!=null)m.put("mobility.calibration.basis",mobilityCalibration.basisName());
        if(mobilityRef1!=null){m.put("mobility.ref1.label",mobilityRef1.label());m.put("mobility.ref1.time.min",Double.toString(mobilityRef1.migrationTimeMinutes()));m.put("mobility.ref1.charge.mC",Double.toString(mobilityRef1.migrationChargeMilliCoulombs()));m.put("mobility.ref1.mu_eff.Ti",Double.toString(mobilityRef1.effectiveMobilityTi()));m.put("mobility.ref1.temperature.C",Double.toString(mobilityRef1.sourceTemperatureC()));}
        if(mobilityRef2!=null){m.put("mobility.ref2.label",mobilityRef2.label());m.put("mobility.ref2.time.min",Double.toString(mobilityRef2.migrationTimeMinutes()));m.put("mobility.ref2.charge.mC",Double.toString(mobilityRef2.migrationChargeMilliCoulombs()));m.put("mobility.ref2.mu_eff.Ti",Double.toString(mobilityRef2.effectiveMobilityTi()));m.put("mobility.ref2.temperature.C",Double.toString(mobilityRef2.sourceTemperatureC()));}
        return m;
    }
    private static boolean mobilityPreferencesEnabled(){return !Boolean.getBoolean("zones.disablePreferences");}
    private void loadMobilityPreferences(){
        if(!mobilityPreferencesEnabled())return;
        mobilityMode=MOBILITY_PREFS.get("mode",mobilityMode);mobilityInstrumentBasis=MOBILITY_PREFS.get("basis",mobilityInstrumentBasis);
        capillaryIdUm=MOBILITY_PREFS.getDouble("capillaryIdUm",capillaryIdUm);bgeConductivitySPerM=MOBILITY_PREFS.getDouble("conductivity",bgeConductivitySPerM);bgeConductivityTemperatureC=MOBILITY_PREFS.getDouble("conductivityTempC",bgeConductivityTemperatureC);
        appliedVoltageKv=MOBILITY_PREFS.getDouble("voltageKv",appliedVoltageKv);totalCapillaryLengthCm=MOBILITY_PREFS.getDouble("totalLengthCm",totalCapillaryLengthCm);runTemperatureC=MOBILITY_PREFS.getDouble("runTempC",runTemperatureC);
        leftDistanceCm=MOBILITY_PREFS.getDouble("firstDistanceCm",leftDistanceCm);rightDistanceCm=MOBILITY_PREFS.getDouble("secondDistanceCm",rightDistanceCm);
    }
    private void saveMobilityPreferences(){
        if(!mobilityPreferencesEnabled())return;
        MOBILITY_PREFS.put("mode",mobilityMode);MOBILITY_PREFS.put("basis",mobilityInstrumentBasis);
        MOBILITY_PREFS.putDouble("capillaryIdUm",capillaryIdUm);MOBILITY_PREFS.putDouble("conductivity",bgeConductivitySPerM);MOBILITY_PREFS.putDouble("conductivityTempC",bgeConductivityTemperatureC);
        MOBILITY_PREFS.putDouble("voltageKv",appliedVoltageKv);MOBILITY_PREFS.putDouble("totalLengthCm",totalCapillaryLengthCm);MOBILITY_PREFS.putDouble("runTempC",runTemperatureC);
        MOBILITY_PREFS.putDouble("firstDistanceCm",leftDistanceCm);MOBILITY_PREFS.putDouble("secondDistanceCm",rightDistanceCm);
    }
    private void selectPeakFromChart(double x){
        if(peaksPanel==null||peaksPanel.result()==null){
            if(result!=null)JOptionPane.showMessageDialog(this,"Open Peak analysis first so the fitted components can be matched to the graph.","Peak selection",JOptionPane.INFORMATION_MESSAGE);
            return;
        }
        if(!peaksPanel.selectQuantitationByX(x)){
            JOptionPane.showMessageDialog(this,"No fitted component was found at the selected position.","Peak selection",JOptionPane.WARNING_MESSAGE);
        }else if(peaksDialog!=null&&!peaksDialog.isVisible())peaksDialog.setVisible(true);
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
                    clearPeaks();data=get();result=null;analysisResult=null;mobilityCalibration=null;mobilityRef1=null;mobilityRef2=null;mobilityReferenceDetector=null;activeDomain=ElectropherogramDomain.TIME;parameterPanel.setSamplingInterval(data.samplingIntervalSeconds());fileLabel.setText(data.source().getFileName().toString());fileLabel.setToolTipText(data.source().toString());
                    loading=true;detectorCombo.setModel(new DefaultComboBoxModel<>(data.detectors().toArray(DetectorChannel[]::new)));
                    DetectorChannel preferred=data.detectors().contains(DetectorChannel.RIGHT)&&!data.isConstant(DetectorChannel.RIGHT)?DetectorChannel.RIGHT:
                        data.detectors().contains(DetectorChannel.LEFT)?DetectorChannel.LEFT:data.detectors().iterator().next();
                    detectorCombo.setSelectedItem(preferred);loading=false;
                    diagnosticsArea.setText(String.format(Locale.ROOT,"%d points | median dt %.1f ms | %.3f Hz%nDefault detector: %s.",data.size(),data.samplingIntervalSeconds()*1000.0,data.samplingRateHz(),preferred));
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
        if(mobilityReferenceDetector!=null && mobilityReferenceDetector!=selectedDetector()){
            mobilityRef1=null;mobilityRef2=null;mobilityReferenceDetector=null;
            if(!"Instrument parameters only".equals(mobilityMode))mobilityCalibration=null;
        }
        if("Instrument parameters only".equals(mobilityMode)){try{rebuildMobilityCalibration(false);}catch(Exception ignored){mobilityCalibration=null;}}
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
            @Override protected AnalysisResult doInBackground(){return engine.analyze(input,detector,parameters);}
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
        statusLabel.setText(value.failedScales().isEmpty()?"Baseline calculated — " + value.detector():"Baseline calculated with one or more scale failures.");
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
            diagnosticsArea.append("\nTransformed domains preserve the natural sign of charge and mobility. Charge-axis inversion is an explicit display option.");
            if(tr.hasCurrentPolarityReversal(pre.cleaned())||!tr.signedChargeMonotonic())diagnosticsArea.append("\nWARNING: current polarity reversal/non-monotonic charge detected. Charge/mobility coordinates can be ambiguous for this run.");
            if(activeDomain==ElectropherogramDomain.MOBILITY)diagnosticsArea.append("\nMobility display/quantitation excludes transformed values with |mobility| > 1000 Ti.");
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
            peaksPanel=new PeakAnalysisPanel();peaksPanel.setInput(result,analysisResult);peaksPanel.setDomain(activeDomain,mobilityCalibration,invertChargeCheck.isSelected());peaksPanel.setMobilityReferences(mobilityRef1,mobilityRef2,mobilityMode);peaksPanel.setExportMetadata(mobilityExportMetadata());peaksPanel.onMobilityReferenceEdit(this::editMobilityReference);
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
    private void setBusy(boolean busy){openButton.setEnabled(!busy);peaksButton.setEnabled(!busy&&result!=null);detectorCombo.setEnabled(!busy && data!=null);processButton.setEnabled(!busy && data!=null && selectedDetector()!=null && !data.isConstant(selectedDetector()));parameterPanel.setBusy(busy);progressBar.setIndeterminate(busy);timeDomainButton.setEnabled(!busy);boolean available=!busy&&hasChargeSource();chargeDomainButton.setEnabled(available);mobilityDomainButton.setEnabled(available);mobilityCalibrationButton.setEnabled(available);invertChargeCheck.setEnabled(available);}
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
        openButton.setText("Open");
        filePanel.add(openButton);
        fileLabel.setText("No file open");
        filePanel.add(fileLabel);
        detectorLabel.setText("Detector:");
        filePanel.add(detectorLabel);
        filePanel.add(detectorCombo);
        processButton.setText("Calculate baseline");
        filePanel.add(processButton);
        peaksButton.setText("Analyze peaks");
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
