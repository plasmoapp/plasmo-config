package su.plo.config.provider.toml;

import com.google.common.base.CaseFormat;
import com.google.common.base.Charsets;
import com.google.common.collect.Maps;
import com.moandjiezana.toml.Toml;
import com.moandjiezana.toml.TomlWriter;
import su.plo.config.Config;
import su.plo.config.ConfigField;
import su.plo.config.ConfigFieldProcessor;
import su.plo.config.ConfigValidator;
import su.plo.config.entry.ConfigEntry;
import su.plo.config.entry.SerializableConfigEntry;
import su.plo.config.provider.ConfigurationProvider;

import javax.annotation.Nonnull;
import java.io.*;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.*;
import java.util.function.Function;
import java.util.function.Predicate;

public class TomlConfigurationOld extends ConfigurationProvider {

    public TomlConfigurationOld() {
    }

    @Override
    public Object load(Class<?> configClass, @Nonnull InputStream is, @Nonnull Object defaultConfiguration) {
        Toml toml = new Toml().read(new InputStreamReader(is, Charsets.UTF_8));

        Object configuration;

        try {
            configuration = configClass.getConstructor().newInstance();
        } catch (InstantiationException | IllegalAccessException | InvocationTargetException |
                 NoSuchMethodException e) {
            throw new IllegalArgumentException("Failed to initialize configuration class", e);
        }

        setDefaults(configClass, configuration, defaultConfiguration);
        loadConfig(toml.toMap(), configClass, configuration);

        return configuration;
    }

    @Override
    public Object load(Class<?> configClass, @Nonnull InputStream is, @Nonnull InputStream defaultConfiguration) {
        Toml toml = new Toml().read(new InputStreamReader(is, Charsets.UTF_8));

        Object configuration;

        try {
            configuration = configClass.getConstructor().newInstance();
        } catch (InstantiationException | IllegalAccessException | InvocationTargetException |
                 NoSuchMethodException e) {
            throw new IllegalArgumentException("Failed to initialize configuration class", e);
        }

        Map<String, Object> configurationMap = toml.toMap();
        setDefaults(configurationMap, new Toml().read(new InputStreamReader(defaultConfiguration, Charsets.UTF_8)).toMap());

        loadConfig(configurationMap, configClass, configuration);

        return configuration;
    }

    private void setDefaults(Map<String, Object> configuration, Map<String, Object> defaults) {
        defaults.forEach((key, value) -> {
            if (!configuration.containsKey(key)) {
                configuration.put(key, value);
            } else if (value instanceof Map) {
                setDefaults((Map<String, Object>) configuration.get(key), (Map<String, Object>) value);
            }
        });
    }

    private void setDefaults(Class<?> clazz, @Nonnull Object configuration, @Nonnull Object defaultConfiguration) {
        for (Field field : getFields(clazz)) {
            if (!field.isAnnotationPresent(ConfigField.class)) continue;

            Optional<Method> fieldGetter = findGetter(clazz, field);

            // skip field if getter doesn't exists
            if (!fieldGetter.isPresent()) continue;

            try {
                Object fieldValue = fieldGetter.get().invoke(configuration);
                Object defaultValue = fieldGetter.get().invoke(defaultConfiguration);

                if (fieldValue == null || defaultValue == null) continue;

                if (fieldValue instanceof ConfigEntry &&
                        defaultValue instanceof ConfigEntry
                ) {
                    ConfigEntry configEntry = (ConfigEntry) fieldValue;
                    ConfigEntry defaultEntry = (ConfigEntry) defaultValue;

                    configEntry.setDefault(defaultEntry.getDefault());
                } else if (fieldValue.getClass().isAnnotationPresent(Config.class) &&
                        defaultValue.getClass().isAnnotationPresent(Config.class)
                ) {
                    setDefaults(fieldValue.getClass(), fieldValue, defaultValue);
                }
            } catch (IllegalAccessException | InvocationTargetException e) {
                e.printStackTrace();
            }
        }
    }

