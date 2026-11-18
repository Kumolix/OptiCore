package dev.corelix.loader;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.stream.Stream;

/**
 * Ищет моды в папке mods. Это и есть «белый список» первого уровня:
 * jar без {@code corelix.mod.properties} (обычные Fabric/Forge-моды) не загружается.
 */
public final class ModDiscovery {
    private ModDiscovery() {}

    public static List<ModManifest> discover(Path modsDir) {
        List<ModManifest> found = new ArrayList<>();
        if (!Files.isDirectory(modsDir)) {
            Log.warn("mods folder not found: " + modsDir + " (Corelix Core is a mod too; without it there are no optimizations)");
            return found;
        }
        List<Path> jars;
        try (Stream<Path> s = Files.list(modsDir)) {
            jars = s.filter(f -> f.getFileName().toString().toLowerCase().endsWith(".jar"))
                    .sorted()
                    .toList();
        } catch (IOException e) {
            throw new ModLoadException("failed to read folder " + modsDir + ": " + e);
        }

        List<String> problems = new ArrayList<>();
        for (Path jar : jars) {
            String fileName = jar.getFileName().toString();
            try (JarFile jf = new JarFile(jar.toFile())) {
                JarEntry entry = jf.getJarEntry(ModManifest.FILE_NAME);
                if (entry == null) {
                    Log.warn("skipped (not a Corelix mod): " + fileName);
                    continue;
                }
                Properties props = new Properties();
                try (InputStream in = jf.getInputStream(entry)) {
                    props.load(in);
                }
                found.add(ModManifest.parse(props, jar));
            } catch (IllegalArgumentException e) {
                problems.add(fileName + ": " + e.getMessage());
            } catch (IOException e) {
                problems.add(fileName + ": failed to read jar (" + e + ")");
            }
        }
        if (!problems.isEmpty()) {
            throw new ModLoadException(problems);
        }
        return found;
    }
}
