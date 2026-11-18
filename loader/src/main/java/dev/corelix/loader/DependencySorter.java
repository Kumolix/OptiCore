package dev.corelix.loader;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.TreeMap;

/** Топологическая сортировка модов: зависимости раньше зависимых, порядок детерминирован (по id). */
public final class DependencySorter {
    private DependencySorter() {}

    public static List<ModManifest> sort(Collection<ModManifest> mods) {
        Map<String, ModManifest> byId = new TreeMap<>();
        List<String> problems = new ArrayList<>();

        for (ModManifest m : mods) {
            ModManifest prev = byId.putIfAbsent(m.id(), m);
            if (prev != null) {
                problems.add("duplicate id '" + m.id() + "': "
                        + prev.path().getFileName() + " and " + m.path().getFileName());
            }
        }
        for (ModManifest m : byId.values()) {
            for (String dep : m.depends()) {
                if (!byId.containsKey(dep)) {
                    problems.add("mod '" + m.id() + "' requires missing mod '" + dep + "'");
                }
            }
        }
        if (!problems.isEmpty()) {
            throw new ModLoadException(problems);
        }

        Map<String, Integer> pending = new HashMap<>();
        Map<String, List<String>> dependents = new HashMap<>();
        for (ModManifest m : byId.values()) {
            pending.put(m.id(), m.depends().size());
            for (String dep : m.depends()) {
                dependents.computeIfAbsent(dep, k -> new ArrayList<>()).add(m.id());
            }
        }
        PriorityQueue<String> ready = new PriorityQueue<>();
        pending.forEach((id, n) -> {
            if (n == 0) {
                ready.add(id);
            }
        });

        List<ModManifest> result = new ArrayList<>(byId.size());
        while (!ready.isEmpty()) {
            String id = ready.poll();
            result.add(byId.get(id));
            for (String child : dependents.getOrDefault(id, List.of())) {
                if (pending.merge(child, -1, Integer::sum) == 0) {
                    ready.add(child);
                }
            }
        }
        if (result.size() != byId.size()) {
            List<String> cyclic = new ArrayList<>();
            pending.forEach((id, n) -> {
                if (n > 0) {
                    cyclic.add(id);
                }
            });
            java.util.Collections.sort(cyclic);
            throw new ModLoadException("dependency cycle between mods: " + String.join(", ", cyclic));
        }
        return result;
    }
}
