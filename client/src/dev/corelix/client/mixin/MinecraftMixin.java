package dev.corelix.client.mixin;

import dev.corelix.core.Profiler;
import net.minecraft.client.Minecraft;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Замер времени кадра для профайлера (строки PROFILE в логе). */
@Mixin(Minecraft.class)
public abstract class MinecraftMixin {
    @Inject(method = "runTick", at = @At("HEAD"))
    private void corelix$frameBegin(boolean renderLevel, CallbackInfo ci) {
        Profiler.frameBegin();
    }

    @Inject(method = "runTick", at = @At("RETURN"))
    private void corelix$frameEnd(boolean renderLevel, CallbackInfo ci) {
        Profiler.frameEnd();
    }
}
