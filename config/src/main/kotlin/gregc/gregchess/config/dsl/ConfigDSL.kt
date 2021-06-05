package gregc.gregchess.config.dsl

@DslMarker
@Retention(AnnotationRetention.SOURCE)
@Target(AnnotationTarget.CLASS)
annotation class ConfigDSL


fun config(block: ConfigRootScope.() -> Unit) = ConfigRootScope("Config").apply(block)