package dev.corelix.client.mixin;

import dev.corelix.core.RenderStats;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiGraphics;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Время отрисовки HUD. */
@Mixin(Gui.class)
public abstract class GuiMixin {
    @Inject(method = "render", at = @At("HEAD"))
    private void corelix$hudBegin(GuiGraphics graphics, DeltaTracker tracker, CallbackInfo ci) {
        RenderStats.hudBegin();
    }

    @Inject(method = "render", at = @At("RETURN"))
    private void corelix$hudEnd(GuiGraphics graphics, DeltaTracker tracker, CallbackInfo ci) {
        RenderStats.hudEnd();
    }
}
