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
}
