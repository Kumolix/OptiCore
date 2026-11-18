package dev.corelix.core;

import dev.corelix.api.ModContext;

import java.lang.management.GarbageCollectorMXBean;
import java.lang.management.ManagementFactory;

/**
 * Пишет в лог, сколько времени игра простояла в сборке мусора. Это первая «линейка» для оптимизаций памяти.
 * В Pojav модуль java.management может отсутствовать, тогда монитор молча отключается.
 */
final class GcMonitor {
    private GcMonitor() {}

    static void start(ModContext ctx, int intervalSeconds) {
        Thread thread = new Thread(() -> run(ctx, intervalSeconds * 1000L), "corelix-gc-monitor");
        thread.setDaemon(true);
        thread.setPriority(Thread.MIN_PRIORITY);
        thread.start();
    }

    private static void run(ModContext ctx, long intervalMillis) {
        try {
            long lastCount = 0;
            long lastTime = 0;
            while (!Thread.currentThread().isInterrupted()) {
                Thread.sleep(intervalMillis);
                long count = 0;
                long time = 0;
                for (GarbageCollectorMXBean bean : ManagementFactory.getGarbageCollectorMXBeans()) {
                    count += Math.max(0, bean.getCollectionCount());
                    time += Math.max(0, bean.getCollectionTime());
                }
                if (count != lastCount) {
                    Runtime rt = Runtime.getRuntime();
                    long usedMb = (rt.totalMemory() - rt.freeMemory()) >> 20;
                    long maxMb = rt.maxMemory() >> 20;
                    ctx.info("GC: " + (count - lastCount) + " collections, " + (time - lastTime) + " ms in "
                            + (intervalMillis / 1000) + " s | heap " + usedMb + "/" + maxMb + " MB");
                    lastCount = count;
                    lastTime = time;
                }
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } catch (Throwable unavailable) {
            ctx.warn("GC monitor unavailable on this JVM: " + unavailable);
        }
    }
}
