package dev.corelix.api;

/**
 * Точка входа мода. Класс указывается в {@code corelix.mod.properties} (ключ {@code entrypoint})
 * и должен иметь публичный конструктор без аргументов.
 * {@link #onInit} вызывается ДО загрузки классов Minecraft, поэтому здесь регистрируют трансформеры.
 */
public interface CorelixMod {
    void onInit(ModContext context);
}
