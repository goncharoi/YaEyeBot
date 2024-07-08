import org.apache.commons.io.FileUtils;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.Date;

public class BotCheckData {
    long checkSum;
    ArrayList<String> vipFiles = new ArrayList<>();

    public BotCheckData(File dir) {
        final long[] result = {0L};

        try {
            //читаем список важных файлов
            BufferedReader reader = new BufferedReader(new FileReader("vipFiles.ini"));
            ArrayList<String> vipFilesYaEyeBot = new ArrayList<>();
            String line;
            while ((line = reader.readLine()) != null)
                vipFilesYaEyeBot.add(line);
            reader.close();
            //считаем проверочные данные
            Files.walkFileTree(dir.toPath(), new SimpleFileVisitor<>() {
                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                    File f = file.toFile();
                    //эти файлы переменные и необязательные
                    if (
                            f.getAbsolutePath().endsWith(".csv") ||
                                    f.getAbsolutePath().endsWith(".zip") ||
                                    f.getAbsolutePath().endsWith(".log") ||
                                    f.getAbsolutePath().endsWith(".mp4") ||
                                    f.getAbsolutePath().endsWith(".jpg") ||
                                    f.getAbsolutePath().endsWith(".sys") ||
                                    f.getAbsolutePath().endsWith(".config") ||
                                    f.getAbsolutePath().endsWith(".dat")
                    )
                        return FileVisitResult.CONTINUE;

                    //эти файлы обязательные, но данные в них переменные
                    boolean found = false;
                    for (var  vipFile:vipFilesYaEyeBot) {
                        if (f.getAbsolutePath().equals(dir.getAbsolutePath() + vipFile)) {
                            found = true;
                            break;
                        }
                    }
                    if (found && !vipFiles.contains(f.getAbsolutePath())
                    ) {
                        vipFiles.add(f.getAbsolutePath());
                    } else if (
                        //эти файлы проверяем на подмену
                            f.getAbsolutePath().endsWith(".exe") ||
                                    f.getAbsolutePath().endsWith(".bat") ||
                                    f.getAbsolutePath().endsWith(".cmd") ||
                                    f.getAbsolutePath().endsWith(".ps1") ||
                                    f.getAbsolutePath().endsWith(".jar") ||
                                    f.getAbsolutePath().endsWith(".dll")
                    ) {
                        result[0] += FileUtils.checksumCRC32(f);
                        System.out.printf("%1$tF %1$tT %2$s", new Date(), ":: Файл добавлен к подсчету " +
                                f.getAbsolutePath() + " (" + FileUtils.checksumCRC32(f) + ")\n");
                    }
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult postVisitDirectory(Path dir, IOException e) throws IOException {
                    if (e == null) {
                        return FileVisitResult.CONTINUE;
                    } else {
                        throw e;
                    }
                }
            });
        } catch (IOException ex) {
            System.err.printf("%1$tF %1$tT %2$s", new Date(), ":: Ошибка:");
            ex.printStackTrace();
        }

        checkSum = result[0];
    }
}