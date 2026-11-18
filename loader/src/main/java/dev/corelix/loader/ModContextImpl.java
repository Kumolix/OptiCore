package dev.corelix.loader;

import dev.corelix.api.ClassTransformer;
import dev.corelix.api.ModContext;
import dev.corelix.api.Platform;

import java.nio.file.Path;

final class ModContextImpl implements ModContext {
    private final String modId;
    private final Path gameDir;
    private final CorelixClassLoader loader;

    ModContextImpl(String modId, Path gameDir, CorelixClassLoader loader) {
        this.modId = modId;
        this.gameDir = gameDir;
        this.loader = loader;
    }

    @Override
    public String modId() {
        return modId;
    }

    @Override
    public Path gameDir() {
        return gameDir;
    }

    @Override
    public Path configDir() {
        return gameDir.resolve("config");
    }

    @Override
    public Platform platform() {
        return Platform.current();
    }

    @Override
    public void registerTransformer(ClassTransformer transformer) {
        loader.addTransformer(transformer);
    }

    @Override
    public void info(String message) {
        Log.info("[" + modId + "] " + message);
    }

    @Override
    public void warn(String message) {
        Log.warn("[" + modId + "] " + message);
    }
}
