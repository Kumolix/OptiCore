package dev.corelix.core;

import dev.corelix.api.ModContext;
import dev.corelix.api.Platform;

import java.io.IOException;
import java.io.InputStream;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Properties;

/** Конфиг {@code config/corelix.properties}. Если файла нет, создаётся с комментариями. */
public final class CoreConfig {
    private static final String TEMPLATE = """
            # Corelix Core
            # profile: auto | pojav | desktop (auto = определить по окружению)
            profile=auto
            # executor.threads: число рабочих потоков, auto = по профилю и числу ядер
            executor.threads=auto
            # gc.monitor: писать в лог паузы сборщика мусора и занятость кучи
            gc.monitor=true
            # gc.monitor.interval: период проверки, секунды
            gc.monitor.interval=10
            """;

    private final Properties props;

    private CoreConfig(Properties props) {
        this.props = props;
    }

    public static CoreConfig load(ModContext ctx) {
        Path file = ctx.configDir().resolve("corelix.properties");
        Properties props = new Properties();
        try {
            if (Files.notExists(file)) {
                Files.createDirectories(file.getParent());
                Files.writeString(file, TEMPLATE, StandardCharsets.UTF_8);
                ctx.info("created config " + file);
            }
            try (InputStream in = Files.newInputStream(file);
                    Reader reader = new java.io.InputStreamReader(in, StandardCharsets.UTF_8)) {
                props.load(reader);
            }
        } catch (IOException e) {
            ctx.warn("failed to read config, using defaults: " + e);
        }
        return new CoreConfig(props);
    }

    public Platform platform(Platform detected) {
        String v = props.getProperty("profile", "auto").trim().toLowerCase(Locale.ROOT);
        return switch (v) {
            case "pojav" -> Platform.POJAV;
            case "desktop" -> Platform.DESKTOP;
            default -> detected;
        };
    }

    public int threads(Platform platform) {
        String v = props.getProperty("executor.threads", "auto").trim();
        if (!v.equalsIgnoreCase("auto")) {
            try {
                return Math.max(1, Integer.parseInt(v));
            } catch (NumberFormatException ignored) {
                // падаем на авто-значение
            }
        }
        int cores = Runtime.getRuntime().availableProcessors();
        // На телефоне render- и game-поток уже занимают ядра, а мелкие ядра big.LITTLE слабы: берём меньше.
        return platform == Platform.POJAV
                ? Math.max(1, Math.min(3, cores / 2))
                : Math.max(1, Math.min(8, cores - 2));
    }

    public boolean gcMonitor() {
        return Boolean.parseBoolean(props.getProperty("gc.monitor", "true").trim());
    }

    public int gcMonitorIntervalSeconds() {
        try {
            return Math.max(1, Integer.parseInt(props.getProperty("gc.monitor.interval", "10").trim()));
        } catch (NumberFormatException e) {
            return 10;
        }
    }
}
