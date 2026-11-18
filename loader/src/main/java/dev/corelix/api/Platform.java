package dev.corelix.api;

import java.util.Locale;

/** Где запущена игра. От этого зависят профили оптимизаций. */
public enum Platform {
    /** Телефон или планшет (Mobilix, Pojav и подобные). */
    MOBILE,
    DESKTOP;

    private static volatile Platform cached;

    /**
     * Определяет платформу. Порядок: системное свойство {@code -Dcorelix.platform=mobile|desktop}
     * (значение {@code pojav} тоже понимается как mobile), затем эвристика по окружению Android/Pojav,
     * иначе DESKTOP. Эвристика приблизительная: лаунчер надёжнее всего задаёт платформу явным свойством.
     */
    public static Platform current() {
        Platform p = cached;
        if (p == null) {
            p = detect();
            cached = p;
        }
        return p;
    }

    /** Разбирает значение из свойства или конфига; {@code null}, если значение не распознано. */
    public static Platform parse(String value) {
        if (value == null) {
            return null;
        }
        switch (value.trim().toLowerCase(Locale.ROOT)) {
            case "mobile":
            case "pojav":
                return MOBILE;
            case "desktop":
                return DESKTOP;
            default:
                return null;
        }
    }

    private static Platform detect() {
        Platform forced = parse(System.getProperty("corelix.platform"));
        if (forced != null) {
            return forced;
        }
        if (System.getenv("POJAV_RENDERER") != null || System.getenv("POJAV_NATIVEDIR") != null) {
            return MOBILE;
        }
        String vendor = System.getProperty("java.vendor", "").toLowerCase(Locale.ROOT);
        String os = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
        if (vendor.contains("android") || os.contains("android")) {
            return MOBILE;
        }
        return DESKTOP;
    }
}
