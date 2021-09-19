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

    val name: MoveName get() = nameOrder.reorder(traits.flatMap { it.nameTokens.tokens })

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

@JvmInline
@Serializable(with = NameOrder.Serializer::class)
value class NameOrder(val nameOrder: List<MoveNameTokenType<*>>) {
    object Serializer :
        CustomListSerializer<NameOrder, MoveNameTokenType<*>>("MoveName", MoveNameTokenType.Serializer) {

        override fun construct(list: List<MoveNameTokenType<*>>) = NameOrder(list)
        override fun elements(custom: NameOrder) = custom.nameOrder
    }

    fun reorder(tokens: List<MoveNameToken<*>>): MoveName {
        val nameTokens = tokens.associateBy { it.type }
        return MoveName(nameOrder.mapNotNull { nameTokens[it] })
    }
}

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

            mainLoop@ while (true) {
                when (val index = decodeElementIndex(descriptor)) {
                    CompositeDecoder.DECODE_DONE -> {
                        break@mainLoop
                    }
                    0 -> {
                        type = decodeSerializableElement(descriptor, index, MoveNameTokenType.Serializer)
                    }
                    1 -> {
                        val serializer = type!!.cl.serializer()
                        type as MoveNameTokenType<Any>
                        ret = MoveNameToken(type, decodeSerializableElement(descriptor, index, serializer))
                    }
                    else -> throw SerializationException("Invalid index")
                }
            }
            ret!!
        }
    }
}

// TODO: make tokens read only in general
@JvmInline
@Serializable(with = MoveName.Serializer::class)
value class MoveName(val tokens: MutableList<MoveNameToken<*>>) {
    constructor(tokens: Collection<MoveNameToken<*>> = emptyList()) : this(tokens.toMutableList())

    object Serializer : CustomListSerializer<MoveName, MoveNameToken<*>>("MoveName", MoveNameToken.Serializer) {
        override fun construct(list: List<MoveNameToken<*>>) = MoveName(list)
        override fun elements(custom: MoveName) = custom.tokens
    }

    val pgn get() = tokens.joinToString("") { it.pgn }

    operator fun plusAssign(other: List<MoveNameToken<*>>) {
        tokens += other
    }

    operator fun plusAssign(other: MoveNameToken<*>) {
        tokens += other
    }

    fun isEmpty(): Boolean = tokens.isEmpty()
    fun isNotEmpty(): Boolean = tokens.isNotEmpty()
}


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
                else -> throw IllegalArgumentException(str)
            }
        }
    }

    val fileStr get() = file?.let { "${'a' + it}" }
    val rankStr get() = rank?.let { (it + 1).toString() }
    override fun toString(): String = fileStr.orEmpty() + rankStr.orEmpty()
}
