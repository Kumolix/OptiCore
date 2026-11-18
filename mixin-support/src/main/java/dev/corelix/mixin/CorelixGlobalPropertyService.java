package dev.corelix.mixin;

import org.spongepowered.asm.service.IGlobalPropertyService;
import org.spongepowered.asm.service.IPropertyKey;

import java.util.HashMap;
import java.util.Map;

/** Глобальные свойства Mixin: простая карта в памяти, общая на весь процесс. */
public final class CorelixGlobalPropertyService implements IGlobalPropertyService {
    private static final class Key implements IPropertyKey {
        final String name;

        Key(String name) {
            this.name = name;
        }
    }

    private static final Map<String, Object> PROPERTIES = new HashMap<>();

    @Override
    public IPropertyKey resolveKey(String name) {
        return new Key(name);
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T> T getProperty(IPropertyKey key) {
        synchronized (PROPERTIES) {
            return (T) PROPERTIES.get(((Key) key).name);
        }
    }

    @Override
    public void setProperty(IPropertyKey key, Object value) {
        synchronized (PROPERTIES) {
            PROPERTIES.put(((Key) key).name, value);
        }
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T> T getProperty(IPropertyKey key, T defaultValue) {
        synchronized (PROPERTIES) {
            Object v = PROPERTIES.get(((Key) key).name);
            return v != null ? (T) v : defaultValue;
        }
    }

    @Override
    public String getPropertyString(IPropertyKey key, String defaultValue) {
        synchronized (PROPERTIES) {
            Object v = PROPERTIES.get(((Key) key).name);
            return v != null ? v.toString() : defaultValue;
        }
    }
}
