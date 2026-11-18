package dev.corelix.loader;

import dev.corelix.api.Corelix;
import dev.corelix.api.CorelixMod;
import dev.corelix.api.Platform;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

/**
 * Точка входа. В профиле версии (json) {@code mainClass} заменяется на этот класс.
 * Порядок: найти библиотеки Corelix (libs), при наличии маппингов перегнать клиент в имена Mojang,
 * найти моды, построить {@link CorelixClassLoader}, вызвать точки входа модов
 * и только потом запустить ванильный {@code net.minecraft.client.main.Main}.
 *
 * <p>Свойства: {@code corelix.libs} (папка jar-библиотек, по умолчанию {@code <gameDir>/corelix/libs}),
 * {@code corelix.mappings} (файл client.txt Mojang, включает ремап), {@code corelix.cache}
 * (кэш ремапа, по умолчанию {@code <gameDir>/corelix/cache}), {@code corelix.platform}, {@code corelix.mainClass}.
 */
public final class CorelixMain {
    private static final String DEFAULT_MAIN = "net.minecraft.client.main.Main";

    private CorelixMain() {}

    public static void main(String[] args) throws Throwable {
        Path gameDir = parseGameDir(args);
        Log.info("loader " + Corelix.version() + ", platform " + Platform.current() + ", game dir " + gameDir);

        CorelixClassLoader loader;
        List<ModManifest> mods;
        try {
            List<Path> classpath = classpathPaths();
            List<Path> libs = discoverLibs(gameDir);

            String mappings = System.getProperty("corelix.mappings");
            if (mappings != null && !mappings.isBlank()) {
                Path clientJar = RemapStep.findClientJar(classpath);
                Path cache = Path.of(System.getProperty("corelix.cache",
                        gameDir.resolve("corelix").resolve("cache").toString())).toAbsolutePath().normalize();
                Path remapped = RemapStep.run(toUrls(libs), clientJar, Path.of(mappings).toAbsolutePath(), cache);
                classpath.set(classpath.indexOf(clientJar), remapped);
                Log.info("client: " + remapped.getFileName());
            }

            List<Path> all = new ArrayList<>(classpath);
            all.addAll(libs);
            loader = new CorelixClassLoader(toUrls(all), CorelixMain.class.getClassLoader());
            Thread.currentThread().setContextClassLoader(loader);

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

    /** Элементы classpath JVM в исходном порядке (изменяемый список). */
    private static List<Path> classpathPaths() {
        List<Path> paths = new ArrayList<>();
        for (String entry : System.getProperty("java.class.path", "").split(File.pathSeparator)) {
            if (!entry.isBlank()) {
                paths.add(Path.of(entry).toAbsolutePath().normalize());
            }
        }
        return paths;
    }

    /** Jar-библиотеки Corelix (ремаппер, позже Mixin и ASM). Они не лежат в classpath игры. */
    private static List<Path> discoverLibs(Path gameDir) {
        Path dir = Path.of(System.getProperty("corelix.libs",
                gameDir.resolve("corelix").resolve("libs").toString())).toAbsolutePath().normalize();
        List<Path> libs = new ArrayList<>();
        if (Files.isDirectory(dir)) {
            try (Stream<Path> s = Files.list(dir)) {
                s.filter(p -> p.getFileName().toString().toLowerCase().endsWith(".jar")).sorted().forEach(libs::add);
            } catch (IOException e) {
                throw new ModLoadException("failed to read libs folder " + dir + ": " + e);
            }
        }
        Log.info("libs: " + libs.size() + " jar in " + dir);
        return libs;
    }

    private static List<URL> toUrls(List<Path> paths) {
        List<URL> urls = new ArrayList<>(paths.size());
        for (Path p : paths) {
            try {
                urls.add(p.toUri().toURL());
            } catch (MalformedURLException e) {
                Log.warn("skipped classpath entry: " + p + " (" + e + ")");
            }
        }
        return urls;
    }
}
