package gregc.gregchess.chess

import gregc.gregchess.*
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.*
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlin.math.abs

class MoveData(
    val piece: Piece, val origin: Square, val target: Square, val name: MoveName,
    val captured: Boolean, val display: Square = target, val undo: () -> Unit
) {

    override fun toString() = "MoveData(piece=$piece, name=$name)"

    fun clear() {
        origin.previousMoveMarker = null
        display.previousMoveMarker = null
    }

    fun render() {
        origin.previousMoveMarker = Floor.LAST_START
        display.previousMoveMarker = Floor.LAST_END
    }
}

open class MoveCandidate(
    val piece: BoardPiece, val target: Square, val floor: Floor,
    val pass: Collection<Pos>, val help: Collection<BoardPiece> = emptyList(), val needed: Collection<Pos> = pass,
    val flagsNeeded: Collection<Pair<Pos, ChessFlagType>> = emptyList(),
    val flagsAdded: Collection<Pair<Pos, ChessFlag>> = emptyList(),
    val control: Square? = target, val promotions: Collection<Piece>? = null,
    val mustCapture: Boolean = false, val display: Square = target
) {

    override fun toString() = buildString {
        append("MoveCandidate(piece=")
        append(piece.piece)
        append(", target=")
        append(target.pos)
        append(", pass=[")
        append(pass.joinToString())
        append("], help=[")
        append(help.map { it.piece }.joinToString())
        append("], needed=[")
        append(needed.joinToString())
        append("], control=")
        append(control?.pos)
        append(", promotions=")
        append(promotions)
        append(", mustCapture=")
        append(mustCapture)
        append(", display=")
        append(display.pos)
        append(")")
    }

    val origin = piece.square

    open fun execute(promotion: Piece? = null): MoveData {
        if ((promotion == null) != (promotions == null))
            throw IllegalArgumentException(promotion.toString())
        if (promotion != null && promotions != null && promotion !in promotions)
            throw IllegalArgumentException(promotion.toString())
        val base = baseName(promotion)
        val hasMoved = piece.hasMoved
        val ct = captured?.capture(piece.side)
        piece.move(target)
        promotion?.let { piece.promote(it) }
        game.variant.finishMove(this)
        for ((p, f) in flagsAdded)
            game.board[p]?.flags?.plusAssign(f)
        val hmc =
            if (piece.type == PieceType.PAWN || ct != null) board.resetMovesSinceLastCapture() else board.increaseMovesSinceLastCapture()
        val ch = base.checkForChecks(piece.side, game)
        return MoveData(piece.piece, origin, target, ch, ct != null, display) {
            hmc()
            promotion?.let { piece.square.piece?.demote(piece) }
            piece.move(origin)
            ct?.let { captured?.resurrect(it) }
            piece.force(hasMoved)
        }
    }

    open fun baseName(promotion: Piece? = null): MoveName = buildList {
        if (piece.type != PieceType.PAWN) {
            this += MoveNameTokenType.PIECE_TYPE.of(piece.type)
            this += MoveNameTokenType.UNIQUENESS_COORDINATE.of(getUniquenessCoordinate(piece, target))
        } else if (control != null)
            this += MoveNameTokenType.UNIQUENESS_COORDINATE.of(UniquenessCoordinate(file = origin.pos.file))
        if (captured != null)
            this += MoveNameTokenType.CAPTURE.mk
        this += MoveNameTokenType.TARGET.of(target.pos)
        promotion?.let {
            this += MoveNameTokenType.PROMOTION.of(it.type)
        }
    }

    fun render() {
        display.moveMarker = floor
    }

    fun clear() {
        display.moveMarker = null
    }

    val board = origin.board
    val game = origin.game

    val blocks: List<BoardPiece>
        get() = needed.mapNotNull { board[it]?.piece }

    val captured = control?.piece
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
        p.square.bakedMoves.orEmpty().any { it.target == target && game.variant.isLegal(it) }
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
    val noMoves = pieces.all { game.board.getMoves(it.pos).none(game.variant::isLegal) }
    return when {
        inCheck && noMoves -> this + MoveNameTokenType.CHECKMATE.mk
        inCheck -> this + MoveNameTokenType.CHECK.mk
        else -> this
    }
}

