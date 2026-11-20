package dev.corelix.client;

import dev.corelix.api.Corelix;
import dev.corelix.api.CorelixMod;
import dev.corelix.api.ModContext;

/** Клиентская часть Corelix: правки интерфейса и (позже) оптимизации, привязанные к классам Minecraft. Код пишется в именах Mojang. */
public final class CorelixClient implements CorelixMod {
    @Override
    public void onInit(ModContext context) {
        context.info("client part ready");
    }

    /** Строка для F3 и главного меню. */
    public static String label() {
        return "Corelix " + Corelix.version();
    }
}
