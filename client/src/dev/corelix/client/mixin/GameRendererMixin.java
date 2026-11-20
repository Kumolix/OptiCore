package dev.corelix.client.mixin;

import dev.corelix.core.RenderStats;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.renderer.GameRenderer;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Время всей отрисовки кадра. */
@Mixin(GameRenderer.class)
public abstract class GameRendererMixin {
    @Inject(method = "render", at = @At("HEAD"))
    private void corelix$renderBegin(DeltaTracker tracker, boolean renderLevel, CallbackInfo ci) {
        RenderStats.renderBegin();
    }

    @Inject(method = "render", at = @At("RETURN"))
    private void corelix$renderEnd(DeltaTracker tracker, boolean renderLevel, CallbackInfo ci) {
        RenderStats.renderEnd();
    }
}