fun defaultColor(square: Square) = if (square.piece == null) Floor.MOVE else Floor.CAPTURE

fun jumps(piece: BoardPiece, dirs: Collection<Dir>) =
    dirs.map { piece.pos + it }.filter { it.isValid() }.mapNotNull { piece.square.board[it] }.map {
        MoveCandidate(piece, it, defaultColor(it), emptyList())
    }

fun interface MoveScheme {
    fun generate(piece: BoardPiece): List<MoveCandidate>
}

class JumpMovement(private val dirs: Collection<Dir>) : MoveScheme {
    override fun generate(piece: BoardPiece): List<MoveCandidate> = jumps(piece, dirs)
}

class RayMovement(private val dirs: Collection<Dir>) : MoveScheme {
    override fun generate(piece: BoardPiece): List<MoveCandidate> =
        dirs.flatMap { dir ->
            PosSteps(piece.pos + dir, dir).mapIndexedNotNull { index, pos ->
                piece.square.board[pos]?.let {
                    MoveCandidate(piece, it, defaultColor(it), PosSteps(piece.pos + dir, dir, index))
                }
            }
        }
}

object KingMovement : MoveScheme {

    class Castles(
        piece: BoardPiece, target: Square,
        val rook: BoardPiece, val rookTarget: Square,
        pass: Collection<Pos>, needed: Collection<Pos>, display: Square
    ) : MoveCandidate(
        piece, target,
        Floor.SPECIAL, pass, help = listOf(piece, rook), needed = needed,
        control = null, display = display
    ) {
        override fun execute(promotion: Piece?): MoveData {
            val base = baseName(promotion)
            val rookOrigin = rook.square
            BoardPiece.autoMove(mapOf(piece to target, rook to rookTarget))
            val ch = base.checkForChecks(piece.side, game)
            val hmc = board.increaseMovesSinceLastCapture()
            return MoveData(piece.piece, origin, target, ch, false, display) {
                hmc()
                BoardPiece.autoMove(mapOf(piece to origin, rook to rookOrigin))
                piece.force(false)
                rook.force(false)
            }
        }

        val boardSide get() = if (piece.pos.file > rook.pos.file) BoardSide.QUEENSIDE else BoardSide.KINGSIDE

        override fun baseName(promotion: Piece?) = listOf(MoveNameTokenType.CASTLE.of(boardSide))
    }

    override fun generate(piece: BoardPiece): List<MoveCandidate> {
        val castles = mutableListOf<MoveCandidate>()

        val game = piece.square.game

        if (!piece.hasMoved)
            for (rook in game.board.piecesOf(piece.side, PieceType.ROOK)) {
                if (rook.hasMoved || rook.pos.rank != piece.pos.rank)
                    continue

                if (game.settings.simpleCastling) {
                    val target: Square
                    val rookTarget: Square
                    if (abs(rook.pos.file - piece.pos.file) == 1) {
                        target = rook.square
                        rookTarget = piece.square
                    } else {
                        target = game.board[piece.pos.copy(file = piece.pos.file.towards(rook.pos.file, 2))]!!
                        rookTarget = game.board[piece.pos.copy(file = piece.pos.file.towards(rook.pos.file, 1))]!!
                    }
                    val pass = between(piece.pos.file, target.pos.file).map { piece.pos.copy(file = it) }
                    val needed = between(piece.pos.file, rook.pos.file).map { piece.pos.copy(file = it) }
                    castles += Castles(
                        piece, target,
                        rook, rookTarget,
                        pass + piece.pos, needed,
                        target
                    )
                } else {
                    val target: Square
                    val rookTarget: Square
                    val pass: List<Pos>
                    val needed: List<Pos>
                    if (piece.pos.file > rook.pos.file) {
                        target = game.board[piece.pos.copy(file = 2)]!!
                        rookTarget = game.board[piece.pos.copy(file = 3)]!!
                        pass = between(piece.pos.file, 2).map { piece.pos.copy(file = it) }
                        needed = (rook.pos.file..3).map { piece.pos.copy(file = it) }
                    } else {
                        target = game.board[piece.pos.copy(file = 6)]!!
                        rookTarget = game.board[piece.pos.copy(file = 5)]!!
                        pass = between(piece.pos.file, 6).map { piece.pos.copy(file = it) }
                        needed = (rook.pos.file..5).map { piece.pos.copy(file = it) }
                    }
                    castles += Castles(
                        piece, target,
                        rook, rookTarget,
                        pass + piece.pos, pass + needed,
                        if (game.board.chess960) rook.square else target
                    )
                }
            }

        return jumps(piece, rotationsOf(1, 0) + rotationsOf(1, 1)) + castles
    }

}

