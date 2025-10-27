import su.plo.config.Config
import su.plo.config.provider.ConfigurationProvider
import su.plo.config.provider.toml.TomlConfiguration
import java.io.ByteArrayOutputStream
import java.nio.charset.StandardCharsets
import kotlin.test.Test

class TestDataClassConfig {
    private val toml = ConfigurationProvider.getProvider<TomlConfiguration>(TomlConfiguration::class.java)

    @Test
    fun saveConfig() {
        val byteArrayOutputStream = ByteArrayOutputStream()
        toml.save(DataClassConfig(), byteArrayOutputStream)

        val serializedString = byteArrayOutputStream.toByteArray().toString(StandardCharsets.UTF_8)
        println(serializedString)
    }

    @Test
    fun loadConfig() {
        val serializedConfig = """
            string = "New String"
        """.trimIndent()

        val testConfig = toml.load<DataClassConfig>(DataClassConfig::class.java, serializedConfig.byteInputStream())
        println(testConfig)
    }
}

@Config(
    loadConfigFieldOnly = false,
)
data class DataClassConfig(
    val string: String = "String",
)
