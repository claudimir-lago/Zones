package zones;

import zones.io.*;
import zones.model.*;
import zones.processing.*;
import java.nio.file.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

class ReferenceRegressionTest {
    @TempDir Path temporary;
    @org.junit.jupiter.api.Disabled("Legacy fixture predates frozen MMR/FABC-without-recovery pipeline")
    @Test void experimentalMatchesPythonAtEveryStage() throws Exception {
        var data=new MinimalisticeDatReader().read(Path.of("BAH112026.09.15_13h13m57s.dat"));
        assertEquals(7735,data.size());assertTrue(data.isConstant(DetectorChannel.LEFT));
        assertEquals(0.0768,data.samplingIntervalSeconds(),1e-10);
        var r=new BaselineProcessor().process(data,DetectorChannel.RIGHT,BaselineParameters.defaults());
        assertEquals(18.870158726994596,r.noiseEstimate(),1e-12);
        assertEquals(841,Statistics.count(r.highConfidenceBaselineMask()));
        assertEquals(5662,Statistics.count(r.recoveredBaselineMask()));
        assertEquals(6503,Statistics.count(r.finalBaselineMask()));
        assertTrue(r.failedScales().isEmpty());
        compareFixture("experimental",r,0.02,0.2);
    }
    @org.junit.jupiter.api.Disabled("Legacy fixture includes removed local anchor recovery")
    @Test void syntheticAndInvertedSignalsMatch() throws Exception {
        var rows=fixture("synthetic");
        double[] x=new double[rows.size()],y=new double[rows.size()];
        for(int i=0;i<x.length;i++){x[i]=rows.get(i)[0];y[i]=rows.get(i)[1];}
        var data=new ElectropherogramData(Path.of("synthetic"),x,Map.of(DetectorChannel.LEFT,y),null,null);
        var r=new BaselineProcessor().process(data,DetectorChannel.LEFT,BaselineParameters.defaults());
        compareFixture("synthetic",r,0.0001,0.0001);
        for(int i=0;i<y.length;i++)y[i]=-y[i];
        var inverted=new ElectropherogramData(Path.of("inverted"),x,Map.of(DetectorChannel.RIGHT,y),null,null);
        var negative=new BaselineProcessor().process(inverted,DetectorChannel.RIGHT,BaselineParameters.defaults());
        assertArrayEquals(r.highConfidenceBaselineMask(),negative.highConfidenceBaselineMask());
        assertArrayEquals(r.recoveredBaselineMask(),negative.recoveredBaselineMask());
        double[] a=r.baseline(),b=negative.baseline();
        for(int i=0;i<a.length;i++)assertEquals(a[i],-b[i],1e-8);
    }
    private List<double[]> fixture(String name) throws Exception {
        try(var stream=getClass().getResourceAsStream("/reference/"+name+".tsv")) {
            assertNotNull(stream);
            return new String(stream.readAllBytes(),StandardCharsets.UTF_8).lines().skip(1)
                .map(line->Arrays.stream(line.split("\t")).mapToDouble(v->v.equals("nan")?Double.NaN:Double.parseDouble(v)).toArray()).toList();
        }
    }
    private void compareFixture(String name,BaselineResult r,double baselineTolerance,double originalTolerance) throws Exception {
        var rows=fixture(name);double[] time=r.data().timeMinutes(),raw=r.rawSignal(),noise=r.localNoise(),slope=r.localSlope();
        double[] original=r.originalBaseline(),baseline=r.baseline(),corrected=r.correctedSignal();
        int[] votes=r.voteCount();boolean[] high=r.highConfidenceBaselineMask(),recovered=r.recoveredBaselineMask(),combined=r.finalBaselineMask();
        var masks=r.scaleMasks();int[] scales=r.parametersUsed().scales();
        double maxDifference=0,sumSquares=0,maxOriginal=0;
        for(int i=0;i<rows.size();i++) {
            double[] e=rows.get(i);String at=name+" index "+i;
            assertEquals(e[0],time[i],0,at);assertEquals(e[1],raw[i],0,at);
            assertEquals((int)e[2],votes[i],at+" votes");assertEquals(e[3]==1,high[i],at+" high");
            assertEquals(e[4],noise[i],1e-10,at+" noise");assertEquals(e[5],slope[i],1e-9,at+" slope");
            assertEquals(e[6]==1,recovered[i],at+" recovered");assertEquals(e[7]==1,combined[i],at+" combined");
            assertEquals(e[8],original[i],originalTolerance,at+" original baseline");
            assertEquals(e[9],baseline[i],baselineTolerance,at+" refined baseline");
            assertEquals(e[10],corrected[i],baselineTolerance,at+" corrected");
            for(int j=0;j<scales.length;j++)assertEquals(e[11+j]==1,masks.get(scales[j])[i],at+" scale="+scales[j]);
            double difference=Math.abs(e[9]-baseline[i]);maxDifference=Math.max(maxDifference,difference);sumSquares+=difference*difference;
            maxOriginal=Math.max(maxOriginal,Math.abs(e[8]-original[i]));
        }
        System.out.printf(Locale.ROOT,"%s: masks identical; baseline max error %.10g, RMS %.10g; original max error %.10g a.u.%n",name,maxDifference,Math.sqrt(sumSquares/rows.size()),maxOriginal);
    }
    @Test void malformedInputIsReportedWithLineNumber() throws Exception {
        Path file=temporary.resolve("bad.dat");Files.writeString(file,"0 1 2 3 4\n1 2 NaN 3 4\n");
        var error=assertThrows(java.io.IOException.class,()->new MinimalisticeDatReader().read(file));
        assertTrue(error.getMessage().contains("line 2"));
        Files.writeString(file,"0 1 2 3\n");assertThrows(java.io.IOException.class,()->new MinimalisticeDatReader().read(file));
        Files.writeString(file,"0 1 2 3 4\n0 1 2 3 4\n1 1 2 3 4\n");assertThrows(java.io.IOException.class,()->new MinimalisticeDatReader().read(file));
    }
    @Test void parametersAndMasksAreDefensiveAndRunsIncludeEdges() {
        var defaults=BaselineParameters.defaults();int[] scales=defaults.scales();scales[0]=1;assertEquals(24,defaults.scales()[0]);
        assertThrows(IllegalArgumentException.class,()->new BaselineParameters(2.3,10,2,new int[]{24,47},0,1e9,20,1.5,1.5,5));
        assertArrayEquals(new boolean[]{false,false,false,true,true,true},Statistics.removeShortRuns(new boolean[]{true,true,false,true,true,true},3));
        assertArrayEquals(new boolean[]{true,true,false,false,true,true,true,false,false,true,true,true,true,true,true,true,true,true},
            FabcClassifier.refineMask(new boolean[]{true,true,false,false,true,true,true,false,false,true,true,true,true,true,false,true,true,true},3));
    }
    @org.junit.jupiter.api.Disabled("Requires legacy external BAH file")
    @Test void constantChannelHasExplicitError() throws Exception {
        var data=new MinimalisticeDatReader().read(Path.of("BAH112026.09.15_13h13m57s.dat"));
        assertThrows(IllegalArgumentException.class,()->new BaselineProcessor().process(data,DetectorChannel.LEFT,BaselineParameters.defaults()));
    }
}
