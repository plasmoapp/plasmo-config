import su.plo.config.Config
import su.plo.config.ConfigField
import su.plo.config.provider.ConfigurationProvider
import su.plo.config.provider.toml.TomlConfiguration
import java.io.ByteArrayOutputStream
import java.nio.charset.StandardCharsets
import kotlin.test.Test
import kotlin.test.assertEquals

class TestKtConfig {
    private val toml = ConfigurationProvider.getProvider<TomlConfiguration>(TomlConfiguration::class.java)

    @Test
    fun saveTest() {
        val byteArrayOutputStream = ByteArrayOutputStream()
        toml.save(TestConfig(), byteArrayOutputStream)

        val serializedString = byteArrayOutputStream.toByteArray().toString(StandardCharsets.UTF_8)

        assertEquals(
            """
                # 1
                # 2
                # 3
                #     4
                boolean_with_comment = false
                short = 65
                # Pepega
                string_with_comment = ""
                enum_field = "TEST_1"
                #Only if null
                #pepega_null_string = "pepega"
                
                [string_map]
                Pepega = "Pepega"
                
                # Test
                [object_map.key1]
                string = "fieldValue"
                [object_map.key2]
                string = "fieldValue"
                
                [object_without_comment]
                string = "fieldValue"
                
                # Always
                #Only if null
                #[pepega_class]
                #pepega = "pepega"
                
            """.trimIndent(),
            serializedString,
        )
    }

    @Test
    fun loadTest() {
        val serializedConfig = """
            # 1
            # 2
            # 3
            #     4
            boolean_with_comment = false
            short = 65
            # Pepega
            string_with_comment = ""
            enum_field = "TEST_1"
            #Only if null
            #pepega_null_string = "pepega"

            [string_map]
            Pepega = "Pepega"

            # Test
            [object_map.key1]
            string = "fieldValue1"
            [object_map.key2]
            string = "fieldValue2"
            [object_map.key3]
            string = "fieldValue3"

            [object_without_comment]
            string = "fieldValue"

            # Always
            #Only if null
            #[pepega_class]
            #pepega = "pepega"
        """.trimIndent()

        val testConfig = toml.load<TestConfig>(TestConfig::class.java, serializedConfig.byteInputStream())

        assertEquals(false, testConfig.booleanWithComment)
        assertEquals(65, testConfig.short)
        assertEquals("", testConfig.stringWithComment)
        assertEquals(TestConfig.PepegaEnum.TEST_1, testConfig.enumField)
        assertEquals(null, testConfig.nullCommentString)

        assertEquals(1, testConfig.stringMap.size)
        assertEquals("Pepega", testConfig.stringMap["Pepega"])

        assertEquals(3, testConfig.objectMap.size)
        assertEquals("fieldValue1", testConfig.objectMap["key1"]?.string)
        assertEquals("fieldValue2", testConfig.objectMap["key2"]?.string)
        assertEquals("fieldValue3", testConfig.objectMap["key3"]?.string)

        assertEquals("fieldValue", testConfig.objectWithoutComment.string)
        assertEquals(null, testConfig.nullObject)
    }
}

@Config
class TestConfig {

    @ConfigField(comment = """
        1
        2
        3
            4
    """)
    val booleanWithComment = false

    @ConfigField
    var short: Short = 65

    @ConfigField(comment = "Pepega")
    val stringWithComment = ""

    @ConfigField
    val enumField = PepegaEnum.TEST_1

    @ConfigField(nullComment = "Only if null\npepega_null_string = \"pepega\"")
    val nullCommentString: String? = null

    @ConfigField
    val stringMap: Map<String, String> = mapOf("Pepega" to "Pepega")

    @ConfigField(comment = "Test")
    val objectMap: Map<String, Pepega> = mapOf(
        "key1" to Pepega(),
        "key2" to Pepega(),
    )

    @ConfigField
    val objectWithoutComment = Pepega()

    @ConfigField(comment = "Always", nullComment = "Only if null\n[pepega_class]\npepega = \"pepega\"")
    val nullObject: Pepega? = null

    enum class PepegaEnum {
        TEST_1,
        TEST_2
    }

    @Config
    class Pepega {

        @ConfigField
        val string = "fieldValue"
    }
}
