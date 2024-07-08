import org.apache.commons.io.FileUtils;

import java.io.*;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Date;
import java.util.Objects;
import java.util.TreeSet;

public class AutoSaver extends Thread {
    private final String dstPath; //папка сохранений
    private final TreeSet<DirInfo> dirs = new TreeSet<>();
    private final String outFNameStorage;
    private final byte savesLifeDays; //сколько хранить сейвы прошлых сессий (дней)
    private final int offset; //сколько ждать перед началом записи сейвов (мин.)

    public AutoSaver(String dstPath, TreeSet<GameInfo> games, Byte savesLifeDays, Integer offset) {
        this.dstPath = dstPath;
        if (games != null)
            for (GameInfo game : games) {
                String sPath = (!Objects.equals(game.sPath, "STEAM")) ? game.sPath : game.sPathSteam;
                if (sPath != null && !sPath.isEmpty()) dirs.add(new DirInfo(this.dstPath, game.gName, sPath));
            }
        this.savesLifeDays = savesLifeDays;
        this.offset = offset;

        this.outFNameStorage = this.dstPath + "\\previousRecordTime.txt";
    }

    public void zipPreviousSaves() {
        //определяем папку сейвов последней сессии
        String fName = "";
        try {
            if (!new File(outFNameStorage).createNewFile()){
                FileReader fr = new FileReader(outFNameStorage);
                BufferedReader buffReader = new BufferedReader(fr);

                if (buffReader.ready()) fName = buffReader.readLine();

                fr.close();
                buffReader.close();
            }
        } catch (Exception ex) {
            System.err.printf("%1$tF %1$tT %2$s", new Date(), ":: Ошибка:");
            ex.printStackTrace();
        }

        //удаляем просроченные архивы сейвов
        try {
            System.out.printf("%1$tF %1$tT %2$s", new Date(), ":: Удаляем просроченные сейвы\n");
            Runtime.getRuntime().exec("Forfiles -p " + dstPath + " -s -m *.zip -d -" + savesLifeDays + " -c \"cmd /c del /q @path\"");
        } catch (IOException ex) {
            System.err.printf("%1$tF %1$tT %2$s", new Date(), ":: Ошибка:");
            ex.printStackTrace();
        }

        //архивируем сейвы прошлой сессии
        if (!Objects.equals(fName, "")) {
            File lastSessionSavesDir = new File(dstPath + "\\" + fName);
            if (lastSessionSavesDir.exists())
                try {
                    System.out.printf("%1$tF %1$tT %2$s", new Date(), ":: Архивируем сейвы прошлой сессии\n");
                    ZippingVisitor.zipWalking(
                            lastSessionSavesDir.toPath(),
                            new File(dstPath + "\\" + fName + ".zip").toPath()
                    );
                    //очищаем дату и время начала записи для пользователя
                    FileWriter fw = new FileWriter(outFNameStorage);
                    fw.write("");
                    fw.close();
                    //удаляем папку
                    FileUtils.deleteDirectory(lastSessionSavesDir);
                } catch (IOException ex) {
                    System.err.printf("%1$tF %1$tT %2$s", new Date(), ":: Ошибка:");
                    ex.printStackTrace();
                }
        }
    }

    @Override
    public void run() {
        //пишем дату и время начала записи для пользователя
        try {
            FileWriter fw = new FileWriter(outFNameStorage);
            SimpleDateFormat formater = new SimpleDateFormat("dd.MM.yyyy_HH.mm");

            fw.write(formater.format(new Date()));
            fw.close();

            for (DirInfo di : dirs) di.dstNameFill(formater.format(new Date()));
        } catch (Exception ex) {
            System.err.printf("%1$tF %1$tT %2$s", new Date(), ":: Ошибка:");
            ex.printStackTrace();
        }

        //выжидаем заданное время, прежде чем начать отслеживание изменений в папках с сейвами
        System.out.printf("%1$tF %1$tT %2$s", new Date(), ":: Выжидаем, перед началом записи сейвов\n");
        long i = 1;
        do {
            try {
                sleep(1000);
            } catch (InterruptedException ex) {
                return; //Завершение потока после прерывания
            }
            i++;
        } while (i <= (long) offset * 60);

        //инициализация размеров для будущего сравнения
        for (DirInfo di : dirs) di.initSize();
        //раз в секунду проверяем изменение сейвов по всем папкам из библиотеки и перезаписываем изменившиеся
        System.out.printf("%1$tF %1$tT %2$s", new Date(), ":: Запущен мониторинг папок из библиотеки\n");
        do {
            try {
                for (DirInfo di : dirs)
                    if (di.checkSizeChanged())
                        di.dstRewrite();
                sleep(10000);
            } catch (InterruptedException ex) {
                return; //Завершение потока после прерывания
            } catch (Exception ex) {
                System.err.printf("%1$tF %1$tT %2$s", new Date(), ":: Ошибка:");
                ex.printStackTrace();
            }
        } while (true);
    }

