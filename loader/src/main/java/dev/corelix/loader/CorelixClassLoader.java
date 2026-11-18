package dev.corelix.loader;

import dev.corelix.api.ClassTransformer;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Path;
import java.security.CodeSigner;
import java.security.CodeSource;
import java.security.ProtectionDomain;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * ClassLoader игры и модов. Работает «child-first»: классы Minecraft и библиотек берутся из его
 * собственного списка URL (тот же classpath, что у лаунчера, плюс jar модов) и проходят через
 * трансформеры. Так игра не успевает загрузиться системным загрузчиком в обход патчей.
 * Parent-first остаются JDK и пакеты API/загрузчика, чтобы интерфейсы были общими для всех.
 */
public final class CorelixClassLoader extends URLClassLoader {
    static {
        ClassLoader.registerAsParallelCapable();
    }

    private static final String[] PARENT_FIRST = {
        "java.", "javax.", "jdk.", "sun.", "com.sun.", "dev.corelix.api.", "dev.corelix.loader."
    };

    private final List<ClassTransformer> transformers = new CopyOnWriteArrayList<>();

    public CorelixClassLoader(List<URL> urls, ClassLoader parent) {
        super("corelix", urls.toArray(new URL[0]), parent);
    }

    public void addPath(Path path) {
        try {
            addURL(path.toUri().toURL());
        } catch (java.net.MalformedURLException e) {
            throw new IllegalArgumentException("invalid path: " + path, e);
        }
    }

    public void addTransformer(ClassTransformer transformer) {
        transformers.add(transformer);
    }

    private static boolean isParentFirst(String name) {
        for (String prefix : PARENT_FIRST) {
            if (name.startsWith(prefix)) {
                return true;
            }
        }
        return false;
    }

    @Override
    protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
        synchronized (getClassLoadingLock(name)) {
            Class<?> c = findLoadedClass(name);
            if (c == null) {
                if (isParentFirst(name)) {
                    c = super.loadClass(name, false);
                } else {
                    try {
                        c = findClass(name);
                    } catch (ClassNotFoundException notInOurUrls) {
                        c = super.loadClass(name, false);
                    }
                }
            }
            if (resolve) {
                resolveClass(c);
            }
            return c;
        }
    }

    @Override
    protected Class<?> findClass(String name) throws ClassNotFoundException {
        String path = name.replace('.', '/').concat(".class");
        URL resource = findResource(path);
        if (resource == null) {
            throw new ClassNotFoundException(name);
        }
        byte[] bytes;
        try (InputStream in = resource.openStream()) {
            bytes = in.readAllBytes();
        } catch (IOException e) {
            throw new ClassNotFoundException(name, e);
        }
        // Код самого Corelix (ядро и моды) трансформерам не отдаём: так нет рекурсии и неожиданных патчей.
        if (!name.startsWith("dev.corelix.")) {
            bytes = transform(name, bytes);
        }
        definePackageIfAbsent(name);
        CodeSource source = new CodeSource(codeSourceUrl(resource, path), (CodeSigner[]) null);
        return defineClass(name, bytes, 0, bytes.length, new ProtectionDomain(source, null, this, null));
    }

    private byte[] transform(String name, byte[] bytes) {
        for (ClassTransformer t : transformers) {
            try {
                byte[] result = t.transform(name, bytes);
                if (result != null) {
                    bytes = result;
                }
            } catch (Throwable e) {
                throw new IllegalStateException("transformer " + t.getClass().getName() + " failed on class " + name, e);
            }
        }
        return bytes;
    }

    private void definePackageIfAbsent(String className) {
        int dot = className.lastIndexOf('.');
        if (dot <= 0) {
            return;
        }
        String pkg = className.substring(0, dot);
        if (getDefinedPackage(pkg) == null) {
            try {
                definePackage(pkg, null, null, null, null, null, null, null);
            } catch (IllegalArgumentException alreadyDefinedByAnotherThread) {
                // нормально при параллельной загрузке
            }
        }
    }

    private static URL codeSourceUrl(URL resource, String path) {
        String s = resource.toString();
        if (s.endsWith(path)) {
            s = s.substring(0, s.length() - path.length());
        }
        if (s.startsWith("jar:") && s.endsWith("!/")) {
            s = s.substring(4, s.length() - 2);
        }
        try {
            return URI.create(s).toURL();
        } catch (Exception e) {
            return null;
        }
    }
}
