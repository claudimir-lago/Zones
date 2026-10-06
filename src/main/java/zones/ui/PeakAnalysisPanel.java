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
import zones.core.ZonesEngine;
import zones.io.PeakAnalysisExporter;
import zones.io.UserResultsExporter;
import zones.processing.DomainPeakCalculator;
import org.jfree.chart.*;
import org.jfree.chart.axis.LogAxis;
import org.jfree.chart.plot.*;
import org.jfree.data.xy.*;

/** NetBeans-editable peak-analysis view. All processing is delegated to a worker/service. */
public class PeakAnalysisPanel extends JPanel {
    private final ZonesEngine engine=new ZonesEngine();
    private BaselineResult baseline;
    private AnalysisResult analysis;
    private PeakAnalysisResult result;
    private Consumer<PeakFit> selection=fit->{};
    private Runnable overlayChanged=()->{};
    private Consumer<PeakFit> zoomToWindow=fit->{};
    private long analysisGeneration=0;
    private boolean rebuildingTables=false;
    private final java.util.List<PeakFit> rowFits=new ArrayList<>();
    private final java.util.List<PeakComponent> quantComponents=new ArrayList<>();
    private final java.util.List<Double> quantR2=new ArrayList<>();
    private final java.util.List<Integer> quantPositionDecimals=new ArrayList<>();
    private final java.util.List<Integer> quantWidthDecimals=new ArrayList<>();
    @FunctionalInterface public interface MobilityReferenceEditor { void apply(String role, MobilityReference reference); }
    private MobilityReferenceEditor mobilityReferenceEditor=(role,ref)->{};
    private boolean invertCharge;
    private MobilityReference mobilityRef1,mobilityRef2; private String mobilityReferenceMode="";
    private boolean busy;
    private final JTable quantTable=new JTable(){
        @Override public java.awt.Component prepareRenderer(javax.swing.table.TableCellRenderer renderer,int row,int column){
            java.awt.Component component=super.prepareRenderer(renderer,row,column);
            if(!isRowSelected(row)){
                int modelRow=convertRowIndexToModel(row);
                boolean lowQuality=modelRow>=0&&modelRow<quantR2.size()&&Double.isFinite(quantR2.get(modelRow))&&quantR2.get(modelRow)<0.99;
                component.setBackground(lowQuality?new java.awt.Color(255,235,238):getBackground());
            }
            return component;
        }
    };
    private final JCheckBox nCheck=new JCheckBox("Plates (N)",true);
    private final JCheckBox ngauCheck=new JCheckBox("Gaussian plates (NGau)",false);
    private final JCheckBox fitDetailsCheck=new JCheckBox("Fit details",false);
    private final JButton exportTableButton=new JButton("Export table");
    private final JButton developerExportButton=new JButton("Developer ZIP");
    private Map<String,String> exportMetadata=Map.of();
    private ElectropherogramDomain activeDomain=ElectropherogramDomain.TIME;
    private MobilityCalibration mobilityCalibration;
    private static void disableTooltips(java.awt.Component component){
        if(component instanceof JComponent jc){jc.setToolTipText(null);ToolTipManager.sharedInstance().unregisterComponent(jc);}
        if(component instanceof java.awt.Container container)
            for(java.awt.Component child:container.getComponents())disableTooltips(child);
    }
    public PeakAnalysisPanel(){
        initComponents();
        for(AbstractButton b:new AbstractButton[]{analyzeButton,exportButton,exportTableButton,developerExportButton}) DomainTheme.themeButton(b);
        quantTable.setAutoCreateRowSorter(true);quantTable.setAutoResizeMode(JTable.AUTO_RESIZE_OFF);
        quantTable.setToolTipText("Rows highlighted in pale red have a time-domain fit with R² < 0.99 and should be inspected.");
        tabs.insertTab("Quantitation",null,new JScrollPane(quantTable),"Primary analytical results",0);
        controlsPanel.add(nCheck);controlsPanel.add(ngauCheck);controlsPanel.add(fitDetailsCheck);controlsPanel.add(exportTableButton);controlsPanel.add(developerExportButton);
        nCheck.addActionListener(e->{if(result!=null)display(result);});ngauCheck.addActionListener(e->{if(result!=null)display(result);});fitDetailsCheck.addActionListener(e->{if(result!=null)display(result);});
        exportTableButton.addActionListener(e->exportTable());developerExportButton.addActionListener(e->exportDeveloper());
        thresholdSpinner.setModel(new SpinnerNumberModel(10.0,.01,1000000.0,1.0));
        var thresholdEditor=new JSpinner.NumberEditor(thresholdSpinner,"0.######");thresholdEditor.getFormat().setDecimalFormatSymbols(java.text.DecimalFormatSymbols.getInstance(Locale.ROOT));thresholdSpinner.setEditor(thresholdEditor);
        thresholdSpinner.setPreferredSize(new java.awt.Dimension(132,28));
        thresholdSpinner.setMinimumSize(new java.awt.Dimension(132,28));
        if(thresholdSpinner.getEditor() instanceof JSpinner.DefaultEditor editor){editor.getTextField().setColumns(9);editor.getTextField().setHorizontalAlignment(JTextField.RIGHT);}
        disableTooltips(thresholdSpinner);
        thresholdLabel.setToolTipText("LPNR = local prominence / local sigma. Default 10; increasing it selects fewer candidates. It is not analytical SNR or LOD/LOQ.");
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
    public void setDomain(ElectropherogramDomain domain,MobilityCalibration calibration){setDomain(domain,calibration,invertCharge);}
    public void setDomain(ElectropherogramDomain domain,MobilityCalibration calibration,boolean invertCharge){
        this.activeDomain=domain==null?ElectropherogramDomain.TIME:domain;this.mobilityCalibration=calibration;this.invertCharge=invertCharge;
        if(result!=null)display(result);
    }
    public void onMobilityReferenceEdit(MobilityReferenceEditor action){mobilityReferenceEditor=action==null?(role,ref)->{}:action;}
    public void setMobilityReferences(MobilityReference r1,MobilityReference r2,String mode){this.mobilityRef1=r1;this.mobilityRef2=r2;this.mobilityReferenceMode=mode==null?"":mode;if(result!=null)display(result);}
    public void setExportMetadata(Map<String,String> metadata){this.exportMetadata=metadata==null?Map.of():Map.copyOf(metadata);}
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
            @Override protected PeakAnalysisResult doInBackground(){return engine.analyzePeaks(snapshot,p);}
            @Override protected void done(){try{PeakAnalysisResult r=get();if(generation==analysisGeneration&&snapshot==baseline)display(r);}catch(Exception ex){statusLabel.setText("Failure: "+(ex.getCause()==null?ex.getMessage():ex.getCause().getMessage()));}finally{if(generation==analysisGeneration)setBusy(false);}}
        }.execute();
    }
    public void display(PeakAnalysisResult value){
        result=value;rowFits.clear();quantComponents.clear();quantR2.clear();quantPositionDecimals.clear();quantWidthDecimals.clear();rebuildingTables=true;
        var table=model("Window","LPNR rank","Prom. rank","Polarity","Status","Observed apex (min)","Migration time a1 (min)","Fitted apex (min)","Local prominence (a.u.)","Local sigma (a.u.)","LPNR","W50 (s)","eta","a2 (s)","a3 (s)","Area (a.u.·s)","Captured (%)","N","NGau","FWHM (s)","R²","RMS (a.u.)","Type");
        var windowModel=windowModel("Window","Type","Start (min)","End (min)","Components","Status","Offset (a.u.)","Experimental algebraic area (a.u.·s)","Experimental absolute area (a.u.·s)","Fitted algebraic area (a.u.·s)","Absolute component sum (a.u.·s)","Isolated difference (%)","R²","RMS (a.u.)","Evaluations","Converged","Diagnostic");
        for(var f:value.fits()){
            String state=f.reliable()?"Fitted — inspect":"UNRELIABLE (diagnostic)";
            windowModel.addRow(new Object[]{f.window().id(),f.window().type(),f.window().startSeconds()/60,f.window().endSeconds()/60,f.components().size(),state,f.offset(),f.experimentalAlgebraicArea(),f.experimentalAbsoluteArea(),f.fittedAlgebraicArea(),f.absoluteComponentSum(),f.isolatedAreaDifferencePercent(),f.r2(),f.rms(),f.evaluations(),f.success(),f.message()});
            for(var c:f.components()){var d=c.candidate();rowFits.add(f);table.addRow(new Object[]{f.window().id(),d.rank(),d.prominenceRank(),d.polarity()>0?"+":"−",state,d.observedApexSeconds()/60,c.a1Seconds()/60,c.fittedApexSeconds()/60,d.localProminence(),d.localNoiseSigma(),d.lpnr(),d.w50Seconds(),c.eta(),c.a2Seconds(),c.a3Seconds(),c.signedArea(),100*c.capturedFraction(),c.effectivePlates(),c.gaussianPlates(),c.fwhmSeconds(),f.r2(),f.rms(),f.window().type()});}
        }
        var metricCalculator=new DomainPeakCalculator(baseline.data(),activeDomain,mobilityCalibration,invertCharge);
        String positionLabel=activeDomain==ElectropherogramDomain.TIME?"Migration time (min)":activeDomain==ElectropherogramDomain.CHARGE?(invertCharge?"Migration -charge (mC)":"Migration charge (mC)"):(mobilityCalibration!=null?mobilityCalibration.mobilityName()+" at 25 °C (Ti)":"Mobility (Ti)");
        String areaLabel=activeDomain==ElectropherogramDomain.TIME?"Area (a.u.·s)":activeDomain==ElectropherogramDomain.CHARGE?"Area (a.u.·mC)":"Area (a.u.·Ti)";
        String widthLabel=activeDomain==ElectropherogramDomain.TIME?"FWHM (s)":activeDomain==ElectropherogramDomain.CHARGE?"FWHM (mC)":"FWHM (Ti)";
        java.util.List<String> qc=new ArrayList<>(java.util.List.of(positionLabel,areaLabel,"Mobility reference","Known μeff (Ti)","μeff temp (°C)"));
        if(nCheck.isSelected())qc.add("Plates (N, time fit)");if(ngauCheck.isSelected())qc.add("Gaussian plates (NGau, time fit)");
        if(fitDetailsCheck.isSelected()){qc.add(widthLabel);qc.add("R² (time fit)");}
        var qm=quantitationModel(qc.toArray(String[]::new));
        for(var f:value.fits())for(var c:f.components()){
            quantComponents.add(c);quantR2.add(f.r2());quantPositionDecimals.add(localAxisDecimals(c,false));quantWidthDecimals.add(localAxisDecimals(c,true));
            var metrics=metricCalculator.metrics(c);java.util.List<Object> row=new ArrayList<>();
            row.add(activeDomain==ElectropherogramDomain.TIME?metrics.position()/60.0:metrics.position());row.add(metrics.signedArea());row.add(referenceRole(c));row.add(referenceKnownMobility(c));row.add(referenceTemperature(c));
            if(nCheck.isSelected())row.add(c.effectivePlates());if(ngauCheck.isSelected())row.add(c.gaussianPlates());
            if(fitDetailsCheck.isSelected()){row.add(metrics.fwhm());row.add(f.r2());}qm.addRow(row.toArray());
        }
        quantTable.setModel(qm);
        installMobilityReferenceEditors(qm);
        for(int i=0;i<quantTable.getColumnCount();i++)quantTable.getColumnModel().getColumn(i).setPreferredWidth(i==2?135:(i==3||i==4?125:150));
        resultsTable.setModel(table);windowsTable.setModel(windowModel);
        windowModel.addTableModelListener(e->{if(rebuildingTables||busy||e.getType()!=javax.swing.event.TableModelEvent.UPDATE||e.getColumn()!=4)return;int row=e.getFirstRow();try{int count=Integer.parseInt(windowModel.getValueAt(row,4).toString());refitWindowComponentCount(row,count);}catch(Exception ex){statusLabel.setText("Component-count refit failed: "+ex.getMessage());display(result);}});
        var candidateModel=model("LPNR rank","Prom. rank","Polarity","Observed apex (min)","Local prominence (a.u.)","Local sigma (a.u.)","Noise points","LPNR","BES","W50 (s)","Status");
        XYSeries series=new XYSeries("LPNR");
        for(var c:value.candidates()){
            candidateModel.addRow(new Object[]{c.rank(),c.prominenceRank(),c.polarity()>0?"+":"−",c.observedApexSeconds()/60,c.localProminence(),c.localNoiseSigma(),c.noisePointCount(),c.lpnr(),c.bes(),c.w50Seconds(),c.selected(value.parametersUsed().lpnrThreshold())?"Selected":"Candidate"});
            if(Double.isFinite(c.lpnr())&&c.lpnr()>0)series.add(c.rank(),c.lpnr());
        }
        candidatesTable.setModel(candidateModel);
        centerAllTables();
        installAdaptiveQuantitationRenderers(qm,positionLabel,widthLabel);
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
        String domainNote=switch(activeDomain){case TIME->"Time-domain quantitation.";case CHARGE->"Charge-domain quantitation preserves charge sign"+(invertCharge?" (displaying -charge).":".")+" Fitting/detection remain in time.";case MOBILITY->(mobilityCalibration==null?"Mobility scale not calibrated.":mobilityCalibration.mobilityName()+" at 25 °C; signed values are shown and |mobility| > 1000 Ti is excluded.");};
        statusLabel.setText(value.candidates().size()+" candidates | "+value.selectedCount()+" selected | "+value.fits().size()+" windows | "+value.fits().stream().filter(f->!f.reliable()).count()+" unreliable. "+domainNote);
        if(!rowFits.isEmpty())resultsTable.setRowSelectionInterval(0,0);else selection.accept(null);
        rebuildingTables=false;exportButton.setEnabled(!busy);
    }

    private String referenceRole(PeakComponent c){
        if("Instrument parameters only".equals(mobilityReferenceMode))return "—";
        if(matchesReference(c,mobilityRef1))return mobilityReferenceMode.startsWith("Two")?"Ref 1":"Reference";
        if(matchesReference(c,mobilityRef2))return "Ref 2";
        return "—";
    }
    private Double referenceKnownMobility(PeakComponent c){
        if(matchesReference(c,mobilityRef1))return mobilityRef1.effectiveMobilityTi();
        if(matchesReference(c,mobilityRef2))return mobilityRef2.effectiveMobilityTi();
        return null;
    }
    private Double referenceTemperature(PeakComponent c){
        if(matchesReference(c,mobilityRef1))return mobilityRef1.sourceTemperatureC();
        if(matchesReference(c,mobilityRef2))return mobilityRef2.sourceTemperatureC();
        return null;
    }
    private double componentCharge(PeakComponent c){
        if(baseline==null)return Double.NaN;
        return new zones.processing.DomainTransform(baseline.data(),null,false).signedChargeAtTime(c.a1Seconds()/60.0);
    }
    private double componentTimeMinutes(PeakComponent c){return c.a1Seconds()/60.0;}
    private boolean matchesReference(PeakComponent c,MobilityReference ref){
        if(ref==null)return false;double t=componentTimeMinutes(c);
        if(Double.isFinite(ref.migrationTimeMinutes())){double tol=Math.max(1e-9,Math.abs(t)*1e-8);return Math.abs(t-ref.migrationTimeMinutes())<=tol;}
        double q=componentCharge(c);if(!Double.isFinite(q)||!Double.isFinite(ref.migrationChargeMilliCoulombs()))return false;double tol=Math.max(1e-9,Math.abs(q)*1e-8);return Math.abs(q-ref.migrationChargeMilliCoulombs())<=tol;
    }
    private void installMobilityReferenceEditors(DefaultTableModel qm){
        int roleColumn=qm.findColumn("Mobility reference"),muColumn=qm.findColumn("Known μeff (Ti)"),tempColumn=qm.findColumn("μeff temp (°C)");
        if(roleColumn<0||muColumn<0||tempColumn<0)return;
        String[] roles="Two effective-mobility standards".equals(mobilityReferenceMode)?new String[]{"—","Ref 1","Ref 2"}:"Instrument parameters + one effective-mobility reference".equals(mobilityReferenceMode)?new String[]{"—","Reference"}:new String[]{"—"};
        quantTable.getColumnModel().getColumn(roleColumn).setCellEditor(new DefaultCellEditor(new JComboBox<>(roles)));
        quantTable.getColumnModel().getColumn(muColumn).setCellEditor(new DefaultCellEditor(new JTextField()));
        quantTable.getColumnModel().getColumn(tempColumn).setCellEditor(new DefaultCellEditor(new JTextField()));
        qm.addTableModelListener(e->{
            if(rebuildingTables||e.getType()!=javax.swing.event.TableModelEvent.UPDATE)return;
            int row=e.getFirstRow(),column=e.getColumn();if(row<0||row>=quantComponents.size()||(column!=roleColumn&&column!=muColumn&&column!=tempColumn))return;
            PeakComponent component=quantComponents.get(row);double q=componentCharge(component),t=componentTimeMinutes(component);
            String role=Objects.toString(qm.getValueAt(row,roleColumn),"—");
            if("Instrument parameters only".equals(mobilityReferenceMode)){statusLabel.setText("Instrument-parameters-only calibration does not use peak references.");SwingUtilities.invokeLater(()->display(result));return;}
            if("—".equals(role)){
                mobilityReferenceEditor.apply("—",new MobilityReference(q,t,Double.NaN,"Peak at "+String.format(Locale.ROOT,"%.6g min",t),25.0));
                return;
            }
            Object raw=qm.getValueAt(row,muColumn);
            if(raw==null||raw.toString().isBlank()){statusLabel.setText("Enter the known effective mobility (Ti) for "+role+".");return;}
            try{
                double mu=raw instanceof Number n?n.doubleValue():Double.parseDouble(raw.toString().trim());
                if(!Double.isFinite(mu))throw new NumberFormatException();
                Object rawTemp=qm.getValueAt(row,tempColumn);double temp=(rawTemp==null||rawTemp.toString().isBlank())?25.0:(rawTemp instanceof Number n?n.doubleValue():Double.parseDouble(rawTemp.toString().trim()));
                zones.processing.WaterViscosity.viscosityMicroPaS(temp);
                String label="Peak at "+String.format(Locale.ROOT,"%.6g min",component.a1Seconds()/60.0);
                mobilityReferenceEditor.apply(role,new MobilityReference(q,t,mu,label,temp));
                statusLabel.setText(String.format(Locale.ROOT,"%s stored: t = %.8g min%s, μeff = %.8g Ti at %.1f °C (normalized to 25 °C for calibration)",role,t,Double.isFinite(q)?String.format(Locale.ROOT,", q = %.8g mC",q):"",mu,temp));
            }catch(Exception ex){JOptionPane.showMessageDialog(this,"Enter a valid effective mobility and source temperature (0–100 °C).","Mobility reference",JOptionPane.ERROR_MESSAGE);SwingUtilities.invokeLater(()->display(result));}
        });
    }
    private void centerAllTables(){
        for(JTable t:new JTable[]{quantTable,resultsTable,windowsTable,candidatesTable}){
            var center=new javax.swing.table.DefaultTableCellRenderer();center.setHorizontalAlignment(SwingConstants.CENTER);
            t.setDefaultRenderer(Object.class,center);t.setDefaultRenderer(String.class,center);t.setDefaultRenderer(Integer.class,center);
            if(t.getTableHeader()!=null){var base=t.getTableHeader().getDefaultRenderer();t.getTableHeader().setDefaultRenderer((table,value,isSelected,hasFocus,row,column)->{var comp=base.getTableCellRendererComponent(table,value,isSelected,hasFocus,row,column);if(comp instanceof JLabel label)label.setHorizontalAlignment(SwingConstants.CENTER);return comp;});}
            var num=new javax.swing.table.DefaultTableCellRenderer(){@Override protected void setValue(Object v){setHorizontalAlignment(SwingConstants.CENTER);if(!(v instanceof Number n)||!Double.isFinite(n.doubleValue()))setText("unavailable");else setText(String.format(Locale.ROOT,"%.6g",n.doubleValue()));}};
            t.setDefaultRenderer(Double.class,num);
            for(int c=0;c<t.getColumnCount();c++){
                String name=t.getColumnName(c);
                if(name.equals("N")||name.equals("NGau")||name.contains("Plates (N")||name.contains("NGau")) t.getColumnModel().getColumn(c).setCellRenderer(new javax.swing.table.DefaultTableCellRenderer(){@Override protected void setValue(Object v){setHorizontalAlignment(SwingConstants.CENTER);if(v instanceof Number n&&Double.isFinite(n.doubleValue()))setText(String.format(Locale.ROOT,"%.0f",n.doubleValue()));else setText("unavailable");}});
                else if(name.startsWith("Area (")) t.getColumnModel().getColumn(c).setCellRenderer(new javax.swing.table.DefaultTableCellRenderer(){@Override protected void setValue(Object v){setHorizontalAlignment(SwingConstants.CENTER);if(v instanceof Number n&&Double.isFinite(n.doubleValue()))setText(String.format(Locale.ROOT,"%.5g",n.doubleValue()));else setText("unavailable");}});
            }
        }
    }

    /**
     * Estimates how many decimal places are justified by the local sampling resolution
     * around a fitted peak. Internally the time vector remains in minutes; the time-domain
     * FWHM is reported in seconds, so its local spacing is converted accordingly.
     */
    private int localAxisDecimals(PeakComponent component,boolean width){
        if(baseline==null)return 6;
        double[] time=baseline.data().timeMinutes();
        if(time.length<2)return 6;
        double target=component.a1Seconds()/60.0;
        int index=Arrays.binarySearch(time,target);
        if(index<0){index=-index-1;if(index>=time.length)index=time.length-1;else if(index>0&&Math.abs(time[index-1]-target)<=Math.abs(time[index]-target))index--;}
        var transform=new zones.processing.DomainTransform(baseline.data(),mobilityCalibration,invertCharge);
        java.util.List<Double> delta=new ArrayList<>();
        int from=Math.max(0,index-3),to=Math.min(time.length-2,index+2);
        for(int j=from;j<=to;j++){
            double a=transform.xAtTime(activeDomain,time[j]),b=transform.xAtTime(activeDomain,time[j+1]);
            double d=Math.abs(b-a);
            if(activeDomain==ElectropherogramDomain.TIME&&width)d*=60.0;
            if(Double.isFinite(d)&&d>0)delta.add(d);
        }
        if(delta.isEmpty())return 6;
        java.util.Collections.sort(delta);int n=delta.size();double resolution=n%2==0?(delta.get(n/2-1)+delta.get(n/2))/2.0:delta.get(n/2);
        return decimalsFromResolution(resolution);
    }
    private static int decimalsFromResolution(double resolution){
        if(!(Double.isFinite(resolution)&&resolution>0))return 6;
        int decimals=(int)Math.ceil(-Math.log10(resolution));
        return Math.max(0,Math.min(9,decimals));
    }
    private void installAdaptiveQuantitationRenderers(DefaultTableModel model,String positionLabel,String widthLabel){
        int positionColumn=model.findColumn(positionLabel);
        if(positionColumn>=0)quantTable.getColumnModel().getColumn(positionColumn).setCellRenderer(adaptiveNumberRenderer(quantPositionDecimals));
        int widthColumn=model.findColumn(widthLabel);
        if(widthColumn>=0)quantTable.getColumnModel().getColumn(widthColumn).setCellRenderer(adaptiveNumberRenderer(quantWidthDecimals));
    }
    private javax.swing.table.TableCellRenderer adaptiveNumberRenderer(java.util.List<Integer> decimalsByModelRow){
        return new javax.swing.table.DefaultTableCellRenderer(){
            @Override public java.awt.Component getTableCellRendererComponent(JTable table,Object value,boolean selected,boolean focus,int row,int column){
                super.getTableCellRendererComponent(table,value,selected,focus,row,column);setHorizontalAlignment(SwingConstants.CENTER);
                if(value instanceof Number n&&Double.isFinite(n.doubleValue())){
                    int modelRow=table.convertRowIndexToModel(row);int decimals=modelRow>=0&&modelRow<decimalsByModelRow.size()?decimalsByModelRow.get(modelRow):6;
                    double v=n.doubleValue(),zero=Math.pow(10.0,-decimals)*0.5;if(Math.abs(v)<zero)v=0.0;
                    setText(String.format(Locale.ROOT,"% ."+decimals+"f",v).trim());
                }else setText("unavailable");
                return this;
            }
        };
    }

    /** Selects the most likely fitted component containing a click on the corrected-signal plot. */
    public boolean selectQuantitationByX(double x){
        if(result==null||baseline==null||quantComponents.isEmpty()||!Double.isFinite(x))return false;
        var tr=new zones.processing.DomainTransform(baseline.data(),mobilityCalibration,invertCharge);
        double[] time=baseline.data().timeMinutes();int best=-1;double bestDistance=Double.POSITIVE_INFINITY;
        for(int i=0;i<quantComponents.size();i++){
            PeakComponent c=quantComponents.get(i);PeakCandidate pc=c.candidate();double a=Double.NaN,b=Double.NaN;
            if(pc.leftBase()>=0&&pc.rightBase()>=0&&pc.leftBase()<time.length&&pc.rightBase()<time.length&&pc.rightBase()>pc.leftBase()){
                a=tr.xAtTime(activeDomain,time[pc.leftBase()]);b=tr.xAtTime(activeDomain,time[pc.rightBase()]);
            }
            if(!Double.isFinite(a)||!Double.isFinite(b)||a==b){
                double half=Math.max(c.fwhmSeconds(),pc.w50Seconds())*.65;
                a=tr.xAtTime(activeDomain,(c.fittedApexSeconds()-half)/60.0);b=tr.xAtTime(activeDomain,(c.fittedApexSeconds()+half)/60.0);
            }
            if(!Double.isFinite(a)||!Double.isFinite(b))continue;double lo=Math.min(a,b),hi=Math.max(a,b);
            if(x<lo||x>hi)continue;double center=tr.xAtTime(activeDomain,c.a1Seconds()/60.0);double d=Double.isFinite(center)?Math.abs(x-center):0;
            if(d<bestDistance){bestDistance=d;best=i;}
        }
        if(best<0)return false;int view=quantTable.convertRowIndexToView(best);if(view<0)return false;
        tabs.setSelectedIndex(0);quantTable.getSelectionModel().setSelectionInterval(view,view);quantTable.scrollRectToVisible(quantTable.getCellRect(view,0,true));
        PeakComponent c=quantComponents.get(best);PeakFit fit=result.fits().stream().filter(f->f.window().id()==c.parentWindowId()).findFirst().orElse(null);if(fit!=null)selection.accept(fit);
        statusLabel.setText("Graph selection matched the highlighted Quantitation row.");return true;
    }

    private void refitWindowComponentCount(int row,int count){
        if(result==null||baseline==null)return;int windowId=result.fits().get(row).window().id();setBusy(true);statusLabel.setText("Refitting W"+windowId+" with "+count+" component(s)…");var snapshot=result;double[] times=Arrays.stream(baseline.data().timeMinutes()).map(v->v*60).toArray();
        new SwingWorker<PeakAnalysisResult,Void>(){@Override protected PeakAnalysisResult doInBackground(){return engine.refitWindowComponentCount(baseline,snapshot,windowId,count);}@Override protected void done(){try{display(get());statusLabel.setText("W"+windowId+" refitted with "+count+" component(s) — USER_EDITED.");}catch(Exception ex){statusLabel.setText("Component-count refit failed: "+(ex.getCause()==null?ex.getMessage():ex.getCause().getMessage()));display(snapshot);}finally{setBusy(false);}}}.execute();
    }
    private DefaultTableModel windowModel(String... columns){return new DefaultTableModel(columns,0){@Override public boolean isCellEditable(int r,int c){return c==4;}@Override public Class<?> getColumnClass(int c){if(c==4)return Integer.class;for(int r=0;r<getRowCount();r++)if(getValueAt(r,c)!=null)return getValueAt(r,c).getClass();return Object.class;}};}
    private DefaultTableModel quantitationModel(String... columns){return new DefaultTableModel(columns,0){
        @Override public boolean isCellEditable(int r,int c){String n=getColumnName(c);if("Instrument parameters only".equals(mobilityReferenceMode))return false;if(n.equals("Mobility reference"))return true;if(n.equals("Known μeff (Ti)")||n.equals("μeff temp (°C)"))return !"—".equals(Objects.toString(getValueAt(r,findColumn("Mobility reference")),"—"));return false;}
        @Override public Class<?> getColumnClass(int c){String n=getColumnName(c);if(n.equals("Mobility reference"))return String.class;if(n.equals("Known μeff (Ti)"))return Object.class;for(int r=0;r<getRowCount();r++)if(getValueAt(r,c)!=null)return getValueAt(r,c).getClass();return Object.class;}
    };}
    private DefaultTableModel model(String... columns){return new DefaultTableModel(columns,0){@Override public boolean isCellEditable(int r,int c){return false;}@Override public Class<?> getColumnClass(int c){for(int r=0;r<getRowCount();r++)if(getValueAt(r,c)!=null)return getValueAt(r,c).getClass();return Object.class;}};}
    private void setBusy(boolean value){
        busy=value;analyzeButton.setEnabled(!value);thresholdSpinner.setEnabled(!value);exportButton.setEnabled(!value&&result!=null);
        if(value){statusLabel.setFont(statusLabel.getFont().deriveFont(java.awt.Font.BOLD,16f));statusLabel.setForeground(new java.awt.Color(180,70,20));}
        else{java.awt.Font f=UIManager.getFont("Label.font");java.awt.Color c=UIManager.getColor("Label.foreground");if(f!=null)statusLabel.setFont(f);if(c!=null)statusLabel.setForeground(c);}
    }
    private void exportTable(){
        if(result==null)return;Object choice=JOptionPane.showInputDialog(this,"Decimal separator:","Export quantitation table",JOptionPane.QUESTION_MESSAGE,null,new Object[]{"Point (.)","Comma (,)"},"Point (.)");if(choice==null)return;
        char decimal=choice.toString().startsWith("Comma")?',':'.';JFileChooser chooser=new JFileChooser();chooser.setSelectedFile(new java.io.File("Zones-quantitation.csv"));if(chooser.showSaveDialog(this)!=JFileChooser.APPROVE_OPTION)return;
        try(var w=java.nio.file.Files.newBufferedWriter(chooser.getSelectedFile().toPath())){for(int c=0;c<quantTable.getColumnCount();c++){if(c>0)w.write('\t');w.write(quantTable.getColumnName(c));}w.newLine();for(int r=0;r<quantTable.getRowCount();r++){for(int c=0;c<quantTable.getColumnCount();c++){if(c>0)w.write('\t');Object v=quantTable.getValueAt(r,c);String text=v==null?"":v.toString();if(decimal==',')text=text.replace('.',',');w.write(text);}w.newLine();}statusLabel.setText("Table exported: "+chooser.getSelectedFile());}catch(Exception ex){statusLabel.setText("Export failed: "+ex.getMessage());}
    }
    private void export(){
        if(result==null)return;
        JCheckBox time=new JCheckBox("Processed time-domain electropherogram",true);
        JCheckBox charge=new JCheckBox("Processed charge-domain electropherogram",baseline!=null&&new zones.processing.DomainTransform(baseline.data(),mobilityCalibration,invertCharge).hasCharge());
        JCheckBox mobility=new JCheckBox("Processed mobility-domain electropherogram",mobilityCalibration!=null);
        JCheckBox zipArchive=new JCheckBox("Save as ZIP archive",true);
        charge.setEnabled(charge.isSelected());mobility.setEnabled(mobility.isSelected());
        JPanel options=new JPanel(new java.awt.GridLayout(0,1));options.add(new JLabel("Quantitation + processing parameters are always included."));options.add(time);options.add(charge);options.add(mobility);options.add(zipArchive);
        if(JOptionPane.showConfirmDialog(this,options,"Export user results",JOptionPane.OK_CANCEL_OPTION,JOptionPane.PLAIN_MESSAGE)!=JOptionPane.OK_OPTION)return;
        JFileChooser chooser=new JFileChooser();
        if(zipArchive.isSelected()){
            chooser.setSelectedFile(new java.io.File("Zones-results.zip"));
        }else{
            chooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);chooser.setDialogTitle("Select output folder");
        }
        if(chooser.showSaveDialog(this)!=JFileChooser.APPROVE_OPTION)return;
        Path path=chooser.getSelectedFile().toPath();
        if(zipArchive.isSelected()&&java.nio.file.Files.exists(path)&&JOptionPane.showConfirmDialog(this,"Replace the existing file?","Export",JOptionPane.YES_NO_OPTION)!=JOptionPane.YES_OPTION)return;
        setBusy(true);var snapshot=result;var opts=new UserResultsExporter.Options(time.isSelected(),charge.isSelected(),mobility.isSelected());boolean zipped=zipArchive.isSelected();
        new SwingWorker<Void,Void>(){
            @Override protected Void doInBackground()throws Exception{
                UserResultsExporter exporter=new UserResultsExporter();
                if(zipped)exporter.write(path,baseline,analysis,snapshot,mobilityCalibration,invertCharge,exportMetadata,opts);
                else exporter.writeDirectory(path,baseline,analysis,snapshot,mobilityCalibration,invertCharge,exportMetadata,opts);
                return null;
            }
            @Override protected void done(){try{get();statusLabel.setText("User results exported: "+path);}catch(Exception e){statusLabel.setText("Export failed: "+e.getMessage());}finally{setBusy(false);}}
        }.execute();
    }
    private void exportDeveloper(){
        if(result==null)return;JFileChooser chooser=new JFileChooser();chooser.setSelectedFile(new java.io.File("Zones-developer-diagnostics.zip"));
        if(chooser.showSaveDialog(this)!=JFileChooser.APPROVE_OPTION)return;Path path=chooser.getSelectedFile().toPath();
        setBusy(true);var snapshot=result;new SwingWorker<Void,Void>(){
            @Override protected Void doInBackground()throws Exception{new PeakAnalysisExporter().write(path,baseline,analysis,snapshot);return null;}
            @Override protected void done(){try{get();statusLabel.setText("Developer diagnostics exported: "+path);}catch(Exception e){statusLabel.setText("Export failed: "+e.getMessage());}finally{setBusy(false);}}
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
        exportButton.setText("Export results");
        controlsPanel.add(exportButton);
        componentsCheck.setText("Components");
        componentsCheck.setSelected(true);
        controlsPanel.add(componentsCheck);
        sumCheck.setText("Fitted sum");
        sumCheck.setSelected(true);
        controlsPanel.add(sumCheck);
        markersCheck.setText("Migration time marker");
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
