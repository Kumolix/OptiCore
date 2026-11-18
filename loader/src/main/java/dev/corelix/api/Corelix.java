package dev.corelix.api;

/** Общие константы загрузчика. */
public final class Corelix {
    /** Версия API модов. Мод с {@code api} больше этого числа не загружается. */
    public static final int API_VERSION = 1;

    private Corelix() {}

    /** Версия загрузчика из манифеста jar, либо "dev" при запуске из IDE. */
    public static String version() {
        String v = Corelix.class.getPackage() == null ? null : Corelix.class.getPackage().getImplementationVersion();
        return v == null ? "dev" : v;
    }
}
