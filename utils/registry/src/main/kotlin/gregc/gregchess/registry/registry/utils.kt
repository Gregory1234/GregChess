package gregc.gregchess.registry.registry

import gregc.gregchess.registry.ChessModule

internal class RegistryValidationException(
    val module: ChessModule, val type: Registry<*, *, *>, val text: String
): IllegalStateException("$module:${type.name}: $text")

internal class RegistryKeyValidationException(
    val module: ChessModule, val type: Registry<*, *, *>, val key: Any?, val value: Any?, val text: String
) : IllegalArgumentException("$module:${type.name}: $text: $key: $value")

internal inline fun <K, T, B : RegistryBlock<K, T>> B.requireValid(type: Registry<K, T, B>, condition: Boolean, message: () -> String) {
    if (!condition)
        throw RegistryValidationException(module, type, message())
}

internal inline fun <K, T, B : RegistryBlock<K, T>> B.requireValidKey(
    type: Registry<K, T, B>, key: K, value: T, condition: Boolean, message: () -> String
) {
    if (!condition)
        throw RegistryKeyValidationException(module, type, key, value, message())
}

internal fun String.isValidId(): Boolean = all { it == '_' || it in ('a'..'z') }


class RegistryKeyNotFoundException(key: Any?) : IllegalArgumentException(key.toString())