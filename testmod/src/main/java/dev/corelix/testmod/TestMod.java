package dev.corelix.testmod;

import dev.corelix.api.CorelixMod;
import dev.corelix.api.ModContext;

import java.nio.charset.StandardCharsets;

/**
 * Проверка конвейера: мод регистрирует трансформер, который в классе «игры» заменяет строку.
 * Замена той же длины, поэтому таблица констант класса остаётся корректной и ASM не нужен.
 */
public final class TestMod implements CorelixMod {
    @Override
    public void onInit(ModContext ctx) {
        ctx.info("testmod started, platform " + ctx.platform());
        ctx.registerTransformer((name, bytes) -> {
            if (!name.equals("net.minecraft.client.main.Main")) {
                return null;
            }
            String text = new String(bytes, StandardCharsets.ISO_8859_1).replace("HELLO_CORELIX", "WORLD_CORELIX");
            return text.getBytes(StandardCharsets.ISO_8859_1);
        });
    }
}
