import org.junit.jupiter.api.assertDoesNotThrow
import org.junit.jupiter.api.assertThrows
import su.plo.config.provider.ConfigurationProvider
import su.plo.config.provider.toml.TomlConfiguration
import kotlin.test.Test
import kotlin.test.assertNotNull

class ServiceLoaderTest {

    @Test
    fun testGetProviderWithDefaultClassLoader() {
        val provider = ConfigurationProvider.getProvider<TomlConfiguration>(TomlConfiguration::class.java)

        assertNotNull(provider)
    }

    @Test
    fun testGetProviderWithCustomClassLoader() {
        val classLoader = Thread.currentThread().contextClassLoader
        assertDoesNotThrow {
            ConfigurationProvider.getProvider<TomlConfiguration>(
                TomlConfiguration::class.java,
                classLoader
            )
        }
    }

    @Test
    fun testGetProviderNotFound() {
        assertThrows<IllegalStateException> {
            ConfigurationProvider.getProvider<FakeProvider>(
                FakeProvider::class.java
            )
        }
    }

    abstract class FakeProvider : ConfigurationProvider()
}
