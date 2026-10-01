package zones.chart;

import java.awt.*;
import java.awt.geom.Ellipse2D;
import java.awt.event.*;
import java.util.Locale;
import java.util.function.Consumer;
import javax.swing.*;
import zones.model.*;
import zones.processing.ChargeCalculator;
import zones.processing.DomainTransform;
import zones.processing.Hvl;
import zones.ui.DomainTheme;
import org.jfree.chart.*;
import org.jfree.chart.axis.NumberAxis;
import org.jfree.chart.plot.*;
import org.jfree.chart.renderer.xy.XYLineAndShapeRenderer;
import org.jfree.data.xy.*;

/** Plot presentation. Scientific preprocessing remains in the time domain. */
public final class ElectropherogramChart extends JPanel {
    private final NumberAxis domainAxis=new NumberAxis(ElectropherogramDomain.TIME.axisLabel());
    private final CombinedDomainXYPlot combined=new CombinedDomainXYPlot(domainAxis);
    private final XYPlot rawPlot=createPlot("C4D (a.u.)");
    private final XYPlot correctedPlot=createPlot("Corrected C4D (a.u.)");
    private final JFreeChart chart=new JFreeChart(null,JFreeChart.DEFAULT_TITLE_FONT,combined,true);
    private final ChartPanel panel=new ChartPanel(chart);
    private boolean correctedVisible=true,rawVisible=true;
    private Consumer<String> cursorListener=text->{};
    private Runnable analysisRangeListener=()->{};
    private double analysisStart=Double.NaN,analysisEnd=Double.NaN;
    private int draggingRange=0;
    private final ValueMarker analysisStartMarker=new ValueMarker(0,new Color(90,90,90),new BasicStroke(1.5f));
    private final ValueMarker analysisEndMarker=new ValueMarker(0,new Color(90,90,90),new BasicStroke(1.5f));
    private ElectropherogramDomain domain=ElectropherogramDomain.TIME;
    private MobilityCalibration mobilityCalibration;
    private ElectropherogramData displayData;
    private double[] displayTime,displayCharge;
    private DomainTransform domainTransform;
    private boolean invertCharge;

    public ElectropherogramChart() {
        super(new BorderLayout());
        domainAxis.setAutoRangeIncludesZero(false);
        combined.setGap(18);combined.add(rawPlot,3);combined.add(correctedPlot,2);
        combined.setDomainPannable(true);rawPlot.setRangePannable(true);correctedPlot.setRangePannable(true);
        chart.setBackgroundPaint(DomainTheme.background(domain));
        panel.setMouseWheelEnabled(true);panel.setMouseZoomable(true,false);
        panel.setMinimumDrawWidth(100);panel.setMinimumDrawHeight(100);
        panel.setMaximumDrawWidth(10000);panel.setMaximumDrawHeight(10000);
        panel.setPreferredSize(new Dimension(880,540));
        panel.addChartMouseListener(new ChartMouseListener() {
            @Override public void chartMouseClicked(ChartMouseEvent e) {}
            @Override public void chartMouseMoved(ChartMouseEvent e) {
                var point=panel.translateScreenToJava2D(e.getTrigger().getPoint());
                var info=panel.getChartRenderingInfo().getPlotInfo();int index=info.getSubplotIndex(point);
                if(index<0)return;
                XYPlot plot=(XYPlot)combined.getSubplots().get(index);
                var area=info.getSubplotInfo(index).getDataArea();
                double x=domainAxis.java2DToValue(point.getX(),area,plot.getDomainAxisEdge());
                double y=plot.getRangeAxis().java2DToValue(point.getY(),area,plot.getRangeAxisEdge());
                cursorListener.accept(String.format(Locale.ROOT,"%s = %.6g   |   %s = %.3f a.u.",cursorName(),x,index==0?"C4D":"corrected",y));
            }
        });
        MouseAdapter rangeDrag=new MouseAdapter(){
            @Override public void mousePressed(MouseEvent e){
                if(domain!=ElectropherogramDomain.TIME||!rawVisible||!Double.isFinite(analysisStart))return;double[] px=rangePixels();if(px==null)return;
                double mx=panel.translateScreenToJava2D(e.getPoint()).getX();if(Math.abs(mx-px[0])<=7)draggingRange=-1;else if(Math.abs(mx-px[1])<=7)draggingRange=1;
                if(draggingRange!=0)panel.setMouseZoomable(false);
            }
            @Override public void mouseDragged(MouseEvent e){if(draggingRange==0)return;Double x=mouseMinute(e);if(x==null)return;if(draggingRange<0)analysisStart=Math.min(x,analysisEnd-1e-9);else analysisEnd=Math.max(x,analysisStart+1e-9);refreshAnalysisMarkers();}
            @Override public void mouseReleased(MouseEvent e){if(draggingRange!=0){draggingRange=0;panel.setMouseZoomable(true,false);analysisRangeListener.run();}}
        };panel.addMouseListener(rangeDrag);panel.addMouseMotionListener(rangeDrag);
        add(panel,BorderLayout.CENTER);
    }

