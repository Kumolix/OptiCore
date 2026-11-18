package dev.corelix.remap;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Парсер официальных маппингов Mojang (client.txt, формат ProGuard).
 * Строка класса: {@code net.minecraft.Foo -> abc:}, члены с отступом:
 * {@code int field -> a}, {@code 1:2:void method(int,java.lang.String):10:11 -> b}.
 * Все имена хранятся во внутреннем виде JVM (со слэшами). Типы в сигнатурах записаны именами Mojang,
 * поэтому для поиска по обфусцированному дескриптору их нужно перевести обратно (второй проход).
 */
final class MojangMappings {
    final Map<String, String> obfToMoj = new HashMap<>();
    final Map<String, String> mojToObf = new HashMap<>();
    /** obf-класс -> (obfName + obfDescriptor -> имя Mojang) */
    final Map<String, Map<String, String>> methods = new HashMap<>();
    /** obf-класс -> (obfName + ":" + obfDescriptor -> имя Mojang) */
    final Map<String, Map<String, String>> fields = new HashMap<>();

    private static final Pattern METHOD =
            Pattern.compile("^(?:\\d+:\\d+:)?(\\S+) ([^\\s(]+)\\(([^)]*)\\)(?::\\d+:\\d+)?$");
    private static final Pattern FIELD = Pattern.compile("^(\\S+) (\\S+)$");

    private MojangMappings() {}

    static MojangMappings parse(Path file) throws IOException {
        MojangMappings m = new MojangMappings();

        // Проход 1: только классы, чтобы знать соответствие имён при разборе сигнатур.
        try (BufferedReader r = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            String line;
            while ((line = r.readLine()) != null) {
                if (!isClassLine(line)) {
                    continue;
                }
                int arrow = line.indexOf(" -> ");
                String moj = line.substring(0, arrow).replace('.', '/');
                String obf = line.substring(arrow + 4, line.length() - 1).replace('.', '/');
                if (!moj.equals(obf)) {
                    m.obfToMoj.put(obf, moj);
                    m.mojToObf.put(moj, obf);
                }
            }
        }

        // Проход 2: поля и методы.
        try (BufferedReader r = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            String line;
            String currentObf = null;
            while ((line = r.readLine()) != null) {
                if (line.isEmpty() || line.charAt(0) == '#') {
                    continue;
                }
                if (isClassLine(line)) {
                    int arrow = line.indexOf(" -> ");
                    currentObf = line.substring(arrow + 4, line.length() - 1).replace('.', '/');
                    continue;
                }
                if (currentObf == null || line.charAt(0) != ' ') {
                    continue;
                }
                String member = line.trim();
                int arrow = member.lastIndexOf(" -> ");
                if (arrow < 0) {
                    continue;
                }
                String left = member.substring(0, arrow);
                String obfName = member.substring(arrow + 4).trim();

                Matcher mm = METHOD.matcher(left);
                if (mm.matches()) {
                    String mojName = mm.group(2);
                    if (mojName.charAt(0) == '<' || mojName.equals(obfName)) {
                        continue;
                    }
                    String desc = m.methodDescriptor(mm.group(1), mm.group(3));
                    m.methods.computeIfAbsent(currentObf, k -> new HashMap<>()).put(obfName + desc, mojName);
                    continue;
                }
                Matcher fm = FIELD.matcher(left);
                if (fm.matches()) {
                    String mojName = fm.group(2);
                    if (mojName.equals(obfName)) {
                        continue;
                    }
                    String desc = m.typeDescriptor(fm.group(1));
                    m.fields.computeIfAbsent(currentObf, k -> new HashMap<>()).put(obfName + ":" + desc, mojName);
                }
            }
        }
        return m;
    }

    private static boolean isClassLine(String line) {
        return !line.isEmpty()
                && line.charAt(0) != '#'
                && line.charAt(0) != ' '
                && line.endsWith(":")
                && line.contains(" -> ");
    }

    /** Дескриптор метода в обфусцированных именах по сигнатуре, записанной типами Mojang. */
    private String methodDescriptor(String returnType, String args) {
        StringBuilder sb = new StringBuilder("(");
        if (!args.isEmpty()) {
            for (String a : args.split(",")) {
                sb.append(typeDescriptor(a.trim()));
            }
        }
        return sb.append(')').append(typeDescriptor(returnType)).toString();
    }

    private String typeDescriptor(String type) {
        int dims = 0;
        while (type.endsWith("[]")) {
            dims++;
            type = type.substring(0, type.length() - 2);
        }
        String base;
        switch (type) {
            case "int" -> base = "I";
            case "long" -> base = "J";
            case "boolean" -> base = "Z";
            case "byte" -> base = "B";
            case "char" -> base = "C";
            case "short" -> base = "S";
            case "float" -> base = "F";
            case "double" -> base = "D";
            case "void" -> base = "V";
            default -> {
                String internal = type.replace('.', '/');
                String obf = mojToObf.get(internal);
                base = "L" + (obf != null ? obf : internal) + ";";
            }
        }
        return "[".repeat(dims) + base;
    }
}
