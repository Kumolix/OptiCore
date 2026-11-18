package dev.corelix.loader;

import dev.corelix.api.Corelix;
import dev.corelix.api.CorelixMod;
import dev.corelix.api.Platform;

import java.io.File;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.URL;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Точка входа. В профиле версии (json) {@code mainClass} заменяется на этот класс.
 * Он находит моды, строит {@link CorelixClassLoader}, вызывает точки входа модов
 * и только потом запускает ванильный {@code net.minecraft.client.main.Main}.
 */
public final class CorelixMain {
    private static final String DEFAULT_MAIN = "net.minecraft.client.main.Main";

    private CorelixMain() {}

    public static void main(String[] args) throws Throwable {
        Path gameDir = parseGameDir(args);
        Log.info("loader " + Corelix.version() + ", platform " + Platform.current() + ", game dir " + gameDir);

        CorelixClassLoader loader = new CorelixClassLoader(classpathUrls(), CorelixMain.class.getClassLoader());
        Thread.currentThread().setContextClassLoader(loader);

        List<ModManifest> mods;
        try {
            mods = DependencySorter.sort(ModDiscovery.discover(gameDir.resolve("mods")));
            for (ModManifest mod : mods) {
                loader.addPath(mod.path());
            }
            for (ModManifest mod : mods) {
                initMod(mod, gameDir, loader);
            }
        } catch (ModLoadException e) {
            Log.error(e.getMessage(), null);
            System.exit(1);
            return;
        }
        Log.info("mods loaded: " + mods.size() + ", launching game");

        String mainName = System.getProperty("corelix.mainClass", DEFAULT_MAIN);
        Class<?> gameMain = Class.forName(mainName, true, loader);
        Method main = gameMain.getMethod("main", String[].class);
        try {
            main.invoke(null, (Object) args);
        } catch (InvocationTargetException e) {
            throw e.getCause() != null ? e.getCause() : e;
        }
    }

    private static void initMod(ModManifest mod, Path gameDir, CorelixClassLoader loader) {
        Object instance;
        try {
            Class<?> cls = Class.forName(mod.entrypoint(), true, loader);
            instance = cls.getDeclaredConstructor().newInstance();
        } catch (ReflectiveOperationException | LinkageError e) {
            throw new ModLoadException("mod '" + mod.id() + "': failed to create entrypoint "
                    + mod.entrypoint() + " (" + e + ")");
        }
        if (!(instance instanceof CorelixMod corelixMod)) {
            throw new ModLoadException("mod '" + mod.id() + "': " + mod.entrypoint()
                    + " does not implement dev.corelix.api.CorelixMod");
        }
        Log.info("mod " + mod.id() + " " + mod.version());
        corelixMod.onInit(new ModContextImpl(mod.id(), gameDir, loader));
    }

    private static Path parseGameDir(String[] args) {
        for (int i = 0; i < args.length - 1; i++) {
            if ("--gameDir".equals(args[i])) {
                return Path.of(args[i + 1]).toAbsolutePath().normalize();
            }
        }
        return Path.of("").toAbsolutePath();
    }

    private static List<URL> classpathUrls() {
        List<URL> urls = new ArrayList<>();
        for (String entry : System.getProperty("java.class.path", "").split(File.pathSeparator)) {
            if (entry.isBlank()) {
                continue;
            }
            try {
                urls.add(Path.of(entry).toAbsolutePath().toUri().toURL());
            } catch (Exception e) {
                Log.warn("skipped classpath entry: " + entry + " (" + e + ")");
            }
        }
        return urls;
    }
}
