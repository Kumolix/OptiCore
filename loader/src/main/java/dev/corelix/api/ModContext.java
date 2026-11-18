package dev.corelix.api;

import java.nio.file.Path;

/** Всё, что загрузчик даёт моду. Намеренно узкий API. */
public interface ModContext {
    String modId();

    Path gameDir();

    /** Папка {@code <gameDir>/config}. */
    Path configDir();

    Platform platform();

    /** Регистрирует трансформер байткода. Вызывать из {@link CorelixMod#onInit}. */
    void registerTransformer(ClassTransformer transformer);

    void info(String message);

    void warn(String message);
}
