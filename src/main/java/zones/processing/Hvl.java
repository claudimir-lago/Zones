package zones.processing;

/** Numerically stable Haarhoff-Van der Linde (HVL) peak function in (area,a1,a2,eta) form. */
public final class Hvl {
    private Hvl(){}
    private static final double SQRT2PI=Math.sqrt(2*Math.PI);

    public static double value(double t,double area,double a1,double a2,double eta){
        if(!(a2>0) || !Double.isFinite(area+a1+a2+eta))return Double.NaN;
        double x=(t-a1)/a2;
        return area/a2*shape(x,eta);
    }
    public static double shape(double x,double eta){
        if(Math.abs(eta)<1e-7)return Math.exp(-.5*x*x)/SQRT2PI;
        if(eta<0)return shape(-x,-eta);
        double phi=Math.exp(-.5*x*x)/SQRT2PI;
        double p=normalCdf(x),q=normalCdf(-x);
        // Equivalent exact form: phi * expm1(eta)/eta / (Phi(-x)+exp(eta)Phi(x)).
        // Divide numerator and denominator by exp(eta) to avoid overflow/cancellation.
        double em=Math.exp(-eta);
        double ratio=-Math.expm1(-eta)/eta;
        return phi*ratio/(p+em*q);
    }
    /** High-accuracy normal CDF approximation, adequate for HVL fitting without external dependencies. */
    static double normalCdf(double x){
        if(x>8)return 1;if(x<-8)return 0;
        double ax=Math.abs(x),t=1/(1+0.2316419*ax);
        double poly=t*(0.319381530+t*(-0.356563782+t*(1.781477937+t*(-1.821255978+t*1.330274429))));
        double tail=Math.exp(-.5*ax*ax)/SQRT2PI*poly;
        return x>=0?1-tail:tail;
    }
    public static double apexOffsetX(double eta){
        double span=Math.max(10,Math.sqrt(2*Math.abs(eta))+8),a=-span,b=span;
        // Golden-section maximum of the normalized shape.
        double gr=(Math.sqrt(5)-1)/2,c=b-gr*(b-a),d=a+gr*(b-a),fc=shape(c,eta),fd=shape(d,eta);
        for(int i=0;i<100;i++){
            if(fc>fd){b=d;d=c;fd=fc;c=b-gr*(b-a);fc=shape(c,eta);}else{a=c;c=d;fc=fd;d=a+gr*(b-a);fd=shape(d,eta);}
        }
        return .5*(a+b);
    }
    public static double apex(double a1,double a2,double eta){return a1+a2*apexOffsetX(eta);}
    public static double a3(double a1,double a2,double eta){return a1!=0?eta*a2*a2/a1:Double.NaN;}

    /** Shape descriptors of the complete fitted HVL component. */
    public record Descriptors(double mean,double variance,double fwhm){}
    public static Descriptors descriptors(double a1,double a2,double eta){
        double span=Math.max(14,Math.sqrt(2*Math.abs(eta))+12),lo=-span,hi=span;
        int n=12001;double dx=(hi-lo)/(n-1),s0=0,s1=0,s2=0;
        double max=-1,xmax=0;
        for(int i=0;i<n;i++){
            double x=lo+i*dx,h=shape(x,eta),w=(i==0||i==n-1)?.5:1;
            s0+=w*h;s1+=w*h*x;s2+=w*h*x*x;if(h>max){max=h;xmax=x;}
        }
        s0*=dx;s1*=dx;s2*=dx;double meanX=s1/s0,varX=Math.max(0,s2/s0-meanX*meanX);
        double half=max/2,left=Double.NaN,right=Double.NaN,prevX=lo,prev=shape(lo,eta);
        for(int i=1;i<n;i++){
            double x=lo+i*dx,h=shape(x,eta);
            if(Double.isNaN(left)&&x<=xmax&&prev<half&&h>=half)left=prevX+(half-prev)*(x-prevX)/(h-prev);
            if(x>=xmax&&prev>=half&&h<half){right=prevX+(half-prev)*(x-prevX)/(h-prev);break;}
            prevX=x;prev=h;
        }
        return new Descriptors(a1+a2*meanX,a2*a2*varX,(Double.isFinite(left)&&Double.isFinite(right))?a2*(right-left):Double.NaN);
    }
}
