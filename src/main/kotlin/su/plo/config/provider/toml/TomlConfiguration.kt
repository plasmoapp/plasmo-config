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
import java.io.BufferedWriter
import java.io.IOException
import java.io.InputStream
import java.io.InputStreamReader
import java.io.OutputStream
import java.io.OutputStreamWriter
import java.lang.reflect.Field
import java.lang.reflect.ParameterizedType
import java.lang.reflect.Type
import java.nio.charset.StandardCharsets
import java.util.function.Function
import java.util.function.Predicate

class TomlConfiguration : ConfigurationProvider() {

    @Throws(IOException::class)
    override fun load(configClass: Class<*>, inputStream: InputStream, defaultConfiguration: Any): Any {
        require(configClass.isAnnotationPresent(Config::class.java)) { "Class not annotated with @Config" }
        val configAnnotation = configClass.getAnnotation(Config::class.java)

        val toml = Toml().read(InputStreamReader(inputStream, StandardCharsets.UTF_8))

        val configuration = try {
            configClass.getConstructor().newInstance()
        } catch (e: Exception) {
            throw IllegalArgumentException("Failed to initialize configuration class", e)
        }

        fillFromMap(configClass, configuration, toml.toMap(), configAnnotation)
        loadDefaults(configClass, configuration, defaultConfiguration)

        return configuration
    }

    @Throws(IOException::class)
    override fun load(configClass: Class<*>, inputStream: InputStream, defaultConfiguration: InputStream): Any {
        require(configClass.isAnnotationPresent(Config::class.java)) { "Class not annotated with @Config" }
        val configAnnotation = configClass.getAnnotation(Config::class.java)

        val toml = Toml().read(InputStreamReader(inputStream, StandardCharsets.UTF_8))
        val tomlDefaults = Toml().read(InputStreamReader(defaultConfiguration, StandardCharsets.UTF_8))
        val mergedMap = loadDefaults(toml.toMap(), tomlDefaults.toMap())

        val configuration = try {
            configClass.getConstructor().newInstance()
        } catch (e: Exception) {
            throw IllegalArgumentException("Failed to initialize configuration class", e)
        }

        fillFromMap(configClass, configuration, mergedMap, configAnnotation)

        return configuration
    }

    @Throws(IOException::class)
    override fun <T> save(configuration: T, outputStream: OutputStream) {
        val targetClass = configuration!!::class.java
        require(targetClass.isAnnotationPresent(Config::class.java)) { "Class not annotated with @Config" }

        BufferedWriter(
            OutputStreamWriter(
                outputStream,
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

        val configAnnotation = targetClass.getAnnotation(Config::class.java)

        val map: Map<String, Any> = serialized as Map<String, Any>
        fillFromMap(targetClass, targetObject, map, configAnnotation)
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
                @Suppress("UNCHECKED_CAST")
                (value as ConfigEntry<Any?>).setDefault(defaultValue.default)
            }
        }
    }

