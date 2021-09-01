package gregc.gregchess.chess

import gregc.gregchess.*
import gregc.gregchess.chess.component.Chessboard
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.*
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlin.reflect.KClass

//@Serializable
data class Move(
    val piece: PieceInfo, val display: Pos, val floor: Floor,
    val stopBlocking: Collection<Pos>, val startBlocking: Collection<Pos>,
    val neededEmpty: Collection<Pos>, val passedThrough: Collection<Pos>,
    val flagsNeeded: Collection<Pair<Pos, ChessFlagType>>, val flagsAdded: Collection<PosFlag>,
    val traits: List<MoveTrait>, val nameOrder: List<MoveNameTokenType<*>>) {
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

    val name: MoveName
        get() {
            val nameTokens = traits.flatMap { it.nameTokens }.associateBy { it.type }
            return nameOrder.mapNotNull { nameTokens[it] }
        }

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
class MoveNameTokenType<T>(@JvmField val toPgnString: (T) -> String = Any?::toString): NameRegistered {
    constructor(constPGN: String) : this({ constPGN })

    object Serializer: NameRegisteredSerializer<MoveNameTokenType<*>>("MoveNameTokenType", RegistryType.MOVE_NAME_TOKEN_TYPE)

    override val module get() = RegistryType.MOVE_NAME_TOKEN_TYPE.getModule(this)
    override val name get() = RegistryType.MOVE_NAME_TOKEN_TYPE[this]

    override fun toString(): String = "${module.namespace}:$name@${hashCode().toString(16)}"

    fun of(v: T) = MoveNameToken(this, v)

    companion object {
        @JvmField
        val PIECE_TYPE = GregChessModule.register("piece_type", MoveNameTokenType<PieceType>{ it.char.uppercase() })
        @JvmField
        val UNIQUENESS_COORDINATE = GregChessModule.register("uniqueness_coordinate", MoveNameTokenType<UniquenessCoordinate>())
        @JvmField
        val CAPTURE = GregChessModule.register("capture", MoveNameTokenType<Unit>("x"))
        @JvmField
        val TARGET = GregChessModule.register("target", MoveNameTokenType<Pos>())
        @JvmField
        val PROMOTION = GregChessModule.register("promotion", MoveNameTokenType<PieceType>{ "=" + it.char.uppercase() })
        @JvmField
        val CHECK = GregChessModule.register("check", MoveNameTokenType<Unit>("+"))
        @JvmField
        val CHECKMATE = GregChessModule.register("checkmate", MoveNameTokenType<Unit>("#"))
        @JvmField
        val CASTLE = GregChessModule.register("castle", MoveNameTokenType<BoardSide>(BoardSide::castles))
        @JvmField
        val EN_PASSANT = GregChessModule.register("en_passant", MoveNameTokenType<Unit>(""))
    }
}

val MoveNameTokenType<Unit>.mk get() = of(Unit)

@Serializable
data class MoveNameToken<T>(val type: MoveNameTokenType<T>, val value: T) {
    val pgn: String get() = type.toPgnString(value)
}

typealias MoveName = List<MoveNameToken<*>>

val MoveName.pgn get() = joinToString("") { it.pgn }

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

fun MoveName.checkForChecks(side: Side, game: ChessGame): MoveName {
    game.board.updateMoves()
    val pieces = game.board.piecesOf(!side)
    val inCheck = game.variant.isInCheck(game, !side)
    val noMoves = pieces.all { game.board.getMoves(it.pos).none { m -> game.variant.isLegal(m, game) } }
    return when {
        inCheck && noMoves -> this + MoveNameTokenType.CHECKMATE.mk
        inCheck -> this + MoveNameTokenType.CHECK.mk
        else -> this
    }
}

