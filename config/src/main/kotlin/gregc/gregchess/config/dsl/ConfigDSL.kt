package gregc.gregchess.config.dsl

import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.asTypeName
import kotlin.reflect.KClass

@DslMarker
@Retention(AnnotationRetention.SOURCE)
@Target(AnnotationTarget.CLASS)
annotation class ConfigDSL

@Retention(AnnotationRetention.RUNTIME)
@Target(AnnotationTarget.CLASS)
@MustBeDocumented
annotation class Represents(val packageName: String, val className: String)

fun KClass<*>.asConfigTypeName() = annotations.filterIsInstance<Represents>().firstOrNull()?.let {
    ClassName(it.packageName, it.className)
} ?: asTypeName()

fun config(block: ConfigRootScope.() -> Unit) = ConfigRootScope("Config").apply(block)