package dev.corelix.mixintest;

import dev.corelix.api.CorelixMod;
import dev.corelix.api.ModContext;

public final class MixinTestMod implements CorelixMod {
    @Override
    public void onInit(ModContext context) {
        context.info("mixintest loaded");
    }
}