    private fun serializeClassToMap(
        targetObject: Any
    ): Map<String, Any> {
        val targetClass: Class<*> = targetObject.javaClass
        require(targetClass.isAnnotationPresent(Config::class.java)) { "Class not annotated with @Config" }

        val configAnnotation = targetClass.getAnnotation(Config::class.java)

        val serialized: MutableMap<String, Any> = Maps.newHashMap()

        getFields(targetClass).forEach { field ->
            try {
                if (configAnnotation.loadConfigFieldOnly && !field.isAnnotationPresent(ConfigField::class.java))
                    return@forEach

                val configField = field.getAnnotationOr { ConfigField() }
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
                if (configAnnotation.loadConfigFieldOnly && !field.isAnnotationPresent(ConfigField::class.java))
                    return@forEach

                val configField = field.getAnnotationOr { ConfigField() }
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
                        writeComment(writer, prefix, configField.nullComment, false)
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
                        writeComment(writer, prefix, configField.nullComment, false)
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
        if (map.values.none { it is Map<*, *> || it.javaClass.isAnnotationPresent(Config::class.java) }) {
            writer.write(String.format("%s[%s%s]\n", prefix, parent, key))
        }

        map.forEach { (mapKey, mapValue) ->
            if (mapValue.javaClass.isAnnotationPresent(Config::class.java)) {
                writer.write("$prefix[$parent$key.$mapKey]\n")
                serializeClassToWriter(writer, mapValue.javaClass, mapValue, "$parent$key.", prefix)
                return@forEach
            }

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
    private fun writeComment(writer: BufferedWriter, prefix: String, comment: String, spaceBetween: Boolean = true) {
        val space = if (spaceBetween) " " else ""

        for (line in comment.trimIndent().split("\n")) {
            writer.write("$prefix#$space$line\n")
        }
    }

    private fun fillFromMap(
        configClass: Class<*>,
        configuration: Any,
        map: Map<String, Any>,
        configAnnotation: Config,
    ) {
        getFields(configClass).forEach { field ->
            field.isAccessible = true

            // ConfigEntry is mutable, so we're populating it here instead of convertToFieldValue
            val fieldValue = field.get(configuration)
            if (fieldValue is SerializableConfigEntry) {
                val configField = field.getAnnotationOr { ConfigField() }
                val configPath = getConfigPath(field, configField)
                map[configPath]?.let { configValue ->
                    fieldValue.deserialize(configValue)
                }

                return@forEach
            }

            val value = convertToFieldValue(field, map, configAnnotation) ?: return@forEach
            field.set(configuration, value)
        }
    }

    private fun convertToFieldValue(
        field: Field,
        map: Map<String, Any>,
        configAnnotation: Config,
    ): Any? {
        if (configAnnotation.loadConfigFieldOnly && !field.isAnnotationPresent(ConfigField::class.java)) return null

        val configField = field.getAnnotationOr { ConfigField() }
        val configPath = getConfigPath(field, configField)

        if (!map.containsKey(configPath)) return null

        val fieldType = field.type
        var configValue = map[configPath]!!

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
        if (fieldType.isAnnotationPresent(Config::class.java)) {
            val fieldValue = fieldType.getConstructor().newInstance()
            fillFromMap(
                fieldValue.javaClass,
                fieldValue,
                map[configPath] as Map<String, Any>,
                fieldValue.javaClass.getAnnotation(Config::class.java),
            )

            return fieldValue
        }

        return convertToFieldValue(field.type, configValue, field)
    }

    private fun convertToFieldValue(
        targetClass: Class<*>,
        configValue: Any,
        field: Field? = null,
    ): Any {
        val primitive = convertPrimitives(targetClass, configValue)
        if (primitive != null) return primitive

        if (targetClass.isEnum) {
            val enumClass = targetClass as Class<out Enum<*>>

            return try {
                java.lang.Enum.valueOf(enumClass, configValue as String)
            } catch (_: Exception) {
                throw IllegalArgumentException("Enum $configValue doesn't exists. Available values: ${enumClass.enumConstants.joinToString(", ")}")
            }
        }

        if (configValue is Map<*, *> && field != null) {
            val newMap = LinkedHashMap<Any, Any>()
            val configMap = configValue as Map<Any, Any>

            val pt = field.genericType as ParameterizedType
            val mapValueType = pt.actualTypeArguments.getOrNull(1) as? Class<*>

            if (mapValueType != null && mapValueType.isAnnotationPresent(Config::class.java)) {
                configMap.forEach { (key, value) ->
                    val deserializedValue = mapValueType.getConstructor().newInstance()
                    fillFromMap(
                        mapValueType,
                        deserializedValue,
                        value as Map<String, Any>,
                        mapValueType.getAnnotation(Config::class.java),
                    )
                    newMap[key] = deserializedValue
                }
            } else {
                configMap.forEach { (key, value) ->
                    newMap[key] = convertPrimitives(mapValueType ?: value.javaClass, value) ?: value
                }
            }

            return newMap
        }

        if (configValue is List<*> && field != null) {
            val pt = field.genericType as ParameterizedType
            val listTypeArg = pt.actualTypeArguments.getOrNull(0)
                ?: throw IllegalArgumentException("Cannot determine list element type")

            return convertListValue(configValue, listTypeArg)
        }

        throw IllegalArgumentException("Unsupported field type: $targetClass ($configValue)")
    }

    private fun convertListValue(list: List<*>, typeArg: Type): List<*> {
        return list.map { element ->
            when {
                element == null -> null
                typeArg is ParameterizedType && typeArg.rawType == List::class.java -> {
                    val nestedTypeArg = typeArg.actualTypeArguments.getOrNull(0)
                        ?: throw IllegalArgumentException("Cannot determine nested list element type")
                    convertListValue(element as List<*>, nestedTypeArg)
                }
                typeArg is Class<*> -> convertToFieldValue(typeArg, element)
                else -> element
            }
        }
    }

    private fun convertPrimitives(targetClass: Class<*>, targetObject: Any): Any? {
        return when (targetClass) {
            Char::class.javaPrimitiveType,
            Char::class.javaObjectType -> (targetObject as String)[0]

            Byte::class.javaPrimitiveType,
            Byte::class.javaObjectType -> (targetObject as Long).toByte()

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

            String::class.java -> (targetObject as String)

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

    private inline fun <reified T : Annotation> Field.getAnnotationOr(orValue: () -> T): T {
        if (!isAnnotationPresent(T::class.java)) return orValue()

        return getAnnotation(T::class.java)
    }
}
