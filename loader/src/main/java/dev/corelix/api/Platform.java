package dev.corelix.api;

import java.util.Locale;

/** Где запущена игра. От этого зависят профили оптимизаций. */
public enum Platform {
    POJAV,
    DESKTOP;

    private static volatile Platform cached;

    /**
     * Определяет платформу. Порядок: системное свойство {@code -Dcorelix.platform=pojav|desktop},
     * затем эвристика по окружению Pojav/Android, иначе DESKTOP.
     * Эвристика приблизительная: лаунчер надёжнее всего задаёт платформу явным свойством.
     */
    public static Platform current() {
        Platform p = cached;
        if (p == null) {
            p = detect();
            cached = p;
        }
        return p;
    }

    private static Platform detect() {
        String forced = System.getProperty("corelix.platform");
        if (forced != null) {
            switch (forced.trim().toLowerCase(Locale.ROOT)) {
                case "pojav": return POJAV;
                case "desktop": return DESKTOP;
                default: break;
            }
        }
        if (System.getenv("POJAV_RENDERER") != null || System.getenv("POJAV_NATIVEDIR") != null) {
            return POJAV;
        }
        String vendor = System.getProperty("java.vendor", "").toLowerCase(Locale.ROOT);
        String os = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
        if (vendor.contains("android") || os.contains("android")) {
            return POJAV;
        }
        return DESKTOP;
    }
}