open class PawnMovementConfig {
    open fun canDouble(piece: PieceInfo): Boolean = !piece.hasMoved
    open fun promotions(piece: PieceInfo): List<Piece> =
        listOf(PieceType.QUEEN, PieceType.ROOK, PieceType.BISHOP, PieceType.KNIGHT).map { it.of(piece.side) }
}


class PawnMovement(private val config: PawnMovementConfig = PawnMovementConfig()) : MoveScheme {
    companion object {
        @JvmField
        val EN_PASSANT = GregChessModule.register("en_passant", ChessFlagType(1u))
        private fun ifProm(promotions: Any?, floor: Floor) = if (promotions == null) floor else Floor.SPECIAL
    }

    class EnPassantCapture(piece: BoardPiece, target: Square, control: Square) :
        MoveCandidate(
            piece, target, Floor.CAPTURE, emptyList(), control = control, mustCapture = true,
            flagsNeeded = listOf(target.pos to EN_PASSANT)
        ) {
        override fun execute(promotion: Piece?): MoveData {
            val base = baseName(promotion)
            val hasMoved = piece.hasMoved
            val ct = captured?.capture(piece.side)
            piece.move(target)
            val ch = base.checkForChecks(piece.side, game) + MoveNameTokenType.EN_PASSANT.mk
            val hmc = board.resetMovesSinceLastCapture()
            return MoveData(piece.piece, origin, target, ch, true, display) {
                hmc()
                piece.move(origin)
                ct?.let { captured?.resurrect(it) }
                piece.force(hasMoved)
            }
        }
    }

    override fun generate(piece: BoardPiece): List<MoveCandidate> =
        buildList {
            fun promotions(pos: Pos) = if (pos.rank in listOf(0, 7)) config.promotions(piece.info) else null

            piece.square.board[piece.pos + piece.side.dir]?.let { t ->
                val promotions = promotions(t.pos)
                this += MoveCandidate(
                    piece, t, ifProm(promotions, Floor.MOVE), emptyList(),
                    control = null, promotions = promotions
                )
            }
            if (config.canDouble(piece.info))
                piece.square.board[piece.pos + piece.side.dir * 2]?.let { t ->
                    val promotions = promotions(t.pos)
                    val pass = piece.pos + piece.side.dir
                    this += MoveCandidate(
                        piece, t, ifProm(promotions, Floor.MOVE), listOf(pass),
                        control = null, promotions = promotions, flagsAdded = listOf(pass to ChessFlag(EN_PASSANT))
                    )
                }
            for (s in listOf(-1, 1)) {
                piece.square.board[piece.pos + Pair(s, piece.side.direction)]?.let { t ->
                    val promotions = promotions(t.pos)
                    this += MoveCandidate(
                        piece, t, ifProm(promotions, Floor.CAPTURE), emptyList(),
                        promotions = promotions, mustCapture = true
                    )
                }
                val p = piece.square.board[piece.pos.plusF(s)]
                if (p != null)
                    piece.square.board[piece.pos + Pair(s, piece.side.direction)]?.let {
                        this += EnPassantCapture(piece, it, p)
                    }
            }
        }
}
