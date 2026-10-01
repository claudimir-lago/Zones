package zones;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;
import zones.model.*;
import zones.processing.*;
import java.nio.file.Path;
import java.util.Map;

class DomainTransformTest {
    @Test void trapezoidalChargeUsesActualIntervalsAndMilliCoulombs(){
        double[] timeMinutes={0,1.0/60.0,3.0/60.0};
        double[] currentMicroamps={1,3,5};
        double[] q=new ChargeCalculator().fromCurrent(timeMinutes,currentMicroamps);
        assertArrayEquals(new double[]{0,0.002,0.010},q,1e-12);
    }

    @Test void twoStandardCalibrationRecoversEffectiveMobility(){
        // Construct references from k=1200 Ti*mC and EOF=20 Ti.
        double q1=1200.0/(80.0+20.0);
        double q2=1200.0/(40.0+20.0);
        var c=MobilityCalibration.fromTwoStandards(q1,80,q2,40);
        assertEquals(1200,c.kTiMilliCoulombs(),1e-10);
        assertEquals(20,c.eofMobilityTi(),1e-10);
        double qUnknown=1200.0/(55.0+20.0);
        assertEquals(55,c.effectiveMobilityTi(qUnknown),1e-10);
    }

    @Test void transformedCoordinatesPreserveSignsAndMobilityLimit(){
        double[] t={0,1.0/60,2.0/60,3.0/60};
        double[] i={-1000,-1000,-1000,-1000};
        var d=new ElectropherogramData(Path.of("test.dat"),t,Map.of(DetectorChannel.LEFT,new double[]{0,1,0,0}),i,null);
        var cal=MobilityCalibration.fromTwoStandards(1.0,100,2.0,40);
        var tr=new DomainTransform(d,cal);
        assertEquals(-1.0,tr.xAtTime(ElectropherogramDomain.CHARGE,1.0/60),1e-12);
        assertTrue(tr.xAtTime(ElectropherogramDomain.MOBILITY,0.0)>DomainTransform.MOBILITY_DISPLAY_LIMIT_TI || Double.isNaN(tr.xAtTime(ElectropherogramDomain.MOBILITY,0.0)));
        assertEquals(-140.0,tr.xAtTime(ElectropherogramDomain.MOBILITY,1.0/60),1e-12);
    }

    @Test void calibrationPreservesMigrationChargeSign(){
        var a=MobilityCalibration.fromTwoStandards(-12,80,-20,40);
        assertTrue(Double.isFinite(a.kTiMilliCoulombs()));
        assertEquals(80,a.effectiveMobilityTi(-12),1e-12);
        assertEquals(40,a.effectiveMobilityTi(-20),1e-12);
    }
    @Test void instrumentalCalibrationLabelsApparentMobility(){
        var c=MobilityCalibration.fromInstrument(75.0,1.0,0.20);
        assertFalse(c.isEffective());
        assertEquals("Apparent mobility",c.mobilityName());
        assertTrue(c.mobilityTi(1.0)>0);
        assertTrue(c.mobilityTi(-1.0)<0);
    }

    @Test void instrumentalReferenceConvertsToEffectiveMobility(){
        var c=MobilityCalibration.fromInstrumentWithReference(75.0,1.0,0.20,1.0,0.0);
        assertTrue(c.isEffective());
        assertEquals(0.0,c.mobilityTi(1.0),1e-12);
    }

    @Test void storedChargeProvidesAbsoluteOffsetWhenCurrentIsReintegrated(){
        double[] t={1.0,1.0+1.0/60.0,1.0+2.0/60.0};
        double[] i={1000,1000,1000};
        double[] stored={10,11,12};
        var d=new ElectropherogramData(Path.of("test.dat"),t,Map.of(DetectorChannel.LEFT,new double[]{0,1,0}),i,stored);
        var tr=new DomainTransform(d,null);
        assertEquals(10,tr.xAtTime(ElectropherogramDomain.CHARGE,t[0]),1e-12);
        assertEquals(12,tr.xAtTime(ElectropherogramDomain.CHARGE,t[2]),1e-12);
        var inv=new DomainTransform(d,null,true);
        assertEquals(-12,inv.xAtTime(ElectropherogramDomain.CHARGE,t[2]),1e-12);
    }

}
