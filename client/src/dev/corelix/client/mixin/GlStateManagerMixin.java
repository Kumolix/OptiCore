package dev.corelix.client.mixin;

import dev.corelix.core.RenderStats;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Считает вызовы GL на кадр (строка RENDER в PROFILE). */
@Mixin(com.mojang.blaze3d.platform.GlStateManager.class)
public abstract class GlStateManagerMixin {
    @Inject(method = "_drawElements", at = @At("HEAD"))
    private static void corelix$gl0(int mode, int count, int type, long offset, CallbackInfo ci) {
        RenderStats.draw();
    }

    @Inject(method = "_glUseProgram", at = @At("HEAD"))
    private static void corelix$gl1(int program, CallbackInfo ci) {
        RenderStats.program();
    }

    @Inject(method = "_bindTexture", at = @At("HEAD"))
    private static void corelix$gl2(int texture, CallbackInfo ci) {
        RenderStats.texture();
    }

    @Inject(method = "_glBindVertexArray", at = @At("HEAD"))
    private static void corelix$gl3(int vao, CallbackInfo ci) {
        RenderStats.vao();
    }

    @Inject(method = "_glBindBuffer", at = @At("HEAD"))
    private static void corelix$gl4(int target, int buffer, CallbackInfo ci) {
        RenderStats.buffer();
    }

    @Inject(method = "_glBufferData(ILjava/nio/ByteBuffer;I)V", at = @At("HEAD"))
    private static void corelix$gl5(int target, java.nio.ByteBuffer data, int usage, CallbackInfo ci) {
        RenderStats.upload(data.remaining());
    }

    @Inject(method = "_glBufferData(IJI)V", at = @At("HEAD"))
    private static void corelix$gl6(int target, long size, int usage, CallbackInfo ci) {
        RenderStats.upload(size);
    }

    @Inject(method = "_glBufferSubData", at = @At("HEAD"))
    private static void corelix$gl7(int target, int offset, java.nio.ByteBuffer data, CallbackInfo ci) {
        RenderStats.upload(data.remaining());
    }

    @Inject(method = "_glUniform1i", at = @At("HEAD"))
    private static void corelix$gl8(int loc, int v, CallbackInfo ci) {
        RenderStats.uniform();
    }

    @Inject(method = "_glUniform1(ILjava/nio/FloatBuffer;)V", at = @At("HEAD"))
    private static void corelix$gl9(int loc, java.nio.FloatBuffer v, CallbackInfo ci) {
        RenderStats.uniform();
    }

    @Inject(method = "_glUniform1(ILjava/nio/IntBuffer;)V", at = @At("HEAD"))
    private static void corelix$gl10(int loc, java.nio.IntBuffer v, CallbackInfo ci) {
        RenderStats.uniform();
    }

    @Inject(method = "_glUniform2(ILjava/nio/FloatBuffer;)V", at = @At("HEAD"))
    private static void corelix$gl11(int loc, java.nio.FloatBuffer v, CallbackInfo ci) {
        RenderStats.uniform();
    }

    @Inject(method = "_glUniform2(ILjava/nio/IntBuffer;)V", at = @At("HEAD"))
    private static void corelix$gl12(int loc, java.nio.IntBuffer v, CallbackInfo ci) {
        RenderStats.uniform();
    }

    @Inject(method = "_glUniform3(ILjava/nio/FloatBuffer;)V", at = @At("HEAD"))
    private static void corelix$gl13(int loc, java.nio.FloatBuffer v, CallbackInfo ci) {
        RenderStats.uniform();
    }

    @Inject(method = "_glUniform3(ILjava/nio/IntBuffer;)V", at = @At("HEAD"))
    private static void corelix$gl14(int loc, java.nio.IntBuffer v, CallbackInfo ci) {
        RenderStats.uniform();
    }

    @Inject(method = "_glUniform4(ILjava/nio/FloatBuffer;)V", at = @At("HEAD"))
    private static void corelix$gl15(int loc, java.nio.FloatBuffer v, CallbackInfo ci) {
        RenderStats.uniform();
    }

    @Inject(method = "_glUniform4(ILjava/nio/IntBuffer;)V", at = @At("HEAD"))
    private static void corelix$gl16(int loc, java.nio.IntBuffer v, CallbackInfo ci) {
        RenderStats.uniform();
    }

    @Inject(method = "_glUniformMatrix2", at = @At("HEAD"))
    private static void corelix$gl17(int loc, boolean t, java.nio.FloatBuffer m, CallbackInfo ci) {
        RenderStats.uniform();
    }

    @Inject(method = "_glUniformMatrix3", at = @At("HEAD"))
    private static void corelix$gl18(int loc, boolean t, java.nio.FloatBuffer m, CallbackInfo ci) {
        RenderStats.uniform();
    }

    @Inject(method = "_glUniformMatrix4", at = @At("HEAD"))
    private static void corelix$gl19(int loc, boolean t, java.nio.FloatBuffer m, CallbackInfo ci) {
        RenderStats.uniform();
    }
}
