package gregc.gregchess.config.dsl

@DslMarker
@Retention(AnnotationRetention.SOURCE)
@Target(AnnotationTarget.CLASS)
annotation class ConfigDSL

@Retention(AnnotationRetention.RUNTIME)
@Target(AnnotationTarget.CLASS)
@MustBeDocumented
annotation class Represents(val packageName: String, val className: String)

fun config(block: ConfigRootScope.() -> Unit) = ConfigRootScope("Config").apply(block)