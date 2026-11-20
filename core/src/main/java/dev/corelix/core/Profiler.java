package dev.corelix.core;

import dev.corelix.api.ModContext;

import java.lang.management.GarbageCollectorMXBean;
import java.lang.management.ManagementFactory;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

import javax.management.NotificationEmitter;

/**
 * Лёгкий профайлер для поиска просадок: время кадров, время обработки чанков на потоке рендера и паузы GC.
 * Раз в период печатает одну строку в лог (её видно в логе лаунчера), чтобы не лазить в системные папки телефона.
 * Данные собираются без блокировок, накладные расходы это пара вызовов nanoTime на кадр.
 */
public final class Profiler {
    private static final long MS = 1_000_000L;

    private static volatile boolean enabled;
    private static volatile Thread renderThread;

    // кадр (только поток рендера)
    private static long frameStart;
    private static long chunkStart;
    private static long chunkInFrame;
    private static long frames, frameSum, frameMax;
    private static long over50, over100, over250;
    private static long slowByChunks, slowByGc;

    // обработка пакетов чанков/света на потоке рендера
    private static long chunkCalls, chunkSum, chunkMax;

    // GC: пишет поток уведомлений, читает рендер и отчёт
    private static final AtomicLong gcEndNanos = new AtomicLong();
    private static final AtomicLong gcCount = new AtomicLong();
    private static final AtomicLong gcSumMs = new AtomicLong();
    private static final AtomicLong gcMaxMs = new AtomicLong();
    private static volatile String gcWorst = "";

    private Profiler() {}

    static void start(ModContext ctx, int intervalSeconds) {
        enabled = true;
        registerGcListeners(ctx);
        Thread sampling = new Thread(Profiler::sampler, "corelix-sampler");
        sampling.setDaemon(true);
        sampling.setPriority(Thread.MIN_PRIORITY);
        sampling.start();
        Thread t = new Thread(() -> report(ctx, intervalSeconds * 1000L), "corelix-profiler");
        t.setDaemon(true);
        t.setPriority(Thread.MIN_PRIORITY);
        t.start();
    }

    // ---- вызывается миксинами на потоке рендера ----

    public static void frameBegin() {
        if (!enabled) {
            return;
        }
        if (renderThread == null) {
            renderThread = Thread.currentThread();
        }
        frameStart = System.nanoTime();
        chunkInFrame = 0;
    }

    public static void frameEnd() {
        if (!enabled || Thread.currentThread() != renderThread || frameStart == 0) {
            return;
        }
        long now = System.nanoTime();
        long d = now - frameStart;
        synchronized (Profiler.class) {
            frames++;
            frameSum += d;
            frameMax = Math.max(frameMax, d);
            if (d > 50 * MS) {
                over50++;
            }
            if (d > 100 * MS) {
                over100++;
                if (chunkInFrame * 2 >= d) {
                    slowByChunks++;
                }
                long gcEnd = gcEndNanos.get();
                if (gcEnd != 0 && gcEnd - frameStart > 0) {
                    slowByGc++;
                }
            }
            if (d > 250 * MS) {
                over250++;
            }
        }
        frameStart = 0;
    }

    public static void chunkBegin() {
        if (enabled && Thread.currentThread() == renderThread) {
            chunkStart = System.nanoTime();
        }
    }

    public static void chunkEnd() {
        if (!enabled || Thread.currentThread() != renderThread || chunkStart == 0) {
            return;
        }
        long d = System.nanoTime() - chunkStart;
        chunkStart = 0;
        chunkInFrame += d;
        synchronized (Profiler.class) {
            chunkCalls++;
            chunkSum += d;
            chunkMax = Math.max(chunkMax, d);
        }
    }

    // ---- GC ----

    private static void registerGcListeners(ModContext ctx) {
        try {
            for (GarbageCollectorMXBean bean : ManagementFactory.getGarbageCollectorMXBeans()) {
                if (bean instanceof NotificationEmitter emitter) {
                    emitter.addNotificationListener((notification, handback) -> onGcNotification(notification), null, null);
                }
            }
        } catch (Throwable unavailable) {
            ctx.warn("GC pause listener unavailable on this JVM: " + unavailable);
        }
    }

