package zones.io;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import zones.model.ElectropherogramData;

/** Selects a strict reader from the first nonblank row; never retries malformed data. */
public final class AutoDetectDatReader implements ElectropherogramReader {
    @Override public ElectropherogramData read(Path path) throws IOException {
        ElectropherogramReader selected=null;
        try (BufferedReader reader=Files.newBufferedReader(path,StandardCharsets.UTF_8)) {
            String line; int lineNumber=0;
            while ((line=reader.readLine())!=null) {
                lineNumber++;
                if(lineNumber==1) line=line.replace("\uFEFF", "");
                if(line.isBlank()) continue;
                if(OldMinimalisticeDatReader.fields(line).length==3) selected=new OldMinimalisticeDatReader();
                else if(line.trim().split("\\s+").length==5) selected=new MinimalisticeDatReader();
                else throw new IOException("Line " + lineNumber + ": expected oldMinimalistiCE (3 columns) or minimalistiCE (5 columns).");
                break;
            }
        }
        if(selected==null) throw new IOException("File contains no electropherogram data.");
        return selected.read(path);
    }
}
