package dev.corelix.remap;

import java.nio.file.Path;

/** Командная строка для CI и отладки: ремап и проверка результата. */
public final class Cli {
    private Cli() {}

    public static void main(String[] args) throws Exception {
        if (args.length == 4 && args[0].equals("remap")) {
            ClientRemapper.remapTo(Path.of(args[1]), Path.of(args[2]), Path.of(args[3]));
        } else if (args.length == 3 && args[0].equals("verify")) {
            System.exit(Verify.run(Path.of(args[1]), Path.of(args[2])));
        } else {
            System.err.println("usage:\n  remap <client.jar> <client.txt> <out.jar>\n  verify <remapped.jar> <libs-dir>");
            System.exit(2);
        }
    }
}
