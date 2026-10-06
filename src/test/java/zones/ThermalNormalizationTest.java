package zones;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;
import zones.processing.WaterViscosity;
import zones.model.MobilityCalibration;

class ThermalNormalizationTest {
    @Test void waterViscosityMatchesReferenceValuesNearRoomTemperature(){
        assertEquals(1001.6,WaterViscosity.viscosityMicroPaS(20.0),0.5);
        assertEquals(890.0,WaterViscosity.viscosityMicroPaS(25.0),0.5);
    }
    @Test void inverseViscosityNormalizationRaisesMobilityFrom20To25(){
        double mu25=WaterViscosity.normalizeTo25C(70.0,20.0);
        assertTrue(mu25>70.0);
        assertEquals(70.0*WaterViscosity.viscosityMicroPaS(20)/WaterViscosity.viscosityMicroPaS(25),mu25,1e-12);
    }
    @Test void twoStandardCalibrationAcceptsDifferentSourceTemperatures(){
        var c=MobilityCalibration.fromTwoStandardsAtTemperatures(0.20,76.0,25.0,0.35,0.0,20.0);
        assertTrue(c.isEffective());
        assertTrue(Double.isFinite(c.eofMobilityTi()));
    }
}
