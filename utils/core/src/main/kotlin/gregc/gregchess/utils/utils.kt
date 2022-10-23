package gregc.gregchess.utils

import java.time.Instant
import kotlin.time.Duration
import kotlin.time.toKotlinDuration

fun rotationsOf(x: Int, y: Int): List<Pair<Int, Int>> =
    listOf(x to y, x to -y, -x to y, -x to -y, y to x, -y to x, y to -x, -y to -x).distinct()

fun String.upperFirst() = replaceFirstChar { it.uppercase() }

fun String.snakeToPascal(): String {
    val snakeRegex = "_[a-zA-Z]".toRegex()
    return snakeRegex.replace(lowercase()) { it.value.replace("_", "").uppercase() }.upperFirst()
}

fun Duration.Companion.between(startInclusive: Instant, endExclusive: Instant) =
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

    fun catch(base: (Exception) -> Exception = { it }, block: (Exception) -> Unit) {
        if (exceptions.isNotEmpty()) {
            block(base(exceptions.last()).apply {
                for (e in exceptions.dropLast(1)) addSuppressed(e)
            })
        }
    }

    fun rethrow(base: (Exception) -> Exception = { it }) = catch(base) { throw it }
}