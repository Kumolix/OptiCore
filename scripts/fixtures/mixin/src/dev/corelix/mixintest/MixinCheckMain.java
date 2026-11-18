package dev.corelix.mixintest;

import java.lang.reflect.Method;
import java.util.Arrays;

/**
 * Запускается вместо игры (-Dcorelix.mainClass). Загружает настоящий net.minecraft.client.Minecraft через загрузчик
 * и проверяет, что Mixin его изменил. getDeclaredMethods заставляет JVM связать и верифицировать изменённый класс.
 */
public final class MixinCheckMain {
    private MixinCheckMain() {}

    public static void main(String[] args) throws Exception {
        ClassLoader loader = Thread.currentThread().getContextClassLoader();
        Class<?> minecraft = Class.forName("net.minecraft.client.Minecraft", false, loader);
        Method[] methods = minecraft.getDeclaredMethods();

        boolean marker = Arrays.stream(methods).anyMatch(m -> m.getName().equals("corelix$marker"));
        boolean injected = Arrays.stream(methods).anyMatch(m -> m.getName().equals("corelix$onTick"));
        System.out.println("MIXIN_CHECK loader=" + minecraft.getClassLoader().getClass().getSimpleName()
                + " marker=" + marker + " injected=" + injected + " methods=" + methods.length);
        if (!marker || !injected) {
            System.out.println("MIXIN_REAL_FAIL");
            System.exit(1);
        }
        System.out.println("MIXIN_REAL_OK");
    }
}
