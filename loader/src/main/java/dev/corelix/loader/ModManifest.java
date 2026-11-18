package dev.corelix.loader;

import dev.corelix.api.Corelix;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.regex.Pattern;

/**
 * Манифест мода: файл {@code corelix.mod.properties} в корне jar.
 * Только jar с таким файлом считаются модами Corelix, остальное из папки mods игнорируется.
 */
public record ModManifest(
        String id,
        String version,
        String name,
        String entrypoint,
        List<String> depends,
        List<String> mixins,
        int api,
        Path path) {

    public static final String FILE_NAME = "corelix.mod.properties";
    private static final Pattern ID = Pattern.compile("[a-z][a-z0-9_-]{1,63}");

    /** @throws IllegalArgumentException с понятным текстом, если манифест некорректен */
    static ModManifest parse(Properties p, Path jar) {
        String id = trimmed(p, "id");
        if (id == null) {
            throw new IllegalArgumentException("missing field: id");
        }
        if (!ID.matcher(id).matches()) {
            throw new IllegalArgumentException("invalid id '" + id + "' (expected [a-z][a-z0-9_-]{1,63})");
        }
        String entrypoint = trimmed(p, "entrypoint");
        if (entrypoint == null) {
            throw new IllegalArgumentException("missing field: entrypoint");
        }
        int api = 1;
        String apiRaw = trimmed(p, "api");
        if (apiRaw != null) {
            try {
                api = Integer.parseInt(apiRaw);
            } catch (NumberFormatException e) {
                throw new IllegalArgumentException("api must be a number, not '" + apiRaw + "'");
            }
        }
        if (api > Corelix.API_VERSION) {
            throw new IllegalArgumentException(
                    "requires API " + api + ", loader supports " + Corelix.API_VERSION);
        }
        List<String> depends = new ArrayList<>();
        String dependsRaw = trimmed(p, "depends");
        if (dependsRaw != null) {
            for (String d : dependsRaw.split(",")) {
                d = d.trim();
                if (d.isEmpty()) {
                    continue;
                }
                if (!ID.matcher(d).matches()) {
                    throw new IllegalArgumentException("invalid id in depends: '" + d + "'");
                }
                depends.add(d);
            }
        }
        List<String> mixins = new ArrayList<>();
        String mixinsRaw = trimmed(p, "mixins");
        if (mixinsRaw != null) {
            for (String m : mixinsRaw.split(",")) {
                m = m.trim();
                if (!m.isEmpty()) {
                    mixins.add(m);
                }
            }
        }
        String version = trimmed(p, "version");
        String name = trimmed(p, "name");
        return new ModManifest(
                id,
                version == null ? "0" : version,
                name == null ? id : name,
                entrypoint,
                List.copyOf(depends),
                List.copyOf(mixins),
                api,
                jar);
    }

    private static String trimmed(Properties p, String key) {
        String v = p.getProperty(key);
        if (v == null) {
            return null;
        }
        v = v.trim();
        return v.isEmpty() ? null : v;
    }
}
