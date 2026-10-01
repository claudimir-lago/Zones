package zones.processing;

import zones.model.*;

/** Calculates reporting metrics after fitting has been completed in the time domain. */
public final class DomainPeakCalculator {
    private final DomainTransform transform;
    private final ElectropherogramDomain domain;
    public DomainPeakCalculator(ElectropherogramData data,ElectropherogramDomain domain,MobilityCalibration calibration){
        this.transform=new DomainTransform(data,calibration);this.domain=domain;
    }
    public DomainPeakMetrics metrics(PeakComponent c){
        if(domain==ElectropherogramDomain.TIME)return new DomainPeakMetrics(c.a1Seconds()/60,c.candidate().observedApexSeconds()/60,c.fittedApexSeconds()/60,c.fwhmSeconds(),c.signedArea(),"min","s","a.u.·s",true);
        String p=domain==ElectropherogramDomain.CHARGE?"mC":"Ti",a=domain==ElectropherogramDomain.CHARGE?"a.u.·mC":"a.u.·Ti";
        double center=x(c.a1Seconds()),observed=x(c.candidate().observedApexSeconds()),apex=x(c.fittedApexSeconds());
        if(!Double.isFinite(center)||!Double.isFinite(apex))return DomainPeakMetrics.unavailable(p,p,a);
        double[] half=halfHeightTimes(c);if(half==null||!monotonic(half[0],half[1]))return DomainPeakMetrics.unavailable(p,p,a);
        double width=Math.abs(x(half[1])-x(half[0]));
        double area=integrateArea(c);
        boolean ok=Double.isFinite(area)&&Double.isFinite(width);
        return new DomainPeakMetrics(center,observed,apex,width,area,p,p,a,ok);
    }
    private double x(double seconds){return transform.xAtTime(domain,seconds/60.0);}
    /** Half-height crossing times for the fitted HVL component. */
    private static double[] halfHeightTimes(PeakComponent c){
        double apex=c.fittedApexSeconds(),peak=Math.abs(Hvl.value(apex,c.signedArea(),c.a1Seconds(),c.a2Seconds(),c.eta()));if(!(peak>0))return null;
        double half=peak/2,span=Math.max(Math.max(14,Math.sqrt(2*Math.abs(c.eta()))+12)*c.a2Seconds(),2*c.fwhmSeconds()+2*c.a2Seconds());
        double left=findCross(c,apex-span,apex,half,true),right=findCross(c,apex,apex+span,half,false);
        return Double.isFinite(left)&&Double.isFinite(right)?new double[]{left,right}:null;
    }
    private static double findCross(PeakComponent c,double lo,double hi,double half,boolean left){
        int n=600;double prevT=lo,prev=Math.abs(Hvl.value(prevT,c.signedArea(),c.a1Seconds(),c.a2Seconds(),c.eta()))-half;
        for(int i=1;i<=n;i++){double t=lo+(hi-lo)*i/n,v=Math.abs(Hvl.value(t,c.signedArea(),c.a1Seconds(),c.a2Seconds(),c.eta()))-half;
            if((left&&prev<=0&&v>=0)||(!left&&prev>=0&&v<=0)){double f=prev==v?.5:prev/(prev-v);return prevT+f*(t-prevT);}prevT=t;prev=v;}return Double.NaN;
    }
    /** Numerical area of the fitted component against the displayed coordinate magnitude. */
    private double integrateArea(PeakComponent c){
        double span=Math.max(Math.max(14,Math.sqrt(2*Math.abs(c.eta()))+12)*c.a2Seconds(),3*c.fwhmSeconds()+3*c.a2Seconds());double lo=c.fittedApexSeconds()-span,hi=c.fittedApexSeconds()+span;
        if(!monotonic(lo,hi))return Double.NaN;
        int n=2400;double prevT=lo,prevX=x(prevT),prevY=Hvl.value(prevT,c.signedArea(),c.a1Seconds(),c.a2Seconds(),c.eta()),sum=0;
        for(int i=1;i<=n;i++){double t=lo+(hi-lo)*i/n,xx=x(t),yy=Hvl.value(t,c.signedArea(),c.a1Seconds(),c.a2Seconds(),c.eta());
            if(Double.isFinite(prevX)&&Double.isFinite(xx)&&Double.isFinite(prevY)&&Double.isFinite(yy))sum+=.5*(prevY+yy)*Math.abs(xx-prevX);
            prevT=t;prevX=xx;prevY=yy;
        }return sum;
    }
    private boolean monotonic(double loSeconds,double hiSeconds){
        int n=500;double prev=Double.NaN;int direction=0;double scale=0;
        for(int i=0;i<=n;i++){double xx=x(loSeconds+(hiSeconds-loSeconds)*i/n);if(!Double.isFinite(xx))continue;scale=Math.max(scale,Math.abs(xx));if(Double.isFinite(prev)){double d=xx-prev,tol=Math.max(1e-12,scale*1e-10);if(Math.abs(d)>tol){int s=d>0?1:-1;if(direction==0)direction=s;else if(s!=direction)return false;}}prev=xx;}return direction!=0;
    }
}
