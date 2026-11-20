package dev.corelix.client.mixin;

import dev.corelix.core.RenderStats;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.RenderType;
import org.joml.Matrix4f;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Время отрисовки мира и слоёв рельефа. */
@Mixin(LevelRenderer.class)
public abstract class LevelRendererMixin {
    @Inject(method = "renderLevel", at = @At("HEAD"))
    private void corelix$levelBegin(CallbackInfo ci) {
        RenderStats.levelBegin();
    }

    @Inject(method = "renderLevel", at = @At("RETURN"))
    private void corelix$levelEnd(CallbackInfo ci) {
        RenderStats.levelEnd();
    }

    @Inject(method = "renderSectionLayer", at = @At("HEAD"))
    private void corelix$terrainBegin(RenderType type, double x, double y, double z, Matrix4f a, Matrix4f b, CallbackInfo ci) {
        RenderStats.terrainBegin();
    }

    @Inject(method = "renderSectionLayer", at = @At("RETURN"))
    private void corelix$terrainEnd(RenderType type, double x, double y, double z, Matrix4f a, Matrix4f b, CallbackInfo ci) {
        RenderStats.terrainEnd();
    }
}
