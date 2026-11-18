package dev.corelix.loader;

/** Минимальный логгер без зависимостей: загрузчик работает до того, как загружены библиотеки игры. */
public final class Log {
    private Log() {}

    public static void info(String message) {
        System.out.println("[Corelix] " + message);
    }

    public static void warn(String message) {
        System.out.println("[Corelix][WARN] " + message);
    }

    public static void error(String message, Throwable t) {
        System.err.println("[Corelix][ERROR] " + message);
        if (t != null) {
            t.printStackTrace();
        }
    }
}
