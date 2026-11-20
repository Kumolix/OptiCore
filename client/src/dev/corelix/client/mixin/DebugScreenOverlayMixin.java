package dev.corelix.client.mixin;

import dev.corelix.client.CorelixClient;
import net.minecraft.client.gui.components.DebugScreenOverlay;

import java.util.List;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Добавляет строку Corelix в левую колонку экрана F3. */
@Mixin(DebugScreenOverlay.class)
public abstract class DebugScreenOverlayMixin {
    @Inject(method = "getGameInformation", at = @At("RETURN"))
    private void corelix$addLabel(CallbackInfoReturnable<List<String>> cir) {
        cir.getReturnValue().add(CorelixClient.label());
    }
}