    private static void onGcNotification(javax.management.Notification n) {
        try {
            if (!com.sun.management.GarbageCollectionNotificationInfo.GARBAGE_COLLECTION_NOTIFICATION.equals(n.getType())) {
                return;
            }
            com.sun.management.GarbageCollectionNotificationInfo info = com.sun.management.GarbageCollectionNotificationInfo
                    .from((javax.management.openmbean.CompositeData) n.getUserData());
            long ms = info.getGcInfo().getDuration();
            gcEndNanos.set(System.nanoTime());
            gcCount.incrementAndGet();
            gcSumMs.addAndGet(ms);
            if (ms > gcMaxMs.get()) {
                gcMaxMs.set(ms);
                gcWorst = info.getGcName() + " / " + info.getGcCause();
            }
        } catch (Throwable ignored) {
            // нет com.sun.management: остаётся обычный монитор GcMonitor
        }
    }

    // ---- «кто мусорит»: выборка стеков потоков раз в 100 мс, выделенные байты относятся к категории по стеку ----

    private static final Map<String, Long> whoBytes = new HashMap<>();
    private static final Map<Long, Long> sampledAllocated = new HashMap<>();

    private static String classify(String thread, StackTraceElement[] st) {
        String prefix = thread.startsWith("Worker-Main") ? "W:" : thread.startsWith("Render thread") ? "R:" : "S:";
        for (StackTraceElement e : st) {
            String c = e.getClassName();
            if (c.startsWith("net.minecraft.client.renderer.chunk") || c.contains("SectionCompiler") || c.contains("SectionRenderDispatcher")) return prefix + "mesh";
            if (c.contains("LightEngine") || c.startsWith("net.minecraft.world.level.lighting")) return prefix + "light";
            if (c.startsWith("net.minecraft.world.level.levelgen") || c.startsWith("net.minecraft.world.level.chunk.status")
                    || c.contains("ChunkGenerator") || c.contains("WorldGenRegion")) return prefix + "worldgen";
            if (c.contains("SerializableChunkData") || c.contains("ChunkSerializer") || c.contains("IOWorker") || c.contains("RegionFile")) return prefix + "chunk-io";
            if (c.startsWith("net.minecraft.client.particle")) return prefix + "particles";
            if (c.startsWith("net.minecraft.client.renderer.entity") || c.contains("EntityRenderDispatcher")) return prefix + "entities";
            if (c.startsWith("net.minecraft.client.renderer.blockentity")) return prefix + "block-entities";
            if (c.startsWith("net.minecraft.client.gui")) return prefix + "gui";
            if (c.contains("LevelRenderer")) return prefix + "level-render";
            if (c.contains("ClientPacketListener") || c.contains("ClientChunkCache") || c.startsWith("net.minecraft.network")) return prefix + "network";
            if (c.startsWith("net.minecraft.server.level.ServerChunkCache") || c.contains("ChunkMap")) return prefix + "chunk-mgmt";
            if (c.startsWith("net.minecraft.server.level.ServerLevel") || c.startsWith("net.minecraft.world.entity")) return prefix + "entities-tick";
        }
        return prefix + "other";
    }

