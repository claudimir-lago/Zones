package zones.processing;
import zones.model.*;
/** Centralized signed x-axis transformation. */
public final class DomainTransform {
 public static final double MOBILITY_DISPLAY_LIMIT_TI=1000.0; private final double[] timeMinutes,signedChargeMilliCoulombs; private final MobilityCalibration calibration; private final boolean invertCharge;
 public DomainTransform(ElectropherogramData data,MobilityCalibration calibration){this(data,calibration,false);}
 public DomainTransform(ElectropherogramData data,MobilityCalibration calibration,boolean invertCharge){
  this.timeMinutes=data.timeMinutes();
  double[] stored=data.chargeMilliCoulombs().orElse(null), current=data.currentMicroamps().orElse(null), q;
  if(current!=null){q=new ChargeCalculator().fromCurrent(timeMinutes,current);if(stored!=null&&stored.length==q.length){double offset=stored[0]-q[0];for(int i=0;i<q.length;i++)q[i]+=offset;}}
  else q=stored;
  this.signedChargeMilliCoulombs=q;this.calibration=calibration;this.invertCharge=invertCharge;
 }
 public boolean hasCharge(){return signedChargeMilliCoulombs!=null;}
 public double signedChargeAtTime(double minute){if(!hasCharge())return Double.NaN;if(minute<=timeMinutes[0])return signedChargeMilliCoulombs[0];int n=timeMinutes.length;if(minute>=timeMinutes[n-1])return signedChargeMilliCoulombs[n-1];int lo=0,hi=n-1;while(hi-lo>1){int mid=(lo+hi)>>>1;if(timeMinutes[mid]<=minute)lo=mid;else hi=mid;}double f=(minute-timeMinutes[lo])/(timeMinutes[hi]-timeMinutes[lo]);return signedChargeMilliCoulombs[lo]+f*(signedChargeMilliCoulombs[hi]-signedChargeMilliCoulombs[lo]);}
 public double xAtTime(ElectropherogramDomain domain,double minute){if(domain==ElectropherogramDomain.TIME)return minute;double q=signedChargeAtTime(minute);if(!Double.isFinite(q))return Double.NaN;if(domain==ElectropherogramDomain.CHARGE)return invertCharge?-q:q;if(calibration==null||Math.abs(q)<calibration.singularityGuardMilliCoulombs())return Double.NaN;double mu=calibration.mobilityTi(q);return Double.isFinite(mu)&&Math.abs(mu)<=MOBILITY_DISPLAY_LIMIT_TI?mu:Double.NaN;}
 public double[] xValues(ElectropherogramDomain domain){double[]o=new double[timeMinutes.length];for(int i=0;i<o.length;i++)o[i]=xAtTime(domain,timeMinutes[i]);return o;}
 public boolean hasCurrentPolarityReversal(ElectropherogramData data){var current=data.currentMicroamps().orElse(null);if(current==null)return false;double max=0;for(double v:current)max=Math.max(max,Math.abs(v));double eps=Math.max(1e-9,max*1e-6);boolean pos=false,neg=false;for(double v:current){if(v>eps)pos=true;else if(v<-eps)neg=true;if(pos&&neg)return true;}return false;}
 public boolean signedChargeMonotonic(){if(!hasCharge())return false;double prev=signedChargeMilliCoulombs[0],scale=0;for(double q:signedChargeMilliCoulombs)scale=Math.max(scale,Math.abs(q));double tol=Math.max(1e-12,scale*1e-10);int dir=0;for(int i=1;i<signedChargeMilliCoulombs.length;i++){double d=signedChargeMilliCoulombs[i]-prev;if(Math.abs(d)>tol){int s=d>0?1:-1;if(dir==0)dir=s;else if(s!=dir)return false;}prev=signedChargeMilliCoulombs[i];}return dir!=0;}
}
