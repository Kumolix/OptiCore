package dev.corelix.mixintest.mixin;

import net.minecraft.client.Minecraft;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Тестовый миксин на настоящий класс клиента (имена Mojang). */
@Mixin(Minecraft.class)
public abstract class MinecraftMixin {
    @Unique
    public String corelix$marker() {
        return "corelix-mixin-ok";
    }

    @Inject(method = "tick", at = @At("HEAD"))
    private void corelix$onTick(CallbackInfo ci) {
        // тело не важно: проверяем, что инъекция применилась и класс проходит верификацию
    }
}
