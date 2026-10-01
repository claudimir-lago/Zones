package zones.processing;

import java.util.Arrays;
import org.ejml.data.DMatrixRMaj;
import org.ejml.dense.row.factory.DecompositionFactory_DDRM;

/** Trust-region reflective least squares, x_scale=1, exact SVD subproblem, soft-L1.
 * Adapted from SciPy optimize/_lsq/trf.py and common.py (BSD-3-Clause).
 * Copyright SciPy Developers; see licenses/SciPy-BSD-3-Clause.txt.
 * Finite bounds, dense Jacobian and synchronous execution are the supported subset.
 */
final class BoundedRobustLeastSquares {
    interface Residual {double[] evaluate(double[] parameters);}
    record Result(double[] parameters,boolean success,String message,int evaluations){}
    private static final double EPS=Math.ulp(1.0),TOL=1e-8;
    static Result solve(Residual function,double[] initial,double[] lower,double[] upper,double scale,int maximum){
        double[] x=initial.clone(),raw=function.evaluate(x);int evaluations=1,n=x.length,m=raw.length;
        double cost=cost(raw,scale),delta=0,alpha=0;
        while(evaluations<maximum){
            if(Thread.currentThread().isInterrupted())return new Result(x,false,"Interrupted",evaluations);
            double[][] jac=new double[m][n];
            for(int j=0;j<n;j++){
                double h=Math.sqrt(EPS)*Math.max(1,Math.abs(x[j]))*(x[j]<0?-1:1);
                if(x[j]+h>upper[j]||x[j]+h<lower[j])h=-h;
                double[] shifted=x.clone();shifted[j]+=h;h=shifted[j]-x[j];double[] r=function.evaluate(shifted);
                for(int i=0;i<m;i++)jac[i][j]=(r[i]-raw[i])/h;
            }
            double[] residual=new double[m],gradient=new double[n];
            for(int i=0;i<m;i++){
                double z=raw[i]/scale,weight=1/Math.sqrt(1+z*z),js=Math.sqrt(Math.max(EPS,weight*weight*weight));
                residual[i]=raw[i]*weight/js;
                for(int j=0;j<n;j++){jac[i][j]*=js;gradient[j]+=jac[i][j]*residual[i];}
            }
            double[] d=new double[n],diagonal=new double[n],gh=new double[n];double gnorm=0;
            for(int j=0;j<n;j++){
                double v=gradient[j]<0?upper[j]-x[j]:gradient[j]>0?x[j]-lower[j]:1;
                double dv=gradient[j]<0?-1:gradient[j]>0?1:0;
                d[j]=Math.sqrt(v);diagonal[j]=gradient[j]*dv;gh[j]=d[j]*gradient[j];gnorm=Math.max(gnorm,Math.abs(gradient[j]*v));
            }
            if(gnorm<TOL)return new Result(x,true,"Gradient converged",evaluations);
            if(delta==0){for(int j=0;j<n;j++)delta+=Math.pow(x[j]/d[j],2);delta=Math.sqrt(delta);if(delta==0)delta=1;}
            double[][] hessian=new double[n][n];DMatrixRMaj augmented=new DMatrixRMaj(m+n,n);
            for(int i=0;i<m;i++)for(int j=0;j<n;j++)augmented.set(i,j,jac[i][j]*d[j]);
            for(int j=0;j<n;j++){
                augmented.set(m+j,j,Math.sqrt(diagonal[j]));
                for(int k=0;k<n;k++){double v=j==k?diagonal[j]:0;for(int i=0;i<m;i++)v+=augmented.get(i,j)*augmented.get(i,k);hessian[j][k]=v;}
            }
            var svd=DecompositionFactory_DDRM.svd(m+n,n,true,true,true);
            if(!svd.decompose(augmented))return new Result(x,false,"SVD failure",evaluations);
            var u=svd.getU(null,false);var v=svd.getV(null,false);double[] s=Arrays.copyOf(svd.getSingularValues(),n),uf=new double[n];
            for(int j=0;j<n;j++)for(int i=0;i<m;i++)uf[j]+=u.get(i,j)*residual[i];
            // EJML does not guarantee sorted singular values.
            for(int j=0;j<n;j++)for(int k=j+1;k<n;k++)if(s[k]>s[j]){
                double tmp=s[j];s[j]=s[k];s[k]=tmp;tmp=uf[j];uf[j]=uf[k];uf[k]=tmp;
                for(int i=0;i<n;i++){tmp=v.get(i,j);v.set(i,j,v.get(i,k));v.set(i,k,tmp);}
            }
            double theta=Math.max(.995,1-gnorm);boolean accepted=false;
            while(!accepted&&evaluations<maximum){
                var trust=trustStep(s,uf,v,delta,alpha,m);alpha=trust.alpha;
                double[] stepH=select(x,trust.step,d,gh,hessian,delta,lower,upper,theta),step=product(stepH,d),next=x.clone();
                for(int j=0;j<n;j++){
                    next[j]+=step[j];if(next[j]<=lower[j])next[j]=Math.nextUp(lower[j]);if(next[j]>=upper[j])next[j]=Math.nextDown(upper[j]);
                }
                double predicted=-quadratic(hessian,gh,stepH),stepNorm=norm(stepH);
                double[] nextRaw=function.evaluate(next);evaluations++;double nextCost=cost(nextRaw,scale);
                if(!Double.isFinite(nextCost)){delta=.25*stepNorm;continue;}
                double reduction=cost-nextCost,ratio=predicted>0?reduction/predicted:predicted==0&&reduction==0?1:0;
                double nextDelta=ratio<.25?.25*stepNorm:ratio>.75&&stepNorm>.95*delta?2*delta:delta;
                boolean ftol=reduction<TOL*cost&&ratio>.25,xtol=norm(step)<TOL*(TOL+norm(x));
                if(reduction>0){x=next;raw=nextRaw;cost=nextCost;accepted=true;}
                if(ftol||xtol)return new Result(x,true,ftol?"Cost converged (TRF)":"Step converged (TRF)",evaluations);
                if(!(nextDelta>0))return new Result(x,false,"Degenerate trust region",evaluations);
                alpha*=delta/nextDelta;delta=nextDelta;
            }
        }
        return new Result(x,false,"Maximum evaluations reached",evaluations);
    }
    private record Trust(double[] step,double alpha){}
    private static Trust trustStep(double[] s,double[] uf,DMatrixRMaj v,double delta,double alpha,int m){
        int n=s.length;boolean full=m>=n&&s[n-1]>EPS*m*s[0];double[] suf=new double[n],coeff=new double[n];
        for(int j=0;j<n;j++){suf[j]=s[j]*uf[j];coeff[j]=full?-uf[j]/s[j]:0;}
        if(full){double[] gn=multiply(v,coeff);if(norm(gn)<=delta)return new Trust(gn,0);}
        double upper=norm(suf)/delta,lower=0;
        if(full){var phi=phi(0,suf,s,delta);lower=-phi[0]/phi[1];}
        if(!full&&alpha==0)alpha=Math.max(.001*upper,Math.sqrt(lower*upper));
        for(int iteration=0;iteration<10;iteration++){
            if(alpha<lower||alpha>upper)alpha=Math.max(.001*upper,Math.sqrt(lower*upper));
            var phi=phi(alpha,suf,s,delta);if(phi[0]<0)upper=alpha;
            double ratio=phi[0]/phi[1];lower=Math.max(lower,alpha-ratio);alpha-=(phi[0]+delta)*ratio/delta;
            if(Math.abs(phi[0])<.01*delta)break;
        }
        for(int j=0;j<n;j++)coeff[j]=-suf[j]/(s[j]*s[j]+alpha);
        double[] step=multiply(v,coeff);double factor=delta/norm(step);for(int j=0;j<n;j++)step[j]*=factor;
        return new Trust(step,alpha);
    }
    private static double[] phi(double alpha,double[] suf,double[] s,double delta){
        double length=0,derivative=0;for(int j=0;j<s.length;j++){double den=s[j]*s[j]+alpha;length+=Math.pow(suf[j]/den,2);derivative+=suf[j]*suf[j]/(den*den*den);}
        length=Math.sqrt(length);return new double[]{length-delta,-derivative/length};
    }
    private static double[] select(double[] x,double[] ph,double[] d,double[] g,double[][] h,double delta,double[] lower,double[] upper,double theta){
        double[] p=product(ph,d);double stride=boundStride(x,p,lower,upper);
        if(stride>=1)return ph;
        double[] reflected=ph.clone();
        for(int j=0;j<p.length;j++)if(p[j]!=0){double hit=(p[j]>0?upper[j]-x[j]:lower[j]-x[j])/p[j];if(hit==stride)reflected[j]*=-1;}
        double[] base=ph.clone(),onBound=x.clone();for(int j=0;j<p.length;j++){base[j]*=stride;onBound[j]+=p[j]*stride;}
        double toTrust=intersection(base,reflected,delta),toBound=boundStride(onBound,product(reflected,d),lower,upper),rStride=Math.min(toTrust,toBound);
        double low=rStride>0?(1-theta)*stride/rStride:0,high=rStride>0?(rStride==toBound?theta*toBound:toTrust):-1;
        double[] best=base.clone();for(int j=0;j<best.length;j++)best[j]*=theta;
        double bestValue=quadratic(h,g,best);
        if(low<=high){double amount=minimize(h,g,base,reflected,low,high);double[] trial=base.clone();for(int j=0;j<trial.length;j++)trial[j]+=amount*reflected[j];double value=quadratic(h,g,trial);if(value<bestValue){best=trial;bestValue=value;}}
        double[] anti=g.clone();for(int j=0;j<anti.length;j++)anti[j]=-anti[j];
        double max=Math.min(delta/norm(anti),theta*boundStride(x,product(anti,d),lower,upper));
        double amount=minimize(h,g,new double[x.length],anti,0,max);for(int j=0;j<anti.length;j++)anti[j]*=amount;
        return quadratic(h,g,anti)<bestValue?anti:best;
    }
    private static double minimize(double[][] h,double[] g,double[] base,double[] direction,double low,double high){
        double a=0,b=dot(g,direction);for(int j=0;j<g.length;j++)for(int k=0;k<g.length;k++){a+=.5*direction[j]*h[j][k]*direction[k];b+=base[j]*h[j][k]*direction[k];}
        double middle=a>0?PeakDetector.clip(-b/(2*a),low,high):low;
        double best=low,val=low*(a*low+b);for(double candidate:new double[]{high,middle})if(candidate*(a*candidate+b)<val){best=candidate;val=candidate*(a*candidate+b);}return best;
    }
    private static double intersection(double[] base,double[] direction,double delta){double a=dot(direction,direction),b=dot(base,direction),c=Math.min(0,dot(base,base)-delta*delta);return (-b+Math.sqrt(b*b-a*c))/a;}
    private static double boundStride(double[] x,double[] p,double[] lower,double[] upper){double result=Double.POSITIVE_INFINITY;for(int j=0;j<x.length;j++)if(p[j]!=0)result=Math.min(result,(p[j]>0?upper[j]-x[j]:lower[j]-x[j])/p[j]);return result;}
    private static double quadratic(double[][] h,double[] g,double[] p){double sum=dot(g,p);for(int j=0;j<p.length;j++)for(int k=0;k<p.length;k++)sum+=.5*p[j]*h[j][k]*p[k];return sum;}
    private static double[] product(double[] a,double[] b){double[] out=new double[a.length];for(int i=0;i<a.length;i++)out[i]=a[i]*b[i];return out;}
    private static double[] multiply(DMatrixRMaj v,double[] c){double[] out=new double[c.length];for(int i=0;i<c.length;i++)for(int j=0;j<c.length;j++)out[i]+=v.get(i,j)*c[j];return out;}
    private static double dot(double[] a,double[] b){double s=0;for(int i=0;i<a.length;i++)s+=a[i]*b[i];return s;}
    private static double norm(double[] a){return Math.sqrt(dot(a,a));}
    private static double cost(double[] r,double scale){double sum=0;for(double v:r){double z=v/scale;sum+=v*v/(Math.hypot(1,z)+1);}return sum;}
}
