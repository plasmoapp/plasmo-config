package su.plo.config.provider;

import su.plo.config.Config;
import su.plo.config.provider.toml.TomlConfiguration;

import javax.annotation.Nonnull;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.InvocationTargetException;
import java.util.HashMap;
import java.util.Map;

public abstract class ConfigurationProvider {
    private static final Map<Class<? extends ConfigurationProvider>, ConfigurationProvider> providers = new HashMap<>();

    static {
        try {
            providers.put(TomlConfiguration.class, new TomlConfiguration());
        } catch (NoClassDefFoundError ignored) {
        }
    }

    public static <T> T getProvider(Class<? extends ConfigurationProvider> provider) {
        return (T) providers.get(provider);
    }

    public <T> T load(Class<?> clazz, @Nonnull File file, boolean saveDefault) throws IOException {
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

    public <T> T load(Class<?> clazz, @Nonnull File file, @Nonnull Object defaultConfiguration) throws IOException {
        try (FileInputStream is = new FileInputStream(file)) {
            return (T) load(clazz, is, defaultConfiguration);
        }
    }

    public <T> T load(Class<?> clazz, @Nonnull File file, @Nonnull InputStream defaultConfiguration) throws IOException {
        try (FileInputStream is = new FileInputStream(file)) {
            return (T) load(clazz, is, defaultConfiguration);
        }
    }

    public <T> T load(Class<?> clazz, @Nonnull InputStream is) throws IOException {
        try {
            return (T) load(clazz, is, clazz.getConstructor().newInstance());
        } catch (InstantiationException | IllegalAccessException | InvocationTargetException |
                 NoSuchMethodException e) {
            throw new IllegalArgumentException("Failed to initialize configuration class", e);
        }
    }

    public <T> T load(Class<?> clazz, @Nonnull File file) throws IOException {
        try (FileInputStream is = new FileInputStream(file)) {
            return (T) load(clazz, is, clazz.getConstructor().newInstance());
        } catch (InstantiationException | IllegalAccessException | InvocationTargetException |
                 NoSuchMethodException e) {
            throw new IllegalArgumentException("Failed to initialize configuration class", e);
        }
    }

    protected abstract Object load(Class<?> clazz, @Nonnull InputStream is, @Nonnull Object defaultConfiguration) throws IOException;

    protected abstract Object load(Class<?> clazz, @Nonnull InputStream is, @Nonnull InputStream defaultConfiguration) throws IOException;

    public abstract Object serialize(Object object);

    public abstract void deserialize(Object object, Object serialized);

    public abstract void save(Class<?> clazz, @Nonnull Object configuration, @Nonnull File file) throws IOException;
}
