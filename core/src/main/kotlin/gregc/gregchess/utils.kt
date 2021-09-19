package gregc.gregchess

import gregc.gregchess.chess.ChessGame
import gregc.gregchess.chess.MoveNameToken
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

fun Int.towards(other: Int, amount: Int) =
    if (this > other) this - amount else if (this < other) this + amount else this

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

interface NameRegistered {
    val key: RegistryKey<String>
}

val NameRegistered.name get() = key.key
val NameRegistered.module get() = key.module

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
                when (val index = decodeElementIndex(ChessGame.Serializer.descriptor)) {
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

abstract class CustomListSerializer<L, E>(val name: String, val elementSerializer: KSerializer<E>) : KSerializer<L> {
    @OptIn(ExperimentalSerializationApi::class)
    final override val descriptor: SerialDescriptor
        get() = object : SerialDescriptor {
            override val elementsCount: Int = 1
            override val kind: SerialKind = StructureKind.LIST
            override val serialName: String = name

            override fun getElementName(index: Int): String = index.toString()
            override fun getElementIndex(name: String): Int = name.toInt()

            override fun isElementOptional(index: Int): Boolean {
                require(index >= 0)
                return false
            }

            override fun getElementAnnotations(index: Int): List<Annotation> {
                require(index >= 0)
                return emptyList()
            }

            override fun getElementDescriptor(index: Int): SerialDescriptor {
                require(index >= 0)
                return MoveNameToken.Serializer.descriptor
            }
        }

    abstract fun construct(list: List<E>): L

    abstract fun elements(custom: L): List<E>

    final override fun serialize(encoder: Encoder, value: L) {
        val size = elements(value).size
        val composite = encoder.beginCollection(descriptor, size)
        val iterator = elements(value).iterator()
        for (index in 0 until size)
            composite.encodeSerializableElement(descriptor, index, elementSerializer, iterator.next())
        composite.endStructure(descriptor)
    }

    final override fun deserialize(decoder: Decoder): L {
        val builder = mutableListOf<E>()
        val compositeDecoder = decoder.beginStructure(descriptor)
        while (true) {
            val index = compositeDecoder.decodeElementIndex(descriptor)
            if (index == CompositeDecoder.DECODE_DONE) break
            builder.add(index, compositeDecoder.decodeSerializableElement(descriptor, index, elementSerializer))
        }
        compositeDecoder.endStructure(descriptor)
        return construct(builder)
    }
}

object DurationSerializer : KSerializer<Duration> {
    override val descriptor: SerialDescriptor get() = PrimitiveSerialDescriptor("Duration", PrimitiveKind.STRING)

    override fun serialize(encoder: Encoder, value: Duration) = encoder.encodeString(value.toString())

    override fun deserialize(decoder: Decoder): Duration = Duration.parse(decoder.decodeString())
}