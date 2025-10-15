package su.plo.config.provider;

import org.jetbrains.annotations.NotNull;
import su.plo.config.Config;

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

    @SuppressWarnings("unchecked")
    public static <T> T getProvider(@NotNull Class<? extends ConfigurationProvider> providerClass) {
        ServiceLoader<ConfigurationProvider> loader = ServiceLoader.load(ConfigurationProvider.class);

        return (T) StreamSupport.stream(loader.spliterator(), false)
                .filter(providerClass::isInstance)
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("Provider not found: " + providerClass));
    }

    @SuppressWarnings("unchecked")
    public <T> T load(@NotNull Class<?> clazz, @NotNull File file, boolean saveDefault) throws IOException {
        if (!clazz.isAnnotationPresent(Config.class)) {
            throw new IllegalArgumentException("Class not annotated with @Config");
        }

        Object config = null;
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
                save(clazz, config, file);
            }
        }

        return (T) config;
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

    public void save(@NotNull Class<?> targetClass, @NotNull Object configuration, @NotNull File file) throws IOException {
        if (!targetClass.isAnnotationPresent(Config.class)) {
            throw new IllegalArgumentException("Class not annotated with @Config");
        }

        if (file.getParentFile() != null && !file.getParentFile().exists()) {
            file.getParentFile().mkdirs();
        }

        try (OutputStream outputStream = Files.newOutputStream(file.toPath())) {
            save(targetClass, configuration, outputStream);
        }
    }

    protected abstract Object load(@NotNull Class<?> configClass, @NotNull InputStream is, @NotNull Object defaultConfiguration) throws IOException;

    protected abstract Object load(@NotNull Class<?> configClass, @NotNull InputStream is, @NotNull InputStream defaultConfiguration) throws IOException;

    public abstract Object serialize(@NotNull Object object);

    public abstract void deserialize(@NotNull Object targetObject, @NotNull Object map);

    public abstract void save(@NotNull Class<?> targetClass, @NotNull Object configuration, @NotNull OutputStream outputStream) throws IOException;
}
