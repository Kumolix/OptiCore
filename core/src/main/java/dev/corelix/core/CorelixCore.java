package dev.corelix.core;

import dev.corelix.api.CorelixMod;
import dev.corelix.api.ModContext;
import dev.corelix.api.Platform;

/**
 * Ядро оптимизаций. Здесь собирается всё общее: профиль платформы, пул потоков, мониторинг.
 * Следующие модули (параллельная загрузка ресурсов, чистка памяти, LOD) подключаются отсюда.
 */
public final class CorelixCore implements CorelixMod {
    private static volatile CorelixExecutor executor;
    private static volatile Platform profile;

    @Override
    public void onInit(ModContext ctx) {
        CoreConfig config = CoreConfig.load(ctx);
        Platform platform = config.platform(ctx.platform());
        int threads = config.threads(platform);

        profile = platform;
        executor = new CorelixExecutor(threads);
        ctx.info("profile=" + platform + ", worker threads=" + threads);

        if (config.gcMonitor()) {
            GcMonitor.start(ctx, config.gcMonitorIntervalSeconds());
        }
    }

    /** Общий пул фоновых задач. Доступен после {@link #onInit}. */
    public static CorelixExecutor executor() {
        CorelixExecutor e = executor;
        if (e == null) {
            throw new IllegalStateException("Corelix Core is not initialized yet");
        }
        return e;
    }

    /** Активный профиль оптимизаций (с учётом конфига). */
    public static Platform profile() {
        Platform p = profile;
        if (p == null) {
            throw new IllegalStateException("Corelix Core is not initialized yet");
        }
        return p;
    }
}