    @Override
    public Object serialize(Object object) {
        if (object instanceof SerializableConfigEntry) {
            return ((SerializableConfigEntry) object).serialize();
        }

        Class<?> clazz = object.getClass();
        if (!clazz.isAnnotationPresent(Config.class)) {
            throw new IllegalArgumentException("Class not annotated with @Config");
        }

        Map<String, Object> serialized = Maps.newHashMap();

        for (Field field : getFields(clazz)) {
            if (!field.isAnnotationPresent(ConfigField.class)) continue;

            ConfigField configField = field.getAnnotation(ConfigField.class);
            String configPath = configField.path().isEmpty()
                    ? CaseFormat.LOWER_CAMEL.to(CaseFormat.LOWER_UNDERSCORE, field.getName())
                    : configField.path();

            Optional<Method> fieldGetter = findGetter(clazz, field);

            // skip field if getter doesn't exists
            if (!fieldGetter.isPresent()) continue;

            try {
                Object fieldValue = fieldGetter.get().invoke(object);
                if (fieldValue == null) continue;

                // ignore default ConfigEntry
                if (fieldValue instanceof ConfigEntry) {
                    if (configField.ignoreDefault() && ((ConfigEntry<?>) fieldValue).isDefault()) {
                        continue;
                    }
                }

                if (fieldValue.getClass().isAnnotationPresent(Config.class)) {
                    serialized.put(configPath, serialize(fieldValue));
                } else if (fieldValue instanceof SerializableConfigEntry) {
                    SerializableConfigEntry configEntry = (SerializableConfigEntry) fieldValue;
                    serialized.put(configPath, configEntry.serialize());
                } else {
                    serialized.put(configPath, fieldValue);
                }

            } catch (InvocationTargetException | IllegalAccessException e) {
                e.printStackTrace();
            }
        }

        return serialized;
    }

    @Override
    public void deserialize(Object targetObject, Object map) {
        if (targetObject instanceof SerializableConfigEntry) {
            ((SerializableConfigEntry) targetObject).deserialize(map);
            return;
        }

        Class<?> clazz = targetObject.getClass();
        if (!clazz.isAnnotationPresent(Config.class)) {
            throw new IllegalArgumentException("Class not annotated with @Config");
        }

        Map<String, Object> serialized = (Map<String, Object>) map;
        
        for (Field field : getFields(clazz)) {
            if (!field.isAnnotationPresent(ConfigField.class)) continue;

            ConfigField configField = field.getAnnotation(ConfigField.class);
            String configPath = configField.path().isEmpty()
                    ? CaseFormat.LOWER_CAMEL.to(CaseFormat.LOWER_UNDERSCORE, field.getName())
                    : configField.path();

            Optional<Method> fieldGetter = findGetter(clazz, field);
            Optional<Method> fieldSetter = findSetter(clazz, field);

            // skip field if getter doesn't exists
            if (!fieldGetter.isPresent() || !fieldSetter.isPresent()) continue;

            try {
                Object fieldValue = fieldGetter.get().invoke(targetObject);
                Object serializedValue = serialized.get(configPath);
                if (fieldValue == null) continue;

                if (field.isAnnotationPresent(ConfigValidator.class)) {
                    ConfigValidator configValidator = field.getAnnotation(ConfigValidator.class);
                    Predicate<Object> validator = (Predicate<Object>) configValidator.value().getConstructor().newInstance();
                    if (!validator.test(serializedValue)) {
                        throw new IllegalArgumentException(configPath + " is invalid. Current value: " + serializedValue + ". Allowed values: " + Arrays.toString(configValidator.allowed()));
                    }
                }

                // apply config processors
                if (field.isAnnotationPresent(ConfigFieldProcessor.class)) {
                    serializedValue = applyProcessors(field.getAnnotation(ConfigFieldProcessor.class), serializedValue);
                }
                if (clazz.isAnnotationPresent(ConfigFieldProcessor.class)) {
                    serializedValue = applyProcessors(clazz.getAnnotation(ConfigFieldProcessor.class), serializedValue);
                }

                if (fieldValue.getClass().isAnnotationPresent(Config.class)) {
                    deserialize(fieldValue, serialized.get(configPath));
                } else if (fieldValue instanceof SerializableConfigEntry) {
                    SerializableConfigEntry configEntry = (SerializableConfigEntry) fieldValue;
                    configEntry.deserialize(serializedValue);
                } else {
                    if (field.getType() == int.class) {
                        fieldSetter.get().invoke(targetObject, ((Long) serializedValue).intValue());
                    } else if (field.getType() == short.class) {
                        fieldSetter.get().invoke(targetObject, ((Long) serializedValue).shortValue());
                    } else if (field.getType() == boolean.class) {
                        fieldSetter.get().invoke(targetObject, ((Boolean) serializedValue).booleanValue());
                    } else if (field.getType() == double.class) {
                        fieldSetter.get().invoke(targetObject, (Double) serializedValue);
                    } else if (field.getType() == float.class) {
                        fieldSetter.get().invoke(targetObject, ((Double) serializedValue).floatValue());
                    } else if (field.getType().isEnum()) {
                        Class<? extends Enum> enumClass = (Class<? extends Enum<?>>) field.getType();

                        try {
                            fieldSetter.get().invoke(targetObject, Enum.valueOf(enumClass, (String) serializedValue));
                        } catch (Exception ignored) {
                            fieldSetter.get().invoke(targetObject, enumClass.getEnumConstants()[0]);
                        }
                    } else {
                        fieldSetter.get().invoke(targetObject, field.getType().cast(serializedValue));
                    }
                }
            } catch (IllegalAccessException | InvocationTargetException | InstantiationException |
                     NoSuchMethodException e) {
                e.printStackTrace();
            }
        }
    }

