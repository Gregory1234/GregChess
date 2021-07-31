package gregc.gregchess.chess

import gregc.gregchess.between
import gregc.gregchess.rotationsOf

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

abstract class MoveCandidate(
    val piece: BoardPiece, val target: Square, val floor: Floor,
    val pass: Collection<Pos>, val help: Collection<BoardPiece> = emptyList(), val needed: Collection<Pos> = pass,
    val flagsNeeded: Collection<Pair<Pos, ChessFlagType>> = emptyList(),
    val flagsAdded: Collection<Pair<Pos, ChessFlag>> = emptyList(),
    val control: Square? = target, val promotions: Collection<Piece>? = null,
    val mustCapture: Boolean = false, val display: Square = target
) {

    override fun toString() =
        "MoveCandidate(piece=${piece.piece}, target=${target.pos}, pass=[${pass.joinToString()}], help=[${help.map{it.piece}.joinToString()}], needed=[${needed.joinToString()}], control=${control?.pos}, promotions=$promotions, mustCapture=$mustCapture, display=${display.pos})"

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
        flagsAdded.forEach { (p, f) ->
            game.board[p]?.flags?.plusAssign(f)
        }
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

class MoveNameTokenType<T>(val name: String, @JvmField val toPgnString: (T) -> String = Any?::toString) {
    constructor(name: String, constPGN: String): this(name, { constPGN })

    companion object {
        @JvmField
        val PIECE_TYPE = MoveNameTokenType<PieceType>("PIECE_TYPE") { it.char.uppercase() }
        @JvmField
        val UNIQUENESS_COORDINATE = MoveNameTokenType<UniquenessCoordinate>("UNIQUENESS_COORDINATE")
        @JvmField
        val CAPTURE = MoveNameTokenType<Unit>("CAPTURE", "x")
        @JvmField
        val TARGET = MoveNameTokenType<Pos>("TARGET")
        @JvmField
        val PROMOTION = MoveNameTokenType<PieceType>("PROMOTION") { "=" + it.char.uppercase() }
        @JvmField
        val CHECK = MoveNameTokenType<Unit>("CHECK", "+")
        @JvmField
        val CHECKMATE = MoveNameTokenType<Unit>("CHECKMATE", "#")
        @JvmField
        val CASTLE = MoveNameTokenType<BoardSide>("CASTLE", BoardSide::castles)
        @JvmField
        val EN_PASSANT = MoveNameTokenType<Unit>("EN_PASSANT", "")
    }

    override fun toString(): String = name
    fun of(v: T) = MoveNameToken(this, v)
}

val MoveNameTokenType<Unit>.mk get() = of(Unit)

data class MoveNameToken<T>(val type: MoveNameTokenType<T>, val value: T) {
    val pgn: String get() = type.toPgnString(value)
}

typealias MoveName = List<MoveNameToken<*>>

val MoveName.pgn get() = joinToString("") { it.pgn }

data class UniquenessCoordinate(val file: Int? = null, val rank: Int? = null) {
    constructor(pos: Pos): this(pos.file, pos.rank)
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

fun jumps(piece: BoardPiece, dirs: Collection<Dir>, f: (Square) -> MoveCandidate) =
    dirs.map { piece.pos + it }.filter { it.isValid() }.mapNotNull { piece.square.board[it] }.map(f)

fun rays(piece: BoardPiece, dirs: Collection<Dir>, f: (Int, Dir, Square) -> MoveCandidate) =
    dirs.flatMap { dir ->
        PosSteps(piece.pos + dir, dir).mapIndexedNotNull { index, pos ->
            piece.square.board[pos]?.let { f(index + 1, dir, it) }
        }
    }

fun knightMovement(piece: BoardPiece): List<MoveCandidate> {
    class KnightJump(piece: BoardPiece, target: Square, floor: Floor) : MoveCandidate(piece, target, floor, emptyList())

    return jumps(piece, rotationsOf(2, 1)) {
        KnightJump(piece, it, defaultColor(it))
    }
}

fun rookMovement(piece: BoardPiece): List<MoveCandidate> {
    class RookMove(piece: BoardPiece, target: Square, dir: Dir, amount: Int, floor: Floor) :
        MoveCandidate(piece, target, floor, PosSteps(piece.pos + dir, dir, amount - 1))

    return rays(piece, rotationsOf(1, 0)) { index, dir, square ->
        RookMove(piece, square, dir, index, defaultColor(square))
    }
}

fun bishopMovement(piece: BoardPiece): List<MoveCandidate> {
    class BishopMove(piece: BoardPiece, target: Square, dir: Dir, amount: Int, floor: Floor) :
        MoveCandidate(piece, target, floor, PosSteps(piece.pos + dir, dir, amount - 1))

    return rays(piece, rotationsOf(1, 1)) { index, dir, square ->
        BishopMove(piece, square, dir, index, defaultColor(square))
    }
}

fun queenMovement(piece: BoardPiece): List<MoveCandidate> {
    class QueenMove(piece: BoardPiece, target: Square, dir: Dir, amount: Int, floor: Floor) :
        MoveCandidate(piece, target, floor, PosSteps(piece.pos + dir, dir, amount - 1))

    return rays(piece, rotationsOf(1, 0) + rotationsOf(1, 1)) { index, dir, square ->
        QueenMove(piece, square, dir, index, defaultColor(square))
    }
}

fun kingMovement(piece: BoardPiece): List<MoveCandidate> {
    class KingMove(piece: BoardPiece, target: Square, floor: Floor) : MoveCandidate(piece, target, floor, emptyList())

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

        override fun baseName(promotion: Piece?) = listOf(MoveNameTokenType.CASTLE.of(
            if (piece.pos.file > rook.pos.file) BoardSide.QUEENSIDE else BoardSide.KINGSIDE
        ))
    }

    val castles = mutableListOf<MoveCandidate>()

    val game = piece.square.game

    if (!piece.hasMoved)
        game.board.piecesOf(piece.side, PieceType.ROOK)
            .filter { !it.hasMoved && it.pos.rank == piece.pos.rank }
            .forEach { rook ->

                if (game.settings.simpleCastling) {
                    TODO()
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

    return jumps(piece, rotationsOf(1, 0) + rotationsOf(1, 1)) {
        KingMove(piece, it, defaultColor(it))
    } + castles
}

interface PawnMovementConfig {
    fun canDouble(piece: PieceInfo): Boolean = DefaultPawnConfig.canDouble(piece)
    fun promotions(piece: PieceInfo): List<Piece> = DefaultPawnConfig.promotions(piece)
}

object DefaultPawnConfig : PawnMovementConfig {
    override fun canDouble(piece: PieceInfo): Boolean = !piece.hasMoved
    override fun promotions(piece: PieceInfo): List<Piece> =
        listOf(PieceType.QUEEN, PieceType.ROOK, PieceType.BISHOP, PieceType.KNIGHT).map { it.of(piece.side) }
}

@JvmField
val EN_PASSANT = ChessFlagType("EN_PASSANT", 1u)

fun pawnMovement(config: PawnMovementConfig): (piece: BoardPiece)-> List<MoveCandidate> = { piece ->

    fun ifProm(promotions: Any?, floor: Floor) =
        if (promotions == null) floor else Floor.SPECIAL

    fun promotions(pos: Pos) = if (pos.rank in listOf(0, 7)) config.promotions(piece.info) else null

    class PawnPush(piece: BoardPiece, target: Square, pass: Pos?, promotions: Collection<Piece>?) : MoveCandidate(
        piece, target, ifProm(promotions, Floor.MOVE), listOfNotNull(pass), control = null, promotions = promotions,
        flagsAdded = listOfNotNull(pass?.to(ChessFlag(EN_PASSANT)))
    )

    class PawnCapture(piece: BoardPiece, target: Square, promotions: Collection<Piece>?) : MoveCandidate(
        piece, target, ifProm(promotions, Floor.CAPTURE), emptyList(), promotions = promotions, mustCapture = true
    )

    class EnPassantCapture(piece: BoardPiece, target: Square, control: Square) :
        MoveCandidate(piece, target, Floor.CAPTURE, emptyList(), control = control, mustCapture = true,
        flagsNeeded = listOf(target.pos to EN_PASSANT)) {
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

    buildList {
        piece.square.board[piece.pos.plusR(piece.side.direction)]?.let { t ->
            this += PawnPush(piece, t, null, promotions(t.pos))
        }
        if (config.canDouble(piece.info))
            piece.square.board[piece.pos.plusR(2 * piece.side.direction)]?.let { t ->
                this += PawnPush(piece, t, piece.pos.plusR(piece.side.direction), promotions(t.pos))
            }
        for (s in listOf(-1, 1)) {
            piece.square.board[piece.pos + Pair(s, piece.side.direction)]?.let { t ->
                this += PawnCapture(piece, t, promotions(t.pos))
            }
            val p = piece.square.board[piece.pos.plusF(s)]
            if (p != null)
                piece.square.board[piece.pos + Pair(s, piece.side.direction)]?.let {
                    this += EnPassantCapture(piece, it, p)
                }
        }
    }
}
