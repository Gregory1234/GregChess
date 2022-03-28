package gregc.gregchess.util

import java.time.Instant
import kotlin.time.Duration
import kotlin.time.toKotlinDuration

// TODO: check this statically
@Target(AnnotationTarget.TYPE)
@Retention(AnnotationRetention.SOURCE)
@MustBeDocumented
annotation class SelfType

internal fun rotationsOf(x: Int, y: Int): List<Pair<Int, Int>> =
    listOf(x to y, x to -y, -x to y, -x to -y, y to x, -y to x, y to -x, -y to -x).distinct()

private fun String.upperFirst() = replaceFirstChar { it.uppercase() }

fun String.snakeToPascal(): String {
    val snakeRegex = "_[a-zA-Z]".toRegex()
    return snakeRegex.replace(lowercase()) { it.value.replace("_", "").uppercase() }.upperFirst()
}

internal fun Duration.Companion.between(startInclusive: Instant, endExclusive: Instant) =
    java.time.Duration.between(startInclusive, endExclusive).toKotlinDuration()

class MultiExceptionContext {
    private val exceptions = mutableListOf<Exception>()

    fun <R> exec(def: R, mapping: (Exception) -> Exception = { it }, block: () -> R): R = try {
        block()
    } catch (e: Exception) {
        exceptions += mapping(e)
        def
    }

    fun exec(mapping: (Exception) -> Exception = { it }, block: () -> Unit) = try {
        block()
    } catch (e: Exception) {
        exceptions += mapping(e)
    }

    fun rethrow(base: (Exception) -> Exception = { it }) {
        if (exceptions.isNotEmpty()) {
            throw base(exceptions.last()).apply {
                for (e in exceptions.dropLast(1)) addSuppressed(e)
            }
        }
    }
}