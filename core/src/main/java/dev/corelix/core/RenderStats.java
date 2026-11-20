package dev.corelix.core;

/**
 * Счётчики отрисовки для строки PROFILE: сколько вызовов GL на кадр и сколько времени уходит на мир, интерфейс и прочее.
 * Нужны, чтобы оптимизации отрисовки (меньше вызовов, пакетная отправка) опирались на цифры, а не на догадки.
 * Всё вызывается на потоке рендера, поэтому обычные поля без блокировок (отчёт читает приблизительно, этого достаточно).
 */
public final class RenderStats {
    private static long draws, programs, textures, vaos, buffers, uploads, uploadBytes, uniforms;
    private static long renderNs, levelNs, terrainNs, hudNs;
    private static long renderT, levelT, terrainT, hudT;

    private RenderStats() {}

    public static void draw() { draws++; }
    public static void program() { programs++; }
    public static void texture() { textures++; }
    public static void vao() { vaos++; }
    public static void buffer() { buffers++; }
    public static void upload(long bytes) { uploads++; uploadBytes += bytes; }
    public static void uniform() { uniforms++; }

    public static void renderBegin() { renderT = System.nanoTime(); }
    public static void renderEnd() { renderNs += System.nanoTime() - renderT; }
    public static void levelBegin() { levelT = System.nanoTime(); }
    public static void levelEnd() { levelNs += System.nanoTime() - levelT; }
    public static void terrainBegin() { terrainT = System.nanoTime(); }
    public static void terrainEnd() { terrainNs += System.nanoTime() - terrainT; }
    public static void hudBegin() { hudT = System.nanoTime(); }
    public static void hudEnd() { hudNs += System.nanoTime() - hudT; }

    /** Строка отчёта (в среднем на кадр) и сброс счётчиков. */
    static String reportAndReset(long frames) {
        if (frames <= 0 || renderNs == 0) {
            return "";
        }
        double f = frames;
        String s = String.format(
                " | RENDER per frame: render %.1f ms (level %.1f, of it terrain %.1f; hud %.1f) | GL: draws %.0f, programs %.0f, textures %.0f,"
                        + " vao %.0f, buffer binds %.0f, uploads %.0f (%.0f KB), uniforms %.0f",
                renderNs / f / 1e6, levelNs / f / 1e6, terrainNs / f / 1e6, hudNs / f / 1e6,
                draws / f, programs / f, textures / f, vaos / f, buffers / f, uploads / f, uploadBytes / f / 1024, uniforms / f);
        draws = programs = textures = vaos = buffers = uploads = uploadBytes = uniforms = 0;
        renderNs = levelNs = terrainNs = hudNs = 0;
        return s;
    }
}
