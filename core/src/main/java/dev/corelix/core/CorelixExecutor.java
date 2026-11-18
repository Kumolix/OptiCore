package dev.corelix.core;

import java.util.concurrent.Callable;
import java.util.concurrent.FutureTask;
import java.util.concurrent.PriorityBlockingQueue;
import java.util.concurrent.RunnableFuture;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Единый пул фоновых задач ядра с приоритетами (меширование чанков, загрузка ресурсов, LOD).
 * Задачи одного приоритета идут в порядке подачи. Потоки демоны и с пониженным приоритетом,
 * чтобы не отбирать время у потоков игры и рендера.
 */
public final class CorelixExecutor {
    public enum Priority {
        /** Видимое прямо сейчас: ближние чанки, текстуры текущего кадра. */
        HIGH,
        NORMAL,
        /** Фон: дальние LOD, прогрев кэшей. */
        LOW
    }

    private static final class Task<T> extends FutureTask<T> implements Comparable<Task<?>> {
        private final int priority;
        private final long sequence;

        Task(Callable<T> callable, Priority priority, long sequence) {
            super(callable);
            this.priority = priority.ordinal();
            this.sequence = sequence;
        }

        @Override
        public int compareTo(Task<?> other) {
            int c = Integer.compare(priority, other.priority);
            return c != 0 ? c : Long.compare(sequence, other.sequence);
        }
    }

    private final ThreadPoolExecutor pool;
    private final AtomicLong sequence = new AtomicLong();
    private final int threads;

    public CorelixExecutor(int threads) {
        this.threads = Math.max(1, threads);
        AtomicInteger counter = new AtomicInteger();
        ThreadFactory factory = runnable -> {
            Thread t = new Thread(runnable, "corelix-worker-" + counter.incrementAndGet());
            t.setDaemon(true);
            t.setPriority(Math.max(Thread.MIN_PRIORITY, Thread.NORM_PRIORITY - 1));
            return t;
        };
        // Очередь неограниченная, поэтому ThreadPoolExecutor держит ровно core-потоков.
        this.pool = new ThreadPoolExecutor(
                this.threads, this.threads, 30, TimeUnit.SECONDS, new PriorityBlockingQueue<>(), factory);
        this.pool.prestartAllCoreThreads();
    }

    public int threads() {
        return threads;
    }

    public int queued() {
        return pool.getQueue().size();
    }

    public <T> RunnableFuture<T> submit(Priority priority, Callable<T> task) {
        Task<T> t = new Task<>(task, priority, sequence.getAndIncrement());
        pool.execute(t);
        return t;
    }

    public RunnableFuture<?> submit(Priority priority, Runnable task) {
        return submit(priority, () -> {
            task.run();
            return null;
        });
    }

    public void shutdown() {
        pool.shutdownNow();
    }
}
