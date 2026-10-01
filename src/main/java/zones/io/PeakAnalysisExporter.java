package zones.io;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.zip.*;
import zones.model.*;

/** A single reproducible archive: candidates, components, windows, signal and provenance. */
public final class PeakAnalysisExporter {
    public void write(Path path,BaselineResult baseline,AnalysisResult analysis,PeakAnalysisResult peaks)throws IOException{
        try(var zip=new ZipOutputStream(Files.newOutputStream(path),StandardCharsets.UTF_8)){
            entry(zip,"metadata.txt","source="+baseline.data().source()+"\ndetector="+baseline.detector()+"\nbaselineAlgorithm="+BaselineResult.ALGORITHM_VERSION
                +"\npeakAlgorithm="+PeakAnalysisResult.ALGORITHM_VERSION+"\nbaselineParameters="+baseline.parametersUsed()+"\nanalysisParameters="+(analysis==null?"unavailable":analysis.parametersUsed())
                +"\npeakParameters="+peaks.parametersUsed()+"\nsamplingIntervalSeconds="+baseline.data().samplingIntervalSeconds()+"\nsamplingRateHz="+baseline.data().samplingRateHz()
                +"\nareas=a.u. * s; failed/unreliable fit values are diagnostic only\nLPNR is not analytical SNR or LOD/LOQ\n");
            StringBuilder candidates=new StringBuilder("lpnr_rank,prominence_rank,index,polarity,observed_apex_min,observed_signal_au,local_prominence_au,noise_sigma_au,noise_points,lpnr,bes,w50_s,left_base,right_base,selected,status\n");
            for(var c:peaks.candidates())candidates.append(candidate(c)).append(',').append(c.leftBase()).append(',').append(c.rightBase()).append(',').append(c.selected(peaks.parametersUsed().lpnrThreshold())).append(',').append(c.selected(peaks.parametersUsed().lpnrThreshold())?"SELECTED_BY_LPNR_AND_BES":"DETECTED_CANDIDATE").append('\n');
            entry(zip,"candidates.csv",candidates.toString());
            StringBuilder components=new StringBuilder("window,lpnr_rank,prominence_rank,index,polarity,observed_apex_min,observed_signal_au,local_prominence_au,noise_sigma_au,noise_points,lpnr,bes,w50_s,migration_time_a1_min,fitted_apex_min,fitted_apex_au,center_minus_observed_s,apex_minus_observed_s,apex_minus_center_s,eta,a2_s,a3_s,signed_area_au_s,absolute_area_au_s,area_within_window_au_s,captured_fraction,effective_variance_s2,fwhm_s,N_effective,N_gaussian,optimizer_success,fit_reliable,parameter_at_bound,r2,rms_au,window_type\n");
            StringBuilder windows=new StringBuilder("window,type,start_min,end_min,member_lpnr_ranks,offset_au,experimental_algebraic_area_au_s,experimental_absolute_area_au_s,fitted_algebraic_area_au_s,absolute_component_sum_au_s,isolated_area_difference_percent,optimizer_success,fit_reliable,r2,rms_au,evaluations,message\n");
            for(var f:peaks.fits()){
                var w=f.window();windows.append(w.id()).append(',').append(w.type()).append(',').append(w.startSeconds()/60).append(',').append(w.endSeconds()/60).append(',').append(quote(w.members().stream().map(c->Integer.toString(c.rank())).toList().toString())).append(',').append(f.offset()).append(',').append(f.experimentalAlgebraicArea()).append(',').append(f.experimentalAbsoluteArea()).append(',').append(f.fittedAlgebraicArea()).append(',').append(f.absoluteComponentSum()).append(',').append(f.isolatedAreaDifferencePercent()).append(',').append(f.success()).append(',').append(f.reliable()).append(',').append(f.r2()).append(',').append(f.rms()).append(',').append(f.evaluations()).append(',').append(quote(f.message())).append('\n');
                for(var c:f.components())components.append(w.id()).append(',').append(candidate(c.candidate())).append(',').append(c.fittedCenterSeconds()/60).append(',').append(c.fittedApexSeconds()/60).append(',').append(c.fittedApexSignal()).append(',').append(c.centerMinusObservedSeconds()).append(',').append(c.fittedApexMinusObservedSeconds()).append(',').append(c.fittedApexMinusCenterSeconds()).append(',').append(c.eta()).append(',').append(c.a2Seconds()).append(',').append(c.a3Seconds()).append(',').append(c.signedArea()).append(',').append(c.absoluteArea()).append(',').append(c.areaWithinWindow()).append(',').append(c.capturedFraction()).append(',').append(c.effectiveVarianceSeconds2()).append(',').append(c.fwhmSeconds()).append(',').append(c.effectivePlates()).append(',').append(c.gaussianPlates()).append(',').append(f.success()).append(',').append(c.fitReliable()).append(',').append(c.parameterAtBound()).append(',').append(f.r2()).append(',').append(f.rms()).append(',').append(w.type()).append('\n');
            }
            entry(zip,"components.csv",components.toString());entry(zip,"windows.csv",windows.toString());
            double[] times=baseline.data().timeMinutes(),input=baseline.rawSignal(),b=baseline.baseline(),corrected=baseline.correctedSignal();
            double[] raw=analysis==null?input:analysis.preprocessing().raw().signal(baseline.detector());
            boolean[] high=baseline.highConfidenceBaselineMask(),recovered=baseline.recoveredBaselineMask(),mask=baseline.finalBaselineMask();int[] votes=baseline.voteCount();
            StringBuilder signals=new StringBuilder("time_min,raw_au,baseline_input_au,baseline_au,corrected_au,votes,high_confidence,recovered,final_mask\n");
            for(int i=0;i<times.length;i++)signals.append(times[i]).append(',').append(raw[i]).append(',').append(input[i]).append(',').append(b[i]).append(',').append(corrected[i]).append(',').append(votes[i]).append(',').append(high[i]).append(',').append(recovered[i]).append(',').append(mask[i]).append('\n');
            entry(zip,"signal.csv",signals.toString());
        }
    }
    private String candidate(PeakCandidate c){return c.rank()+","+c.prominenceRank()+","+c.index()+","+c.polarity()+","+c.observedApexSeconds()/60+","+c.observedApexSignal()+","+c.localProminence()+","+c.localNoiseSigma()+","+c.noisePointCount()+","+c.lpnr()+","+c.bes()+","+c.w50Seconds();}
    private String quote(String text){return "\""+text.replace("\"","\"\"")+"\"";}
    private void entry(ZipOutputStream zip,String name,String text)throws IOException{zip.putNextEntry(new ZipEntry(name));zip.write(text.getBytes(StandardCharsets.UTF_8));zip.closeEntry();}
}
