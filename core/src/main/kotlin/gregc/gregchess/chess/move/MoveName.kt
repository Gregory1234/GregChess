package gregc.gregchess.chess.move

import gregc.gregchess.GregChessModule
import gregc.gregchess.chess.*
import gregc.gregchess.chess.piece.PieceType
import gregc.gregchess.register
import gregc.gregchess.registry.*
import kotlinx.serialization.*
import kotlinx.serialization.descriptors.*
import kotlinx.serialization.encoding.*
import kotlin.reflect.KClass
import kotlin.reflect.full.createType

@Serializable(with = MoveNameTokenType.Serializer::class)
class MoveNameTokenType<T : Any> private constructor(val cl: KClass<T>, @JvmField val toPgnString: (T) -> String) :
    NameRegistered {

    object Serializer :
        NameRegisteredSerializer<MoveNameTokenType<*>>("MoveNameTokenType", RegistryType.MOVE_NAME_TOKEN_TYPE)

    override val key get() = RegistryType.MOVE_NAME_TOKEN_TYPE[this]

    override fun toString(): String = "$key@${hashCode().toString(16)}"

    companion object {
        fun <T : Any> build(cl: KClass<T>, toPgnString: (T) -> String = { it.toString() }) =
            MoveNameTokenType(cl, toPgnString)

        inline fun <reified T : Any> build(noinline toPgnString: (T) -> String = { it.toString() }) =
            build(T::class, toPgnString)

        fun build(const: String) = build<Unit> { const }

        @JvmField
        val PIECE_TYPE = GregChessModule.register("piece_type", build<PieceType> { it.char.uppercase() })
        @JvmField
        val UNIQUENESS_COORDINATE = GregChessModule.register("uniqueness_coordinate", build<UniquenessCoordinate>())
        @JvmField
        val CAPTURE = GregChessModule.register("capture", build("x"))
        @JvmField
        val TARGET = GregChessModule.register("target", build<Pos>())
        @JvmField
        val PROMOTION = GregChessModule.register("promotion", build<PieceType> { "=" + it.char.uppercase() })
        @JvmField
        val CHECK = GregChessModule.register("check", build("+"))
        @JvmField
        val CHECKMATE = GregChessModule.register("checkmate", build("#"))
        @JvmField
        val CASTLE = GregChessModule.register("castle", build<BoardSide> { it.castles })
        @JvmField
        val EN_PASSANT = GregChessModule.register("en_passant", build(""))
    }
}

@Serializable(with = MoveName.Serializer::class)
data class MoveName(private val tokens: Map<MoveNameTokenType<*>, Any>) {
    constructor(names: Collection<MoveName>) : this(names.flatMap { it.tokens.toList() }.toMap())
    init {
        for ((t,v) in tokens)
            require(t.cl.isInstance(v))
    }

    operator fun <T : Any> get(type: MoveNameTokenType<T>): T = getOrNull(type)!!

    @Suppress("UNCHECKED_CAST")
    fun <T : Any> getOrNull(type: MoveNameTokenType<T>): T? = tokens[type] as T?

    fun format(formatter: MoveNameFormatter): String = formatter.format(this)

    @OptIn(ExperimentalSerializationApi::class, InternalSerializationApi::class)
    @Suppress("UNCHECKED_CAST")
    object Serializer : KSerializer<MoveName> {
        override val descriptor: SerialDescriptor
            get() = buildSerialDescriptor("MoveNameToken", StructureKind.MAP) {
                element("type", MoveNameTokenType.Serializer.descriptor)
                element("value", buildSerialDescriptor("MoveNameTokenValue", SerialKind.CONTEXTUAL))
            }

        override fun serialize(encoder: Encoder, value: MoveName) = encoder.encodeStructure(descriptor) {
            var index = 0
            for ((t,v) in value.tokens) {
                encodeSerializableElement(descriptor, index++, MoveNameTokenType.Serializer, t)
                val serializer = encoder.serializersModule.serializer(t.cl.createType())
                encodeSerializableElement(descriptor, index++, serializer as KSerializer<Any>, v)
            }
        }

        override fun deserialize(decoder: Decoder): MoveName = decoder.decodeStructure(descriptor) {
            val ret = mutableMapOf<MoveNameTokenType<*>, Any>()

            fun readElement(index: Int) {
                val key = decodeSerializableElement(descriptor, index, MoveNameTokenType.Serializer)
                val serializer = decoder.serializersModule.serializer(key.cl.createType())
                val value = if (key in ret && serializer.descriptor.kind !is PrimitiveKind) {
                    decodeSerializableElement(descriptor, index+1, serializer, ret[key])
                } else {
                    decodeSerializableElement(descriptor, index+1, serializer)
                }
                ret[key] = value!!
            }

            if (decodeSequentially()) { // sequential decoding protocol
                val size = decodeCollectionSize(descriptor)
                for (index in 0 until size * 2 step 2)
                    readElement(index)
            } else {
                while (true) {
                    val index = decodeElementIndex(descriptor)
                    if (index == CompositeDecoder.DECODE_DONE) break
                    readElement(index)
                }
            }
            MoveName(ret)
        }
    }
}

fun interface MoveNameFormatter {
    fun format(name: MoveName): String
}