    static private class DirInfo implements Comparable<DirInfo> {
        public DirInfo(String dstPath, String gName, String srcPath) {
            this.dstPath = dstPath;
            this.gName = gName;
            this.srcPath = srcPath;

            this.gName = this.gName
                    .replaceAll("[-: ]", "_")
                    .replaceAll("__", "_")
                    .replaceAll("[’'\"]", "")
                    .replaceAll("[^a-zA-ZА-Яа-я0-9_ ]", "");
            this.srcPath = this.srcPath.replace("USER$", System.getProperty("user.home") + "\\")
                    .replaceAll("/", "\\\\")
                    .replaceAll("\\\\\\\\", "\\\\");
        }

        private String srcPath;
        private String gName;
        private String dstPath;
        private Long[] srcSize = {0L, 0L, 0L};

        @Override
        public int compareTo(DirInfo o) {
            return o.srcPath.compareTo(srcPath);
        }

        public void initSize() {
            File dir = new File(this.srcPath);
            if (dir.exists())
                this.srcSize = calcDirChecksumAndCount(dir);
            else
                System.out.printf("%1$tF %1$tT %2$s %3$s %4$s", new Date(), ":: Папка ", this.srcPath, " отсутствует\n");
        }

        public boolean checkSizeChanged() {
            File dir = new File(srcPath);

            if (!dir.exists()) return false;

            Long[] newSize = calcDirChecksumAndCount(dir);
            System.out.printf("%1$tF %1$tT %2$s %3$s %4$s %5$s", new Date(), ":: Сравниваем контр.сумму ", srcPath, ", было: " + srcSize[0], ", стало: " + newSize[0] + "\n");
            System.out.printf("%1$tF %1$tT %2$s %3$s %4$s %5$s", new Date(), ":: Сравниваем число элементов ", srcPath, ", было: " + srcSize[1], ", стало: " + newSize[1] + "\n");
            System.out.printf("%1$tF %1$tT %2$s %3$s %4$s %5$s", new Date(), ":: Сравниваем размер ", srcPath, ", было: " + srcSize[2], ", стало: " + newSize[2] + "\n\n");
            boolean result = (!Arrays.equals(srcSize, newSize));
            srcSize = newSize;
            return result;
        }

        public void dstRewrite() throws InterruptedException {
            System.out.printf("%1$tF %1$tT %2$s %3$s %4$s", new Date(), ":: Копируем из ", srcPath, " в " + dstPath + "\n");

            boolean copy_failed = false;
            do {
                try {
                    FileUtils.copyDirectory(new File(srcPath), new File(dstPath));
                } catch (IOException ex) {
                    copy_failed = true;
                    System.err.printf("%1$tF %1$tT %2$s", new Date(), ":: Ошибка:");
                    ex.printStackTrace();
                    sleep(500);
                }
            } while (copy_failed);
        }

        public void dstNameFill(String outFName) {
            dstPath += "\\" + outFName + "\\" + gName;
        }

        public Long[] calcDirChecksumAndCount(File dir) {
            final Long[] result = {0L, 0L, 0L};

            try {
                Files.walkFileTree(dir.toPath(), new SimpleFileVisitor<>() {
                    @Override
                    public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                        result[0] += FileUtils.checksumCRC32(file.toFile());
                        result[1]++;
                        return FileVisitResult.CONTINUE;
                    }

                    @Override
                    public FileVisitResult postVisitDirectory(Path dir, IOException e) throws IOException {
                        if (e == null) {
                            result[1]++;
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
            result[2] = FileUtils.sizeOfDirectory(dir);
            return result;
        }
    }
}
