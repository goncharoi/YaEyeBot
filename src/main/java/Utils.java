import java.io.IOException;
import java.util.ArrayList;

public class Utils {
    public static void restartPC() throws IOException {
        java.util.List<String> command = new ArrayList<>();
        command.add("shutdown");
        command.add("-t");
        command.add("0");
        command.add("-r");
        command.add("-f");
        ProcessBuilder pb = new ProcessBuilder(command);
        pb.redirectErrorStream(true);
        pb.start();
    }
}
