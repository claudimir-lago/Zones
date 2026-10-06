package zones.io;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.zip.*;
import zones.model.*;
import zones.processing.*;

/** User-facing, reproducible, TAB-delimited export (files keep the familiar .csv extension). */
public final class UserResultsExporter {
    public record Options(boolean timeElectropherogram, boolean chargeElectropherogram, boolean mobilityElectropherogram) {}

    public void write(Path path, BaselineResult baseline, AnalysisResult analysis, PeakAnalysisResult peaks,
            MobilityCalibration calibration, boolean invertCharge, Map<String,String> extraMetadata, Options options) throws IOException {
        try (var zip=new ZipOutputStream(Files.newOutputStream(path), StandardCharsets.UTF_8)) {
            entry(zip,"quantitation.csv",quantitation(baseline,analysis,peaks,calibration,invertCharge,extraMetadata));
            if(options.timeElectropherogram())entry(zip,"electropherogram_time.csv",electropherogram(baseline,ElectropherogramDomain.TIME,calibration,invertCharge));
            if(options.chargeElectropherogram()&&new DomainTransform(baseline.data(),calibration,invertCharge).hasCharge())entry(zip,"electropherogram_charge.csv",electropherogram(baseline,ElectropherogramDomain.CHARGE,calibration,invertCharge));
            if(options.mobilityElectropherogram()&&calibration!=null)entry(zip,"electropherogram_mobility.csv",electropherogram(baseline,ElectropherogramDomain.MOBILITY,calibration,invertCharge));
            entry(zip,"README.txt","Zones user export. All .csv files use TAB-separated columns.\nThe quantitation file begins with reproducibility metadata (# key<TAB>value), followed by the peak table.\nElectropherogram files contain the processed, baseline-corrected signal limited to the analyzed data range.\n");
        }
    }


    public void writeDirectory(Path directory, BaselineResult baseline, AnalysisResult analysis, PeakAnalysisResult peaks,
            MobilityCalibration calibration, boolean invertCharge, Map<String,String> extraMetadata, Options options) throws IOException {
        Files.createDirectories(directory);
        Files.writeString(directory.resolve("quantitation.csv"),quantitation(baseline,analysis,peaks,calibration,invertCharge,extraMetadata),StandardCharsets.UTF_8);
        if(options.timeElectropherogram())Files.writeString(directory.resolve("electropherogram_time.csv"),electropherogram(baseline,ElectropherogramDomain.TIME,calibration,invertCharge),StandardCharsets.UTF_8);
        if(options.chargeElectropherogram()&&new DomainTransform(baseline.data(),calibration,invertCharge).hasCharge())Files.writeString(directory.resolve("electropherogram_charge.csv"),electropherogram(baseline,ElectropherogramDomain.CHARGE,calibration,invertCharge),StandardCharsets.UTF_8);
        if(options.mobilityElectropherogram()&&calibration!=null)Files.writeString(directory.resolve("electropherogram_mobility.csv"),electropherogram(baseline,ElectropherogramDomain.MOBILITY,calibration,invertCharge),StandardCharsets.UTF_8);
        Files.writeString(directory.resolve("README.txt"),"Zones user export. All .csv files use TAB-separated columns.\nThe quantitation file begins with reproducibility metadata (# key<TAB>value), followed by the peak table.\nElectropherogram files contain the processed, baseline-corrected signal limited to the analyzed data range.\n",StandardCharsets.UTF_8);
    }

