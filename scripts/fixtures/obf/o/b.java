package o;

import java.util.function.Supplier;

/** Наследует a и реализует f: проверяет унаследованные методы и поля, массивы, лямбды. */
public class b extends a implements f {
    public int g() {
        return d(10) + c;
    }

    public a[] h() {
        return new a[] {this};
    }

    public String i() {
        Supplier<String> s = () -> a.e() + c;
        return s.get();
    }
}
