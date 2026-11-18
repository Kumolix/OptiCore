package dev.corelix.mixin;

import org.spongepowered.asm.service.IMixinServiceBootstrap;

/** Подсказывает Mixin, какой сервис использовать. Реальная инициализация делается в {@link CorelixMixin}. */
public final class CorelixMixinServiceBootstrap implements IMixinServiceBootstrap {
    @Override
    public String getName() {
        return "Corelix";
    }

    @Override
    public String getServiceClassName() {
        return CorelixMixinService.class.getName();
    }

    @Override
    public void bootstrap() {
        // всё уже подготовлено загрузчиком
    }
}