    private String quantitation(BaselineResult baseline, AnalysisResult analysis, PeakAnalysisResult peaks,
            MobilityCalibration calibration, boolean invertCharge, Map<String,String> extra) {
        StringBuilder s=new StringBuilder();
        meta(s,"zones.version","0.7.2");meta(s,"source",baseline.data().source().toString());meta(s,"detector",baseline.detector().toString());
        meta(s,"analysis.pipeline",AnalysisResult.PIPELINE_VERSION);meta(s,"baseline.algorithm",BaselineResult.ALGORITHM_VERSION);meta(s,"peak.algorithm",PeakAnalysisResult.ALGORITHM_VERSION);
        meta(s,"baseline.parameters",baseline.parametersUsed().toString());meta(s,"analysis.parameters",analysis==null?"unavailable":analysis.parametersUsed().toString());meta(s,"peak.parameters",peaks.parametersUsed().toString());
        meta(s,"sampling.interval.s",Double.toString(baseline.data().samplingIntervalSeconds()));meta(s,"sampling.rate.hz",Double.toString(baseline.data().samplingRateHz()));meta(s,"charge.axis.inverted",Boolean.toString(invertCharge));
        meta(s,"mobility.normalization","25 °C; aqueous inverse-viscosity model (water)");
        if(calibration!=null){meta(s,"mobility.kind",calibration.kind().toString());meta(s,"mobility.basis",calibration.basisName());if(calibration.usesCharge())meta(s,"mobility.k.Ti_mC",Double.toString(calibration.kTiMilliCoulombs()));else meta(s,"mobility.k.Ti_min",Double.toString(calibration.kTiMinutes()));meta(s,"mobility.eof.Ti",Double.toString(calibration.eofMobilityTi()));}
        if(extra!=null)extra.forEach((k,v)->meta(s,k,v));s.append('\n');
        s.append("window\tcomponent\tmigration_time_min\ttime_area_au_s\tmigration_charge_mC\tcharge_area_au_mC\tmobility_Ti_25C\tmobility_area_au_Ti\tN\tNGau\tFWHM_time_s\tR2_time_fit\tfit_reliable\n");
        DomainPeakCalculator time=new DomainPeakCalculator(baseline.data(),ElectropherogramDomain.TIME,calibration,invertCharge);
        DomainPeakCalculator charge=new DomainPeakCalculator(baseline.data(),ElectropherogramDomain.CHARGE,calibration,invertCharge);
        DomainPeakCalculator mobility=calibration==null?null:new DomainPeakCalculator(baseline.data(),ElectropherogramDomain.MOBILITY,calibration,invertCharge);
        int component=0;
        for(PeakFit fit:peaks.fits())for(PeakComponent c:fit.components()){
            component++;DomainPeakMetrics tm=time.metrics(c),qm=charge.metrics(c),mm=mobility==null?null:mobility.metrics(c);
            s.append(fit.window().id()).append('\t').append(component).append('\t').append(num(tm.position()/60.0)).append('\t').append(num(tm.signedArea())).append('\t')
             .append(qm.available()?num(qm.position()):"").append('\t').append(qm.available()?num(qm.signedArea()):"").append('\t')
             .append(mm!=null&&mm.available()?num(mm.position()):"").append('\t').append(mm!=null&&mm.available()?num(mm.signedArea()):"").append('\t')
             .append(num(c.effectivePlates())).append('\t').append(num(c.gaussianPlates())).append('\t').append(num(c.fwhmSeconds())).append('\t').append(num(fit.r2())).append('\t').append(c.fitReliable()).append('\n');
        }
        return s.toString();
    }

    private String electropherogram(BaselineResult baseline, ElectropherogramDomain domain, MobilityCalibration calibration, boolean invertCharge){
        DomainTransform tr=new DomainTransform(baseline.data(),calibration,invertCharge);double[] t=baseline.data().timeMinutes(),y=baseline.correctedSignal();
        String xname=switch(domain){case TIME->"time_min";case CHARGE->invertCharge?"minus_charge_mC":"charge_mC";case MOBILITY->calibration!=null&&calibration.isEffective()?"effective_mobility_Ti_25C":"apparent_mobility_Ti_25C";};
        StringBuilder s=new StringBuilder(xname+"\tcorrected_C4D_au\n");
        for(int i=0;i<t.length;i++){double x=tr.xAtTime(domain,t[i]);if(Double.isFinite(x)&&Double.isFinite(y[i]))s.append(num(x)).append('\t').append(num(y[i])).append('\n');}
        return s.toString();
    }
    private static void meta(StringBuilder s,String k,String v){s.append("# ").append(k).append('\t').append(v==null?"":v.replace('\n',' ')).append('\n');}
    private static String num(double v){return Double.isFinite(v)?String.format(Locale.ROOT,"%.12g",v):"";}
    private static void entry(ZipOutputStream zip,String name,String text)throws IOException{zip.putNextEntry(new ZipEntry(name));zip.write(text.getBytes(StandardCharsets.UTF_8));zip.closeEntry();}
}
