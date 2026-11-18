package dev.corelix.remap;

import org.objectweb.asm.ClassReader;

import java.io.IOException;
import java.io.InputStream;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * Иерархия классов клиента (обфусцированные имена). Нужна, чтобы вызов унаследованного метода
 * через подкласс находил имя Mojang, объявленное в родителе или интерфейсе.
 */
final class Hierarchy {
    record Info(String superName, String[] interfaces) {}

    private final Map<String, Info> classes = new HashMap<>();

    static Hierarchy read(ZipFile zip) throws IOException {
        Hierarchy h = new Hierarchy();
        Enumeration<? extends ZipEntry> entries = zip.entries();
        while (entries.hasMoreElements()) {
            ZipEntry e = entries.nextElement();
            if (e.isDirectory() || !e.getName().endsWith(".class")) {
                continue;
            }
            try (InputStream in = zip.getInputStream(e)) {
                ClassReader cr = new ClassReader(in);
                h.classes.put(cr.getClassName(), new Info(cr.getSuperName(), cr.getInterfaces()));
            }
        }
        return h;
    }

    Info get(String internalName) {
        return classes.get(internalName);
    }
}
