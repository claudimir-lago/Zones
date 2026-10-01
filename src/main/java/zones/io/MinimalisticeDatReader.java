package zones.io;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import zones.model.*;

/** Explicit five-column format; other formats can implement ElectropherogramReader. */
public final class MinimalisticeDatReader implements ElectropherogramReader {
    @Override public ElectropherogramData read(Path path) throws IOException {
        List<double[]> rows = new ArrayList<>();
        try (BufferedReader reader=Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
            String line; int lineNumber=0;
            while ((line=reader.readLine())!=null) {
                lineNumber++;
                if (lineNumber==1) line=line.replace("\uFEFF", "");
                if (line.isBlank()) continue;
                String[] fields=line.trim().split("\\s+");
                if (fields.length!=5) throw new IOException("Line " + lineNumber + ": expected minimalistiCE format with 5 columns.");
                double[] row=new double[5];
                for(int j=0;j<5;j++) {
                    try { row[j]=Double.parseDouble(fields[j]); }
                    catch(NumberFormatException e) { throw new IOException("Invalid number at line " + lineNumber + ", column " + (j+1), e); }
                    if (!Double.isFinite(row[j])) throw new IOException("Non-finite value at line " + lineNumber + ", column " + (j+1));
                }
                rows.add(row);
            }
        }
        double[][] columns=new double[5][rows.size()];
        for(int i=0;i<rows.size();i++) for(int j=0;j<5;j++) columns[j][i]=rows.get(i)[j];
        try {
            return new ElectropherogramData(path,columns[0],Map.of(DetectorChannel.LEFT,columns[1],DetectorChannel.RIGHT,columns[2]),columns[3],columns[4]);
        } catch(IllegalArgumentException e) { throw new IOException(e.getMessage(),e); }
    }
}
