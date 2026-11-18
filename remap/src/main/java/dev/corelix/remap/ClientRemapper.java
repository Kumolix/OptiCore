package dev.corelix.remap;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.commons.ClassRemapper;

import java.io.BufferedOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Enumeration;
import java.util.zip.Deflater;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;

/**
 * Перегоняет клиентский jar Minecraft в имена Mojang. Исходный jar пользователя не меняется,
 * результат кладётся в кэш (ключ: хэши jar и маппингов + версия ремаппера), поэтому долго только в первый запуск.
 * Вызывается загрузчиком через рефлексию: {@code remap(Path, Path, Path)}.
 */
public final class ClientRemapper {
    /** Поднимать при любом изменении результата ремапа, чтобы старый кэш не использовался. */
    public static final int VERSION = 1;

    private ClientRemapper() {}

    public static Path remap(Path clientJar, Path mappings, Path cacheDir) throws IOException {
        Files.createDirectories(cacheDir);
        String key = sha1(clientJar).substring(0, 12) + "-" + sha1(mappings).substring(0, 12) + "-v" + VERSION;
        Path out = cacheDir.resolve("client-" + key + "-mojmap.jar");
        if (Files.isRegularFile(out)) {
            System.out.println("[Corelix] remap: using cached " + out.getFileName());
            return out;
        }
        Path tmp = cacheDir.resolve(out.getFileName() + ".tmp");
        remapTo(clientJar, mappings, tmp);
        try {
            Files.move(tmp, out, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } catch (AtomicMoveNotSupportedException e) {
            Files.move(tmp, out, StandardCopyOption.REPLACE_EXISTING);
        }
        return out;
    }

    public static void remapTo(Path clientJar, Path mappingsFile, Path outJar) throws IOException {
        long start = System.currentTimeMillis();
        System.out.println("[Corelix] remap: converting client to Mojang names (first launch only)...");
        MojangMappings mappings = MojangMappings.parse(mappingsFile);
        int classes = 0;
        try (ZipFile zip = new ZipFile(clientJar.toFile())) {
            Hierarchy hierarchy = Hierarchy.read(zip);
            MojangRemapper remapper = new MojangRemapper(mappings, hierarchy);
            try (ZipOutputStream out = new ZipOutputStream(new BufferedOutputStream(Files.newOutputStream(outJar)))) {
                out.setLevel(Deflater.BEST_SPEED);
                Enumeration<? extends ZipEntry> entries = zip.entries();
                while (entries.hasMoreElements()) {
                    ZipEntry e = entries.nextElement();
                    String name = e.getName();
                    if (e.isDirectory() || isSignature(name)) {
                        continue;
                    }
                    byte[] data;
                    try (InputStream in = zip.getInputStream(e)) {
                        data = in.readAllBytes();
                    }
                    if (name.endsWith(".class")) {
                        ClassReader cr = new ClassReader(data);
                        ClassWriter cw = new ClassWriter(0);
                        cr.accept(new ClassRemapper(cw, remapper), 0);
                        data = cw.toByteArray();
                        name = remapper.map(cr.getClassName()) + ".class";
                        classes++;
                    }
                    out.putNextEntry(new ZipEntry(name));
                    out.write(data);
                    out.closeEntry();
                }
            }
        }
        System.out.println("[Corelix] remap: " + classes + " classes in " + (System.currentTimeMillis() - start) + " ms");
    }

    /** Подписи Mojang относятся к исходным классам и после ремапа недействительны. */
    private static boolean isSignature(String name) {
        if (!name.startsWith("META-INF/")) {
            return false;
        }
        String n = name.toUpperCase();
        return n.endsWith(".SF") || n.endsWith(".RSA") || n.endsWith(".DSA") || n.endsWith(".EC");
    }

    private static String sha1(Path file) throws IOException {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-1");
            try (InputStream in = Files.newInputStream(file)) {
                byte[] buf = new byte[1 << 16];
                int n;
                while ((n = in.read(buf)) > 0) {
                    md.update(buf, 0, n);
                }
            }
            StringBuilder sb = new StringBuilder();
            for (byte b : md.digest()) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IOException(e);
        }
    }
}
