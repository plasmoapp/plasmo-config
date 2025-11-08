import su.plo.config.Config
import su.plo.config.ConfigField
import su.plo.config.provider.ConfigurationProvider
import su.plo.config.provider.toml.TomlConfiguration
import java.io.ByteArrayOutputStream
import java.nio.charset.StandardCharsets
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ListMappingTest {
    private val toml = ConfigurationProvider.getProvider<TomlConfiguration>(TomlConfiguration::class.java)

    @Test
    fun testSimpleListSerialization() {
        val config = SimpleListConfig(
            strings = listOf("one", "two", "three"),
            integers = listOf(1, 2, 3),
            booleans = listOf(true, false, true),
            javaIntegers = listOf(1, 2, 3).map { it.javaInt() },
        )

        val output = ByteArrayOutputStream()
        toml.save(config, output)

        val serialized = output.toByteArray().toString(StandardCharsets.UTF_8)

        assertTrue(serialized.contains("""strings = ["one", "two", "three"]"""))
        assertTrue(serialized.contains("""integers = [1, 2, 3]"""))
        assertTrue(serialized.contains("""booleans = [true, false, true]"""))
        assertTrue(serialized.contains("""java_integers = [1, 2, 3]"""))
    }

    @Test
    fun testSimpleListDeserialization() {
        val tomlContent = """
            strings = ["one", "two", "three"]
            integers = [1, 2, 3]
            booleans = [true, false, true]
            java_integers = [1, 2, 3]
        """.trimIndent()

        val config = toml.load<SimpleListConfig>(
            SimpleListConfig::class.java,
            tomlContent.byteInputStream()
        )

        assertEquals(listOf("one", "two", "three"), config.strings)
        assertEquals(listOf(1, 2, 3), config.integers)
        assertEquals(listOf(true, false, true), config.booleans)
        assertEquals(listOf(1, 2, 3).map { it.javaInt() }, config.javaIntegers)
    }

    @Test
    fun testNestedListSerialization() {
        val config = NestedListConfig(
            nestedStrings = listOf(
                listOf("a", "b"),
                listOf("c", "d", "e")
            ),
            nestedIntegers = listOf(
                listOf(1, 2),
                listOf(3, 4, 5)
            )
        )

        val output = ByteArrayOutputStream()
        toml.save(config, output)

        val serialized = output.toByteArray().toString(StandardCharsets.UTF_8)

        assertTrue(serialized.contains("""nested_strings = [["a", "b"], ["c", "d", "e"]]"""))
        assertTrue(serialized.contains("""nested_integers = [[1, 2], [3, 4, 5]]"""))
    }

    @Test
    fun testNestedListDeserialization() {
        val tomlContent = """
            nested_strings = [["a", "b"], ["c", "d", "e"]]
            nested_integers = [[1, 2], [3, 4, 5]]
        """.trimIndent()

        val config = toml.load<NestedListConfig>(
            NestedListConfig::class.java,
            tomlContent.byteInputStream()
        )

        assertEquals(listOf(listOf("a", "b"), listOf("c", "d", "e")), config.nestedStrings)
        assertEquals(listOf(listOf(1, 2), listOf(3, 4, 5)), config.nestedIntegers)
    }

    @Test
    fun testEmptyLists() {
        val config = SimpleListConfig(
            strings = emptyList(),
            integers = emptyList(),
            booleans = emptyList()
        )

        val output = ByteArrayOutputStream()
        toml.save(config, output)

        val serialized = output.toByteArray().toString(StandardCharsets.UTF_8)

        val loaded = toml.load<SimpleListConfig>(
            SimpleListConfig::class.java,
            serialized.byteInputStream()
        )

        assertEquals(emptyList(), loaded.strings)
        assertEquals(emptyList(), loaded.integers)
        assertEquals(emptyList(), loaded.booleans)
    }

    @Test
    fun testEnumList() {
        val config = EnumListConfig(
            enums = listOf(TestEnum.FIRST, TestEnum.SECOND, TestEnum.THIRD)
        )

        val output = ByteArrayOutputStream()
        toml.save(config, output)

        val serialized = output.toByteArray().toString(StandardCharsets.UTF_8)

        val loaded = toml.load<EnumListConfig>(
            EnumListConfig::class.java,
            serialized.byteInputStream()
        )

        assertEquals(listOf(TestEnum.FIRST, TestEnum.SECOND, TestEnum.THIRD), loaded.enums)
    }

    private fun Int.javaInt(): Integer = Integer.valueOf(this) as Integer
}

@Config
data class SimpleListConfig(
    @ConfigField
    val strings: List<String> = emptyList(),

    @ConfigField
    val integers: List<Int> = emptyList(),

    @ConfigField
    val booleans: List<Boolean> = emptyList(),

    @ConfigField
    val javaIntegers: List<Integer> = emptyList()
)

@Config
data class NestedListConfig(
    @ConfigField
    val nestedStrings: List<List<String>> = emptyList(),

    @ConfigField
    val nestedIntegers: List<List<Int>> = emptyList()
)

@Config
data class EnumListConfig(
    @ConfigField
    val enums: List<TestEnum> = emptyList()
)

enum class TestEnum {
    FIRST, SECOND, THIRD
}
