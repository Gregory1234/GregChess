package gregc.gregchess.chess

import gregc.gregchess.*
import gregc.gregchess.chess.component.Chessboard
import kotlinx.serialization.*
import kotlinx.serialization.descriptors.*
import kotlinx.serialization.encoding.*
import kotlin.reflect.KClass

// TODO: serialize flagsAdded in a way that doesn't go out of sync
@Serializable
data class Move(
    val piece: PieceInfo, val display: Pos, val floor: Floor,
    val stopBlocking: Collection<Pos>, val startBlocking: Collection<Pos>,
    val neededEmpty: Collection<Pos>, val passedThrough: Collection<Pos>,
    val flagsNeeded: Collection<Pair<Pos, ChessFlagType>>, val flagsAdded: Collection<PosFlag>,
    val traits: List<MoveTrait>, val nameOrder: NameOrder) {
    fun <T : MoveTrait> getTrait(cl: KClass<T>): T? = traits.filterIsInstance(cl.java).firstOrNull()
    inline fun <reified T : MoveTrait> getTrait(): T? = getTrait(T::class)

    fun setup(game: ChessGame) {
        traits.forEach { it.setup(game, this) }
    }

    fun execute(game: ChessGame) {
        var remainingTraits = traits
        for (pass in 0u..255u) {
            remainingTraits = remainingTraits.filter {
                !it.execute(game, this, pass.toUByte(), remainingTraits)
            }
            if (remainingTraits.isEmpty()) {
                game.variant.finishMove(this, game)
                for ((p, f) in flagsAdded) {
                    game.board[p]?.flags?.plusAssign(f)
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
            remainingTraits = remainingTraits.filter {
                !it.undo(game, this, pass.toUByte(), remainingTraits)
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
class MoveNameTokenType<T: Any> private constructor(val cl: KClass<T>, @JvmField val toPgnString: (T) -> String): NameRegistered {

    object Serializer: NameRegisteredSerializer<MoveNameTokenType<*>>("MoveNameTokenType", RegistryType.MOVE_NAME_TOKEN_TYPE)

    override val module get() = RegistryType.MOVE_NAME_TOKEN_TYPE.getModule(this)
    override val name get() = RegistryType.MOVE_NAME_TOKEN_TYPE[this]

    override fun toString(): String = "${module.namespace}:$name@${hashCode().toString(16)}"

    fun of(v: T) = MoveNameToken(this, v)

    companion object {
        fun <T: Any> build(cl: KClass<T>, toPgnString: (T) -> String = { it.toString() }) =
            MoveNameTokenType(cl, toPgnString)
        inline fun <reified T: Any> build(noinline toPgnString: (T) -> String = { it.toString() }) =
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
    object Serializer: KSerializer<NameOrder> {
        @OptIn(ExperimentalSerializationApi::class)
        override val descriptor: SerialDescriptor
            get() = object : SerialDescriptor {
                override val elementsCount: Int = 1
                override val kind: SerialKind = StructureKind.LIST
                override val serialName: String = "NameOrder"

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
                    return MoveNameTokenType.Serializer.descriptor
                }

            }

        override fun serialize(encoder: Encoder, value: NameOrder) {
            val size = value.nameOrder.size
            val composite = encoder.beginCollection(descriptor, size)
            val iterator = value.nameOrder.iterator()
            for (index in 0 until size)
                composite.encodeSerializableElement(descriptor, index, MoveNameTokenType.Serializer, iterator.next())
            composite.endStructure(descriptor)
        }

        override fun deserialize(decoder: Decoder): NameOrder {
            val builder = mutableListOf<MoveNameTokenType<*>>()
            val compositeDecoder = decoder.beginStructure(descriptor)
            while (true) {
                val index = compositeDecoder.decodeElementIndex(descriptor)
                if (index == CompositeDecoder.DECODE_DONE) break
                builder.add(index, compositeDecoder.decodeSerializableElement(descriptor, index, MoveNameTokenType.Serializer))
            }
            compositeDecoder.endStructure(descriptor)
            return NameOrder(builder)
        }
    }

    fun reorder(tokens: List<MoveNameToken<*>>): MoveName {
        val nameTokens = tokens.associateBy { it.type }
        return MoveName(nameOrder.mapNotNull { nameTokens[it] })
    }
}

val MoveNameTokenType<Unit>.mk get() = of(Unit)

@Serializable(with = MoveNameToken.Serializer::class)
data class MoveNameToken<T: Any>(val type: MoveNameTokenType<T>, val value: T) {
    val pgn: String get() = type.toPgnString(value)

    @OptIn(ExperimentalSerializationApi::class, InternalSerializationApi::class)
    @Suppress("UNCHECKED_CAST")
    object Serializer: KSerializer<MoveNameToken<*>> {
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
                val serializer = type.cl.serializer()
                return MoveNameToken(type as MoveNameTokenType<Any>, decodeSerializableElement(descriptor, 1, serializer))
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
                        ret = MoveNameToken(type as MoveNameTokenType<Any>, decodeSerializableElement(descriptor, index, serializer))
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
    constructor(tokens: Collection<MoveNameToken<*>> = emptyList()): this(tokens.toMutableList())
    object Serializer : KSerializer<MoveName> {
        @OptIn(ExperimentalSerializationApi::class)
        override val descriptor: SerialDescriptor
            get() = object : SerialDescriptor {
                override val elementsCount: Int = 1
                override val kind: SerialKind = StructureKind.LIST
                override val serialName: String = "MoveName"

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

        override fun serialize(encoder: Encoder, value: MoveName) {
            val size = value.tokens.size
            val composite = encoder.beginCollection(descriptor, size)
            val iterator = value.tokens.iterator()
            for (index in 0 until size)
                composite.encodeSerializableElement(descriptor, index, MoveNameToken.Serializer, iterator.next())
            composite.endStructure(descriptor)
        }

        override fun deserialize(decoder: Decoder): MoveName {
            val builder = mutableListOf<MoveNameToken<*>>()
            val compositeDecoder = decoder.beginStructure(descriptor)
            while (true) {
                val index = compositeDecoder.decodeElementIndex(descriptor)
                if (index == CompositeDecoder.DECODE_DONE) break
                builder.add(index, compositeDecoder.decodeSerializableElement(descriptor, index, MoveNameToken.Serializer))
            }
            compositeDecoder.endStructure(descriptor)
            return MoveName(builder)
        }
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

    object Serializer: KSerializer<UniquenessCoordinate> {
        override val descriptor: SerialDescriptor
            get() = PrimitiveSerialDescriptor("UniquenessCoordinate", PrimitiveKind.STRING)

        override fun serialize(encoder: Encoder, value: UniquenessCoordinate) {
            encoder.encodeString(value.toString())
        }

        override fun deserialize(decoder: Decoder): UniquenessCoordinate {
            val str = decoder.decodeString()
            return if (str.length == 2)
                UniquenessCoordinate(Pos.parseFromString(str))
            else when(str.single()) {
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

fun getUniquenessCoordinate(piece: BoardPiece, target: Square): UniquenessCoordinate {
    val game = target.game
    val pieces = game.board.pieces.filter { it.side == piece.side && it.type == piece.type }
    val consideredPieces = pieces.filter { p ->
        p.square.bakedMoves.orEmpty().any { it.getTrait<TargetTrait>()?.target == target.pos && game.variant.isLegal(it, piece.square.game) }
    }
    return when {
        consideredPieces.size == 1 -> UniquenessCoordinate()
        consideredPieces.count { it.pos.file == piece.pos.file } == 1 -> UniquenessCoordinate(file = piece.pos.file)
        consideredPieces.count { it.pos.rank == piece.pos.rank } == 1 -> UniquenessCoordinate(rank = piece.pos.rank)
        else -> UniquenessCoordinate(piece.pos)
    }
}

fun checkForChecks(side: Side, game: ChessGame): List<MoveNameToken<*>> {
    game.board.updateMoves()
    val pieces = game.board.piecesOf(!side)
    val inCheck = game.variant.isInCheck(game, !side)
    val noMoves = pieces.all { game.board.getMoves(it.pos).none { m -> game.variant.isLegal(m, game) } }
    return when {
        inCheck && noMoves -> listOf(MoveNameTokenType.CHECKMATE.mk)
        inCheck -> listOf(MoveNameTokenType.CHECK.mk)
        else -> emptyList()
    }
}

