package gregc.gregchess.chess

import gregc.gregchess.*
import gregc.gregchess.chess.component.Chessboard
import kotlinx.serialization.*
import kotlinx.serialization.descriptors.*
import kotlinx.serialization.encoding.*
import kotlin.reflect.KClass

@Serializable
data class Move(
    val piece: BoardPiece, val display: Pos, val floor: Floor,
    val stopBlocking: Set<Pos>, val startBlocking: Set<Pos>,
    val neededEmpty: Set<Pos>, val passedThrough: Set<Pos>,
    val flagsNeeded: Set<Pair<Pos, ChessFlagType>>, val flagsAdded: Set<PosFlag>,
    val traits: List<MoveTrait>, val nameOrder: NameOrder
) {
    fun <T : MoveTrait> getTrait(cl: KClass<T>): T? = traits.filterIsInstance(cl.java).firstOrNull()
    inline fun <reified T : MoveTrait> getTrait(): T? = getTrait(T::class)

    fun setup(game: ChessGame) {
        traits.forEach { it.setup(game, this) }
    }

    fun execute(game: ChessGame) {
        var remainingTraits = traits
        for (pass in 0u..255u) {
            remainingTraits = remainingTraits.filterNot { mt ->
                if (remainingTraits.any { it::class in mt.shouldComeBefore })
                    false
                else if (remainingTraits.any { mt::class in it.shouldComeAfter })
                    false
                else
                    mt.execute(game, this, pass.toUByte(), remainingTraits)
            }
            if (remainingTraits.isEmpty()) {
                // TODO: move this into traits
                for ((p, f) in flagsAdded) {
                    game.board[p]?.flags?.plusAssign(f.copy())
                }
                return
            }
        }
        throw TraitsCouldNotExecuteException(remainingTraits)
    }

    val name: MoveName get() = nameOrder.reorder(traits.flatMap { it.nameTokens })

    fun undo(game: ChessGame) {
        var remainingTraits = traits
        for (pass in 0u..255u) {
            remainingTraits = remainingTraits.filterNot { mt ->
                if (remainingTraits.any { it::class in mt.shouldComeAfter })
                    false
                else if (remainingTraits.any { mt::class in it.shouldComeBefore })
                    false
                else
                    mt.undo(game, this, pass.toUByte(), remainingTraits)
            }
            if (remainingTraits.isEmpty()) {
                return
            }
        }
        throw TraitsCouldNotExecuteException(remainingTraits)
    }

    fun show(board: Chessboard) {
        board[display]?.moveMarker = floor
    }

    fun hide(board: Chessboard) {
        board[display]?.moveMarker = null
    }

    fun showDone(board: Chessboard) {
        board[piece.pos]?.previousMoveMarker = Floor.LAST_START
        board[display]?.moveMarker = Floor.LAST_END
    }

    fun hideDone(board: Chessboard) {
        board[piece.pos]?.previousMoveMarker = null
        board[display]?.moveMarker = null
    }
}

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
            get() = SerialDescriptor("NameOrderElement", MoveNameTokenType.Serializer.descriptor)

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
            val actualSerializer = value.type.cl.serializer()
            encoder.encodeStructure(descriptor) {
                encodeSerializableElement(descriptor, 0, MoveNameTokenType.Serializer, value.type)
                encodeSerializableElement(descriptor, 1, actualSerializer as KSerializer<Any>, value.value)
            }
        }

        override fun deserialize(decoder: Decoder): MoveNameToken<*> = decoder.decodeStructure(descriptor) {
            var type: MoveNameTokenType<*>? = null
            var ret: MoveNameToken<*>? = null
            if (decodeSequentially()) {
                type = decodeSerializableElement(descriptor, 0, MoveNameTokenType.Serializer)
                type as MoveNameTokenType<Any>
                val serializer = type.cl.serializer()
                return MoveNameToken(type, decodeSerializableElement(descriptor, 1, serializer))
            }

            if (decodeSequentially()) { // sequential decoding protocol
                type = decodeSerializableElement(descriptor, 0, MoveNameTokenType.Serializer)
                val serializer = type.cl.serializer()
                type as MoveNameTokenType<Any>
                ret = MoveNameToken(type, decodeSerializableElement(descriptor, 1, serializer))
            } else {
                while (true) {
                    when (val index = decodeElementIndex(descriptor)) {
                        0 -> type = decodeSerializableElement(descriptor, index, MoveNameTokenType.Serializer)
                        1 -> {
                            val serializer = type!!.cl.serializer()
                            type as MoveNameTokenType<Any>
                            ret = MoveNameToken(type, decodeSerializableElement(descriptor, index, serializer))
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

@Serializable(with = UniquenessCoordinate.Serializer::class)
data class UniquenessCoordinate(val file: Int? = null, val rank: Int? = null) {
    constructor(pos: Pos) : this(pos.file, pos.rank)

    object Serializer : KSerializer<UniquenessCoordinate> {
        override val descriptor: SerialDescriptor
            get() = PrimitiveSerialDescriptor("UniquenessCoordinate", PrimitiveKind.STRING)

        override fun serialize(encoder: Encoder, value: UniquenessCoordinate) {
            encoder.encodeString(value.toString())
        }

        override fun deserialize(decoder: Decoder): UniquenessCoordinate {
            val str = decoder.decodeString()
            return if (str.length == 2)
                UniquenessCoordinate(Pos.parseFromString(str))
            else when (str.single()) {
                in '1'..'8' -> UniquenessCoordinate(rank = str.single() - '1')
                in 'a'..'h' -> UniquenessCoordinate(file = str.single() - 'a')
                else -> error("Bad chessboard coordinate: $str")
            }
        }
    }

    val fileStr get() = file?.let { "${'a' + it}" }
    val rankStr get() = rank?.let { (it + 1).toString() }
    override fun toString(): String = fileStr.orEmpty() + rankStr.orEmpty()
}
