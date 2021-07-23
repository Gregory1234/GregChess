package gregc.gregchess.chess

import gregc.gregchess.between
import gregc.gregchess.rotationsOf
import kotlin.math.abs

class MoveData(
    val piece: Piece, val origin: Square, val target: Square, val standardName: String,
    val captured: Boolean, val display: Square = target, val undo: () -> Unit
) {

    override fun toString() = "MoveData(piece=$piece, standardName=$standardName)"

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
    val control: Square? = target, val promotion: Piece? = null,
    val mustCapture: Boolean = false, val display: Square = target
) {

    override fun toString() =
        "MoveCandidate(piece=${piece.piece}, target=${target.pos}, pass=[${pass.joinToString()}], help=[${help.map{it.piece}.joinToString()}], needed=[${needed.joinToString()}], control=${control?.pos}, promotion=$promotion, mustCapture=$mustCapture, display=${display.pos})"

    val origin = piece.square

    open fun execute(): MoveData {
        val standardBase = baseStandardName()
        val hasMoved = piece.hasMoved
        val ct = captured?.capture(piece.side)
        piece.move(target)
        promotion?.let { piece.promote(it) }
        game.variant.finishMove(this)
        val hmc =
            if (piece.type == PieceType.PAWN || ct != null) board.resetMovesSinceLastCapture() else board.increaseMovesSinceLastCapture()
        val ch = checkForChecks(piece.side, game)
        return MoveData(piece.piece, origin, target, standardBase + ch, ct != null, display) {
            hmc()
            promotion?.let { piece.square.piece?.demote(piece) }
            piece.move(origin)
            ct?.let { captured?.resurrect(it) }
            piece.force(hasMoved)
        }
    }

    open fun baseStandardName() = buildString {
        if (piece.type != PieceType.PAWN) {
            append(piece.type.standardChar.uppercaseChar())
            append(getUniquenessCoordinate(piece, target))
        } else if (control != null)
            append(origin.pos.fileStr)
        if (captured != null)
            append("x")
        promotion?.let {
            append("=")
            append(it.standardChar.uppercaseChar())
        }
        append(target.pos)
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

fun getUniquenessCoordinate(piece: BoardPiece, target: Square): String {
    val game = target.game
    val pieces = game.board.pieces.filter { it.side == piece.side && it.type == piece.type }
    val consideredPieces = pieces.filter { p ->
        p.square.bakedMoves.orEmpty().any { it.target == target && game.variant.isLegal(it) }
    }
    return when {
        consideredPieces.size == 1 -> ""
        consideredPieces.count { it.pos.file == piece.pos.file } == 1 -> piece.pos.fileStr
        consideredPieces.count { it.pos.rank == piece.pos.rank } == 1 -> piece.pos.rankStr
        else -> piece.pos.toString()
    }
}

fun checkForChecks(side: Side, game: ChessGame): String {
    game.board.updateMoves()
    val pieces = game.board.piecesOf(!side)
    val inCheck = game.variant.isInCheck(game, !side)
    val noMoves = pieces.all { game.board.getMoves(it.pos).none(game.variant::isLegal) }
    return when {
        inCheck && noMoves -> "#"
        inCheck -> "+"
        else -> ""
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
        override fun execute(): MoveData {
            val base = baseStandardName()
            val rookOrigin = rook.square
            BoardPiece.autoMove(mapOf(piece to target, rook to rookTarget))
            val ch = checkForChecks(piece.side, game)
            val hmc = board.increaseMovesSinceLastCapture()
            return MoveData(piece.piece, origin, target, base + ch, false, display) {
                hmc()
                BoardPiece.autoMove(mapOf(piece to origin, rook to rookOrigin))
                piece.force(false)
                rook.force(false)
            }
        }

        override fun baseStandardName() = if (piece.pos.file > rook.pos.file) "O-O-O" else "O-O"
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

fun pawnMovement(config: PawnMovementConfig): (piece: BoardPiece)-> List<MoveCandidate> = { piece ->

    fun ifProm(promotion: Piece?, floor: Floor) =
        if (promotion == null) floor else Floor.SPECIAL

    fun handlePromotion(pos: Pos, f: (Piece?) -> Unit) =
        (piece.info.takeIf { pos.rank in listOf(0, 7) }?.let { config.promotions(piece.info) } ?: listOf(null)).forEach(f)

    class PawnPush(piece: BoardPiece, target: Square, pass: Pos?, promotion: Piece?) : MoveCandidate(
        piece, target, ifProm(promotion, Floor.MOVE), listOfNotNull(pass), control = null, promotion = promotion
    )

    class PawnCapture(piece: BoardPiece, target: Square, promotion: Piece?) : MoveCandidate(
        piece, target, ifProm(promotion, Floor.CAPTURE), emptyList(), promotion = promotion, mustCapture = true
    )

    class EnPassantCapture(piece: BoardPiece, target: Square, control: Square) :
        MoveCandidate(piece, target, Floor.CAPTURE, emptyList(), control = control, mustCapture = true) {
        override fun execute(): MoveData {
            val standardBase = baseStandardName()
            val hasMoved = piece.hasMoved
            val ct = captured?.capture(piece.side)
            piece.move(target)
            val ch = checkForChecks(piece.side, game)
            val hmc = board.resetMovesSinceLastCapture()
            return MoveData(piece.piece, origin, target, standardBase + ch, true, display) {
                hmc()
                piece.move(origin)
                ct?.let { captured?.resurrect(it) }
                piece.force(hasMoved)
            }
        }
    }

    buildList {
        piece.square.board[piece.pos.plusR(piece.side.direction)]?.let { t ->
            handlePromotion(t.pos) {
                this += PawnPush(piece, t, null, it)
            }
        }
        if (config.canDouble(piece.info))
            piece.square.board[piece.pos.plusR(2 * piece.side.direction)]?.let { t ->
                handlePromotion(t.pos) {
                    this += PawnPush(piece, t, piece.pos.plusR(piece.side.direction), it)
                }
            }
        for (s in listOf(-1, 1)) {
            piece.square.board[piece.pos + Pair(s, piece.side.direction)]?.let { t ->
                handlePromotion(t.pos) {
                    this += PawnCapture(piece, t, it)
                }
            }
            val p = piece.square.board[piece.pos.plusF(s)]?.piece
            val lm = piece.square.board.lastMove
            if (p?.type == PieceType.PAWN && lm?.piece == p.piece && abs(lm.origin.pos.rank - lm.target.pos.rank) == 2) {
                piece.square.board[piece.pos + Pair(s, piece.side.direction)]?.let {
                    this += EnPassantCapture(piece, it, p.square)
                }
            }
        }
    }
}
