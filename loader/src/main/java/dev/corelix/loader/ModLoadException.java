package dev.corelix.loader;

import java.util.List;

/** Ошибка загрузки модов. Хранит все найденные проблемы разом, чтобы показать их одним списком. */
public final class ModLoadException extends RuntimeException {
    private final List<String> problems;

    public ModLoadException(List<String> problems) {
        super("Corelix: failed to load mods:\n - " + String.join("\n - ", problems));
        this.problems = List.copyOf(problems);
    }

    public ModLoadException(String problem) {
        this(List.of(problem));
    }

    public List<String> problems() {
        return problems;
    }
}
