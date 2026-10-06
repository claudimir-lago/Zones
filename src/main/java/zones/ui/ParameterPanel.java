package zones.ui;

import java.util.Arrays;
import java.util.Locale;
import java.util.prefs.Preferences;
import javax.swing.*;
import zones.model.*;
import zones.parameters.ScaleGenerator;

/** Layout and individual fields are editable in NetBeans Design (.form). */
public class ParameterPanel extends JPanel {
    private static final Preferences PREFS=Preferences.userNodeForPackage(ParameterPanel.class).node("parameters");
    private static boolean preferencesEnabled(){return !Boolean.getBoolean("zones.disablePreferences");}
    private Runnable changed=()->{};
    private boolean updating;
    private String voteAdjustmentNotice="";
    private int[] lastGeneratedScales=new int[0];
    private double samplingIntervalSeconds=PhysicalBaselineParameters.REFERENCE_INTERVAL_SECONDS;
    public ParameterPanel() {
        initComponents();
        DomainTheme.themeButton(resetButton);
        DomainTheme.themeButton(applyButton);
        numStdSpinner.setModel(new SpinnerNumberModel(3.0,2.0,3.5,0.05));
        logLambdaSpinner.setModel(new SpinnerNumberModel(9.0,5.0,15.0,0.1));
        scaleCountSpinner.setModel(new SpinnerNumberModel(8,1,64,1));
        votesSpinner.setModel(new SpinnerNumberModel(2,1,8,1));
        for(JSpinner spinner:new JSpinner[]{minLengthSpinner,widthMinimumSpinner,widthMaximumSpinner,windowSpinner,runSpinner,durationSpinner})
            spinner.setModel(new SpinnerNumberModel(1.0,0.000001,86400.0,0.01));
        for(JSpinner spinner:new JSpinner[]{noiseSpinner,slopeSpinner})spinner.setModel(new SpinnerNumberModel(1.5,0.0,100.0,0.1));
        for(JSpinner spinner:spinners()) {
            if(spinner!=scaleCountSpinner && spinner!=votesSpinner){
                var editor=new JSpinner.NumberEditor(spinner,"0.######");
                editor.getFormat().setDecimalFormatSymbols(java.text.DecimalFormatSymbols.getInstance(java.util.Locale.ROOT));spinner.setEditor(editor);
            }
            JFormattedTextField text=((JSpinner.DefaultEditor)spinner.getEditor()).getTextField();
            if(text.getFormatter() instanceof javax.swing.text.NumberFormatter formatter)formatter.setAllowsInvalid(true);
        }
        lambdaSlider.setMinimum(50);lambdaSlider.setMaximum(150);lambdaSlider.setMajorTickSpacing(20);lambdaSlider.setPaintTicks(true);
        // Keep help on labels rather than on JSpinner itself. FlatLaf propagates a spinner's
        // tooltip to its arrow buttons, which produces distracting truncated popups.
        ParameterHelp.install(numStdLabel,"numStd");ParameterHelp.install(lambdaLabel,"lambda");
        ParameterHelp.install(minLengthLabel,"minLength");ParameterHelp.install(votesLabel,"votes");
        ParameterHelp.install(widthMinimumLabel,"width");ParameterHelp.install(widthMaximumLabel,"width");ParameterHelp.install(scaleCountLabel,"count");
        ParameterHelp.install(windowLabel,"window");ParameterHelp.install(noiseLabel,"noise");ParameterHelp.install(slopeLabel,"slope");ParameterHelp.install(runLabel,"run");
        ParameterHelp.install(durationLabel,"spikes");ParameterHelp.install(medianCheck,"spikes");ParameterHelp.install(chargeCheck,"charge");
        // Use a fixed editor width and a layout that reserves the spinner column.
        // GridLayout may compress spinner arrow buttons when the parameter panel is narrow.
        installStableParameterLayout();
        for(JSpinner spinner:spinners()) {
            configureSpinner(spinner);
        }
        settingsScroll.getVerticalScrollBar().setUnitIncrement(16);
        setAnalysisParameters(AnalysisParameters.defaults());
        loadPersistedSettings();
        resetButton.addActionListener(e->{setAnalysisParameters(AnalysisParameters.defaults());saveSettings();changed.run();});
        for(JSpinner spinner:spinners())spinner.addChangeListener(e->{
            if(updating)return;
            if(spinner==votesSpinner)voteAdjustmentNotice="";
            if(spinner==logLambdaSpinner){updating=true;lambdaSlider.setValue((int)Math.round(value(logLambdaSpinner)*10));updating=false;}
            updateConversions();changed.run();
        });
        lambdaSlider.addChangeListener(e->{if(!updating)logLambdaSpinner.setValue(lambdaSlider.getValue()/10.0);});
        medianCheck.addActionListener(e->{updateConversions();changed.run();});chargeCheck.addActionListener(e->changed.run());
        advancedCheck.addActionListener(e->{conversionArea.setVisible(advancedCheck.isSelected());revalidate();});
        conversionArea.setVisible(false);
        localPanel.setVisible(false); // Local anchor recovery was removed after TestHVL09 validation.
    }
    private static void disableTooltips(java.awt.Component component){
        if(component instanceof JComponent jc){
            jc.setToolTipText(null);
            ToolTipManager.sharedInstance().unregisterComponent(jc);
        }
        if(component instanceof java.awt.Container container)
            for(java.awt.Component child:container.getComponents())disableTooltips(child);
    }
    private static void configureSpinner(JSpinner spinner){
        java.awt.Dimension size=new java.awt.Dimension(132,28);
        spinner.setPreferredSize(size);spinner.setMinimumSize(size);
        if(spinner.getEditor() instanceof JSpinner.DefaultEditor editor){
            editor.getTextField().setColumns(9);
            editor.getTextField().setHorizontalAlignment(JTextField.RIGHT);
        }
        disableTooltips(spinner);
    }
    private void installStableParameterLayout(){
        valuesPanel.removeAll();
        valuesPanel.setLayout(new java.awt.GridBagLayout());
        java.awt.GridBagConstraints label=new java.awt.GridBagConstraints();
        label.gridx=0;label.weightx=1.0;label.fill=java.awt.GridBagConstraints.HORIZONTAL;
        label.anchor=java.awt.GridBagConstraints.LINE_START;label.insets=new java.awt.Insets(3,2,3,10);
        java.awt.GridBagConstraints field=new java.awt.GridBagConstraints();
        field.gridx=1;field.weightx=0.0;field.fill=java.awt.GridBagConstraints.NONE;
        field.anchor=java.awt.GridBagConstraints.LINE_END;field.insets=new java.awt.Insets(3,2,3,2);
        javax.swing.JLabel[] labels={numStdLabel,minLengthLabel,widthMinimumLabel,widthMaximumLabel,scaleCountLabel,votesLabel};
        JSpinner[] fields={numStdSpinner,minLengthSpinner,widthMinimumSpinner,widthMaximumSpinner,scaleCountSpinner,votesSpinner};
        for(int row=0;row<labels.length;row++){
            label.gridy=row;field.gridy=row;valuesPanel.add(labels[row],label);valuesPanel.add(fields[row],field);
        }
        durationPanel.removeAll();durationPanel.setLayout(new java.awt.BorderLayout(10,0));
        durationPanel.add(durationLabel,java.awt.BorderLayout.CENTER);durationPanel.add(durationSpinner,java.awt.BorderLayout.EAST);
        setMinimumSize(new java.awt.Dimension(430,520));
        setPreferredSize(new java.awt.Dimension(445,520));
    }
    private void loadPersistedSettings(){
        if(!preferencesEnabled())return;
        updating=true;
        try{
            numStdSpinner.setValue(PREFS.getDouble("numStd",value(numStdSpinner)));
            minLengthSpinner.setValue(PREFS.getDouble("minimumBaselineSeconds",value(minLengthSpinner)));
            votesSpinner.setValue(PREFS.getInt("votes",(int)value(votesSpinner)));
            widthMinimumSpinner.setValue(PREFS.getDouble("minimumPeakWidthSeconds",value(widthMinimumSpinner)));
            widthMaximumSpinner.setValue(PREFS.getDouble("maximumPeakWidthSeconds",value(widthMaximumSpinner)));
            scaleCountSpinner.setValue(PREFS.getInt("scaleCount",(int)value(scaleCountSpinner)));
            logLambdaSpinner.setValue(PREFS.getDouble("logLambda",value(logLambdaSpinner)));
            lambdaSlider.setValue((int)Math.round(value(logLambdaSpinner)*10));
            windowSpinner.setValue(PREFS.getDouble("localWindowSeconds",value(windowSpinner)));
            noiseSpinner.setValue(PREFS.getDouble("noiseFactor",value(noiseSpinner)));
            slopeSpinner.setValue(PREFS.getDouble("slopeFactor",value(slopeSpinner)));
            runSpinner.setValue(PREFS.getDouble("minimumRunSeconds",value(runSpinner)));
            durationSpinner.setValue(PREFS.getDouble("mmrDurationSeconds",value(durationSpinner)));
            medianCheck.setSelected(PREFS.getBoolean("mmrEnabled",medianCheck.isSelected()));
            chargeCheck.setSelected(PREFS.getBoolean("recalculateCharge",chargeCheck.isSelected()));
        }finally{updating=false;}
        updateConversions();
    }
    private void saveSettings(){
        if(!preferencesEnabled())return;
        PREFS.putDouble("numStd",value(numStdSpinner));
        PREFS.putDouble("minimumBaselineSeconds",value(minLengthSpinner));
        PREFS.putInt("votes",(int)value(votesSpinner));
        PREFS.putDouble("minimumPeakWidthSeconds",value(widthMinimumSpinner));
        PREFS.putDouble("maximumPeakWidthSeconds",value(widthMaximumSpinner));
        PREFS.putInt("scaleCount",(int)value(scaleCountSpinner));
        PREFS.putDouble("logLambda",value(logLambdaSpinner));
        PREFS.putDouble("localWindowSeconds",value(windowSpinner));
        PREFS.putDouble("noiseFactor",value(noiseSpinner));
        PREFS.putDouble("slopeFactor",value(slopeSpinner));
        PREFS.putDouble("minimumRunSeconds",value(runSpinner));
        PREFS.putDouble("mmrDurationSeconds",value(durationSpinner));
        PREFS.putBoolean("mmrEnabled",medianCheck.isSelected());
        PREFS.putBoolean("recalculateCharge",chargeCheck.isSelected());
    }
    private static String formatSeconds(double seconds){
        return String.format(Locale.ROOT,seconds<10?"%.3f":seconds<100?"%.2f":"%.1f",seconds);
    }
    private static String formatScalesSeconds(int[] scales,double dtSeconds){
        StringBuilder out=new StringBuilder("[");
        for(int i=0;i<scales.length;i++){
            if(i>0)out.append(", ");
            double seconds=scales[i]*dtSeconds;
            out.append(formatSeconds(seconds));
        }
        return out.append("]").toString();
    }
    private JSpinner[] spinners(){return new JSpinner[]{numStdSpinner,minLengthSpinner,votesSpinner,widthMinimumSpinner,widthMaximumSpinner,scaleCountSpinner,logLambdaSpinner,windowSpinner,noiseSpinner,slopeSpinner,runSpinner,durationSpinner};}
    public void onChanged(Runnable action){changed=()->{saveSettings();action.run();};}
    public void onApply(Runnable action){applyButton.addActionListener(e->action.run());}
    public void setBusy(boolean busy){for(JSpinner field:spinners())field.setEnabled(!busy);lambdaSlider.setEnabled(!busy);medianCheck.setEnabled(!busy);chargeCheck.setEnabled(!busy);applyButton.setEnabled(!busy);resetButton.setEnabled(!busy);}
    private static double value(JSpinner field){return ((Number)field.getValue()).doubleValue();}
    private PhysicalBaselineParameters physicalParameters() {
        return new PhysicalBaselineParameters(value(numStdSpinner),value(minLengthSpinner),(int)value(votesSpinner),value(widthMinimumSpinner),value(widthMaximumSpinner),
                (int)value(scaleCountSpinner),value(logLambdaSpinner),value(windowSpinner),value(noiseSpinner),value(slopeSpinner),value(runSpinner));
    }
    public AnalysisParameters analysisParameters() {
        try { for(JSpinner spinner:spinners())spinner.commitEdit(); }
        catch(java.text.ParseException e){throw new IllegalArgumentException("Check the parameter values.",e);}
        var parameters=new AnalysisParameters(physicalParameters(),new SpikeRemovalParameters(medianCheck.isSelected(),value(durationSpinner)),chargeCheck.isSelected());
        parameters.baseline().toInternal(samplingIntervalSeconds);
        return parameters;
    }
    public BaselineParameters parameters(){return analysisParameters().baseline().toInternal(samplingIntervalSeconds);}
    public void setSamplingInterval(double seconds){samplingIntervalSeconds=seconds;updateConversions();}
    public void setAnalysisParameters(AnalysisParameters a) {
        voteAdjustmentNotice="";lastGeneratedScales=new int[0];
        updating=true;
        try {
            var p=a.baseline();numStdSpinner.setValue(p.numStd());minLengthSpinner.setValue(p.minimumLengthSeconds());
            widthMinimumSpinner.setValue(p.peakWidthMinimumSeconds());widthMaximumSpinner.setValue(p.peakWidthMaximumSeconds());scaleCountSpinner.setValue(p.numberOfScales());
            ((SpinnerNumberModel)votesSpinner.getModel()).setMaximum(64);votesSpinner.setValue(p.voteThresholdK());logLambdaSpinner.setValue(p.logLambda());lambdaSlider.setValue((int)Math.round(10*p.logLambda()));
            windowSpinner.setValue(p.localWindowSeconds());noiseSpinner.setValue(p.noiseFactor());slopeSpinner.setValue(p.slopeFactor());runSpinner.setValue(p.minimumRunSeconds());
            medianCheck.setSelected(a.spikeRemoval().enabled());durationSpinner.setValue(a.spikeRemoval().durationSeconds());chargeCheck.setSelected(a.recalculateCharge());
        }finally{updating=false;}
        updateConversions();
    }
    private void updateConversions() {
        if(updating)return;
        updating=true;
        try {
            int requested=(int)value(scaleCountSpinner);
            int[] scales=new ScaleGenerator().generate(value(widthMinimumSpinner),value(widthMaximumSpinner),requested,samplingIntervalSeconds);
            if(!Arrays.equals(scales,lastGeneratedScales))voteAdjustmentNotice="";
            lastGeneratedScales=scales;
            int previousK=(int)value(votesSpinner);boolean adjusted=previousK>scales.length;
            if(adjusted){votesSpinner.setValue(scales.length);voteAdjustmentNotice="K adjusted from "+previousK+" to "+scales.length+".";}
            ((SpinnerNumberModel)votesSpinner.getModel()).setMaximum(scales.length);
            var spikes=new SpikeRemovalParameters(medianCheck.isSelected(),value(durationSpinner));
            String scaleRange=scales.length==1?formatSeconds(scales[0]*samplingIntervalSeconds)+" s":
                    formatSeconds(scales[0]*samplingIntervalSeconds)+"–"+formatSeconds(scales[scales.length-1]*samplingIntervalSeconds)+" s";
            conversionStatus.setText(voteAdjustmentNotice.isEmpty()?scales.length+" scales ("+scaleRange+")":voteAdjustmentNotice+" ("+scaleRange+")");
            conversionArea.setText(String.format(Locale.ROOT,
                    "median dt = %.1f ms%nScales (s): %s%nMinimum baseline = %.4g s%nMMR: %s",
                    samplingIntervalSeconds*1000.0,formatScalesSeconds(scales,samplingIntervalSeconds),value(minLengthSpinner),
                    spikes.enabled()?String.format(Locale.ROOT,"enabled; characteristic duration = %.4g s",spikes.durationSeconds()):"disabled"));
        }catch(IllegalArgumentException e){conversionStatus.setText("Check durations and the peak-width range.");conversionArea.setText(e.getMessage());}
        finally{updating=false;}
    }
    @SuppressWarnings("unchecked")
    // <editor-fold defaultstate="collapsed" desc="Generated Code">//GEN-BEGIN:initComponents
    private void initComponents() {

        settingsScroll = new javax.swing.JScrollPane();
        contentPanel = new javax.swing.JPanel();
        spikePanel = new javax.swing.JPanel();
        medianCheck = new javax.swing.JCheckBox();
        durationPanel = new javax.swing.JPanel();
        durationLabel = new javax.swing.JLabel();
        durationSpinner = new javax.swing.JSpinner();
        chargeCheck = new javax.swing.JCheckBox();
        baselinePanel = new javax.swing.JPanel();
        valuesPanel = new javax.swing.JPanel();
        numStdLabel = new javax.swing.JLabel();
        numStdSpinner = new javax.swing.JSpinner();
        minLengthLabel = new javax.swing.JLabel();
        minLengthSpinner = new javax.swing.JSpinner();
        widthMinimumLabel = new javax.swing.JLabel();
        widthMinimumSpinner = new javax.swing.JSpinner();
        widthMaximumLabel = new javax.swing.JLabel();
        widthMaximumSpinner = new javax.swing.JSpinner();
        scaleCountLabel = new javax.swing.JLabel();
        scaleCountSpinner = new javax.swing.JSpinner();
        votesLabel = new javax.swing.JLabel();
        votesSpinner = new javax.swing.JSpinner();
        smoothnessPanel = new javax.swing.JPanel();
        lambdaLabel = new javax.swing.JLabel();
        lambdaSlider = new javax.swing.JSlider();
        logLambdaSpinner = new javax.swing.JSpinner();
        conversionPanel = new javax.swing.JPanel();
        conversionStatus = new javax.swing.JLabel();
        advancedCheck = new javax.swing.JCheckBox();
        conversionArea = new javax.swing.JTextArea();
        localPanel = new javax.swing.JPanel();
        windowLabel = new javax.swing.JLabel();
        windowSpinner = new javax.swing.JSpinner();
        noiseLabel = new javax.swing.JLabel();
        noiseSpinner = new javax.swing.JSpinner();
        slopeLabel = new javax.swing.JLabel();
        slopeSpinner = new javax.swing.JSpinner();
        runLabel = new javax.swing.JLabel();
        runSpinner = new javax.swing.JSpinner();
        actionsPanel = new javax.swing.JPanel();
        resetButton = new javax.swing.JButton();
        applyButton = new javax.swing.JButton();

        setBorder(javax.swing.BorderFactory.createTitledBorder("Parameters"));
        setPreferredSize(new java.awt.Dimension(445, 520));
        setLayout(new java.awt.BorderLayout(4, 4));

        contentPanel.setLayout(new java.awt.BorderLayout(4, 8));

        spikePanel.setBorder(javax.swing.BorderFactory.createTitledBorder("Outlier removal"));
        spikePanel.setLayout(new java.awt.BorderLayout(4, 4));

        medianCheck.setText("Enable selective MMR");
        medianCheck.setSelected(true);
        spikePanel.add(medianCheck, java.awt.BorderLayout.NORTH);

        durationPanel.setLayout(new java.awt.GridLayout(1, 2, 4, 4));

        durationLabel.setText("Characteristic duration (s)");
        durationPanel.add(durationLabel);
        durationPanel.add(durationSpinner);

        spikePanel.add(durationPanel, java.awt.BorderLayout.CENTER);

        chargeCheck.setText("Recalculate charge from current");
        spikePanel.add(chargeCheck, java.awt.BorderLayout.SOUTH);

        contentPanel.add(spikePanel, java.awt.BorderLayout.NORTH);

        baselinePanel.setBorder(javax.swing.BorderFactory.createTitledBorder("Baseline"));
        baselinePanel.setLayout(new java.awt.BorderLayout(4, 8));

        valuesPanel.setLayout(new java.awt.GridLayout(6, 2, 6, 6));

        numStdLabel.setText("FABC threshold (σ)");
        valuesPanel.add(numStdLabel);
        valuesPanel.add(numStdSpinner);

        minLengthLabel.setText("Minimum baseline (s)");
        valuesPanel.add(minLengthLabel);
        valuesPanel.add(minLengthSpinner);

        widthMinimumLabel.setText("Minimum peak width (s)");
        valuesPanel.add(widthMinimumLabel);
        valuesPanel.add(widthMinimumSpinner);

        widthMaximumLabel.setText("Maximum peak width (s)");
        valuesPanel.add(widthMaximumLabel);
        valuesPanel.add(widthMaximumSpinner);

        scaleCountLabel.setText("Number of scales");
        valuesPanel.add(scaleCountLabel);
        valuesPanel.add(scaleCountSpinner);

        votesLabel.setText("K (votes)");
        valuesPanel.add(votesLabel);
        valuesPanel.add(votesSpinner);

        baselinePanel.add(valuesPanel, java.awt.BorderLayout.NORTH);

        smoothnessPanel.setLayout(new java.awt.BorderLayout(4, 4));

        lambdaLabel.setText("Baseline stiffness — log(λ)");
        smoothnessPanel.add(lambdaLabel, java.awt.BorderLayout.NORTH);
        smoothnessPanel.add(lambdaSlider, java.awt.BorderLayout.CENTER);
        smoothnessPanel.add(logLambdaSpinner, java.awt.BorderLayout.SOUTH);

        baselinePanel.add(smoothnessPanel, java.awt.BorderLayout.CENTER);

        conversionPanel.setLayout(new java.awt.BorderLayout(4, 4));

        conversionStatus.setText("Generated scales");
        conversionPanel.add(conversionStatus, java.awt.BorderLayout.NORTH);

        advancedCheck.setText("Details");
        conversionPanel.add(advancedCheck, java.awt.BorderLayout.CENTER);

        conversionArea.setEditable(false);
        conversionArea.setRows(5);
        conversionArea.setColumns(24);
        conversionArea.setLineWrap(true);
        conversionArea.setWrapStyleWord(true);
        conversionPanel.add(conversionArea, java.awt.BorderLayout.SOUTH);

        baselinePanel.add(conversionPanel, java.awt.BorderLayout.SOUTH);

        contentPanel.add(baselinePanel, java.awt.BorderLayout.CENTER);

        localPanel.setBorder(javax.swing.BorderFactory.createTitledBorder("Local recovery (disabled)"));
        localPanel.setLayout(new java.awt.GridLayout(4, 2, 6, 6));

        windowLabel.setText("Local window (s)");
        localPanel.add(windowLabel);
        localPanel.add(windowSpinner);

        noiseLabel.setText("Noise factor");
        localPanel.add(noiseLabel);
        localPanel.add(noiseSpinner);

        slopeLabel.setText("Slope factor");
        localPanel.add(slopeLabel);
        localPanel.add(slopeSpinner);

        runLabel.setText("Minimum run (s)");
        localPanel.add(runLabel);
        localPanel.add(runSpinner);

        contentPanel.add(localPanel, java.awt.BorderLayout.SOUTH);

        settingsScroll.setViewportView(contentPanel);

        add(settingsScroll, java.awt.BorderLayout.CENTER);

        actionsPanel.setLayout(new java.awt.FlowLayout(java.awt.FlowLayout.RIGHT, 6, 6));

        resetButton.setText("Restore defaults");
        actionsPanel.add(resetButton);

        applyButton.setText("Recalculate");
        actionsPanel.add(applyButton);

        add(actionsPanel, java.awt.BorderLayout.SOUTH);
    }// </editor-fold>//GEN-END:initComponents
    // Variables declaration - do not modify//GEN-BEGIN:variables
    private javax.swing.JPanel actionsPanel;
    private javax.swing.JCheckBox advancedCheck;
    private javax.swing.JButton applyButton;
    private javax.swing.JPanel baselinePanel;
    private javax.swing.JCheckBox chargeCheck;
    private javax.swing.JPanel contentPanel;
    private javax.swing.JTextArea conversionArea;
    private javax.swing.JPanel conversionPanel;
    private javax.swing.JLabel conversionStatus;
    private javax.swing.JLabel durationLabel;
    private javax.swing.JPanel durationPanel;
    private javax.swing.JSpinner durationSpinner;
    private javax.swing.JLabel lambdaLabel;
    private javax.swing.JSlider lambdaSlider;
    private javax.swing.JPanel localPanel;
    private javax.swing.JSpinner logLambdaSpinner;
    private javax.swing.JCheckBox medianCheck;
    private javax.swing.JLabel minLengthLabel;
    private javax.swing.JSpinner minLengthSpinner;
    private javax.swing.JLabel noiseLabel;
    private javax.swing.JSpinner noiseSpinner;
    private javax.swing.JLabel numStdLabel;
    private javax.swing.JSpinner numStdSpinner;
    private javax.swing.JButton resetButton;
    private javax.swing.JLabel runLabel;
    private javax.swing.JSpinner runSpinner;
    private javax.swing.JLabel scaleCountLabel;
    private javax.swing.JSpinner scaleCountSpinner;
    private javax.swing.JScrollPane settingsScroll;
    private javax.swing.JLabel slopeLabel;
    private javax.swing.JSpinner slopeSpinner;
    private javax.swing.JPanel smoothnessPanel;
    private javax.swing.JPanel spikePanel;
    private javax.swing.JPanel valuesPanel;
    private javax.swing.JLabel votesLabel;
    private javax.swing.JSpinner votesSpinner;
    private javax.swing.JLabel widthMaximumLabel;
    private javax.swing.JSpinner widthMaximumSpinner;
    private javax.swing.JLabel widthMinimumLabel;
    private javax.swing.JSpinner widthMinimumSpinner;
    private javax.swing.JLabel windowLabel;
    private javax.swing.JSpinner windowSpinner;
    // End of variables declaration//GEN-END:variables
}
