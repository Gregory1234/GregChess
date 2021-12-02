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

    fun of(v: T) = MoveNameToken(this, v)

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

@Serializable(with = NameOrderElement.Serializer::class)
data class NameOrderElement(val type: MoveNameTokenType<*>) {
    @OptIn(ExperimentalSerializationApi::class)
    object Serializer : KSerializer<NameOrderElement> {
        override val descriptor: SerialDescriptor
            get() = PrimitiveSerialDescriptor("NameOrderElement", PrimitiveKind.STRING)

        override fun serialize(encoder: Encoder, value: NameOrderElement) =
            encoder.encodeSerializableValue(MoveNameTokenType.Serializer, value.type)

        override fun deserialize(decoder: Decoder): NameOrderElement =
            NameOrderElement(decoder.decodeSerializableValue(MoveNameTokenType.Serializer))
    }
}

typealias NameOrder = List<NameOrderElement>

fun NameOrder.reorder(tokens: List<AnyMoveNameToken>): MoveName {
    val nameTokens = tokens.associateBy { it.type }
    return mapNotNull { nameTokens[it.type] }
}

fun nameOrder(vararg types: MoveNameTokenType<*>) = types.map { NameOrderElement(it) }

val MoveNameTokenType<Unit>.mk get() = of(Unit)

@Serializable(with = MoveNameToken.Serializer::class)
data class MoveNameToken<T : Any>(val type: MoveNameTokenType<T>, val value: T) {
    val pgn: String get() = type.toPgnString(value)

    @OptIn(ExperimentalSerializationApi::class, InternalSerializationApi::class)
    @Suppress("UNCHECKED_CAST")
    object Serializer : KSerializer<MoveNameToken<*>> {
        override val descriptor: SerialDescriptor
            get() = buildClassSerialDescriptor("MoveNameToken") {
                element("type", MoveNameTokenType.Serializer.descriptor)
                element("value", buildSerialDescriptor("MoveNameTokenValue", SerialKind.CONTEXTUAL))
            }

        override fun serialize(encoder: Encoder, value: MoveNameToken<*>) {
            val actualSerializer = encoder.serializersModule.serializer(value.value::class.createType())
            encoder.encodeStructure(descriptor) {
                encodeSerializableElement(descriptor, 0, MoveNameTokenType.Serializer, value.type)
                encodeSerializableElement(descriptor, 1, actualSerializer as KSerializer<Any>, value.value)
            }
        }

        override fun deserialize(decoder: Decoder): MoveNameToken<*> = decoder.decodeStructure(descriptor) {
            var type: MoveNameTokenType<*>? = null
            var ret: MoveNameToken<*>? = null

            if (decodeSequentially()) { // sequential decoding protocol
                type = decodeSerializableElement(descriptor, 0, MoveNameTokenType.Serializer)
                type as MoveNameTokenType<Any>
                val serializer = decoder.serializersModule.serializer(type.cl.createType())
                ret = MoveNameToken(type, decodeSerializableElement(descriptor, 1, serializer)!!)
            } else {
                while (true) {
                    when (val index = decodeElementIndex(descriptor)) {
                        0 -> type = decodeSerializableElement(descriptor, index, MoveNameTokenType.Serializer)
                        1 -> {
                            val serializer = decoder.serializersModule.serializer(type!!.cl.createType())
                            ret = MoveNameToken(
                                type as MoveNameTokenType<Any>,
                                decodeSerializableElement(descriptor, index, serializer)!!
                            )
                        }
                        CompositeDecoder.DECODE_DONE -> break
                        else -> error("Unexpected index: $index")
                    }
                }
            }
            ret!!
        }
    }
}

@Serializable(with = AnyMoveNameToken.Serializer::class)
data class AnyMoveNameToken(val token: MoveNameToken<*>) {
    val type get() = token.type

    @OptIn(ExperimentalSerializationApi::class)
    object Serializer : KSerializer<AnyMoveNameToken> {
        override val descriptor: SerialDescriptor
            get() = SerialDescriptor("AnyMoveNameToken", MoveNameToken.Serializer.descriptor)

        override fun serialize(encoder: Encoder, value: AnyMoveNameToken) =
            encoder.encodeSerializableValue(MoveNameToken.Serializer, value.token)

        override fun deserialize(decoder: Decoder): AnyMoveNameToken =
            AnyMoveNameToken(decoder.decodeSerializableValue(MoveNameToken.Serializer))
    }
}

typealias MoveName = List<AnyMoveNameToken>

typealias MutableTokenList = MutableList<AnyMoveNameToken>

fun emptyTokenList(): MutableTokenList = mutableListOf()

operator fun MutableTokenList.plusAssign(token: MoveNameToken<*>) = plusAssign(AnyMoveNameToken(token))

fun nameOf(vararg tokens: MoveNameToken<*>): MoveName = tokens.map { AnyMoveNameToken(it) }

val MoveName.pgn get() = joinToString("") { it.token.pgn }
