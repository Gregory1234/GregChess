package gregc.gregchess

import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.*
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import java.time.Duration

fun randomString(size: Int) =
    String(CharArray(size) { (('a'..'z') + ('A'..'Z') + ('0'..'9')).random() })

fun rotationsOf(x: Int, y: Int): List<Pair<Int, Int>> =
    listOf(x to y, x to -y, -x to y, -x to -y, y to x, -y to x, y to -x, -y to -x).distinct()

fun between(i: Int, j: Int): IntRange = if (i > j) (j + 1 until i) else (i + 1 until j)

fun Int.towards(other: Int, amount: Int) =
    if (this > other) this - amount else if (this < other) this + amount else this

operator fun <E> List<E>.component6(): E = this[5]

fun isValidUUID(s: String) =
    Regex("""^[0-9a-f]{8}-[0-9a-f]{4}-[1-5][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}${'$'}""")
        .matches(s)

operator fun Pair<Int, Int>.rangeTo(other: Pair<Int, Int>) = (first..other.first).flatMap { i ->
    (second..other.second).map { j -> Pair(i, j) }
}

operator fun Pair<Int, Int>.times(m: Int) = Pair(m * first, m * second)

fun String.upperFirst() = replaceFirstChar { it.uppercase() }

val Int.seconds: Duration
    get() = Duration.ofSeconds(toLong())
val Long.minutes: Duration
    get() = Duration.ofMinutes(this)

fun String.snakeToPascal(): String {
    val snakeRegex = "_[a-zA-Z]".toRegex()
    return snakeRegex.replace(lowercase()) { it.value.replace("_", "").uppercase() }.upperFirst()
}

fun String.isValidName(): Boolean = all { it == '_' || it in ('A'..'Z') }
fun String.isValidId(): Boolean = all { it == '_' || it in ('a'..'z') }

@Serializable
data class Loc(val x: Int, val y: Int, val z: Int) {
    operator fun plus(offset: Loc) = Loc(x + offset.x, y + offset.y, z + offset.z)
}

interface NameRegistered {
    val module: ChessModule
    val name: String
}

open class NameRegisteredSerializer<T : NameRegistered>(val name: String, val registryType: NameRegistryType<T>) :
    KSerializer<T> {

    override val descriptor: SerialDescriptor get() = PrimitiveSerialDescriptor(name, PrimitiveKind.STRING)

    override fun serialize(encoder: Encoder, value: T) {
        encoder.encodeString(value.module.namespace + ":" + value.name)
    }

    override fun deserialize(decoder: Decoder): T {
        val (namespace, name) = decoder.decodeString().split(":")
        return ChessModule[namespace][registryType][name]
    }

}

object DurationSerializer: KSerializer<Duration> {
    override val descriptor: SerialDescriptor get() = PrimitiveSerialDescriptor("Duration", PrimitiveKind.STRING)

    override fun serialize(encoder: Encoder, value: Duration) = encoder.encodeString(value.toString())

    override fun deserialize(decoder: Decoder): Duration = Duration.parse(decoder.decodeString())
}