    public ElectropherogramDomain domain(){return domain;}
    public void setDomain(ElectropherogramDomain value,MobilityCalibration calibration){setDomain(value,calibration,invertCharge);}
    public void setDomain(ElectropherogramDomain value,MobilityCalibration calibration,boolean invertChargeValue){
        domain=value==null?ElectropherogramDomain.TIME:value;mobilityCalibration=calibration;invertCharge=invertChargeValue;
        domainAxis.setLabel(domain==ElectropherogramDomain.CHARGE?(invertCharge?"-Charge (mC)":"Charge (mC)"):(domain==ElectropherogramDomain.MOBILITY&&calibration!=null?calibration.mobilityName()+" (Ti)":domain.axisLabel()));
        chart.setBackgroundPaint(DomainTheme.background(domain));
        Color grid=blend(DomainTheme.background(domain),Color.GRAY,.82);
        rawPlot.setDomainGridlinePaint(grid);rawPlot.setRangeGridlinePaint(grid);correctedPlot.setDomainGridlinePaint(grid);correctedPlot.setRangeGridlinePaint(grid);
        refreshAnalysisMarkers();resetZoom();repaint();
    }
    private static Color blend(Color a,Color b,double wa){double wb=1-wa;return new Color((int)(a.getRed()*wa+b.getRed()*wb),(int)(a.getGreen()*wa+b.getGreen()*wb),(int)(a.getBlue()*wa+b.getBlue()*wb));}
    private String cursorName(){return switch(domain){case TIME->"t (min)";case CHARGE->invertCharge?"-q (mC)":"q (mC)";case MOBILITY->mobilityCalibration!=null&&mobilityCalibration.isEffective()?"mu_eff (Ti)":"mu_app (Ti)";};}
    private static Color color(String key,Color fallback){Color c=UIManager.getColor(key);return c==null?fallback:c;}
    private static XYPlot createPlot(String label) {
        NumberAxis axis=new NumberAxis(label);axis.setAutoRangeIncludesZero(false);
        XYPlot plot=new XYPlot(null,null,axis,null);plot.setDatasetRenderingOrder(DatasetRenderingOrder.FORWARD);
        plot.setBackgroundPaint(Color.WHITE);plot.setDomainGridlinePaint(new Color(225,230,238));plot.setRangeGridlinePaint(new Color(225,230,238));plot.setOutlineVisible(false);return plot;
    }
    private void prepareTransform(ElectropherogramData data){
        displayData=data;displayTime=data.timeMinutes();
        domainTransform=new DomainTransform(data,mobilityCalibration,invertCharge);
        displayCharge=data.currentMicroamps().map(cur->new ChargeCalculator().fromCurrent(displayTime,cur)).orElseGet(()->data.chargeMilliCoulombs().orElse(null));
    }
    private boolean hasCharge(){return displayCharge!=null;}
    private double[] xFor(ElectropherogramData data){
        double[] t=data.timeMinutes(),x=new double[t.length];
        if(domain==ElectropherogramDomain.TIME)return t;
        for(int i=0;i<t.length;i++)x[i]=xAtTime(t[i]);
        return x;
    }
    private double xAtTime(double minute){
        if(domain==ElectropherogramDomain.TIME)return minute;
        if(domainTransform==null)return Double.NaN;
        return domainTransform.xAtTime(domain,minute);
    }
    private double chargeAtTime(double minute){
        if(!hasCharge()||displayTime==null)return Double.NaN;
        if(minute<=displayTime[0])return displayCharge[0];int n=displayTime.length;if(minute>=displayTime[n-1])return displayCharge[n-1];
        int lo=0,hi=n-1;while(hi-lo>1){int mid=(lo+hi)>>>1;if(displayTime[mid]<=minute)lo=mid;else hi=mid;}
        double f=(minute-displayTime[lo])/(displayTime[hi]-displayTime[lo]);return displayCharge[lo]+f*(displayCharge[hi]-displayCharge[lo]);
    }
    private void refreshAnalysisMarkers(){
        rawPlot.removeDomainMarker(analysisStartMarker);rawPlot.removeDomainMarker(analysisEndMarker);if(!Double.isFinite(analysisStart)||!Double.isFinite(analysisEnd))return;
        double a=xAtTime(analysisStart),b=xAtTime(analysisEnd);if(!Double.isFinite(a)||!Double.isFinite(b))return;
        analysisStartMarker.setValue(a);analysisEndMarker.setValue(b);analysisStartMarker.setLabel("Analysis start");analysisEndMarker.setLabel("Analysis end");
        rawPlot.addDomainMarker(analysisStartMarker,org.jfree.chart.ui.Layer.FOREGROUND);rawPlot.addDomainMarker(analysisEndMarker,org.jfree.chart.ui.Layer.FOREGROUND);
    }
    private double[] rangePixels(){int idx=combined.getSubplots().indexOf(rawPlot);if(idx<0)return null;var info=panel.getChartRenderingInfo().getPlotInfo();if(idx>=info.getSubplotCount())return null;var area=info.getSubplotInfo(idx).getDataArea();return new double[]{domainAxis.valueToJava2D(analysisStart,area,rawPlot.getDomainAxisEdge()),domainAxis.valueToJava2D(analysisEnd,area,rawPlot.getDomainAxisEdge())};}
    private Double mouseMinute(MouseEvent e){int idx=combined.getSubplots().indexOf(rawPlot);if(idx<0)return null;var info=panel.getChartRenderingInfo().getPlotInfo();if(idx>=info.getSubplotCount())return null;var area=info.getSubplotInfo(idx).getDataArea();double mx=panel.translateScreenToJava2D(e.getPoint()).getX();double x=domainAxis.java2DToValue(mx,area,rawPlot.getDomainAxisEdge());return Math.max(domainAxis.getLowerBound(),Math.min(domainAxis.getUpperBound(),x));}
    public double analysisStartMinute(){return analysisStart;}public double analysisEndMinute(){return analysisEnd;}
    public void setAnalysisRangeMinutes(double start,double end){if(Double.isFinite(start)&&Double.isFinite(end)&&end>start){analysisStart=start;analysisEnd=end;refreshAnalysisMarkers();}}
    public void onAnalysisRangeChanged(Runnable listener){analysisRangeListener=listener==null?()->{}:listener;}
    public void onCursor(Consumer<String> listener){cursorListener=listener;}
    public void showSignal(ElectropherogramData data,DetectorChannel detector) {
        prepareTransform(data);showPeakFit(null,false,false,false);for(int i=0;i<5;i++)rawPlot.setDataset(i,null);correctedPlot.setDataset(null);
        double[] tx=data.timeMinutes();series(rawPlot,0,detector.toString(),xFor(data),data.signal(detector),null,color("Baseline.raw",Color.BLUE),false);
        analysisStart=tx[0];analysisEnd=tx[tx.length-1];refreshAnalysisMarkers();resetZoom();
    }
    public void showResult(BaselineResult result) {
        double[] x=xFor(result.data()),y=result.rawSignal();
        series(rawPlot,1,"Baseline",x,result.baseline(),null,color("Baseline.baseline",Color.ORANGE),false);
        series(rawPlot,2,"Baseline anchors",x,y,result.highConfidenceBaselineMask(),color("Baseline.highConfidence",Color.MAGENTA),true);
        series(rawPlot,3,"Recovered anchors",x,y,result.recoveredBaselineMask(),color("Baseline.recovered",Color.GREEN),true);
        series(correctedPlot,0,"Corrected signal",x,result.correctedSignal(),null,color("Baseline.corrected",Color.BLUE),false);
    }
    public void showAnalysis(AnalysisResult analysis) {
        showResult(analysis.baseline());
        if(analysis.preprocessing().parametersUsed().enabled())series(rawPlot,4,"MMR cleaned",xFor(analysis.preprocessing().raw()),analysis.preprocessing().cleaned().signal(analysis.baseline().detector()),null,new Color(84,108,125),false);else rawPlot.setDataset(4,null);
    }
    public void cleanedVisible(boolean visible){if(rawPlot.getRenderer(4)!=null)rawPlot.getRenderer(4).setSeriesVisible(0,visible);}
    public void showPeakFit(PeakFit fit,boolean components,boolean sum,boolean markers){
        correctedPlot.clearDomainMarkers();rawPlot.clearDomainMarkers();refreshAnalysisMarkers();int oldPeakDatasetCount=correctedPlot.getDatasetCount();for(int i=1;i<oldPeakDatasetCount;i++){correctedPlot.setDataset(i,null);correctedPlot.setRenderer(i,null);}if(fit==null)return;
        var w=fit.window();double wx1=xAtTime(w.startSeconds()/60),wx2=xAtTime(w.endSeconds()/60);
        if(Double.isFinite(wx1)&&Double.isFinite(wx2))for(var plot:new XYPlot[]{rawPlot,correctedPlot}){var shade=new IntervalMarker(Math.min(wx1,wx2),Math.max(wx1,wx2),new Color(37,99,235,45));shade.setLabel("W"+w.id()+(fit.reliable()?"":" — UNRELIABLE"));plot.addDomainMarker(shade,org.jfree.chart.ui.Layer.BACKGROUND);}
        int samples=1000;double[] t=new double[samples],x=new double[samples],total=new double[samples];for(int i=0;i<samples;i++){t[i]=(w.startSeconds()+(w.endSeconds()-w.startSeconds())*i/(samples-1))/60;x[i]=xAtTime(t[i]);total[i]=fit.offset();}
        int dataset=1;for(var c:fit.components()){double[] component=new double[samples],displayComponent=new double[samples];for(int i=0;i<samples;i++){component[i]=Hvl.value(t[i]*60,c.signedArea(),c.fittedCenterSeconds(),c.sigmaSeconds(),c.alpha());displayComponent[i]=fit.offset()+component[i];total[i]+=component[i];}
            if(components){areaSeries(correctedPlot,dataset++,"Peak area rank "+c.candidate().rank(),x,displayComponent,fit.offset(),new Color(20,130,120,51));series(correctedPlot,dataset,"Component rank "+c.candidate().rank(),x,displayComponent,null,new Color(20,130,120),false);correctedPlot.getRenderer(dataset++).setSeriesStroke(0,new BasicStroke(1.4f,BasicStroke.CAP_BUTT,BasicStroke.JOIN_ROUND,1,new float[]{5,4},0));}
            if(markers)domainMarker(c.fittedCenterSeconds(),"Migration time",Color.ORANGE,new BasicStroke(1,BasicStroke.CAP_BUTT,BasicStroke.JOIN_ROUND,1,new float[]{7,5},0));}
        if(sum)series(correctedPlot,dataset,"Fitted sum + offset",x,total,null,new Color(220,70,40),false);
    }
    private void domainMarker(double seconds,String label,Color color,BasicStroke stroke){double x=xAtTime(seconds/60);if(!Double.isFinite(x))return;var marker=new ValueMarker(x,color,stroke);marker.setLabel(label);correctedPlot.addDomainMarker(marker);}
    private static void series(XYPlot plot,int index,String name,double[] x,double[] y,boolean[] mask,Color color,boolean points) {XYSeries s=new XYSeries(name,false,true);for(int i=0;i<x.length;i++)if((mask==null||mask[i])&&Double.isFinite(x[i])&&Double.isFinite(y[i]))s.add(x[i],y[i],false);plot.setDataset(index,new XYSeriesCollection(s));XYLineAndShapeRenderer renderer=new XYLineAndShapeRenderer(!points,points);renderer.setSeriesPaint(0,color);renderer.setSeriesStroke(0,new BasicStroke(index==1?1.8f:1f));renderer.setSeriesShape(0,new Ellipse2D.Double(-0.9,-0.9,1.8,1.8));plot.setRenderer(index,renderer);}
    private static void areaSeries(XYPlot plot,int index,String name,double[] x,double[] y,double baseline,Color color) {
        java.util.List<double[]> points=new java.util.ArrayList<>();
        for(int i=0;i<x.length;i++)if(Double.isFinite(x[i])&&Double.isFinite(y[i]))points.add(new double[]{x[i],y[i]});
        // XYDifferenceRenderer expects an ordered domain. Mobility is commonly traversed in
        // decreasing x as migration time increases, so sort only the filled-area dataset.
        points.sort(java.util.Comparator.comparingDouble(a->a[0]));
        XYSeries fitted=new XYSeries(name,false,true),reference=new XYSeries(name+" reference",false,true);
        for(double[] point:points){fitted.add(point[0],point[1],false);reference.add(point[0],baseline,false);}
        var dataset=new XYSeriesCollection();dataset.addSeries(fitted);dataset.addSeries(reference);plot.setDataset(index,dataset);
        var renderer=new org.jfree.chart.renderer.xy.XYDifferenceRenderer(color,color,false);renderer.setSeriesPaint(0,color);renderer.setSeriesPaint(1,new Color(0,0,0,0));renderer.setSeriesVisibleInLegend(1,false);plot.setRenderer(index,renderer);
    }
    public void overlays(boolean high,boolean recovered,boolean original,boolean corrected) {if(rawPlot.getRenderer(2)!=null)rawPlot.getRenderer(2).setSeriesVisible(0,high);if(rawPlot.getRenderer(3)!=null)rawPlot.getRenderer(3).setSeriesVisible(0,recovered);if(rawVisible!=original){if(original)combined.add(rawPlot,3);else combined.remove(rawPlot);rawVisible=original;}if(correctedVisible!=corrected){if(corrected)combined.add(correctedPlot,2);else combined.remove(correctedPlot);correctedVisible=corrected;}}
    public void setStale(boolean stale){if(rawPlot.getRenderer(0)!=null)rawPlot.getRenderer(0).setSeriesPaint(0,stale?Color.GRAY:color("Baseline.raw",Color.BLUE));if(rawPlot.getRenderer(1)!=null)rawPlot.getRenderer(1).setSeriesPaint(0,stale?Color.LIGHT_GRAY:color("Baseline.baseline",Color.ORANGE));if(correctedPlot.getRenderer(0)!=null)correctedPlot.getRenderer(0).setSeriesPaint(0,stale?Color.GRAY:color("Baseline.corrected",Color.BLUE));}
    public void zoomToWindow(PeakWindow window,double marginMinutes){double a=xAtTime(window.startSeconds()/60),b=xAtTime(window.endSeconds()/60);if(!Double.isFinite(a)||!Double.isFinite(b))return;double lo=Math.min(a,b),hi=Math.max(a,b),margin=domain==ElectropherogramDomain.TIME?marginMinutes:Math.max((hi-lo)*.08,Math.ulp(Math.max(Math.abs(lo),Math.abs(hi))));domainAxis.setRange(lo-margin,hi+margin);rawPlot.getRangeAxis().setAutoRange(true);correctedPlot.getRangeAxis().setAutoRange(true);}
    public void resetZoom(){
        panel.restoreAutoBounds();
        if(domain==ElectropherogramDomain.MOBILITY){
            double lo=Math.max(domainAxis.getLowerBound(),-DomainTransform.MOBILITY_DISPLAY_LIMIT_TI);
            double hi=Math.min(domainAxis.getUpperBound(), DomainTransform.MOBILITY_DISPLAY_LIMIT_TI);
            if(lo<hi)domainAxis.setRange(lo,hi);
        }
    }
    public void zoomIn(){panel.zoomInBoth(panel.getWidth()/2.0,panel.getHeight()/2.0);}
    public void zoomOut(){panel.zoomOutBoth(panel.getWidth()/2.0,panel.getHeight()/2.0);}
}
