package net.minecraft.client.main;

import o.b;
import o.f;

/** Имя Main в клиенте Mojang не обфусцируется, остальные классы ссылаются на обфусцированные o.a/o.b/o.f. */
public class Main {
    public static void main(String[] args) {
        b o = new b();
        f t = o;
        System.out.println("OBF_GAME g=" + t.g() + " h=" + o.h().length + " i=" + o.i()
                + " class=" + o.getClass().getName());
    }
}
