import su.plo.config.Config
import su.plo.config.ConfigField
import su.plo.config.entry.BooleanConfigEntry
import su.plo.config.entry.DoubleConfigEntry
import su.plo.config.entry.EnumConfigEntry
import su.plo.config.entry.IntConfigEntry
import su.plo.config.provider.ConfigurationProvider
import su.plo.config.provider.toml.TomlConfiguration
import java.io.ByteArrayOutputStream
import java.nio.charset.StandardCharsets
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ConfigEntryTest {
    private val toml = ConfigurationProvider.getProvider<TomlConfiguration>(TomlConfiguration::class.java)

    @Test
    fun testIntConfigEntry() {
        val config = IntEntryConfig()

        val output = ByteArrayOutputStream()
        toml.save(config, output)

        val serialized = output.toByteArray().toString(StandardCharsets.UTF_8)

        assertTrue(serialized.contains("port = 8080"))
        assertTrue(serialized.contains("bounded_value = 50"))
    }

    @Test
    fun testIntConfigEntryDeserialization() {
        val tomlContent = """
            port = 3000
            bounded_value = 75
        """.trimIndent()

        val config = toml.load<IntEntryConfig>(
            IntEntryConfig::class.java,
            tomlContent.byteInputStream()
        )

        assertEquals(3000, config.port.value())
        assertEquals(75, config.boundedValue.value())
    }

    @Test
    fun testIntConfigEntryBounds() {
        val tomlContent = """
            bounded_value = 999
        """.trimIndent()

        val config = toml.load<IntEntryConfig>(
            IntEntryConfig::class.java,
            tomlContent.byteInputStream()
        )

        assertEquals(100, config.boundedValue.value())
    }

    @Test
    fun testBooleanConfigEntry() {
        val config = BooleanEntryConfig()

        val output = ByteArrayOutputStream()
        toml.save(config, output)

        val serialized = output.toByteArray().toString(StandardCharsets.UTF_8)

        assertTrue(serialized.contains("enabled = true"))
        assertTrue(serialized.contains("disabled = false"))
    }

    @Test
    fun testBooleanConfigEntryDeserialization() {
        val tomlContent = """
            enabled = false
            disabled = true
        """.trimIndent()

        val config = toml.load<BooleanEntryConfig>(
            BooleanEntryConfig::class.java,
            tomlContent.byteInputStream()
        )

        assertEquals(false, config.enabled.value())
        assertEquals(true, config.disabled.value())
    }

    @Test
    fun testDoubleConfigEntry() {
        val config = DoubleEntryConfig()

        val output = ByteArrayOutputStream()
        toml.save(config, output)

        val serialized = output.toByteArray().toString(StandardCharsets.UTF_8)

        assertTrue(serialized.contains("ratio = 0.5"))
    }

    @Test
    fun testDoubleConfigEntryDeserialization() {
        val tomlContent = """
            ratio = 0.75
        """.trimIndent()

        val config = toml.load<DoubleEntryConfig>(
            DoubleEntryConfig::class.java,
            tomlContent.byteInputStream()
        )

        assertEquals(0.75, config.ratio.value())
    }

    @Test
    fun testEnumConfigEntry() {
        val config = EnumEntryConfig()

        val output = ByteArrayOutputStream()
        toml.save(config, output)

        val serialized = output.toByteArray().toString(StandardCharsets.UTF_8)

        assertTrue(serialized.contains("mode = \"DEVELOPMENT\""))
    }

    @Test
    fun testEnumWithInvalidValue() {
        val tomlContent = """
            mode = "INVALID_VALUE"
        """.trimIndent()

        val config = toml.load<EnumEntryConfig>(
            EnumEntryConfig::class.java,
            tomlContent.byteInputStream()
        )

        assertEquals(Mode.DEVELOPMENT, config.mode.value())
    }

    @Test
    fun testEnumConfigEntryDeserialization() {
        val tomlContent = """
            mode = "PRODUCTION"
        """.trimIndent()

        val config = toml.load<EnumEntryConfig>(
            EnumEntryConfig::class.java,
            tomlContent.byteInputStream()
        )

        assertEquals(Mode.PRODUCTION, config.mode.value())
    }

    @Test
    fun testConfigEntryDefaults() {
        val config = IntEntryConfig()

        assertEquals(8080, config.port.default)
        assertTrue(config.port.isDefault)

        config.port.set(3000)
        assertEquals(8080, config.port.default)
        assertEquals(3000, config.port.value())
        assertTrue(!config.port.isDefault)

        config.port.reset()
        assertEquals(8080, config.port.value())
        assertTrue(config.port.isDefault)
    }

    @Test
    fun testConfigEntryChangeListener() {
        val config = IntEntryConfig()
        var listenerCalled = false
        var newValue: Int? = null

        config.port.addChangeListener { value ->
            listenerCalled = true
            newValue = value
        }

        config.port.set(9000)

        assertTrue(listenerCalled)
        assertEquals(9000, newValue)
    }

    @Test
    fun testIntConfigEntryIncrementDecrement() {
        val config = IntEntryConfig()

        config.boundedValue.set(50)
        config.boundedValue.increment()
        assertEquals(51, config.boundedValue.value())

        config.boundedValue.decrement()
        assertEquals(50, config.boundedValue.value())

        config.boundedValue.set(100)
        config.boundedValue.increment()
        assertEquals(1, config.boundedValue.value())

        config.boundedValue.set(1)
        config.boundedValue.decrement()
        assertEquals(100, config.boundedValue.value())
    }

    @Test
    fun testConfigEntryRoundTrip() {
        val originalConfig = IntEntryConfig()
        originalConfig.port.set(5000)
        originalConfig.boundedValue.set(75)

        val output = ByteArrayOutputStream()
        toml.save(originalConfig, output)

        val loadedConfig = toml.load<IntEntryConfig>(
            IntEntryConfig::class.java,
            output.toByteArray().toString(StandardCharsets.UTF_8).byteInputStream()
        )

        assertEquals(5000, loadedConfig.port.value())
        assertEquals(75, loadedConfig.boundedValue.value())
    }

    @Test
    fun testLongToIntDeserialization() {
        val tomlContent = """
            port = 9999999
        """.trimIndent()

        val config = toml.load<IntEntryConfig>(
            IntEntryConfig::class.java,
            tomlContent.byteInputStream()
        )

        assertEquals(9999999, config.port.value())
    }
}

@Config
class IntEntryConfig {
    @ConfigField
    val port = IntConfigEntry(8080, 0, 0)

    @ConfigField
    val boundedValue = IntConfigEntry(50, 1, 100)
}

@Config
class BooleanEntryConfig {
    @ConfigField
    val enabled = BooleanConfigEntry(true)

    @ConfigField
    val disabled = BooleanConfigEntry(false)
}

@Config
class DoubleEntryConfig {
    @ConfigField
    val ratio = DoubleConfigEntry(0.5, 0.0, 1.0)
}

@Config
class EnumEntryConfig {
    @ConfigField
    val mode = EnumConfigEntry(Mode::class.java, Mode.DEVELOPMENT)
}

enum class Mode {
    DEVELOPMENT,
    PRODUCTION,
}
