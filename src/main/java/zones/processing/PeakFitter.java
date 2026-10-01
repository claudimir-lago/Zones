package zones.processing;

import java.util.*;
import zones.model.*;
import static zones.model.PeakParameters.*;

/** Simultaneous signed HVL components plus a shared constant offset; apex-aligned 2-D multistart. */
public final class PeakFitter {
    private static final double[] ETA_STARTS={-36,-24,-18,-12,-6,-3,-1,0,1,3,6,12,18,24,36};
    private static final double[] A2_FACTORS={.65,1.0,1.6};
    private static final double A2_MIN=.08,A2_MAX=30.0,A1_PAD_MIN=8.0;

    public PeakFit fit(PeakWindow window,double[] times,double[] signal,PeakParameters settings){
        int lo=window.leftIndex(),hi=window.rightIndex(),count=hi-lo+1,edge=Math.max(FIT_EDGE_MINIMUM,Math.min(FIT_EDGE_MAXIMUM,count/FIT_EDGE_DIVISOR));
        double[] y=Arrays.copyOfRange(signal,lo,hi+1),t=Arrays.copyOfRange(times,lo,hi+1),edges=new double[2*edge];
        System.arraycopy(y,0,edges,0,edge);System.arraycopy(y,count-edge,edges,edge,edge);
        double offset=Statistics.median(edges),noise=1.4826*Statistics.mad(edges);
        if(!(noise>0)){double mean=Arrays.stream(edges).average().orElse(0),variance=0;for(double v:edges)variance+=(v-mean)*(v-mean);noise=Math.max(Math.sqrt(variance/edges.length),FIT_NOISE_FLOOR);}
        double maxHeight=0;for(double v:y)maxHeight=Math.max(maxHeight,Math.abs(v-offset));
        BoundedRobustLeastSquares.Result best=null;double bestCost=Double.POSITIVE_INFINITY;double[] bestLower=null,bestUpper=null;
        for(double eta0:ETA_STARTS)for(double factor:A2_FACTORS){
            Setup setup=setup(window,t,offset,noise,maxHeight,settings,eta0,factor);
            var solved=BoundedRobustLeastSquares.solve(params->residual(params,t,y,window.members()),setup.initial,setup.lower,setup.upper,Math.max(noise,FIT_NOISE_FLOOR),settings.maximumEvaluations());
            double cost=softCost(residual(solved.parameters(),t,y,window.members()),Math.max(noise,FIT_NOISE_FLOOR));
            if(best==null || (solved.success()&&!best.success()) || (solved.success()==best.success()&&cost<bestCost)){bestCost=cost;best=solved;bestLower=setup.lower;bestUpper=setup.upper;}
        }
        if(best==null)throw new IllegalStateException("HVL multistart fitting failed.");
        double[] x=best.parameters(),fitted=model(x,t,window.members());
        double ss=0,total=0,mean=Arrays.stream(y).average().orElse(0),experimental=0,absolute=0;
        for(int i=0;i<count;i++){
            ss+=Math.pow(y[i]-fitted[i],2);total+=Math.pow(y[i]-mean,2);
            if(i>0){double dt=t[i]-t[i-1];experimental+=.5*(y[i]+y[i-1]-2*x[0])*dt;absolute+=.5*(Math.abs(y[i]-x[0])+Math.abs(y[i-1]-x[0]))*dt;}
        }
        List<PeakComponent> components=new ArrayList<>();double areaSum=0,absSum=0;int k=1;
        for(var c:window.members()){
            double area=c.polarity()*x[k],a1=x[k+1],a2=x[k+2],eta=x[k+3],apex=Hvl.apex(a1,a2,eta);
            double within=integrate(t[0],t[count-1],area,a1,a2,eta),captured=area!=0?Math.abs(within/area):Double.NaN;
            boolean atBound=false;for(int j=k;j<k+4;j++)if(Math.min(x[j]-bestLower[j],bestUpper[j]-x[j])<1e-5*Math.max(1,bestUpper[j]-bestLower[j]))atBound=true;
            var d=Hvl.descriptors(a1,a2,eta);double neff=d.variance()>0?a1*a1/d.variance():Double.NaN,ngau=a2>0?a1*a1/(a2*a2):Double.NaN;
            components.add(new PeakComponent(c,window.id(),x[k],a1,apex,Hvl.value(apex,area,a1,a2,eta),a2,eta,area,within,captured,best.success(),atBound,Hvl.a3(a1,a2,eta),d.variance(),d.fwhm(),neff,ngau));
            areaSum+=area;absSum+=Math.abs(area);k+=4;
        }
        components.sort(Comparator.comparingDouble(PeakComponent::fittedApexSeconds));
        return new PeakFit(window,components,x[0],best.success(),best.message(),best.evaluations(),total>0?1-ss/total:Double.NaN,Math.sqrt(ss/count),experimental,absolute,areaSum,absSum);
    }
    private record Setup(double[] initial,double[] lower,double[] upper){}
    private Setup setup(PeakWindow window,double[] t,double offset,double noise,double maxHeight,PeakParameters settings,double eta0,double factor){
        int size=1+4*window.members().size();double[] initial=new double[size],lower=new double[size],upper=new double[size];
        double ymin=Double.POSITIVE_INFINITY,ymax=Double.NEGATIVE_INFINITY;for(double v:t){} // bounds below use signal-scale arguments supplied through maxHeight
        initial[0]=offset;lower[0]=offset-2*Math.max(maxHeight,noise);upper[0]=offset+2*Math.max(maxHeight,noise);int k=1;
        for(var c:window.members()){
            double baseA2=Math.max(.20,c.w50Seconds()/FWHM_TO_SIGMA);double a2=Math.max(A2_MIN,baseA2*factor);
            double xApex=Hvl.apexOffsetX(eta0),a1=c.observedApexSeconds()-a2*xApex;
            double area=Math.max(c.localProminence()*a2*Math.sqrt(2*Math.PI),noise*a2);
            double pad=Math.max(A1_PAD_MIN,4*baseA2);
            initial[k]=area;initial[k+1]=a1;initial[k+2]=a2;initial[k+3]=eta0;
            lower[k]=0;upper[k]=Math.max(area*12,2*maxHeight*40);
            lower[k+1]=Math.max(t[0],c.observedApexSeconds()-pad);upper[k+1]=Math.min(t[t.length-1],c.observedApexSeconds()+pad);
            lower[k+2]=A2_MIN;upper[k+2]=A2_MAX;lower[k+3]=-settings.alphaMax();upper[k+3]=settings.alphaMax();
            for(int j=k;j<k+4;j++)initial[j]=PeakDetector.clip(initial[j],lower[j]+1e-8,upper[j]-1e-8);k+=4;
        }
        return new Setup(initial,lower,upper);
    }
    private static double[] residual(double[] p,double[] t,double[] y,List<PeakCandidate> members){double[] r=model(p,t,members);for(int i=0;i<r.length;i++)r[i]-=y[i];return r;}
    private static double[] model(double[] p,double[] t,List<PeakCandidate> members){
        double[] result=new double[t.length];Arrays.fill(result,p[0]);int k=1;
        for(var c:members){double area=c.polarity()*p[k];for(int i=0;i<t.length;i++)result[i]+=Hvl.value(t[i],area,p[k+1],p[k+2],p[k+3]);k+=4;}return result;
    }
    private static double integrate(double lo,double hi,double area,double a1,double a2,double eta){int n=1200;double h=(hi-lo)/n,sum=0;for(int i=0;i<=n;i++){double v=Hvl.value(lo+i*h,area,a1,a2,eta);sum+=(i==0||i==n?1:i%2==0?2:4)*v;}return sum*h/3;}
    private static double softCost(double[] r,double scale){double s=0;for(double v:r){double z=v/scale;s+=v*v/(Math.hypot(1,z)+1);}return s;}
}
