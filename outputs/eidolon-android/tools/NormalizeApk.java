import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashSet;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;

/** Normalize local and central ZIP paths while retaining an installable resource table. */
public final class NormalizeApk {
    public static void main(String[] args) throws Exception {
        if (args.length != 2) throw new IllegalArgumentException("usage: NormalizeApk input.apk output.apk");
        Path input = Paths.get(args[0]);
        Path output = Paths.get(args[1]);
        Set<String> names = new HashSet<>();
        boolean resourceTableFound = false;
        try (ZipFile source = new ZipFile(input.toFile());
             OutputStream file = Files.newOutputStream(output);
             ZipOutputStream destination = new ZipOutputStream(file)) {
            byte[] buffer = new byte[32768];
            for (ZipEntry original : java.util.Collections.list(source.entries())) {
                String name = original.getName().replace('\\', '/');
                if (!names.add(name)) throw new IllegalStateException("Duplicate APK entry: " + name);
                ZipEntry entry = new ZipEntry(name);
                if ("resources.arsc".equals(name)) {
                    if (original.getSize() < 0 || original.getCrc() < 0)
                        throw new IllegalStateException("Missing resources.arsc size or CRC");
                    entry.setMethod(ZipEntry.STORED);
                    entry.setSize(original.getSize());
                    entry.setCompressedSize(original.getSize());
                    entry.setCrc(original.getCrc());
                    resourceTableFound = true;
                } else {
                    entry.setMethod(ZipEntry.DEFLATED);
                }
                destination.putNextEntry(entry);
                try (InputStream content = source.getInputStream(original)) {
                    int count;
                    while ((count = content.read(buffer)) != -1) destination.write(buffer, 0, count);
                }
                destination.closeEntry();
            }
        }
        if (!resourceTableFound) throw new IllegalStateException("APK has no resources.arsc");
        try (ZipFile check = new ZipFile(output.toFile())) {
            if (check.getEntry("resources.arsc").getMethod() != ZipEntry.STORED)
                throw new IllegalStateException("resources.arsc must be STORED");
        }
    }
}
