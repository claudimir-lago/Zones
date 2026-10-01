package zones;

import java.nio.file.Path;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import zones.model.*;
import zones.parameters.*;
import zones.processing.*;
import zones.application.*;
import zones.io.*;

class PhysicalPreprocessingTest {
    @Test void timeAndLogarithmicConversionsAreBounded() {
        var spikes=SpikeRemovalParameters.defaults();
        assertEquals(3,spikes.halfWindow(0.0768));assertEquals(7,spikes.windowPoints(0.0768));
        assertEquals(2,spikes.halfWindow(0.1));assertEquals(5,spikes.windowPoints(0.1));
        assertEquals(1,new SpikeRemovalParameters(true,0.001).halfWindow(0.1));
        assertEquals(5,PhysicalUnits.points(0.4,0.1,3,true));
        assertEquals(1,PhysicalUnits.points(0.001,0.1,1,false));
        assertEquals(1e5,PhysicalUnits.lambda(5));assertEquals(1e8,PhysicalUnits.lambda(8));
        assertEquals(1e9,PhysicalUnits.lambda(9));assertEquals(1e15,PhysicalUnits.lambda(15));
        assertThrows(IllegalArgumentException.class,()->PhysicalUnits.lambda(15.1));
        assertThrows(IllegalArgumentException.class,()->PhysicalUnits.points(1,0,1,false));
        assertThrows(IllegalArgumentException.class,()->new SpikeRemovalParameters(true,Double.NaN));
    }
    @Test void generatedScalesPreserveReferenceAndRespectUniqueCount() {
        var p=PhysicalBaselineParameters.defaults();
        assertArrayEquals(BaselineParameters.defaults().scales(),p.toInternal(PhysicalBaselineParameters.REFERENCE_INTERVAL_SECONDS).scales());
        assertEquals(10,p.toInternal(PhysicalBaselineParameters.REFERENCE_INTERVAL_SECONDS).minLength());assertEquals(21,p.toInternal(PhysicalBaselineParameters.REFERENCE_INTERVAL_SECONDS).localWindow());assertEquals(5,p.toInternal(PhysicalBaselineParameters.REFERENCE_INTERVAL_SECONDS).minimumRun());
        assertEquals(20,p.toInternal(0.0384).minLength());
        assertEquals(43,p.toInternal(0.0384).localWindow());
        var generator=new ScaleGenerator();
        int[] changed=generator.generate(2,30,12,0.1);
        assertEquals(12,changed.length);assertEquals(20,changed[0]);assertEquals(300,changed[changed.length-1]);
        for(int i=1;i<changed.length;i++)assertTrue(changed[i]>changed[i-1]);
        assertArrayEquals(new int[]{1},generator.generate(0.01,0.04,8,0.1));
        var invalidK=new PhysicalBaselineParameters(2.3,1,2,0.01,0.04,8,9,1,1.5,1.5,1);
        assertThrows(IllegalArgumentException.class,()->invalidK.toInternal(0.1));
        assertThrows(IllegalArgumentException.class,()->generator.generate(3,2,8,0.1));
        assertThrows(IllegalArgumentException.class,()->new PhysicalBaselineParameters(3.6,1,2,2,30,8,9,1,1.5,1.5,1));
    }
    @Test void movingMedianRemovesBothPolaritiesWithoutShiftingOrMutating() {
        double[] raw={0,0,90,0,0,0,-70,0,0};double[] saved=raw.clone();
        assertArrayEquals(new double[9],new MovingMedian().apply(raw,1));assertArrayEquals(saved,raw);
        assertArrayEquals(new double[]{10,1,1,1,1},new MovingMedian().apply(new double[]{10,1,1,1,1},1));
        assertThrows(IllegalArgumentException.class,()->new MovingMedian().apply(raw,5));
    }
    @Test void channelsAreIndependentAndStoredChargeIsRetained() {
        double[] time=new double[11],left=new double[11],right=new double[11],current=new double[11],stored=new double[11];
        Arrays.fill(right,10);Arrays.fill(current,2);Arrays.fill(stored,5);
        for(int i=0;i<time.length;i++)time[i]=i*0.1/60;
        left[3]=50;right[4]=-50;current[5]=99;
        var data=new ElectropherogramData(Path.of("channels"),time,Map.of(DetectorChannel.LEFT,left,DetectorChannel.RIGHT,right),current,stored);
        var output=new SignalPreprocessor().process(data,new SpikeRemovalParameters(true,0.1),true);
        assertSame(data,output.raw());assertArrayEquals(time,output.cleaned().timeMinutes());
        assertArrayEquals(left,output.cleaned().signal(DetectorChannel.LEFT));
        assertArrayEquals(right,output.cleaned().signal(DetectorChannel.RIGHT));
        assertArrayEquals(current,output.cleaned().currentMicroamps().orElseThrow());
        assertArrayEquals(stored,output.cleaned().chargeMilliCoulombs().orElseThrow());
        assertEquals(99,output.raw().currentMicroamps().orElseThrow()[5]);
        double[] charge=output.recalculatedChargeMilliCoulombs().orElseThrow();assertEquals(0,charge[0]);assertArrayEquals(new ChargeCalculator().fromCurrent(time,current),charge,1e-15);
        charge[0]=123;assertEquals(0,output.recalculatedChargeMilliCoulombs().orElseThrow()[0]);
        assertTrue(new SignalPreprocessor().process(data,new SpikeRemovalParameters(true,0.1),false).recalculatedChargeMilliCoulombs().isEmpty());
        var single=new ElectropherogramData(Path.of("single"),time,Map.of(DetectorChannel.RIGHT,right),null,null);
        assertTrue(new SignalPreprocessor().process(single,new SpikeRemovalParameters(true,0.1),true).recalculatedChargeMilliCoulombs().isEmpty());
    }
    @Test void chargeUsesActualNonuniformIntervalsAndPreservesSign() {
        assertArrayEquals(new double[]{0,0,-0.004},new ChargeCalculator().fromCurrent(new double[]{0,1.0/60,3.0/60},new double[]{2,-2,-2}),1e-15);
    }
    @Test void conversionsUseMedianIntervalWithNonuniformTimes() {
        var data=new ElectropherogramData(Path.of("nonuniform"),new double[]{0,0.1/60,0.3/60,0.4/60,0.5/60},Map.of(DetectorChannel.LEFT,new double[]{0,1,2,3,4}),null,null);
        assertEquals(0.1,data.samplingIntervalSeconds(),1e-15);
        assertEquals(2,SpikeRemovalParameters.defaults().halfWindow(data.samplingIntervalSeconds()));
    }
    @org.junit.jupiter.api.Disabled("Requires legacy external BAH file")
    @Test void disabledPreprocessingMatchesExistingJavaExactly() throws Exception {
        var data=new MinimalisticeDatReader().read(Path.of("BAH112026.09.15_13h13m57s.dat"));
        var old=new BaselineProcessor().process(data,DetectorChannel.RIGHT,BaselineParameters.defaults());
        var configuration=new AnalysisParameters(PhysicalBaselineParameters.defaults(),new SpikeRemovalParameters(false,0.2),false);
        var updated=new AnalysisProcessor().process(data,DetectorChannel.RIGHT,configuration);
        assertArrayEquals(old.baseline(),updated.baseline().baseline());assertArrayEquals(old.originalBaseline(),updated.baseline().originalBaseline());
        assertArrayEquals(old.correctedSignal(),updated.correctedSignal());assertArrayEquals(old.voteCount(),updated.baseline().voteCount());
        assertArrayEquals(old.highConfidenceBaselineMask(),updated.baseline().highConfidenceBaselineMask());
        assertArrayEquals(old.recoveredBaselineMask(),updated.baseline().recoveredBaselineMask());
        assertArrayEquals(old.finalBaselineMask(),updated.baseline().finalBaselineMask());
        var cleaned=new AnalysisProcessor().process(data,DetectorChannel.RIGHT,AnalysisParameters.defaults());
        assertEquals(7,cleaned.preprocessing().windowPoints());assertEquals(data.size(),cleaned.correctedSignal().length);
        assertFalse(Arrays.equals(data.signal(DetectorChannel.RIGHT),cleaned.preprocessing().cleaned().signal(DetectorChannel.RIGHT)));
        var independentlyComputed=new BaselineProcessor().process(cleaned.preprocessing().cleaned(),DetectorChannel.RIGHT,BaselineParameters.defaults());
        assertArrayEquals(independentlyComputed.baseline(),cleaned.baseline().baseline());
        assertArrayEquals(data.signal(DetectorChannel.RIGHT),cleaned.preprocessing().raw().signal(DetectorChannel.RIGHT));
    }
}
