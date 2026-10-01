package zones.processing;

/** Signed Test50 component, with an unnormalised Gaussian magnitude. */
public final class SkewNormal {
    private SkewNormal(){}
    public static double value(double time,double magnitude,double center,double sigma,double alpha,int polarity){
        double z=(time-center)/sigma;
        return polarity*magnitude*Math.exp(-.5*z*z)*2*cdf(alpha*z);
    }
    /** Normal CDF via convergent erf series centrally and a continued fraction in tails. */
    public static double cdf(double x){
        if(x==0)return .5;
        double a=Math.abs(x),tail;
        if(a>38)tail=0;
        else if(a<7){
            double sum=a,term=a;
            for(int k=1;k<1000;k++){term*=a*a/(2*k+1);double previous=sum;sum+=term;if(sum==previous)break;}
            tail=.5-sum*Math.exp(-a*a/2)/Math.sqrt(2*Math.PI);
        }else{
            double fraction=0;for(int k=150;k>=1;k--)fraction=k/(a+fraction);
            tail=Math.exp(-a*a/2)/Math.sqrt(2*Math.PI)/(a+fraction);
        }
        return x<0?tail:1-tail;
    }
    public static double area(double magnitude,double sigma,int polarity){return polarity*magnitude*sigma*Math.sqrt(2*Math.PI);}
    public static double apex(double center,double sigma,double alpha){
        // Unimodal oriented shape: bounded golden-section search is independent of acquisition grid.
        double left=-10,right=10,g=(Math.sqrt(5)-1)/2;
        double a=right-g*(right-left),b=left+g*(right-left);
        for(int i=0;i<100;i++){
            if(value(a,1,0,1,alpha,1)<value(b,1,0,1,alpha,1)){left=a;a=b;b=left+g*(right-left);}
            else{right=b;b=a;a=right-g*(right-left);}
        }
        return center+sigma*(left+right)/2;
    }
    public static double integrate(double start,double end,double magnitude,double center,double sigma,double alpha,int polarity){
        // Resolve the narrow skew transition as well as sigma; truncate negligible Gaussian tails.
        double lo=Math.max(start,center-12*sigma),hi=Math.min(end,center+12*sigma);
        if(hi<=lo)return 0;
        int n=Math.max(256,(int)Math.ceil((hi-lo)/sigma*Math.max(1,Math.abs(alpha))*20));if(n%2!=0)n++;
        double h=(hi-lo)/n,sum=value(lo,magnitude,center,sigma,alpha,polarity)+value(hi,magnitude,center,sigma,alpha,polarity);
        for(int i=1;i<n;i++)sum+=(i%2==0?2:4)*value(lo+i*h,magnitude,center,sigma,alpha,polarity);
        return sum*h/3;
    }
}
