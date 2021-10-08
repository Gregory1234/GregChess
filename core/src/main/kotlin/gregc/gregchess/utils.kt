package gregc.gregchess

import kotlinx.coroutines.*
import kotlinx.serialization.*
import kotlinx.serialization.descriptors.*
import kotlinx.serialization.encoding.*
import java.time.Duration
import kotlin.reflect.KClass

fun randomString(size: Int) =
    String(CharArray(size) { (('a'..'z') + ('A'..'Z') + ('0'..'9')).random() })

fun rotationsOf(x: Int, y: Int): List<Pair<Int, Int>> =
    listOf(x to y, x to -y, -x to y, -x to -y, y to x, -y to x, y to -x, -y to -x).distinct()

fun between(i: Int, j: Int): IntRange = if (i > j) (j + 1 until i) else (i + 1 until j)

fun betweenInc(i: Int, j: Int): IntRange = if (i > j) (j..i) else (i..j)

operator fun <E> List<E>.component6(): E = this[5]

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

interface GregLogger {
    fun info(msg: String)
    fun warn(msg: String)
    fun err(msg: String)
}

class SystemGregLogger : GregLogger {
    @Suppress("ReplaceJavaStaticMethodWithKotlinAnalog")
    override fun info(msg: String) = System.out.println(msg)
    override fun warn(msg: String) = System.err.println(msg)
    override fun err(msg: String) = System.err.println(msg)
}

@Serializable
data class Loc(val x: Int, val y: Int, val z: Int) {
    operator fun plus(offset: Loc) = Loc(x + offset.x, y + offset.y, z + offset.z)
}

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

interface NameRegistered {
    val key: RegistryKey<String>
}

val NameRegistered.name get() = key.key
val NameRegistered.module get() = key.module

@OptIn(ExperimentalCoroutinesApi::class, InternalCoroutinesApi::class)
fun Job.passExceptions() = invokeOnCompletion {
    if (it != null)
        throw it
}

// TODO: make names package qualified
open class NameRegisteredSerializer<T : NameRegistered>(val name: String, val registryView: RegistryView<String, T>) :
    KSerializer<T> {

    final override val descriptor: SerialDescriptor get() = PrimitiveSerialDescriptor(name, PrimitiveKind.STRING)

    final override fun serialize(encoder: Encoder, value: T) {
        encoder.encodeString(value.key.toString())
    }

    final override fun deserialize(decoder: Decoder): T {
        return registryView[decoder.decodeString().toKey()]
    }

}

@OptIn(ExperimentalSerializationApi::class, InternalSerializationApi::class)
@Suppress("UNCHECKED_CAST")
open class ClassRegisteredSerializer<T : Any>(
    val name: String,
    val registryType: DoubleRegistryView<String, KClass<out T>>
) : KSerializer<T> {
    final override val descriptor: SerialDescriptor
        get() = buildClassSerialDescriptor(name) {
            element<String>("type")
            element("value", buildSerialDescriptor(name + "Value", SerialKind.CONTEXTUAL))
        }

    final override fun serialize(encoder: Encoder, value: T) {
        val cl = value::class
        val actualSerializer = cl.serializer()
        val id = registryType[cl].toString()
        encoder.encodeStructure(descriptor) {
            encodeStringElement(descriptor, 0, id)
            encodeSerializableElement(descriptor, 1, actualSerializer as KSerializer<T>, value)
        }
    }

    final override fun deserialize(decoder: Decoder): T = decoder.decodeStructure(descriptor) {
        var type: String? = null
        var ret: T? = null

        if (decodeSequentially()) { // sequential decoding protocol
            type = decodeStringElement(descriptor, 0)
            val serializer = registryType[type.toKey()].serializer()
            ret = decodeSerializableElement(descriptor, 1, serializer)
        } else {
            while (true) {
                when (val index = decodeElementIndex(descriptor)) {
                    0 -> type = decodeStringElement(descriptor, index)
                    1 -> {
                        val serializer = registryType[type!!.toKey()].serializer()
                        ret = decodeSerializableElement(descriptor, index, serializer)
                    }
                    CompositeDecoder.DECODE_DONE -> break
                    else -> error("Unexpected index: $index")
                }
            }
        }
        ret!!
    }
}

object DurationSerializer : KSerializer<Duration> {
    override val descriptor: SerialDescriptor get() = PrimitiveSerialDescriptor("Duration", PrimitiveKind.STRING)

    override fun serialize(encoder: Encoder, value: Duration) = encoder.encodeString(value.toString())

    override fun deserialize(decoder: Decoder): Duration = Duration.parse(decoder.decodeString())
}