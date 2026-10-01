package zones.ui;

import java.awt.BorderLayout;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.nio.file.Path;
import java.util.*;
import java.util.function.Consumer;
import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import zones.model.*;
import zones.application.PeakAnalysisProcessor;
import zones.io.PeakAnalysisExporter;
import zones.processing.DomainPeakCalculator;
import org.jfree.chart.*;
import org.jfree.chart.axis.LogAxis;
import org.jfree.chart.plot.*;
import org.jfree.data.xy.*;

/** NetBeans-editable peak-analysis view. All processing is delegated to a worker/service. */
public class PeakAnalysisPanel extends JPanel {
    private BaselineResult baseline;
    private AnalysisResult analysis;
    private PeakAnalysisResult result;
    private Consumer<PeakFit> selection=fit->{};
    private Runnable overlayChanged=()->{};
    private Consumer<PeakFit> zoomToWindow=fit->{};
    private long analysisGeneration=0;
    private boolean rebuildingTables=false;
    private final java.util.List<PeakFit> rowFits=new ArrayList<>();
    private boolean busy;
    private final JTable quantTable=new JTable();
    private final JCheckBox nCheck=new JCheckBox("Plates (N)",true);
    private final JCheckBox ngauCheck=new JCheckBox("Gaussian plates (NGau)",false);
    private final JCheckBox fitDetailsCheck=new JCheckBox("Fit details",false);
    private final JButton exportTableButton=new JButton("Export table…");
    private ElectropherogramDomain activeDomain=ElectropherogramDomain.TIME;
    private MobilityCalibration mobilityCalibration;
    public PeakAnalysisPanel(){
        initComponents();
        quantTable.setAutoCreateRowSorter(true);quantTable.setAutoResizeMode(JTable.AUTO_RESIZE_OFF);
        tabs.insertTab("Quantitation",null,new JScrollPane(quantTable),"Primary analytical results",0);
        controlsPanel.add(nCheck);controlsPanel.add(ngauCheck);controlsPanel.add(fitDetailsCheck);controlsPanel.add(exportTableButton);
        nCheck.addActionListener(e->{if(result!=null)display(result);});ngauCheck.addActionListener(e->{if(result!=null)display(result);});fitDetailsCheck.addActionListener(e->{if(result!=null)display(result);});
        exportTableButton.addActionListener(e->exportTable());
        thresholdSpinner.setModel(new SpinnerNumberModel(10.0,.01,1000000.0,1.0));
        var thresholdEditor=new JSpinner.NumberEditor(thresholdSpinner,"0.######");thresholdEditor.getFormat().setDecimalFormatSymbols(java.text.DecimalFormatSymbols.getInstance(Locale.ROOT));thresholdSpinner.setEditor(thresholdEditor);
        thresholdSpinner.setToolTipText("LPNR = local prominence / local sigma. Default 10; increasing it selects fewer candidates. It is not analytical SNR or LOD/LOQ.");
        analyzeButton.addActionListener(e->analyze());exportButton.addActionListener(e->export());
        thresholdSpinner.addChangeListener(e->{if(result!=null&&!busy)analyze();});
        componentsCheck.addActionListener(e->overlayChanged.run());sumCheck.addActionListener(e->overlayChanged.run());markersCheck.addActionListener(e->overlayChanged.run());
        resultsTable.setAutoResizeMode(JTable.AUTO_RESIZE_OFF);resultsTable.setAutoCreateRowSorter(true);
        candidatesTable.setAutoResizeMode(JTable.AUTO_RESIZE_OFF);candidatesTable.setAutoCreateRowSorter(true);
        windowsTable.setAutoResizeMode(JTable.AUTO_RESIZE_OFF);windowsTable.setAutoCreateRowSorter(true);
        for(JTable table:new JTable[]{resultsTable,windowsTable,candidatesTable})table.setDefaultRenderer(Double.class,new javax.swing.table.DefaultTableCellRenderer(){
            private final java.text.DecimalFormat format=new java.text.DecimalFormat("0.000000",java.text.DecimalFormatSymbols.getInstance(Locale.ROOT));
            @Override protected void setValue(Object value){setHorizontalAlignment(SwingConstants.RIGHT);setText(value instanceof Double number&&Double.isFinite(number)?format.format(number):"unavailable");}
        });
        resultsTable.getSelectionModel().addListSelectionListener(e->{if(!e.getValueIsAdjusting()&&resultsTable.getSelectedRow()>=0)selection.accept(rowFits.get(resultsTable.convertRowIndexToModel(resultsTable.getSelectedRow())));});
        windowsTable.getSelectionModel().addListSelectionListener(e->{if(!e.getValueIsAdjusting()&&result!=null&&windowsTable.getSelectedRow()>=0)selection.accept(result.fits().get(windowsTable.convertRowIndexToModel(windowsTable.getSelectedRow())));});
        MouseAdapter zoomListener=new MouseAdapter(){@Override public void mouseClicked(MouseEvent e){if(e.getClickCount()!=2||result==null)return;JTable t=(JTable)e.getSource();int vr=t.rowAtPoint(e.getPoint());if(vr<0)return;int mr=t.convertRowIndexToModel(vr);PeakFit f=null;if(t==resultsTable&&mr<rowFits.size())f=rowFits.get(mr);else if(t==quantTable){int k=0;outer:for(var fit:result.fits())for(var c:fit.components()){if(k++==mr){f=fit;break outer;}}}else if(t==windowsTable&&mr<result.fits().size())f=result.fits().get(mr);if(f!=null){selection.accept(f);zoomToWindow.accept(f);}}};
        resultsTable.addMouseListener(zoomListener);quantTable.addMouseListener(zoomListener);windowsTable.addMouseListener(zoomListener);
        exportButton.setEnabled(false);
    }
    public void setInput(BaselineResult baseline,AnalysisResult analysis){this.baseline=baseline;this.analysis=analysis;}
    public void setDomain(ElectropherogramDomain domain,MobilityCalibration calibration){
        this.activeDomain=domain==null?ElectropherogramDomain.TIME:domain;this.mobilityCalibration=calibration;
        if(result!=null)display(result);
    }
    public void onSelection(Consumer<PeakFit> action){selection=action;}
    public void onOverlayChanged(Runnable action){overlayChanged=action;}
    public void onZoomToWindow(Consumer<PeakFit> action){zoomToWindow=action==null?fit->{}:action;}
    public boolean componentsVisible(){return componentsCheck.isSelected();}
    public boolean sumVisible(){return sumCheck.isSelected();}
    public boolean markersVisible(){return markersCheck.isSelected();}
    public PeakAnalysisResult result(){return result;}
    public void analyze(){
        if(baseline==null||busy)return;
        PeakParameters p=PeakParameters.defaults().withThreshold(((Number)thresholdSpinner.getValue()).doubleValue());
        var snapshot=baseline;long generation=++analysisGeneration;result=null;setBusy(true);statusLabel.setText("Detecting candidates and fitting peak windows…");
        new SwingWorker<PeakAnalysisResult,Void>(){
            @Override protected PeakAnalysisResult doInBackground(){return new PeakAnalysisProcessor().process(snapshot,p);}
            @Override protected void done(){try{PeakAnalysisResult r=get();if(generation==analysisGeneration&&snapshot==baseline)display(r);}catch(Exception ex){statusLabel.setText("Failure: "+(ex.getCause()==null?ex.getMessage():ex.getCause().getMessage()));}finally{if(generation==analysisGeneration)setBusy(false);}}
        }.execute();
    }
    public void display(PeakAnalysisResult value){
        result=value;rowFits.clear();rebuildingTables=true;
        var table=model("Window","LPNR rank","Prom. rank","Polarity","Status","Observed apex (min)","Migration time a1 (min)","Fitted apex (min)","Local prominence (a.u.)","Local sigma (a.u.)","LPNR","W50 (s)","eta","a2 (s)","a3 (s)","Area (a.u.·s)","Captured (%)","N","NGau","FWHM (s)","R²","RMS (a.u.)","Type");
        var windowModel=windowModel("Window","Type","Start (min)","End (min)","Components","Status","Offset (a.u.)","Experimental algebraic area (a.u.·s)","Experimental absolute area (a.u.·s)","Fitted algebraic area (a.u.·s)","Absolute component sum (a.u.·s)","Isolated difference (%)","R²","RMS (a.u.)","Evaluations","Converged","Diagnostic");
        for(var f:value.fits()){
            String state=f.reliable()?"Fitted — inspect":"UNRELIABLE (diagnostic)";
            windowModel.addRow(new Object[]{f.window().id(),f.window().type(),f.window().startSeconds()/60,f.window().endSeconds()/60,f.components().size(),state,f.offset(),f.experimentalAlgebraicArea(),f.experimentalAbsoluteArea(),f.fittedAlgebraicArea(),f.absoluteComponentSum(),f.isolatedAreaDifferencePercent(),f.r2(),f.rms(),f.evaluations(),f.success(),f.message()});
            for(var c:f.components()){var d=c.candidate();rowFits.add(f);table.addRow(new Object[]{f.window().id(),d.rank(),d.prominenceRank(),d.polarity()>0?"+":"−",state,d.observedApexSeconds()/60,c.a1Seconds()/60,c.fittedApexSeconds()/60,d.localProminence(),d.localNoiseSigma(),d.lpnr(),d.w50Seconds(),c.eta(),c.a2Seconds(),c.a3Seconds(),c.signedArea(),100*c.capturedFraction(),c.effectivePlates(),c.gaussianPlates(),c.fwhmSeconds(),f.r2(),f.rms(),f.window().type()});}
        }
        var metricCalculator=new DomainPeakCalculator(baseline.data(),activeDomain,mobilityCalibration);
        String positionLabel=activeDomain==ElectropherogramDomain.TIME?"Migration time (min)":activeDomain==ElectropherogramDomain.CHARGE?"Migration charge |q| (mC)":"Mobility |mu| (Ti)";
        String areaLabel=activeDomain==ElectropherogramDomain.TIME?"Area (a.u.·s)":activeDomain==ElectropherogramDomain.CHARGE?"Area (a.u.·mC)":"Area (a.u.·Ti)";
        String widthLabel=activeDomain==ElectropherogramDomain.TIME?"FWHM (s)":activeDomain==ElectropherogramDomain.CHARGE?"FWHM (mC)":"FWHM (Ti)";
        java.util.List<String> qc=new ArrayList<>(java.util.List.of(positionLabel,areaLabel));
        if(nCheck.isSelected())qc.add("Plates (N, time fit)");if(ngauCheck.isSelected())qc.add("Gaussian plates (NGau, time fit)");
        if(fitDetailsCheck.isSelected()){qc.add(widthLabel);qc.add("R² (time fit)");}
        var qm=model(qc.toArray(String[]::new));
        for(var f:value.fits())for(var c:f.components()){
            var metrics=metricCalculator.metrics(c);java.util.List<Object> row=new ArrayList<>();row.add(metrics.position());row.add(metrics.signedArea());
            if(nCheck.isSelected())row.add(c.effectivePlates());if(ngauCheck.isSelected())row.add(c.gaussianPlates());
            if(fitDetailsCheck.isSelected()){row.add(metrics.fwhm());row.add(f.r2());}qm.addRow(row.toArray());
        }
        quantTable.setModel(qm);for(int i=0;i<quantTable.getColumnCount();i++)quantTable.getColumnModel().getColumn(i).setPreferredWidth(150);
        resultsTable.setModel(table);windowsTable.setModel(windowModel);
        windowModel.addTableModelListener(e->{if(rebuildingTables||busy||e.getType()!=javax.swing.event.TableModelEvent.UPDATE||e.getColumn()!=4)return;int row=e.getFirstRow();try{int count=Integer.parseInt(windowModel.getValueAt(row,4).toString());refitWindowComponentCount(row,count);}catch(Exception ex){statusLabel.setText("Component-count refit failed: "+ex.getMessage());display(result);}});
        var candidateModel=model("LPNR rank","Prom. rank","Polarity","Observed apex (min)","Local prominence (a.u.)","Local sigma (a.u.)","Noise points","LPNR","BES","W50 (s)","Status");
        XYSeries series=new XYSeries("LPNR");
        for(var c:value.candidates()){
            candidateModel.addRow(new Object[]{c.rank(),c.prominenceRank(),c.polarity()>0?"+":"−",c.observedApexSeconds()/60,c.localProminence(),c.localNoiseSigma(),c.noisePointCount(),c.lpnr(),c.bes(),c.w50Seconds(),c.selected(value.parametersUsed().lpnrThreshold())?"Selected":"Candidate"});
            if(Double.isFinite(c.lpnr())&&c.lpnr()>0)series.add(c.rank(),c.lpnr());
        }
        candidatesTable.setModel(candidateModel);
        for(JTable tab:new JTable[]{resultsTable,windowsTable,candidatesTable})for(int i=0;i<tab.getColumnCount();i++)tab.getColumnModel().getColumn(i).setPreferredWidth(i==4?215:150);
        for(int i=0;i<4;i++)resultsTable.getColumnModel().getColumn(i).setPreferredWidth(i==0?65:90);
        var chart=ChartFactory.createScatterPlot("LPNR — detection metric", "LPNR rank","LPNR",new XYSeriesCollection(series));
        var logAxis=new LogAxis("LPNR (log)");chart.getXYPlot().setRangeAxis(logAxis);
        double low=value.parametersUsed().lpnrThreshold(),high=low;
        for(var c:value.candidates())if(Double.isFinite(c.lpnr())&&c.lpnr()>0){low=Math.min(low,c.lpnr());high=Math.max(high,c.lpnr());}
        logAxis.setRange(low/1.3,high*1.3);
        chart.setBackgroundPaint(java.awt.Color.WHITE);chart.getXYPlot().setBackgroundPaint(java.awt.Color.WHITE);
        chart.getXYPlot().setDomainGridlinePaint(new java.awt.Color(225,230,238));chart.getXYPlot().setRangeGridlinePaint(new java.awt.Color(225,230,238));
        chart.getXYPlot().getRenderer().setSeriesPaint(0,new java.awt.Color(37,99,235));
        var threshold=new ValueMarker(value.parametersUsed().lpnrThreshold(),java.awt.Color.DARK_GRAY,new java.awt.BasicStroke(1.3f));threshold.setLabel("Threshold "+value.parametersUsed().lpnrThreshold());
        threshold.setLabelAnchor(org.jfree.chart.ui.RectangleAnchor.TOP_RIGHT);threshold.setLabelTextAnchor(org.jfree.chart.ui.TextAnchor.BOTTOM_RIGHT);chart.getXYPlot().addRangeMarker(threshold);
        rankingHost.removeAll();rankingHost.add(new ChartPanel(chart),BorderLayout.CENTER);rankingHost.revalidate();rankingHost.repaint();
        String domainNote=switch(activeDomain){case TIME->"Time-domain quantitation.";case CHARGE->"Charge-domain quantitation uses |q|; fitting/detection remain in time.";case MOBILITY->"Mobility-domain quantitation uses |mu| and excludes >1000 Ti; fitting/detection remain in time.";};
        statusLabel.setText(value.candidates().size()+" candidates | "+value.selectedCount()+" selected | "+value.fits().size()+" windows | "+value.fits().stream().filter(f->!f.reliable()).count()+" unreliable. "+domainNote);
        if(!rowFits.isEmpty())resultsTable.setRowSelectionInterval(0,0);else selection.accept(null);
        rebuildingTables=false;exportButton.setEnabled(!busy);
    }
    private void refitWindowComponentCount(int row,int count){
        if(result==null||baseline==null)return;int windowId=result.fits().get(row).window().id();setBusy(true);statusLabel.setText("Refitting W"+windowId+" with "+count+" component(s)…");var snapshot=result;double[] times=Arrays.stream(baseline.data().timeMinutes()).map(v->v*60).toArray();
        new SwingWorker<PeakAnalysisResult,Void>(){@Override protected PeakAnalysisResult doInBackground(){return new PeakAnalysisProcessor().refitWindowComponentCount(times,baseline.correctedSignal(),snapshot,windowId,count);}@Override protected void done(){try{display(get());statusLabel.setText("W"+windowId+" refitted with "+count+" component(s) — USER_EDITED.");}catch(Exception ex){statusLabel.setText("Component-count refit failed: "+(ex.getCause()==null?ex.getMessage():ex.getCause().getMessage()));display(snapshot);}finally{setBusy(false);}}}.execute();
    }
    private DefaultTableModel windowModel(String... columns){return new DefaultTableModel(columns,0){@Override public boolean isCellEditable(int r,int c){return c==4;}@Override public Class<?> getColumnClass(int c){if(c==4)return Integer.class;for(int r=0;r<getRowCount();r++)if(getValueAt(r,c)!=null)return getValueAt(r,c).getClass();return Object.class;}};}
    private DefaultTableModel model(String... columns){return new DefaultTableModel(columns,0){@Override public boolean isCellEditable(int r,int c){return false;}@Override public Class<?> getColumnClass(int c){for(int r=0;r<getRowCount();r++)if(getValueAt(r,c)!=null)return getValueAt(r,c).getClass();return Object.class;}};}
    private void setBusy(boolean value){busy=value;analyzeButton.setEnabled(!value);thresholdSpinner.setEnabled(!value);exportButton.setEnabled(!value&&result!=null);}
    private void exportTable(){
        if(result==null)return;Object choice=JOptionPane.showInputDialog(this,"Decimal separator:","Export quantitation table",JOptionPane.QUESTION_MESSAGE,null,new Object[]{"Point (.)","Comma (,)"},"Point (.)");if(choice==null)return;
        char decimal=choice.toString().startsWith("Comma")?',':'.';JFileChooser chooser=new JFileChooser();chooser.setSelectedFile(new java.io.File("Zones-quantitation.tsv"));if(chooser.showSaveDialog(this)!=JFileChooser.APPROVE_OPTION)return;
        try(var w=java.nio.file.Files.newBufferedWriter(chooser.getSelectedFile().toPath())){for(int c=0;c<quantTable.getColumnCount();c++){if(c>0)w.write('\t');w.write(quantTable.getColumnName(c));}w.newLine();for(int r=0;r<quantTable.getRowCount();r++){for(int c=0;c<quantTable.getColumnCount();c++){if(c>0)w.write('\t');Object v=quantTable.getValueAt(r,c);String text=v==null?"":v.toString();if(decimal==',')text=text.replace('.',',');w.write(text);}w.newLine();}statusLabel.setText("Table exported: "+chooser.getSelectedFile());}catch(Exception ex){statusLabel.setText("Export failed: "+ex.getMessage());}
    }
    private void export(){
        if(result==null)return;JFileChooser chooser=new JFileChooser();chooser.setSelectedFile(new java.io.File("Zones-results.zip"));
        if(chooser.showSaveDialog(this)!=JFileChooser.APPROVE_OPTION)return;
        Path path=chooser.getSelectedFile().toPath();
        if(java.nio.file.Files.exists(path)&&JOptionPane.showConfirmDialog(this,"Replace the existing file?","Export",JOptionPane.YES_NO_OPTION)!=JOptionPane.YES_OPTION)return;
        setBusy(true);var snapshot=result;
        new SwingWorker<Void,Void>(){
            @Override protected Void doInBackground()throws Exception{new PeakAnalysisExporter().write(path,baseline,analysis,snapshot);return null;}
            @Override protected void done(){try{get();statusLabel.setText("Exported: "+path);}catch(Exception e){statusLabel.setText("Export failed: "+e.getMessage());}finally{setBusy(false);}}
        }.execute();
    }
    @SuppressWarnings("unchecked")
    // <editor-fold defaultstate="collapsed" desc="Generated Code">//GEN-BEGIN:initComponents
    private void initComponents() {
        controlsPanel = new javax.swing.JPanel();
        thresholdLabel = new javax.swing.JLabel();
        thresholdSpinner = new javax.swing.JSpinner();
        analyzeButton = new javax.swing.JButton();
        exportButton = new javax.swing.JButton();
        componentsCheck = new javax.swing.JCheckBox();
        sumCheck = new javax.swing.JCheckBox();
        markersCheck = new javax.swing.JCheckBox();
        tableHost = new javax.swing.JPanel();
        tabs = new javax.swing.JTabbedPane();
        resultsScroll = new javax.swing.JScrollPane();
        resultsTable = new javax.swing.JTable();
        windowsScroll = new javax.swing.JScrollPane();
        windowsTable = new javax.swing.JTable();
        candidatesScroll = new javax.swing.JScrollPane();
        candidatesTable = new javax.swing.JTable();
        rankingHost = new javax.swing.JPanel();
        statusLabel = new javax.swing.JLabel();
        setLayout(new java.awt.BorderLayout(6, 6));
        controlsPanel.setLayout(new java.awt.FlowLayout(0, 8, 6));
        thresholdLabel.setText("LPNR threshold:");
        controlsPanel.add(thresholdLabel);
        controlsPanel.add(thresholdSpinner);
        analyzeButton.setText("Analyze / refit");
        controlsPanel.add(analyzeButton);
        exportButton.setText("Export ZIP…");
        controlsPanel.add(exportButton);
        componentsCheck.setText("Components");
        componentsCheck.setSelected(true);
        controlsPanel.add(componentsCheck);
        sumCheck.setText("Fitted sum");
        sumCheck.setSelected(true);
        controlsPanel.add(sumCheck);
        markersCheck.setText("Time markers");
        markersCheck.setSelected(true);
        controlsPanel.add(markersCheck);
        add(controlsPanel, java.awt.BorderLayout.NORTH);
        tableHost.setLayout(new java.awt.BorderLayout(0, 0));
        resultsScroll.setViewportView(resultsTable);
        tabs.addTab("Components", resultsScroll);
        windowsScroll.setViewportView(windowsTable);
        tabs.addTab("Windows", windowsScroll);
        candidatesScroll.setViewportView(candidatesTable);
        tabs.addTab("All candidates", candidatesScroll);
        rankingHost.setLayout(new java.awt.BorderLayout(0, 0));
        tabs.addTab("LPNR × rank", rankingHost);
        tableHost.add(tabs, java.awt.BorderLayout.CENTER);
        add(tableHost, java.awt.BorderLayout.CENTER);
        statusLabel.setText("Waiting for analysis.");
        add(statusLabel, java.awt.BorderLayout.SOUTH);
    }// </editor-fold>//GEN-END:initComponents
    // Variables declaration - do not modify//GEN-BEGIN:variables
    private javax.swing.JPanel controlsPanel;
    private javax.swing.JLabel thresholdLabel;
    private javax.swing.JSpinner thresholdSpinner;
    private javax.swing.JButton analyzeButton;
    private javax.swing.JButton exportButton;
    private javax.swing.JCheckBox componentsCheck;
    private javax.swing.JCheckBox sumCheck;
    private javax.swing.JCheckBox markersCheck;
    private javax.swing.JPanel tableHost;
    private javax.swing.JTabbedPane tabs;
    private javax.swing.JScrollPane resultsScroll;
    private javax.swing.JTable resultsTable;
    private javax.swing.JScrollPane windowsScroll;
    private javax.swing.JTable windowsTable;
    private javax.swing.JScrollPane candidatesScroll;
    private javax.swing.JTable candidatesTable;
    private javax.swing.JPanel rankingHost;
    private javax.swing.JLabel statusLabel;
    // End of variables declaration//GEN-END:variables
}
