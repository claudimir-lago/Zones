package zones.io;
import java.io.IOException;
import java.nio.file.Path;
import zones.model.ElectropherogramData;
public interface ElectropherogramReader {
    ElectropherogramData read(Path path) throws IOException;
}
