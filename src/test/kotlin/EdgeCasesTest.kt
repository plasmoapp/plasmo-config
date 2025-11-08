import org.junit.jupiter.api.assertThrows
import su.plo.config.Config
import su.plo.config.ConfigField
import su.plo.config.provider.ConfigurationProvider
import su.plo.config.provider.toml.TomlConfiguration
import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class EdgeCasesTest {
    private val toml = ConfigurationProvider.getProvider<TomlConfiguration>(TomlConfiguration::class.java)

    @Test
    fun testEnumWithInvalidValue() {
        val tomlContent = """
            status = "INVALID_VALUE"
        """.trimIndent()

        assertThrows<IllegalArgumentException> {
            toml.load<EnumConfig>(
                EnumConfig::class.java,
                tomlContent.byteInputStream()
            )
        }
    }

    @Test
    fun testNullableFields() {
        val config = NullableConfig(
            nullableString = null,
            nullableInt = null,
            nonNullString = "value"
        )

        val output = ByteArrayOutputStream()
        toml.save(config, output)

        val serialized = output.toByteArray().toString(StandardCharsets.UTF_8)

        val loaded = toml.load<NullableConfig>(
            NullableConfig::class.java,
            serialized.byteInputStream()
        )

        assertEquals(null, loaded.nullableString)
        assertEquals(null, loaded.nullableInt)
        assertEquals("value", loaded.nonNullString)
    }

    @Test
    fun testMapWithPrimitiveTypes() {
        val config = MapConfig(
            stringMap = mapOf("key1" to "value1", "key2" to "value2"),
            intMap = mapOf("a" to 1, "b" to 2),
            booleanMap = mapOf("true_key" to true, "false_key" to false)
        )

        val output = ByteArrayOutputStream()
        toml.save(config, output)

        val serialized = output.toByteArray().toString(StandardCharsets.UTF_8)

        val loaded = toml.load<MapConfig>(
            MapConfig::class.java,
            serialized.byteInputStream()
        )

        assertEquals(config.stringMap, loaded.stringMap)
        assertEquals(config.intMap, loaded.intMap)
        assertEquals(config.booleanMap, loaded.booleanMap)
    }

    @Test
    fun testNestedConfigObjects() {
        val config = OuterConfig(
            inner = InnerConfig(
                deepInner = DeepInnerConfig(
                    value = "deep_value"
                )
            )
        )

        val output = ByteArrayOutputStream()
        toml.save(config, output)

        val serialized = output.toByteArray().toString(StandardCharsets.UTF_8)

        val loaded = toml.load<OuterConfig>(
            OuterConfig::class.java,
            serialized.byteInputStream()
        )

        assertEquals("deep_value", loaded.inner.deepInner.value)
    }

    @Test
    fun testFileLoadAndSave() {
        val tempFile = Files.createTempFile("test-config", ".toml").toFile()
        tempFile.deleteOnExit()

        val config = SimpleConfig(text = "test_value", number = 42)

        toml.save(config, tempFile)
        assertTrue(tempFile.exists())
        assertTrue(tempFile.length() > 0)

        val loaded = toml.load<SimpleConfig>(SimpleConfig::class.java, tempFile)
        assertEquals("test_value", loaded.text)
        assertEquals(42, loaded.number)
    }

    @Test
    fun testFileLoadWithDefault() {
        val tempFile = File("non_existent_file_${System.currentTimeMillis()}.toml")
        tempFile.deleteOnExit()

        val loaded = toml.load<SimpleConfig>(SimpleConfig::class.java, tempFile, true)

        assertNotNull(loaded)
        assertEquals("default", loaded.text)
        assertEquals(0, loaded.number)
        assertTrue(tempFile.exists())
    }

    @Test
    fun testLoadWithoutConfigAnnotation() {
        assertThrows<IllegalArgumentException> {
            toml.save(NotAConfig(), ByteArrayOutputStream())
        }
    }

    @Test
    fun testEmptyMaps() {
        val config = MapConfig(
            stringMap = emptyMap(),
            intMap = emptyMap(),
            booleanMap = emptyMap()
        )

        val output = ByteArrayOutputStream()
        toml.save(config, output)

        val loaded = toml.load<MapConfig>(
            MapConfig::class.java,
            output.toByteArray().toString(StandardCharsets.UTF_8).byteInputStream()
        )

        assertEquals(emptyMap(), loaded.stringMap)
        assertEquals(emptyMap(), loaded.intMap)
        assertEquals(emptyMap(), loaded.booleanMap)
    }

    @Test
    fun testPrimitiveTypes() {
        val config = PrimitiveTypesConfig(
            byteVal = 127,
            shortVal = 32767,
            intVal = 2147483647,
            longVal = 9223372036854775807L,
            floatVal = 3.14f,
            doubleVal = 3.14159265359,
            boolVal = true,
            charVal = 'A'
        )

        val output = ByteArrayOutputStream()
        toml.save(config, output)

        val loaded = toml.load<PrimitiveTypesConfig>(
            PrimitiveTypesConfig::class.java,
            output.toByteArray().toString(StandardCharsets.UTF_8).byteInputStream()
        )

        assertEquals(config.byteVal, loaded.byteVal)
        assertEquals(config.shortVal, loaded.shortVal)
        assertEquals(config.intVal, loaded.intVal)
        assertEquals(config.longVal, loaded.longVal)
        assertEquals(config.floatVal, loaded.floatVal)
        assertEquals(config.doubleVal, loaded.doubleVal)
        assertEquals(config.boolVal, loaded.boolVal)
    }

    @Test
    fun testMultilineComments() {
        val config = MultilineCommentConfig()

        val output = ByteArrayOutputStream()
        toml.save(config, output)

        val serialized = output.toByteArray().toString(StandardCharsets.UTF_8)

        assertTrue(serialized.contains("# Line 1"))
        assertTrue(serialized.contains("# Line 2"))
        assertTrue(serialized.contains("# Line 3"))
    }

    @Test
    fun testEmptyConfig() {
        val config = EmptyConfig()

        val output = ByteArrayOutputStream()
        toml.save(config, output)

        val serialized = output.toByteArray().toString(StandardCharsets.UTF_8)

        assertTrue(serialized.isEmpty())
    }
}