    private static void sampler() {
        try {
            java.lang.management.ThreadMXBean base = ManagementFactory.getThreadMXBean();
            if (!(base instanceof com.sun.management.ThreadMXBean tmx) || !tmx.isThreadAllocatedMemorySupported()) {
                return;
            }
            long[] watched = new long[0];
            long refresh = 0;
            while (!Thread.currentThread().isInterrupted()) {
                Thread.sleep(100);
                long now = System.nanoTime();
                if (now - refresh > 2_000_000_000L || watched.length == 0) {
                    refresh = now;
                    long[] all = tmx.getAllThreadIds();
                    java.lang.management.ThreadInfo[] names = tmx.getThreadInfo(all);
                    long[] tmp = new long[all.length];
                    int n = 0;
                    for (int i = 0; i < all.length; i++) {
                        if (names[i] != null) {
                            String nm = names[i].getThreadName();
                            if (nm.startsWith("Worker-Main") || nm.startsWith("Render thread") || nm.startsWith("Server thread")) {
                                tmp[n++] = all[i];
                            }
                        }
                    }
                    watched = java.util.Arrays.copyOf(tmp, n);
                }
                if (watched.length == 0) {
                    continue;
                }
                java.lang.management.ThreadInfo[] infos = tmx.getThreadInfo(watched, 40);
                long[] bytes = tmx.getThreadAllocatedBytes(watched);
                synchronized (whoBytes) {
                    for (int i = 0; i < watched.length; i++) {
                        if (infos[i] == null || bytes[i] < 0) {
                            continue;
                        }
                        long delta = bytes[i] - sampledAllocated.getOrDefault(watched[i], bytes[i]);
                        sampledAllocated.put(watched[i], bytes[i]);
                        if (delta > 0) {
                            whoBytes.merge(classify(infos[i].getThreadName(), infos[i].getStackTrace()), delta, Long::sum);
                        }
                    }
                }
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } catch (Throwable unavailable) {
            // выборка недоступна на этой JVM: остаётся итог по потокам
        }
    }

    private static String whoSummary() {
        List<Map.Entry<String, Long>> top;
        synchronized (whoBytes) {
            top = new ArrayList<>(whoBytes.entrySet());
            whoBytes.clear();
        }
        if (top.isEmpty()) {
            return "";
        }
        top.sort((a, b) -> Long.compare(b.getValue(), a.getValue()));
        StringBuilder sb = new StringBuilder(" | WHO (MB):");
        for (int i = 0; i < Math.min(7, top.size()); i++) {
            sb.append(i == 0 ? " " : ", ").append(top.get(i).getKey()).append(' ').append(top.get(i).getValue() >> 20);
        }
        return sb.toString();
    }

    // ---- выделения памяти по потокам (кто делает мусор) ----

    private static final Map<Long, Long> lastAllocated = new HashMap<>();

    /** Топ потоков по объёму выделенной памяти за период, МБ. Пусто, если JVM не умеет считать выделения по потокам. */
    private static String allocationSummary() {
        try {
            java.lang.management.ThreadMXBean base = ManagementFactory.getThreadMXBean();
            if (!(base instanceof com.sun.management.ThreadMXBean tmx) || !tmx.isThreadAllocatedMemorySupported()) {
                return "";
            }
            long[] ids = tmx.getAllThreadIds();
            long[] bytes = tmx.getThreadAllocatedBytes(ids);
            java.lang.management.ThreadInfo[] infos = tmx.getThreadInfo(ids);
            Map<String, Long> byName = new HashMap<>();
            long total = 0;
            for (int i = 0; i < ids.length; i++) {
                if (infos[i] == null || bytes[i] < 0) {
                    continue;
                }
                long delta = bytes[i] - lastAllocated.getOrDefault(ids[i], 0L);
                lastAllocated.put(ids[i], bytes[i]);
                if (delta <= 0) {
                    continue;
                }
                total += delta;
                byName.merge(infos[i].getThreadName().replaceAll("[0-9]+", "#"), delta, Long::sum);
            }
            List<Map.Entry<String, Long>> top = new ArrayList<>(byName.entrySet());
            top.sort((a, b) -> Long.compare(b.getValue(), a.getValue()));
            StringBuilder sb = new StringBuilder(" | ALLOC " + (total >> 20) + " MB:");
            for (int i = 0; i < Math.min(5, top.size()); i++) {
                sb.append(i == 0 ? " " : ", ").append(top.get(i).getKey()).append(' ').append(top.get(i).getValue() >> 20);
            }
            return sb.toString();
        } catch (Throwable unavailable) {
            return "";
        }
    }

    // ---- отчёт ----

    private static void report(ModContext ctx, long intervalMillis) {
        try {
            while (!Thread.currentThread().isInterrupted()) {
                Thread.sleep(intervalMillis);
                String line;
                synchronized (Profiler.class) {
                    if (frames == 0) {
                        continue;
                    }
                    line = String.format(
                            "frames %d, avg %.1f ms, worst %d ms | >50 ms: %d, >100 ms: %d, >250 ms: %d | slow(>100) from chunks: %d, from GC: %d"
                                    + " | chunk packets %d, sum %d ms, worst %d ms",
                            frames, frameSum / (double) frames / MS, frameMax / MS, over50, over100, over250,
                            slowByChunks, slowByGc, chunkCalls, chunkSum / MS, chunkMax / MS);
                    line += RenderStats.reportAndReset(frames);
                    frames = frameSum = frameMax = 0;
                    over50 = over100 = over250 = slowByChunks = slowByGc = 0;
                    chunkCalls = chunkSum = chunkMax = 0;
                }
                long gcs = gcCount.getAndSet(0);
                long gcSum = gcSumMs.getAndSet(0);
                long gcMax = gcMaxMs.getAndSet(0);
                Runtime rt = Runtime.getRuntime();
                line += " | GC pauses " + gcs + ", sum " + gcSum + " ms, worst " + gcMax + " ms"
                        + (gcMax > 0 ? " (" + gcWorst + ")" : "")
                        + " | heap " + ((rt.totalMemory() - rt.freeMemory()) >> 20) + "/" + (rt.maxMemory() >> 20) + " MB"
                        + allocationSummary() + whoSummary();
                ctx.info("PROFILE " + line);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
