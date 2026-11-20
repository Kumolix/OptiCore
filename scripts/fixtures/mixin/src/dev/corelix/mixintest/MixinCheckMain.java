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
        // Mixin переименовывает обработчики инъекций в handler$<id>$corelix$onTick
        boolean injected = Arrays.stream(methods).anyMatch(m -> m.getName().contains("corelix$onTick"));
        System.out.println("MIXIN_CHECK loader=" + minecraft.getClassLoader().getClass().getSimpleName()
                + " marker=" + marker + " injected=" + injected + " methods=" + methods.length);
        if (!marker || !injected) {
            System.out.println("MIXIN_REAL_FAIL");
            System.exit(1);
        }
        // клиентский мод (если подключён): F3 и главное меню. getDeclaredMethods заставляет JVM верифицировать класс.
        for (String cls : new String[] {"net.minecraft.client.gui.components.DebugScreenOverlay", "net.minecraft.client.gui.screens.TitleScreen", "net.minecraft.client.multiplayer.ClientPacketListener"}) {
            Method[] ms = Class.forName(cls, false, loader).getDeclaredMethods();
            boolean ok = Arrays.stream(ms).anyMatch(m -> m.getName().contains("corelix$"));
            System.out.println("MIXIN_CLIENT " + cls + " patched=" + ok);
            if (!ok && System.getProperty("corelix.expectClientMod") != null) {
                System.out.println("MIXIN_REAL_FAIL");
                System.exit(1);
            }
        }
        System.out.println("MIXIN_REAL_OK");
    }
}
