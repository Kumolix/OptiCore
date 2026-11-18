package dev.corelix.api;

/**
 * Преобразует байткод класса перед его определением в JVM.
 * Именно сюда позже подключится Mixin или ASM-патчи ядра.
 * Трансформеры вызываются в порядке регистрации, то есть в порядке загрузки модов.
 */
@FunctionalInterface
public interface ClassTransformer {
    /**
     * @param className имя класса через точки, например {@code net.minecraft.client.Minecraft}
     * @param classBytes исходный байткод
     * @return новый байткод, либо {@code null}, если класс не менялся
     */
    byte[] transform(String className, byte[] classBytes);
}
