package dev.corelix.client.mixin;

import dev.corelix.core.Profiler;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.protocol.game.ClientboundLevelChunkWithLightPacket;
import net.minecraft.network.protocol.game.ClientboundLightUpdatePacket;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Сколько времени поток рендера тратит на пакеты чанков и света (источник фризов при подгрузке мира). */
@Mixin(ClientPacketListener.class)
public abstract class ClientPacketListenerMixin {
    @Inject(method = "handleLevelChunkWithLight", at = @At("HEAD"))
    private void corelix$chunkBegin(ClientboundLevelChunkWithLightPacket packet, CallbackInfo ci) {
        Profiler.chunkBegin();
    }

    @Inject(method = "handleLevelChunkWithLight", at = @At("RETURN"))
    private void corelix$chunkEnd(ClientboundLevelChunkWithLightPacket packet, CallbackInfo ci) {
        Profiler.chunkEnd();
    }

    @Inject(method = "handleLightUpdatePacket", at = @At("HEAD"))
    private void corelix$lightBegin(ClientboundLightUpdatePacket packet, CallbackInfo ci) {
        Profiler.chunkBegin();
    }

    @Inject(method = "handleLightUpdatePacket", at = @At("RETURN"))
    private void corelix$lightEnd(ClientboundLightUpdatePacket packet, CallbackInfo ci) {
        Profiler.chunkEnd();
    }
}
