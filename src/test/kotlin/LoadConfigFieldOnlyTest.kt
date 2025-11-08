import su.plo.config.Config
import su.plo.config.ConfigField
import su.plo.config.provider.ConfigurationProvider
import su.plo.config.provider.toml.TomlConfiguration
import java.io.ByteArrayOutputStream
import java.nio.charset.StandardCharsets
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class LoadConfigFieldOnlyTest {
    private val toml = ConfigurationProvider.getProvider<TomlConfiguration>(TomlConfiguration::class.java)

    @Test
    fun testLoadConfigFieldOnlyTrue() {
        val config = ConfigFieldOnlyTrueConfig()

        val output = ByteArrayOutputStream()
        toml.save(config, output)

        val serialized = output.toByteArray().toString(StandardCharsets.UTF_8)

        assertTrue(serialized.contains("with_annotation = \"annotated\""))
        assertFalse(serialized.contains("without_annotation"))
        assertFalse(serialized.contains("unannotated"))
    }

    @Test
    fun testLoadConfigFieldOnlyFalse() {
        val config = ConfigFieldOnlyFalseConfig()

        val output = ByteArrayOutputStream()
        toml.save(config, output)

        val serialized = output.toByteArray().toString(StandardCharsets.UTF_8)

        assertTrue(serialized.contains("with_annotation = \"annotated\""))
        assertTrue(serialized.contains("without_annotation = \"unannotated\""))
    }

    @Test
    fun testLoadConfigFieldOnlyTrueDeserialization() {
        val tomlContent = """
            with_annotation = "custom_value"
            without_annotation = "should_be_ignored"
        """.trimIndent()

        val config = toml.load<ConfigFieldOnlyTrueConfig>(
            ConfigFieldOnlyTrueConfig::class.java,
            tomlContent.byteInputStream()
        )

        assertEquals("custom_value", config.withAnnotation)
        assertEquals("unannotated", config.withoutAnnotation)
    }

    @Test
    fun testLoadConfigFieldOnlyFalseDeserialization() {
        val tomlContent = """
            with_annotation = "custom_value"
            without_annotation = "custom_unannotated"
        """.trimIndent()

        val config = toml.load<ConfigFieldOnlyFalseConfig>(
            ConfigFieldOnlyFalseConfig::class.java,
            tomlContent.byteInputStream()
        )

        assertEquals("custom_value", config.withAnnotation)
        assertEquals("custom_unannotated", config.withoutAnnotation)
    }

    @Test
    fun testDefaultBehaviorIsTrue() {
        val config = DefaultBehaviorConfig()

        val output = ByteArrayOutputStream()
        toml.save(config, output)

        val serialized = output.toByteArray().toString(StandardCharsets.UTF_8)

        assertTrue(serialized.contains("annotated_field = \"value\""))
        assertFalse(serialized.contains("unannotated_field"))
    }

    @Test
    fun testMixedFieldsInNestedObjects() {
        val config = ParentConfig()

        val output = ByteArrayOutputStream()
        toml.save(config, output)

        val serialized = output.toByteArray().toString(StandardCharsets.UTF_8)

        assertTrue(serialized.contains("[nested]"))
        assertTrue(serialized.contains("with_annotation = \"annotated\""))
        assertFalse(serialized.contains("without_annotation"))
    }
}

@Config(loadConfigFieldOnly = true)
data class ConfigFieldOnlyTrueConfig(
    @ConfigField
    val withAnnotation: String = "annotated",

    val withoutAnnotation: String = "unannotated"
)

@Config(loadConfigFieldOnly = false)
data class ConfigFieldOnlyFalseConfig(
    @ConfigField
    val withAnnotation: String = "annotated",

    val withoutAnnotation: String = "unannotated"
)

@Config
data class DefaultBehaviorConfig(
    @ConfigField
    val annotatedField: String = "value",

    val unannotatedField: String = "should_not_serialize"
)

@Config
data class ParentConfig(
    @ConfigField
    val nested: ConfigFieldOnlyTrueConfig = ConfigFieldOnlyTrueConfig()
)
