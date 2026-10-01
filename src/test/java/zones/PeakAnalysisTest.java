package zones;

import java.nio.file.*;
import java.util.*;
import zones.application.*;
import zones.model.*;
import zones.processing.*;
import zones.io.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class PeakAnalysisTest {
    static AnalysisResult test50Analysis()throws Exception{
        var data=new MinimalisticeDatReader().read(Path.of("BAH112026.09.15_13h13m57s.dat"));
        var p=PhysicalBaselineParameters.defaults();
        var physical=new PhysicalBaselineParameters(3,p.minimumLengthSeconds(),p.voteThresholdK(),p.peakWidthMinimumSeconds(),p.peakWidthMaximumSeconds(),p.numberOfScales(),p.logLambda(),p.localWindowSeconds(),p.noiseFactor(),p.slopeFactor(),p.minimumRunSeconds());
        return new AnalysisProcessor().process(data,DetectorChannel.RIGHT,new AnalysisParameters(physical,SpikeRemovalParameters.defaults(),false));
    }
    static double[][] fixture(String name)throws Exception{
        try(var lines=Files.lines(Path.of("src/test/resources/test50/"+name+".tsv"))){return lines.skip(1).map(l->Arrays.stream(l.split("\t")).mapToDouble(Double::parseDouble).toArray()).toArray(double[][]::new);}
    }
    @org.junit.jupiter.api.Disabled("Legacy skew-normal fit fixture; production fitter is now HVL")
    @Test void test50StagesAndFits()throws Exception{
        var rows=fixture("signal");int n=rows.length;double[] t=new double[n],y=new double[n];boolean[] mask=new boolean[n];
        for(int i=0;i<n;i++){t[i]=rows[i][0]*60;y[i]=rows[i][2];mask[i]=rows[i][3]!=0;}
        double dt=.0768;var p=PeakParameters.defaults();var candidates=new PeakDetector().detect(t,y,mask,dt,p);
        var expected=fixture("candidates");assertEquals(expected.length,candidates.size());
        for(var row:expected){var c=candidates.stream().filter(v->v.index()==(int)row[1]).findFirst().orElseThrow();
            assertEquals((int)row[0],c.prominenceRank());assertEquals(row[2],c.polarity());assertEquals(row[3],c.localProminence(),1e-8);
            assertEquals(row[4],c.w50Seconds(),1e-8);assertEquals(row[5],c.localNoiseSigma(),1e-8);assertEquals(row[6],c.noisePointCount());assertEquals(row[7],c.lpnr(),1e-7);
        }
        var result=new PeakAnalysisProcessor().refit(t,y,dt,candidates,p);assertEquals(8,result.selectedCount());assertEquals(5,result.fits().size());
        for(var fit:result.fits()){
            assertEquals(fit.components().stream().mapToDouble(PeakComponent::signedArea).sum(),fit.fittedAlgebraicArea(),1e-8);
            assertTrue(fit.experimentalAbsoluteArea()>=Math.abs(fit.experimentalAlgebraicArea())-1e-8);
            if(fit.window().type()==PeakWindow.Type.BIPOLAR_COMPOSITE)assertTrue(fit.absoluteComponentSum()>Math.abs(fit.fittedAlgebraicArea()));
            for(var c:fit.components()){assertEquals(c.candidate().polarity(),Math.signum(c.signedArea()));assertEquals(Math.abs(c.areaWithinWindow()/c.signedArea()),c.capturedFraction());}
        }
        StringBuilder report=new StringBuilder("window,referenceRank,success,r2,area,centerMinutes,sigma,alpha,evaluations,message\n");
        for(var fit:result.fits())for(var c:fit.components())report.append(String.format(Locale.ROOT,"%d,%d,%s,%.9f,%.6f,%.9f,%.6f,%.6f,%d,%s%n",fit.window().id(),c.candidate().prominenceRank(),fit.success(),fit.r2(),c.signedArea(),c.fittedCenterSeconds()/60,c.sigmaSeconds(),c.alpha(),fit.evaluations(),fit.message()));
        Files.writeString(Path.of("target/test50-java.csv"),report);
        for(var row:fixture("fits")){
            var fit=result.fits().get((int)row[0]-1);assertEquals((int)row[1],fit.window().leftIndex());assertEquals((int)row[2],fit.window().rightIndex());
            if(row[4]==0){assertFalse(fit.success());assertFalse(fit.reliable(),"Reference failed window must remain unreliable");assertEquals(5000,fit.evaluations());continue;}
            var c=fit.components().stream().filter(v->v.candidate().prominenceRank()==(int)row[3]).findFirst().orElseThrow();
            assertTrue(fit.success(),fit.message());assertEquals(row[5],fit.r2(),.002);
            assertEquals(row[7]*row[9]*Math.sqrt(2*Math.PI),c.absoluteArea(),.0001*c.absoluteArea());
            assertEquals(row[8]*60,c.fittedCenterSeconds(),.002);
            double referenceApex=SkewNormal.apex(row[8]*60,row[9],row[10]);
            assertEquals(referenceApex,c.fittedApexSeconds(),.002);assertTrue(c.capturedFraction()>.995);
        }
    }
    @Test void skewNormalAreasModesAndPolarity(){
        for(int sign:new int[]{-1,1})for(double alpha:new double[]{-24.5,0,7.8,30}){
            double area=SkewNormal.area(12,1.7,sign),numerical=SkewNormal.integrate(-30,30,12,0,1.7,alpha,sign);
            assertEquals(area,numerical,1e-8);double apex=SkewNormal.apex(0,1.7,alpha);
            assertTrue(sign*SkewNormal.value(apex,12,0,1.7,alpha,sign)>=sign*SkewNormal.value(apex+.001,12,0,1.7,alpha,sign));
            if(alpha==0)assertEquals(0,apex,1e-6);else assertEquals(Math.signum(alpha),Math.signum(apex));
        }
        assertEquals(.975,SkewNormal.cdf(1.959963984540054),1e-12);
    }
    @Test void insufficientNoiseRemainsUnavailableAndNoFixedCount(){
        double[] y=new double[101],t=new double[101];boolean[] mask=new boolean[101];
        for(int i=0;i<101;i++){t[i]=i*.1;y[i]=100*Math.exp(-Math.pow((t[i]-5)/.5,2));}
        var c=new PeakDetector().detect(t,y,mask,.1,PeakParameters.defaults());assertEquals(1,c.size());assertTrue(Double.isNaN(c.get(0).lpnr()));assertFalse(c.get(0).selected(10));
        Arrays.fill(y,0);assertTrue(new PeakDetector().detect(t,y,mask,.1,PeakParameters.defaults()).isEmpty());
    }
    @org.junit.jupiter.api.Disabled("Requires legacy external BAH file and pre-HVL fixture")
    @Test void completeJavaPipelineAndReproducibleExport()throws Exception{
        var analysis=test50Analysis();var baseline=analysis.baseline();
        var result=new PeakAnalysisProcessor().process(baseline,PeakParameters.defaults());
        var reference=fixture("signal");var corrected=baseline.correctedSignal();var mask=baseline.finalBaselineMask();double max=0,rms=0;int differentMasks=0;
        for(int i=0;i<reference.length;i++){double error=corrected[i]-reference[i][2];max=Math.max(max,Math.abs(error));rms+=error*error;if(mask[i]!=(reference[i][3]!=0))differentMasks++;}
        String summary="Java complete pipeline (endpoint median vs Python reflection): candidates="+result.candidates().size()+", selected="+result.selectedCount()+", windows="+result.fits().size()+", corrected max error="+max+", RMS="+Math.sqrt(rms/reference.length)+", mask differences="+differentMasks+"\n";
        StringBuilder detail=new StringBuilder(summary);
        for(var f:result.fits())for(var c:f.components())detail.append("W"+f.window().id()+" rank="+c.candidate().prominenceRank()+" reliable="+c.fitReliable()+" area="+c.signedArea()+" r2="+f.r2()+" apex="+c.fittedApexSeconds()/60+"\n");
        Files.writeString(Path.of("target/test50-end-to-end.txt"),detail);System.out.print(summary);
        assertEquals(151,result.candidates().size());assertEquals(8,result.selectedCount());assertEquals(5,result.fits().size());
        for(var row:fixture("fits"))if(row[4]!=0){
            var fit=result.fits().get((int)row[0]-1);var component=fit.components().stream().filter(c->c.candidate().prominenceRank()==(int)row[3]).findFirst().orElseThrow();
            assertTrue(fit.reliable());assertEquals(row[5],fit.r2(),1e-4);
            assertEquals(row[7]*row[9]*Math.sqrt(2*Math.PI),component.absoluteArea(),.001*component.absoluteArea());
        }
        var file=Path.of("target/test50-export.zip");new PeakAnalysisExporter().write(file,baseline,analysis,result);
        try(var zip=new java.util.zip.ZipFile(file.toFile())){
            for(String name:List.of("metadata.txt","candidates.csv","components.csv","windows.csv","signal.csv"))assertNotNull(zip.getEntry(name));
            String metadata=new String(zip.getInputStream(zip.getEntry("metadata.txt")).readAllBytes(),java.nio.charset.StandardCharsets.UTF_8);assertTrue(metadata.contains("numStd=3.0"));assertTrue(metadata.contains(PeakAnalysisResult.ALGORITHM_VERSION));
            String windows=new String(zip.getInputStream(zip.getEntry("windows.csv")).readAllBytes(),java.nio.charset.StandardCharsets.UTF_8);assertTrue(windows.contains("Limite de avaliações"));
        }
        // A new threshold consumes the same candidates and leaves the baseline snapshot untouched.
        var t=Arrays.stream(baseline.data().timeMinutes()).map(v->v*60).toArray();
        var none=new PeakAnalysisProcessor().refit(t,corrected,baseline.data().samplingIntervalSeconds(),result.candidates(),PeakParameters.defaults().withThreshold(1e6));
        assertTrue(none.fits().isEmpty());assertEquals(result.candidates(),none.candidates());assertArrayEquals(corrected,baseline.correctedSignal());
    }
    private PeakCandidate candidate(int index,int sign,double time,double width){return new PeakCandidate(index,index,index,sign,time,sign*100,100,index-1,index+1,width,1,100,100,100);}
    @Test void windowsMergeAndClassifyWithoutChemistry(){
        int n=301;double[] t=new double[n],y=new double[n];for(int i=0;i<n;i++)t[i]=i*.1;
        var a=candidate(100,1,10,1);var b=candidate(115,-1,11.5,1);
        var builder=new PeakWindowBuilder();var windows=builder.build(t,y,.1,List.of(a,b),PeakParameters.defaults());
        assertEquals(1,windows.size());assertEquals(PeakWindow.Type.BIPOLAR_COMPOSITE,windows.get(0).type());
        windows=builder.build(t,y,.1,List.of(a,candidate(115,1,11.5,1)),PeakParameters.defaults());assertEquals(PeakWindow.Type.SAME_POLARITY_OVERLAP,windows.get(0).type());
        windows=builder.build(t,y,.1,List.of(a,candidate(250,-1,25,1)),PeakParameters.defaults());assertEquals(2,windows.size());assertEquals(PeakWindow.Type.ISOLATED,windows.get(0).type());
    }
    @Test void localProminenceIgnoresDistantBasesAndWidthUsesLocalHalfHeight(){
        double[] t=new double[301],y=new double[301];boolean[] mask=new boolean[301];
        for(int i=0;i<301;i++){t[i]=i;y[i]=100-Math.abs(i-150);mask[i]=true;}
        var candidates=new PeakDetector().detect(t,y,mask,1,new PeakParameters(1,.5,20,10,60,2,20,.55,.35,.12,.55,.22,2.5,4,3,35,.5,.5,4,1,30,.75,.5,4,5000));
        assertEquals(1,candidates.size());assertEquals(10,candidates.get(0).localProminence());assertEquals(10,candidates.get(0).w50Seconds());
    }
    @org.junit.jupiter.api.Disabled("Legacy baseline fixture includes removed local anchor recovery")
    @Test void identicalPythonPreprocessingInputIsolatesBaselineAgreement()throws Exception{
        var reference=fixture("signal");double[] minutes=new double[reference.length],cleaned=new double[reference.length];
        for(int i=0;i<reference.length;i++){minutes[i]=reference[i][0];cleaned[i]=reference[i][1];}
        var data=new ElectropherogramData(Path.of("test50-fixture"),minutes,Map.of(DetectorChannel.RIGHT,cleaned),null,null);
        var d=BaselineParameters.defaults();var p=new BaselineParameters(3,d.minLength(),d.voteThresholdK(),d.scales(),d.dilation(),d.lambda(),d.localWindow(),d.noiseFactor(),d.slopeFactor(),d.minimumRun());
        var baseline=new BaselineProcessor().process(data,DetectorChannel.RIGHT,p);var mask=baseline.finalBaselineMask();var corrected=baseline.correctedSignal();double max=0;
        for(int i=0;i<reference.length;i++){assertEquals(reference[i][3]!=0,mask[i],"mask index "+i);max=Math.max(max,Math.abs(corrected[i]-reference[i][2]));}
        // Same masks/input; lambda=1e9 is ill-conditioned. Independent SciPy sparse LU
        // differs from its banded solver by 0.0311 a.u.; EJML differs by 0.02165.
        assertTrue(max<.04,"baseline error="+max);System.out.println("Test50 identical cleaned input: masks identical, corrected max error="+max);
    }
    @Test void hvlGaussianLimitAreaMirrorAndDescriptors(){
        double a1=120,a2=2.5,area=37;
        assertEquals(area/(a2*Math.sqrt(2*Math.PI)),Hvl.value(a1,area,a1,a2,0),1e-12);
        for(double x:new double[]{-4,-1,0,1,4})assertEquals(Hvl.shape(x,-12),Hvl.shape(-x,12),1e-12);
        var d=Hvl.descriptors(a1,a2,0);
        assertEquals(a1,d.mean(),1e-4);assertEquals(a2*a2,d.variance(),1e-3);
        assertEquals(2*Math.sqrt(2*Math.log(2))*a2,d.fwhm(),1e-3);
    }

}
