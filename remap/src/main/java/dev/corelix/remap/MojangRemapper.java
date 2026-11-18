package dev.corelix.remap;

import org.objectweb.asm.commons.Remapper;

import java.util.HashMap;
import java.util.Map;

/** Переводит обфусцированные имена клиента в имена Mojang. Не потокобезопасен (кэши без синхронизации). */
final class MojangRemapper extends Remapper {
    private static final String NONE = "";

    private final MojangMappings mappings;
    private final Hierarchy hierarchy;
    private final Map<String, String> methodCache = new HashMap<>();
    private final Map<String, String> fieldCache = new HashMap<>();

    MojangRemapper(MojangMappings mappings, Hierarchy hierarchy) {
        this.mappings = mappings;
        this.hierarchy = hierarchy;
    }

    @Override
    public String map(String internalName) {
        String r = mappings.obfToMoj.get(internalName);
        return r != null ? r : internalName;
    }

    @Override
    public String mapMethodName(String owner, String name, String descriptor) {
        if (name.charAt(0) == '<') {
            return name;
        }
        String key = owner + '.' + name + descriptor;
        String cached = methodCache.get(key);
        if (cached == null) {
            String found = find(mappings.methods, owner, name + descriptor);
            cached = found != null ? found : NONE;
            methodCache.put(key, cached);
        }
        return cached.isEmpty() ? name : cached;
    }

    @Override
    public String mapFieldName(String owner, String name, String descriptor) {
        String key = owner + '.' + name + ':' + descriptor;
        String cached = fieldCache.get(key);
        if (cached == null) {
            String found = find(mappings.fields, owner, name + ':' + descriptor);
            cached = found != null ? found : NONE;
            fieldCache.put(key, cached);
        }
        return cached.isEmpty() ? name : cached;
    }

    /** Ищет член в самом классе, затем в родителе и интерфейсах (в глубину). */
    private String find(Map<String, Map<String, String>> table, String owner, String memberKey) {
        Map<String, String> own = table.get(owner);
        if (own != null) {
            String r = own.get(memberKey);
            if (r != null) {
                return r;
            }
        }
        Hierarchy.Info info = hierarchy.get(owner);
        if (info == null) {
            return null;
        }
        if (info.superName() != null) {
            String r = find(table, info.superName(), memberKey);
            if (r != null) {
                return r;
            }
        }
        for (String itf : info.interfaces()) {
            String r = find(table, itf, memberKey);
            if (r != null) {
                return r;
            }
        }
        return null;
    }
}