@Config
data class EnumConfig(
    @ConfigField
    val status: Status = Status.ACTIVE
)

enum class Status {
    ACTIVE, INACTIVE, PENDING
}

@Config
data class NullableConfig(
    @ConfigField
    val nullableString: String? = null,

    @ConfigField
    val nullableInt: Int? = null,

    @ConfigField
    val nonNullString: String = "default"
)

@Config
data class MapConfig(
    @ConfigField
    val stringMap: Map<String, String> = emptyMap(),

    @ConfigField
    val intMap: Map<String, Int> = emptyMap(),

    @ConfigField
    val booleanMap: Map<String, Boolean> = emptyMap()
)

@Config
data class OuterConfig(
    @ConfigField
    val inner: InnerConfig = InnerConfig()
)

@Config
data class InnerConfig(
    @ConfigField
    val deepInner: DeepInnerConfig = DeepInnerConfig()
)

@Config
data class DeepInnerConfig(
    @ConfigField
    val value: String = "default"
)

@Config
data class SimpleConfig(
    @ConfigField
    val text: String = "default",

    @ConfigField
    val number: Int = 0
)

class NotAConfig {
    val field = "value"
}

@Config
data class PrimitiveTypesConfig(
    @ConfigField
    val byteVal: Byte = 0,

    @ConfigField
    val shortVal: Short = 0,

    @ConfigField
    val intVal: Int = 0,

    @ConfigField
    val longVal: Long = 0L,

    @ConfigField
    val floatVal: Float = 0.0f,

    @ConfigField
    val doubleVal: Double = 0.0,

    @ConfigField
    val boolVal: Boolean = false,

    @ConfigField
    val charVal: Char = 'X'
)

@Config
data class MultilineCommentConfig(
    @ConfigField(comment = """
        Line 1
        Line 2
        Line 3
    """)
    val field: String = "value"
)

@Config
data class EmptyConfig(
    @ConfigField
    private val map: Map<String, String> = mapOf()
)
