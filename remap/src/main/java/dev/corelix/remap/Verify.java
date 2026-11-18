package dev.corelix.remap;

import java.io.IOException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * Проверка результата ремапа: загружает каждый класс и заставляет JVM связать его
 * (getDeclaredMethods вызывает линковку и верификацию байткода). Так ловятся битые ссылки на методы и поля.
 */
final class Verify {
    private static final String[] REQUIRED = {
        "net.minecraft.client.main.Main",
        "net.minecraft.client.Minecraft",
        "net.minecraft.world.level.block.Block",
        "net.minecraft.server.level.ServerLevel",
        "net.minecraft.client.renderer.LevelRenderer",
    };

    private Verify() {}

    static int run(Path remappedJar, Path libsDir) throws IOException {
        List<URL> urls = new ArrayList<>();
        urls.add(remappedJar.toUri().toURL());
        if (Files.isDirectory(libsDir)) {
            try (Stream<Path> s = Files.list(libsDir)) {
                for (Path p : s.filter(f -> f.toString().endsWith(".jar")).sorted().toList()) {
                    urls.add(p.toUri().toURL());
                }
            }
        }
        int checked = 0;
        int failed = 0;
        Map<String, Integer> byError = new TreeMap<>();
        List<String> samples = new ArrayList<>();
        try (URLClassLoader loader = new URLClassLoader(urls.toArray(new URL[0]), ClassLoader.getPlatformClassLoader());
                ZipFile zip = new ZipFile(remappedJar.toFile())) {
            Enumeration<? extends ZipEntry> entries = zip.entries();
            while (entries.hasMoreElements()) {
                String entry = entries.nextElement().getName();
                if (!entry.endsWith(".class") || entry.endsWith("module-info.class") || entry.endsWith("package-info.class")) {
                    continue;
                }
                String name = entry.substring(0, entry.length() - 6).replace('/', '.');
                checked++;
                try {
                    Class<?> c = Class.forName(name, false, loader);
                    c.getDeclaredMethods();
                    c.getDeclaredFields();
                    c.getDeclaredConstructors();
                } catch (Throwable t) {
                    failed++;
                    String kind = t.getClass().getSimpleName();
                    byError.merge(kind, 1, Integer::sum);
                    if (samples.size() < 25) {
                        samples.add(name + " -> " + kind + ": " + t.getMessage());
                    }
                }
            }
            for (String required : REQUIRED) {
                try {
                    Class.forName(required, false, loader);
                } catch (Throwable t) {
                    failed++;
                    samples.add("REQUIRED " + required + " -> " + t);
                }
            }
        }
        System.out.println("verify: checked " + checked + " classes, failed " + failed);
        byError.forEach((k, v) -> System.out.println("  " + k + ": " + v));
        samples.forEach(s -> System.out.println("  e.g. " + s));
        return failed == 0 ? 0 : 1;
    }
}
