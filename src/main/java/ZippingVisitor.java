import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Date;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static java.nio.file.FileVisitResult.CONTINUE;

public class ZippingVisitor extends SimpleFileVisitor<Path> implements java.lang.AutoCloseable {
    public static final int BUFFER_SIZE = 4096;
    private final Path _source;
    private final FileOutputStream _fos;
    private final ZipOutputStream _zos;

    public static void zipWalking(Path source, Path target) {
        try (ZippingVisitor zippingVisitor = new ZippingVisitor(source, target)) {
            Files.walkFileTree(source, zippingVisitor);
        } catch (IOException ioe) {
            System.err.printf("%1$tF %1$tT %2$s", new Date(), ":: Ошибка:");
            ioe.printStackTrace();
        }
    }

    public ZippingVisitor(Path source, Path target) throws FileNotFoundException {
        this._source = source;
        _fos = new FileOutputStream(target.toFile());
        _zos = new ZipOutputStream(_fos);
    }

    @Override
    public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
        if (!Files.exists(file)) {
            throw new IOException("File " + file + " not found.");
        }
        Path zipEntryPath = _source.relativize(file);
        byte[] buffer = new byte[BUFFER_SIZE];
        try (FileInputStream fis = new FileInputStream(file.toFile())) {
            _zos.putNextEntry(new ZipEntry(zipEntryPath.toString()));
            int length;
            while ((length = fis.read(buffer)) > 0) {
                _zos.write(buffer, 0, length);
            }
            _zos.closeEntry();
        } catch (IOException ioe) {
            System.err.printf("%1$tF %1$tT %2$s", new Date(), ":: Ошибка:");
            ioe.printStackTrace();
        }
        return CONTINUE;
    }

    @Override
    public void close() throws IOException {
        _zos.close();
        _fos.close();
    }
}
