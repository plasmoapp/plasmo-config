package su.plo.config.provider;

import com.google.common.hash.HashCode;
import com.google.common.hash.Hashing;
import org.jetbrains.annotations.NotNull;
import su.plo.config.Config;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.reflect.InvocationTargetException;
import java.nio.file.Files;
import java.util.ServiceLoader;
import java.util.stream.StreamSupport;

public abstract class ConfigurationProvider {

    public static <T> T getProvider(@NotNull Class<? extends ConfigurationProvider> providerClass) {
        return getProvider(providerClass, providerClass.getClassLoader());
    }

    @SuppressWarnings("unchecked")
    public static <T> T getProvider(
            @NotNull Class<? extends ConfigurationProvider> providerClass,
            @NotNull ClassLoader classLoader
    ) {
        ServiceLoader<ConfigurationProvider> loader = ServiceLoader.load(ConfigurationProvider.class, classLoader);

        return (T) StreamSupport.stream(loader.spliterator(), false)
                .filter(providerClass::isInstance)
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("Provider not found: " + providerClass));
    }

    public <T> T load(@NotNull Class<T> clazz, @NotNull File file, boolean saveDefault) throws IOException {
        if (!clazz.isAnnotationPresent(Config.class)) {
            throw new IllegalArgumentException("Class not annotated with @Config");
        }

        T config = null;
        if (file.exists()) {
            config = load(clazz, file);
        }

        if (config == null) {
            try {
                config = clazz.getConstructor().newInstance();
            } catch (InstantiationException | IllegalAccessException | InvocationTargetException | NoSuchMethodException e) {
                throw new IllegalArgumentException("No default constructor in class");
            }

            if (saveDefault) {
                save(config, file);
            }
        }

        return config;
    }

    @SuppressWarnings("unchecked")
    public <T> T load(Class<?> clazz, @NotNull File file, @NotNull Object defaultConfiguration) throws IOException {
        try (FileInputStream is = new FileInputStream(file)) {
            return (T) load(clazz, is, defaultConfiguration);
        }
    }

    @SuppressWarnings("unchecked")
    public <T> T load(@NotNull Class<?> clazz, @NotNull File file, @NotNull InputStream defaultConfiguration) throws IOException {
        try (FileInputStream is = new FileInputStream(file)) {
            return (T) load(clazz, is, defaultConfiguration);
        }
    }

    @SuppressWarnings("unchecked")
    public <T> T load(@NotNull Class<?> clazz, @NotNull InputStream is) throws IOException {
        try {
            return (T) load(clazz, is, clazz.getConstructor().newInstance());
        } catch (InstantiationException | IllegalAccessException | InvocationTargetException |
                 NoSuchMethodException e) {
            throw new IllegalArgumentException("Failed to initialize configuration class", e);
        }
    }

    @SuppressWarnings("unchecked")
    public <T> T load(@NotNull Class<?> clazz, @NotNull File file) throws IOException {
        try (FileInputStream is = new FileInputStream(file)) {
            return (T) load(clazz, is, clazz.getConstructor().newInstance());
        } catch (InstantiationException | IllegalAccessException | InvocationTargetException |
                 NoSuchMethodException e) {
            throw new IllegalArgumentException("Failed to initialize configuration class", e);
        }
    }

    /**
     * @deprecated use {@link #save(Object, File)}
     */
    @Deprecated
    public <T> void save(@NotNull Class<T> targetClass, @NotNull T configuration, @NotNull File file) throws IOException {
        save(configuration, file);
    }

    public <T> void save(@NotNull T configuration, @NotNull File file) throws IOException {
        Class<?> targetClass = configuration.getClass();
        if (!targetClass.isAnnotationPresent(Config.class)) {
            throw new IllegalArgumentException("Class not annotated with @Config");
        }

        if (file.getParentFile() != null && !file.getParentFile().exists()) {
            file.getParentFile().mkdirs();
        }

        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        save(configuration, outputStream);

        if (file.exists()) {
            HashCode existingHash = com.google.common.io.Files.asByteSource(file).hash(Hashing.sha256());
            HashCode newHash = Hashing.sha256().hashBytes(outputStream.toByteArray());

            if (existingHash.equals(newHash)) return;
        }

        Files.write(file.toPath(), outputStream.toByteArray());
    }

    protected abstract Object load(@NotNull Class<?> configClass, @NotNull InputStream is, @NotNull Object defaultConfiguration) throws IOException;

    protected abstract Object load(@NotNull Class<?> configClass, @NotNull InputStream is, @NotNull InputStream defaultConfiguration) throws IOException;

    public abstract Object serialize(@NotNull Object object);

    public abstract void deserialize(@NotNull Object targetObject, @NotNull Object map);

    /**
     * @deprecated use {@link #save(Object, OutputStream)}
     */
    @Deprecated
    public <T> void save(@NotNull Class<T> targetClass, @NotNull T configuration, @NotNull OutputStream outputStream) throws IOException {
        save(configuration, outputStream);
    }

    public abstract <T> void save(@NotNull T configuration, @NotNull OutputStream outputStream) throws IOException;
}
