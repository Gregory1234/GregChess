package gregc.gregchess

import kotlinx.serialization.*
import kotlinx.serialization.descriptors.*
import kotlinx.serialization.encoding.*
import kotlinx.serialization.modules.SerializersModule
import kotlin.time.Duration

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

object DurationSerializer : KSerializer<Duration> {
    override val descriptor: SerialDescriptor get() = PrimitiveSerialDescriptor("Duration", PrimitiveKind.STRING)

    override fun serialize(encoder: Encoder, value: Duration) = encoder.encodeString(value.toIsoString())

    override fun deserialize(decoder: Decoder): Duration = Duration.parseIsoString(decoder.decodeString())
}

@OptIn(ExperimentalSerializationApi::class, InternalSerializationApi::class)
abstract class ClassMapSerializer<T, K, V>(val name: String, private val keySerializer: KSerializer<K>) : KSerializer<T> {
    final override val descriptor: SerialDescriptor
        get() = SerialDescriptor(name, mapSerialDescriptor(keySerializer.descriptor, buildSerialDescriptor(name + "Value", SerialKind.CONTEXTUAL)))

    protected abstract fun T.asMap(): Map<K, V>
    protected abstract fun fromMap(m: Map<K, V>): T
    protected abstract fun K.valueSerializer(module: SerializersModule): KSerializer<V>

    final override fun serialize(encoder: Encoder, value: T) {
        val map = value.asMap()
        val composite = encoder.beginCollection(descriptor, map.size)
        var index = 0
        for ((t,v) in map) {
            composite.encodeSerializableElement(descriptor, index++, keySerializer, t)
            composite.encodeSerializableElement(descriptor, index++, t.valueSerializer(encoder.serializersModule), v)
        }
        composite.endStructure(descriptor)
    }

    final override fun deserialize(decoder: Decoder): T = decoder.decodeStructure(descriptor) {
        val ret = mutableMapOf<K, V>()

        fun readElement(index: Int, checkIndex: Boolean) {
            val key = decodeSerializableElement(descriptor, index, keySerializer)
            val serializer = key.valueSerializer(decoder.serializersModule)
            if (checkIndex) {
                check(decodeElementIndex(descriptor) == index + 1)
            }
            val value = if (key in ret && serializer.descriptor.kind !is PrimitiveKind) {
                decodeSerializableElement(descriptor, index+1, serializer, ret[key])
            } else {
                decodeSerializableElement(descriptor, index+1, serializer)
            }
            ret[key] = value
        }

        if (decodeSequentially()) { // sequential decoding protocol
            val size = decodeCollectionSize(descriptor)
            for (index in 0 until size * 2 step 2)
                readElement(index, false)
        } else {
            while (true) {
                val index = decodeElementIndex(descriptor)
                if (index == CompositeDecoder.DECODE_DONE) break
                readElement(index, true)
            }
        }
        fromMap(ret)
    }
}