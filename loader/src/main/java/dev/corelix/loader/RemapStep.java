package dev.corelix.loader;

import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Path;
import java.util.List;
import java.util.zip.ZipFile;

/**
 * Шаг ремапа: клиентский jar (обфусцированный) превращается в jar с именами Mojang.
 * Сам ремаппер лежит в папке libs (jar corelix-remap) и грузится отдельным временным загрузчиком,
 * чтобы загрузчик Corelix оставался без зависимостей.
 */
final class RemapStep {
    private static final String CLIENT_MAIN_ENTRY = "net/minecraft/client/main/Main.class";
    private static final String REMAPPER_CLASS = "dev.corelix.remap.ClientRemapper";

    private RemapStep() {}

    /** Клиентский jar это тот элемент classpath, где лежит net.minecraft.client.main.Main (его имя не обфусцируется). */
    static Path findClientJar(List<Path> classpath) {
        for (Path p : classpath) {
            if (!p.getFileName().toString().toLowerCase().endsWith(".jar")) {
                continue;
            }
            try (ZipFile zip = new ZipFile(p.toFile())) {
                if (zip.getEntry(CLIENT_MAIN_ENTRY) != null) {
                    return p;
                }
            } catch (IOException ignored) {
                // не читается как zip: это не клиент
            }
        }
        throw new ModLoadException("ремап запрошен (corelix.mappings), но клиентский jar не найден в classpath");
    }

    static Path run(List<URL> libUrls, Path clientJar, Path mappings, Path cacheDir) {
        try (URLClassLoader tool = new URLClassLoader(
                "corelix-remap", libUrls.toArray(new URL[0]), RemapStep.class.getClassLoader())) {
            Class<?> remapper;
            try {
                remapper = Class.forName(REMAPPER_CLASS, true, tool);
            } catch (ClassNotFoundException e) {
                throw new ModLoadException("не найден ремаппер (corelix-remap.jar) в папке libs");
            }
            Method remap = remapper.getMethod("remap", Path.class, Path.class, Path.class);
            return (Path) remap.invoke(null, clientJar, mappings, cacheDir);
        } catch (InvocationTargetException e) {
            Throwable cause = e.getCause() != null ? e.getCause() : e;
            throw new ModLoadException("ремап клиента не удался: " + cause);
        } catch (ReflectiveOperationException | IOException e) {
            throw new ModLoadException("ремап клиента не удался: " + e);
        }
    }
}