    @Override
    public void save(Class<?> targetClass, @Nonnull Object configuration, @Nonnull File file) throws IOException {
        if (!targetClass.isAnnotationPresent(Config.class)) {
            throw new IllegalArgumentException("Class not annotated with @Config");
        }

        if (file.getParentFile() != null && !file.getParentFile().exists()) {
            file.getParentFile().mkdirs();
        }

        try (BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(Files.newOutputStream(Paths.get(file.getAbsolutePath())), StandardCharsets.UTF_8))) {
            writeClass(writer, targetClass, configuration, "", "");
        }
    }

    private void loadConfig(Map<String, Object> mapConfig, Class<?> clazz, Object configuration) {
        for (Field field : getFields(clazz)) {
            if (!field.isAnnotationPresent(ConfigField.class)) continue;

            ConfigField configField = field.getAnnotation(ConfigField.class);
            String configPath = configField.path().isEmpty()
                    ? CaseFormat.LOWER_CAMEL.to(CaseFormat.LOWER_UNDERSCORE, field.getName())
                    : configField.path();

            if (!mapConfig.containsKey(configPath)) continue;

            Optional<Method> fieldGetter = findGetter(clazz, field);
            Optional<Method> fieldSetter = findSetter(clazz, field);

            // skip field if getter doesn't exists
            if (!fieldGetter.isPresent() || !fieldSetter.isPresent()) {
                continue;
            }

            try {
                Object fieldValue = fieldGetter.get().invoke(configuration);
                Class<?> returnType = fieldGetter.get().getReturnType();
                Object configValue = mapConfig.get(configPath);

                if (fieldValue == null) {
                    if (!returnType.isAnnotationPresent(Config.class)) {
                        continue;
                    }

                    fieldValue = returnType.getConstructor().newInstance();
                    fieldSetter.get().invoke(configuration, fieldValue);
                }

                if (field.isAnnotationPresent(ConfigValidator.class)) {
                    ConfigValidator configValidator = field.getAnnotation(ConfigValidator.class);
                    Predicate<Object> validator = (Predicate<Object>) configValidator.value().getConstructor().newInstance();
                    if (!validator.test(configValue)) {
                        throw new IllegalArgumentException(configPath + " is invalid. Current value: " + configValue + ". Allowed values: " + Arrays.toString(configValidator.allowed()));
                    }
                }

                // apply config processors
                if (field.isAnnotationPresent(ConfigFieldProcessor.class)) {
                    configValue = applyProcessors(field.getAnnotation(ConfigFieldProcessor.class), configValue);
                }

                if (fieldValue.getClass().isAnnotationPresent(Config.class)) {
                    loadConfig(
                            (Map<String, Object>) mapConfig.get(configPath),
                            fieldValue.getClass(),
                            fieldValue
                    );
                } else if (fieldValue instanceof SerializableConfigEntry) {
                    SerializableConfigEntry configEntry = (SerializableConfigEntry) fieldValue;
                    configEntry.deserialize(configValue);
                } else {
                    if (field.getType() == int.class) {
                        fieldSetter.get().invoke(configuration, ((Long) configValue).intValue());
                    } else if (field.getType() == short.class) {
                        fieldSetter.get().invoke(configuration, ((Long) configValue).shortValue());
                    } else if (field.getType() == boolean.class) {
                        fieldSetter.get().invoke(configuration, ((Boolean) configValue).booleanValue());
                    } else if (field.getType() == double.class) {
                        fieldSetter.get().invoke(configuration, (Double) configValue);
                    } else if (field.getType() == float.class) {
                        fieldSetter.get().invoke(configuration, ((Double) configValue).floatValue());
                    } else if (field.getType().isEnum()) {
                        Class<? extends Enum> enumClass = (Class<? extends Enum<?>>) field.getType();

                        try {
                            fieldSetter.get().invoke(configuration, Enum.valueOf(enumClass, (String) configValue));
                        } catch (Exception ignored) {
                            fieldSetter.get().invoke(configuration, enumClass.getEnumConstants()[0]);
                        }
                    } else {
                        fieldSetter.get().invoke(configuration, field.getType().cast(configValue));
                    }
                }
            } catch (IllegalAccessException | InvocationTargetException | InstantiationException |
                     NoSuchMethodException e) {
                e.printStackTrace();
            }
        }
    }

    private void writeClass(BufferedWriter writer, Class<?> clazz, Object object, String parent, String prefix) throws IOException {
        Config anConfig = clazz.getAnnotation(Config.class);

        if (!anConfig.comment().isEmpty()) {
            this.writeComment(writer, prefix, anConfig.comment());
            writer.write("\n");
        }

        for (Field field : getFields(clazz)) {
            if (!field.isAnnotationPresent(ConfigField.class)) continue;

            ConfigField configField = field.getAnnotation(ConfigField.class);
            String configPath = configField.path().isEmpty()
                    ? CaseFormat.LOWER_CAMEL.to(CaseFormat.LOWER_UNDERSCORE, field.getName())
                    : configField.path();

            Optional<Method> fieldGetter = findGetter(clazz, field);

            // skip field if getter doesn't exists
            if (!fieldGetter.isPresent()) {
                continue;
            }

            try {
                Object fieldValue = fieldGetter.get().invoke(object);

                if (fieldValue == null) continue;

                // ignore default ConfigEntry
                if (fieldValue instanceof ConfigEntry) {
                    if (configField.ignoreDefault() && ((ConfigEntry<?>) fieldValue).isDefault()) {
                        continue;
                    }
                }

                if (fieldValue instanceof SerializableConfigEntry) {
                    SerializableConfigEntry configEntry = (SerializableConfigEntry) fieldValue;

                    fieldValue = configEntry.serialize();
                }

                // # comment
                // key: value
                if (fieldValue.getClass().isAnnotationPresent(Config.class)) {
                    writer.newLine();
                }

                if (!configField.comment().isEmpty()) {
                    this.writeComment(writer, prefix, configField.comment());
                }

                if (fieldValue.getClass().isAnnotationPresent(Config.class)) {
                    writer.write(String.format("%s[%s%s]\n", prefix, parent, configPath));
                    writeClass(writer, fieldValue.getClass(), fieldValue, parent + configPath + ".", prefix);
                    continue;
                }

                if (fieldValue instanceof Map) {
                    writeMap(writer, prefix, parent, configPath, (Map<String, Object>) fieldValue);
                    continue;
                }

                writeValue(writer, prefix, configPath, fieldValue);
            } catch (IllegalAccessException | InvocationTargetException e) {
                e.printStackTrace();
                writer.write("# Failed to serialize field with name: " + configPath);
            }
//            writer.write("\n");
        }
    }

    private void writeMap(BufferedWriter writer, String prefix, String parent, String key, Map<String, Object> map) throws IOException {
        if (!map.values().stream().allMatch(value -> value instanceof Map)) {
            writer.write(String.format("%s[%s%s]\n", prefix, parent, key));
        }

        for (Map.Entry<String, Object> entry : map.entrySet()) {
            Object mapEntry = entry.getValue();
            if (mapEntry instanceof Map) {
                writeMap(writer, prefix, parent + key + ".", entry.getKey(), (Map<String, Object>) mapEntry);
                continue;
            }

            writeValue(writer, prefix, entry.getKey(), mapEntry);
        }
    }

    private void writeValue(BufferedWriter writer, String prefix, String key, Object value) throws IOException {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put(key, value);

        if (!prefix.isEmpty()) writer.write(prefix);
        new TomlWriter().write(data, writer);
    }

    private void writeComment(BufferedWriter writer, String prefix, String comment) throws IOException {
        for (String line : comment.split("\n")) {
            writer.write(prefix + "# " + line + "\n");
        }
    }

    private Optional<Method> findSetter(Class<?> clazz, Field field) {
        return Arrays.stream(clazz.getMethods()).filter(method ->
                        method.getParameterCount() == 1 && method.getParameterTypes()[0].equals(field.getType())
                                && (method.getName().equalsIgnoreCase(field.getName()) ||
                                method.getName().equalsIgnoreCase("set" + field.getName()))
                )
                .findFirst();
    }

    private Optional<Method> findGetter(Class<?> clazz, Field field) {
        return Arrays.stream(clazz.getMethods()).filter(method ->
                        method.getReturnType().equals(field.getType())
                                && (method.getName().equalsIgnoreCase(field.getName()) ||
                                method.getName().equalsIgnoreCase("get" + field.getName()) ||
                                method.getName().equalsIgnoreCase("is" + field.getName()))
                )
                .findFirst();
    }

    private List<Field> getFields(Class<?> clazz) {
        List<Field> fields = new ArrayList<>();
        while (clazz != Object.class) {
            fields.addAll(0, Arrays.asList(clazz.getDeclaredFields()));
            clazz = clazz.getSuperclass();
        }
        return fields;
    }

    private Object applyProcessors(final ConfigFieldProcessor fieldProcessor, final Object value)
            throws InstantiationException, IllegalAccessException {
        Object processedValue = value;
        for (Class<? extends Function<?, ?>> classProcessor : fieldProcessor.value()) {
            Function<Object, Object> processor = (Function<Object, Object>) classProcessor.newInstance();
            processedValue = processor.apply(value);
        }

        return processedValue;
    }
}
