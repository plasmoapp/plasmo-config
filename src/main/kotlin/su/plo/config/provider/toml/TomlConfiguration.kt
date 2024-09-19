package su.plo.config.provider.toml

import com.google.common.base.CaseFormat
import com.google.common.base.Charsets
import com.google.common.collect.Maps
import com.moandjiezana.toml.Toml
import com.moandjiezana.toml.TomlWriter
import su.plo.config.Config
import su.plo.config.ConfigField
import su.plo.config.ConfigFieldProcessor
import su.plo.config.ConfigValidator
import su.plo.config.entry.ConfigEntry
import su.plo.config.entry.SerializableConfigEntry
import su.plo.config.provider.ConfigurationProvider
import java.io.*
import java.lang.reflect.Field
import java.lang.reflect.ParameterizedType
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Paths
import java.util.*
import java.util.function.Function
import java.util.function.Predicate

class TomlConfiguration : ConfigurationProvider() {

    @Throws(IOException::class)
    override fun load(configClass: Class<*>, inputStream: InputStream, defaultConfiguration: Any): Any {
        val toml = Toml().read(InputStreamReader(inputStream, Charsets.UTF_8))

        val configuration = try {
            configClass.getConstructor().newInstance()
        } catch (e: Exception) {
            throw IllegalArgumentException("Failed to initialize configuration class", e)
        }

        deserializeFromMap(configClass, configuration, toml.toMap())
        loadDefaults(configClass, configuration, defaultConfiguration)

        return configuration
    }

    @Throws(IOException::class)
    override fun load(configClass: Class<*>, inputStream: InputStream, defaultConfiguration: InputStream): Any {
        val toml = Toml().read(InputStreamReader(inputStream, Charsets.UTF_8))
        val tomlDefaults = Toml().read(InputStreamReader(defaultConfiguration, Charsets.UTF_8))
        val mergedMap = loadDefaults(toml.toMap(), tomlDefaults.toMap())

        val configuration = try {
            configClass.getConstructor().newInstance()
        } catch (e: Exception) {
            throw IllegalArgumentException("Failed to initialize configuration class", e)
        }

        deserializeFromMap(configClass, configuration, mergedMap)

        return configuration
    }

    @Throws(IOException::class)
    override fun save(targetClass: Class<*>, configuration: Any, file: File) {
        require(targetClass.isAnnotationPresent(Config::class.java)) { "Class not annotated with @Config" }

        if (file.parentFile != null && !file.parentFile.exists()) {
            file.parentFile.mkdirs()
        }

        BufferedWriter(
            OutputStreamWriter(
                Files.newOutputStream(Paths.get(file.absolutePath)),
                StandardCharsets.UTF_8
            )
        ).use { writer ->
            serializeClassToWriter(
                writer,
                targetClass,
                configuration
            )
        }
    }

    override fun serialize(targetObject: Any): Any {
        if (targetObject is SerializableConfigEntry) {
            return targetObject.serialize()
        }

        return serializeClassToMap(targetObject)
    }

    override fun deserialize(targetObject: Any, serialized: Any) {
        if (targetObject is SerializableConfigEntry) {
            return targetObject.deserialize(serialized)
        }

        val targetClass = targetObject.javaClass
        require(targetClass.isAnnotationPresent(Config::class.java)) { "Class not annotated with @Config" }

        val map: Map<String, Any> = serialized as Map<String, Any>
        deserializeFromMap(targetClass, targetObject, map)
    }

    private fun loadDefaults(map: MutableMap<String, Any>, defaults: Map<String, Any>): MutableMap<String, Any> {
        defaults.forEach { (key, value) ->
            if (!map.containsKey(key)) {
                map[key] = value
                return@forEach
            }

            if (value is Map<*, *>) {
                loadDefaults(
                    map[key] as MutableMap<String, Any>,
                    value as Map<String, Any>
                )
            }
        }

        return map
    }

