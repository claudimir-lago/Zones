package zones.processing;

import zones.model.ElectropherogramData;
import zones.model.ElectropherogramDomain;
import zones.model.MobilityCalibration;

/**
 * Centralized x-axis transformation used by charting and quantitative reporting.
 * Time remains the scientific preprocessing/detection coordinate. Charge and
 * mobility are presentation/quantitation coordinates derived sample-by-sample.
 */
public final class DomainTransform {
    public static final double MOBILITY_DISPLAY_LIMIT_TI = 1000.0;
    private final double[] timeMinutes;
    private final double[] signedChargeMilliCoulombs;
    private final MobilityCalibration calibration;

    public DomainTransform(ElectropherogramData data, MobilityCalibration calibration) {
        this.timeMinutes=data.timeMinutes();
        this.signedChargeMilliCoulombs=data.currentMicroamps()
                .map(cur->new ChargeCalculator().fromCurrent(timeMinutes,cur))
                .orElseGet(()->data.chargeMilliCoulombs().orElse(null));
        this.calibration=calibration;
    }

    public boolean hasCharge(){return signedChargeMilliCoulombs!=null;}
    public double signedChargeAtTime(double minute){
        if(!hasCharge())return Double.NaN;
        if(minute<=timeMinutes[0])return signedChargeMilliCoulombs[0];
        int n=timeMinutes.length;if(minute>=timeMinutes[n-1])return signedChargeMilliCoulombs[n-1];
        int lo=0,hi=n-1;while(hi-lo>1){int mid=(lo+hi)>>>1;if(timeMinutes[mid]<=minute)lo=mid;else hi=mid;}
        double f=(minute-timeMinutes[lo])/(timeMinutes[hi]-timeMinutes[lo]);
        return signedChargeMilliCoulombs[lo]+f*(signedChargeMilliCoulombs[hi]-signedChargeMilliCoulombs[lo]);
    }
    public double magnitudeChargeAtTime(double minute){double q=signedChargeAtTime(minute);return Double.isFinite(q)?Math.abs(q):Double.NaN;}
    public double xAtTime(ElectropherogramDomain domain,double minute){
        if(domain==ElectropherogramDomain.TIME)return minute;
        double q=signedChargeAtTime(minute);if(!Double.isFinite(q))return Double.NaN;
        if(domain==ElectropherogramDomain.CHARGE)return Math.abs(q);
        if(calibration==null||Math.abs(q)<calibration.singularityGuardMilliCoulombs())return Double.NaN;
        double mu=calibration.effectiveMobilityTi(Math.abs(q));
        if(!Double.isFinite(mu))return Double.NaN;
        mu=Math.abs(mu);
        return mu<=MOBILITY_DISPLAY_LIMIT_TI?mu:Double.NaN;
    }
    public double[] xValues(ElectropherogramDomain domain){
        double[] out=new double[timeMinutes.length];for(int i=0;i<out.length;i++)out[i]=xAtTime(domain,timeMinutes[i]);return out;
    }
    /** True when non-zero current samples of both polarities occur. */
    public boolean hasCurrentPolarityReversal(ElectropherogramData data){
        var current=data.currentMicroamps().orElse(null);if(current==null)return false;
        double max=0;for(double v:current)max=Math.max(max,Math.abs(v));
        double eps=Math.max(1e-9,max*1e-6);boolean pos=false,neg=false;
        for(double v:current){if(v>eps)pos=true;else if(v<-eps)neg=true;if(pos&&neg)return true;}return false;
    }
    /** Magnitude charge should be monotonic for a valid charge coordinate. */
    public boolean magnitudeChargeMonotonic(){
        if(!hasCharge())return false;double prev=Math.abs(signedChargeMilliCoulombs[0]);
        double scale=0;for(double q:signedChargeMilliCoulombs)scale=Math.max(scale,Math.abs(q));double tol=Math.max(1e-12,scale*1e-10);
        for(int i=1;i<signedChargeMilliCoulombs.length;i++){double now=Math.abs(signedChargeMilliCoulombs[i]);if(now+tol<prev)return false;prev=now;}return true;
    }
}
