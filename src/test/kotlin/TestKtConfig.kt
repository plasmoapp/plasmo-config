import su.plo.config.Config
import su.plo.config.ConfigField

@Config
class TestKtConfig {

    @ConfigField(comment = """
        1
        2
        3
            4
    """)
    val test = false

    @ConfigField
    var jukeboxDistance: Short = 65

    @ConfigField(comment = "Pepega")
    val pepega = ""

    @ConfigField
    val enumField = PepegaEnum.TEST_1

    @ConfigField(nullComment = "Only if null\npepega_null_string = \"pepega\"")
    val pepegaNullString: String? = null

    @ConfigField(comment = "Always", nullComment = "Only if null\n[pepega_class]\npepega = \"pepega\"")
    val pepegaClass: Pepega? = null

    enum class PepegaEnum {
        TEST_1,
        TEST_2
    }

    @Config
    class Pepega {

        @ConfigField
        val pepega = "Pepega"
    }
}
