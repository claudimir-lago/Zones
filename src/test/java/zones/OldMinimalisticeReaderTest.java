package zones;

import java.io.IOException;
import java.nio.file.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;
import zones.io.*;
import zones.model.*;
import zones.processing.SignalPreprocessor;

class OldMinimalisticeReaderTest {
    @TempDir Path temporary;

    @Test void readsChannelsMinutesAndMissingPhysicalData() throws Exception {
        Path file=temporary.resolve("old.dat");
        Files.writeString(file,"\uFEFF\n0.1, 10, -20\n0.2,11,-21\n0.3,12,-22\n");
        var data=new AutoDetectDatReader().read(file);
        assertArrayEquals(new double[]{0.1,0.2,0.3},data.timeMinutes());
        assertArrayEquals(new double[]{10,11,12},data.signal(DetectorChannel.LEFT));
        assertArrayEquals(new double[]{-20,-21,-22},data.signal(DetectorChannel.RIGHT));
        assertEquals(6,data.samplingIntervalSeconds(),1e-12);
        assertTrue(data.currentMicroamps().isEmpty());
        assertTrue(data.chargeMilliCoulombs().isEmpty());
        var pre=new SignalPreprocessor().process(data,SpikeRemovalParameters.defaults(),true);
        assertTrue(pre.recalculatedChargeMilliCoulombs().isEmpty());
        assertTrue(pre.cleaned().currentMicroamps().isEmpty());
    }

    @Test void acceptsWhitespaceAndPreservesFiveColumnFormat() throws Exception {
        Path file=temporary.resolve("input.dat");
        Files.writeString(file,"0 1 2\n1\t3\t4\n2 5 6\n");
        assertEquals(6,new AutoDetectDatReader().read(file).signal(DetectorChannel.RIGHT)[2]);
        Files.writeString(file,"0 1 2 3 4\n1 2 3 4 5\n2 3 4 5 6\n");
        var data=new AutoDetectDatReader().read(file);
        assertArrayEquals(new double[]{3,4,5},data.currentMicroamps().orElseThrow());
        assertArrayEquals(new double[]{4,5,6},data.chargeMilliCoulombs().orElseThrow());
    }

    @Test void rejectsMalformedRowsWithoutDiscardingFields() throws Exception {
        Path file=temporary.resolve("bad.dat");
        for(String bad:new String[]{"1,2,", "1,,3", "1,2,3,4", "1,NaN,3", "1,Infinity,3", "1,no,3"}) {
            Files.writeString(file,"0,1,2\n"+bad+"\n2,3,4\n");
            var error=assertThrows(IOException.class,()->new AutoDetectDatReader().read(file));
            assertTrue(error.getMessage().toLowerCase().contains("line 2"),error.getMessage());
        }
        for(String content:new String[]{"", "0,1,2\n0,3,4\n1,5,6\n", "0,1,2\n", "0 1 2 3\n"}) {
            Files.writeString(file,content);
            assertThrows(IOException.class,()->new AutoDetectDatReader().read(file));
        }
    }

    @Test void preservesExperimentalValuesExactly() throws Exception {
        Path path=temporary.resolve("experimental.dat");
        Files.writeString(path,"0.00097, 12.5, -3.25\n0.00224, 13.75, -4.5\n0.00351, 11.0, 2.125\n");
        var data=new AutoDetectDatReader().read(path);
        assertArrayEquals(new double[]{0.00097,0.00224,0.00351},data.timeMinutes());
        assertArrayEquals(new double[]{12.5,13.75,11.0},data.signal(DetectorChannel.LEFT));
        assertArrayEquals(new double[]{-3.25,-4.5,2.125},data.signal(DetectorChannel.RIGHT));
    }
}