    private fun loadDefaults(configClass: Class<*>, configuration: Any, defaultConfiguration: Any) {
        getFields(configClass).forEach { field ->
            try {
                if (!field.isAnnotationPresent(ConfigField::class.java)) return@forEach

                field.isAccessible = true

                val fieldType = field.type

                val value = field.get(configuration)
                val defaultValue = field.get(defaultConfiguration)

                if (value == null || defaultValue == null) return@forEach

                if (fieldType.isAnnotationPresent(Config::class.java)) {
                    loadDefaults(fieldType, value, defaultValue)
                    return@forEach
                }

                if (value is ConfigEntry<*> && defaultValue is ConfigEntry<*>) {
                    value.default = defaultValue.default
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    private fun serializeClassToMap(
        targetObject: Any
    ): Map<String, Any> {
        val targetClass: Class<*> = targetObject.javaClass
        require(targetClass.isAnnotationPresent(Config::class.java)) { "Class not annotated with @Config" }

        val serialized: MutableMap<String, Any> = Maps.newHashMap()

        getFields(targetClass).forEach { field ->
            try {
                if (!field.isAnnotationPresent(ConfigField::class.java)) return@forEach

                val configField = field.getAnnotation(ConfigField::class.java)
                val configPath = getConfigPath(field, configField)

                field.isAccessible = true

                val fieldValue = field.get(targetObject) ?: return@forEach

                // ignore default ConfigEntry
                if (fieldValue is ConfigEntry<*> &&
                    configField.ignoreDefault &&
                    fieldValue.isDefault
                ) return@forEach

                if (fieldValue.javaClass.isAnnotationPresent(Config::class.java)) {
                    serialized[configPath] = serialize(fieldValue)
                } else if (fieldValue is SerializableConfigEntry) {
                    serialized[configPath] = fieldValue.serialize()
                } else {
                    serialized[configPath] = fieldValue
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        return serialized
    }

    @Throws(IOException::class)
    private fun serializeClassToWriter(
        writer: BufferedWriter,
        configClass: Class<*>,
        configuration: Any,
        parent: String = "",
        prefix: String = ""
    ) {

        val configAnnotation = configClass.getAnnotation(Config::class.java)

        if (configAnnotation.comment.isNotEmpty()) {
            this.writeComment(writer, prefix, configAnnotation.comment)
        }

        getFields(configClass).forEach { field ->
            try {
                if (!field.isAnnotationPresent(ConfigField::class.java)) return@forEach

                val configField = field.getAnnotation(ConfigField::class.java)
                val configPath = getConfigPath(field, configField)

                field.isAccessible = true

                var fieldValue = field.get(configuration) ?: run {
                    if (configField.nullComment.isNotEmpty()) {
                        if (field.type.isAnnotationPresent(Config::class.java) ||
                            Map::class.java.isAssignableFrom(field.type)
                        ) {
                            writer.newLine()
                        }

                        if (configField.comment.isNotEmpty())
                            writeComment(writer, prefix, configField.comment)
                        writeComment(writer, prefix, configField.nullComment)
                    }

                    return@forEach
                }

                // ignore default ConfigEntry
                if (fieldValue is ConfigEntry<*> &&
                    configField.ignoreDefault &&
                    fieldValue.isDefault
                ) return@forEach

                if (fieldValue is SerializableConfigEntry) {
                    fieldValue = fieldValue.serialize()
                }

                // # comment
                // key: value
                if (fieldValue.javaClass.isAnnotationPresent(Config::class.java) || fieldValue is Map<*, *>) {
                    writer.newLine()
                }

                if (configField.comment.isNotEmpty()) {
                    writeComment(writer, prefix, configField.comment)
                }

                if (fieldValue.javaClass.isAnnotationPresent(Config::class.java)) {
                    writer.write(String.format("%s[%s%s]\n", prefix, parent, configPath))
                    serializeClassToWriter(writer, fieldValue.javaClass, fieldValue, "$parent$configPath.", prefix)
                    return@forEach
                }

                if (fieldValue is Map<*, *>) {
                    if (configField.nullComment.isNotEmpty() && fieldValue.isEmpty()) {
                        if (configField.comment.isNotEmpty())
                            writeComment(writer, prefix, configField.comment)
                        writeComment(writer, prefix, configField.nullComment)
                    } else {
                        writeMap(writer, prefix, parent, configPath, fieldValue as Map<String, Any>)
                    }
                    return@forEach
                }

                writeValue(writer, prefix, configPath, fieldValue)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    @Throws(IOException::class)
    private fun writeMap(writer: BufferedWriter, prefix: String, parent: String, key: String, map: Map<String, Any>) {
        if (!map.values.stream().allMatch { value -> value is Map<*, *> }) {
            writer.write(String.format("%s[%s%s]\n", prefix, parent, key))
        }

        map.forEach { (mapKey, mapValue) ->
            if (mapValue is Map<*, *>) {
                writeMap(writer, prefix, "$parent$key.", mapKey, mapValue as Map<String, Any>)
                return@forEach
            }

            writeValue(writer, prefix, mapKey, mapValue)
        }
    }

    @Throws(IOException::class)
    private fun writeValue(writer: BufferedWriter, prefix: String, key: String, value: Any) {
        val data: MutableMap<String, Any> = LinkedHashMap()
        data[key] = value

        if (prefix.isNotEmpty()) writer.write(prefix)
        TomlWriter().write(data, writer)
    }

    @Throws(IOException::class)
    private fun writeComment(writer: BufferedWriter, prefix: String, comment: String) {
        for (line in comment.trimIndent().split("\n")) {
            writer.write("$prefix# $line\n")
        }
    }

    private fun deserializeFromMap(configClass: Class<*>, configuration: Any, map: Map<String, Any>) {
        getFields(configClass).forEach {
            try {
                deserializeField(it, map, configuration)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    private fun deserializeField(field: Field, map: Map<String, Any>, configuration: Any) {
        if (!field.isAnnotationPresent(ConfigField::class.java)) return

        val configField = field.getAnnotation(ConfigField::class.java)
        val configPath = getConfigPath(field, configField)

        if (!map.containsKey(configPath)) return

        field.isAccessible = true

        var fieldValue: Any? = field.get(configuration)
        val fieldType = field.type
        var configValue = map[configPath]!!

        if (fieldValue == null && fieldType.isAnnotationPresent(Config::class.java)) {
            fieldValue = fieldType.getConstructor().newInstance()
            field.set(configuration, fieldValue)
        }

        // validator with @ConfigValidator
        if (field.isAnnotationPresent(ConfigValidator::class.java)) {
            val configValidator = field.getAnnotation(ConfigValidator::class.java)
            val validator = configValidator.value.java.getConstructor().newInstance() as Predicate<Any>

            require(validator.test(configValue)) {
                "$configPath is invalid. Current value: $configValue. Allowed values: ${configValidator.allowed.contentToString()}"
            }
        }

        // apply @ConfigFieldProcessor
        if (field.isAnnotationPresent(ConfigFieldProcessor::class.java)) {
            configValue = applyProcessors(field.getAnnotation(ConfigFieldProcessor::class.java), configValue)
        }

        // load nested map
        if (fieldValue != null && fieldValue.javaClass.isAnnotationPresent(Config::class.java)) {
            deserializeFromMap(
                fieldValue.javaClass,
                fieldValue,
                map[configPath] as Map<String, Any>
            )
            return
        }

        // load SerializableConfigEntry
        if (fieldValue is SerializableConfigEntry) {
            val configEntry = fieldValue
            configEntry.deserialize(configValue)
            return
        }

        // primitive types
        convertPrimitives(fieldType, configValue)?.let {
            return field.set(configuration, it)
        }

        if (fieldType.isEnum) {
            val enumClass = fieldType as Class<out Enum<*>>

            try {
                field.set(configuration, java.lang.Enum.valueOf(enumClass, configValue as String))
            } catch (ignored: java.lang.Exception) {
                field.set(configuration, enumClass.enumConstants[0])
            }

            return
        }

        if (Map::class.java.isAssignableFrom(fieldType) &&
            fieldValue != null
        ) {

            val fieldMap = fieldValue as MutableMap<Any, Any>
            val configMap = configValue as Map<Any, Any>

            val pt = field.genericType as ParameterizedType
            val mapValueType = pt.actualTypeArguments.getOrNull(1) as? Class<*>


            configMap.forEach { (key, value) ->
                fieldMap[key] = convertPrimitives(mapValueType ?: value.javaClass, value) ?: value
            }

            return
        }

        field.set(configuration, fieldType.cast(configValue))
    }

    private fun convertPrimitives(targetClass: Class<*>, targetObject: Any): Any? {
        return when (targetClass) {
            Int::class.javaPrimitiveType,
            Int::class.javaObjectType -> (targetObject as Long).toInt()

            Short::class.javaPrimitiveType,
            Short::class.javaObjectType -> (targetObject as Long).toShort()

            Long::class.javaPrimitiveType,
            Long::class.javaObjectType -> (targetObject as Long).toLong()

            Double::class.javaPrimitiveType,
            Double::class.javaObjectType -> (targetObject as Double)

            Float::class.javaPrimitiveType,
            Float::class.javaObjectType -> (targetObject as Double).toFloat()

            Boolean::class.javaPrimitiveType,
            Boolean::class.javaObjectType -> (targetObject as Boolean)

            else -> null
        }
    }

    private fun getConfigPath(field: Field, configField: ConfigField) =
        configField.path.ifEmpty {
            CaseFormat.LOWER_CAMEL.to(
                CaseFormat.LOWER_UNDERSCORE,
                field.name
            )
        }

    private fun getFields(targetClass: Class<*>): List<Field> {
        var clazz = targetClass
        val fields: MutableList<Field> = ArrayList()
        while (clazz != Any::class.java) {
            fields.addAll(0, listOf(*clazz.declaredFields))
            clazz = clazz.superclass
        }

        return fields.sortedBy { field ->
            val fieldType = field.type

            Map::class.java.isAssignableFrom(fieldType) || fieldType.isAnnotationPresent(Config::class.java)
        }
    }

    @Throws(Exception::class)
    private fun applyProcessors(fieldProcessor: ConfigFieldProcessor, value: Any): Any {
        var processedValue = value
        for (classProcessor in fieldProcessor.value) {
            val processor = classProcessor.java.getConstructor().newInstance() as Function<Any, Any>
            processedValue = processor.apply(value)
        }
        return processedValue
    }
}
