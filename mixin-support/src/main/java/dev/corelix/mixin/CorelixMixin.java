package dev.corelix.mixin;

import dev.corelix.loader.CorelixClassLoader;

import org.spongepowered.asm.launch.MixinBootstrap;
import org.spongepowered.asm.mixin.MixinEnvironment;
import org.spongepowered.asm.mixin.Mixins;

import java.lang.reflect.Method;
import java.util.List;

/** Запуск Mixin. Вызывается загрузчиком через рефлексию: {@code init(CorelixClassLoader, List)}. */
public final class CorelixMixin {
    private CorelixMixin() {}

    public static void init(CorelixClassLoader loader, List<String> configs) throws ReflectiveOperationException {
        CorelixMixinService.loader = loader;
        System.setProperty("mixin.bootstrapService", CorelixMixinServiceBootstrap.class.getName());
        System.setProperty("mixin.service", CorelixMixinService.class.getName());

        MixinBootstrap.init();
        MixinEnvironment.getDefaultEnvironment().setSide(MixinEnvironment.Side.CLIENT);

        for (String config : configs) {
            try {
                Mixins.addConfiguration(config);
            } catch (Throwable t) {
                throw new IllegalStateException("error parsing or using Mixin config " + config, t);
            }
        }

        if (CorelixMixinService.transformer == null) {
            throw new IllegalStateException("Mixin did not hand over its transformer (service not selected?)");
        }
        // Без перехода в фазу DEFAULT конфиги Mixin не применяются (так же делает Fabric Loader).
        gotoPhase(MixinEnvironment.Phase.INIT);
        gotoPhase(MixinEnvironment.Phase.DEFAULT);

        loader.setMixinTransformer((name, bytes) -> CorelixMixinService.transformer.transformClassBytes(name, name, bytes));
    }

    private static void gotoPhase(MixinEnvironment.Phase phase) throws ReflectiveOperationException {
        Method m = MixinEnvironment.class.getDeclaredMethod("gotoPhase", MixinEnvironment.Phase.class);
        m.setAccessible(true);
        m.invoke(null, phase);
    }
}
