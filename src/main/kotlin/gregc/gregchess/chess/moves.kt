package gregc.gregchess.chess

import gregc.gregchess.ConfigManager
import gregc.gregchess.between
import gregc.gregchess.rotationsOf
import org.bukkit.Material
import kotlin.math.abs

class MoveData(
    val piece: Piece,
    val origin: Square,
    val target: Square,
    val name: String,
    val standardName: String,
    val captured: Boolean,
    val display: Square = target,
    val undo: () -> Unit
) {

    override fun toString() =
        "MoveData(piece = $piece, name = $name, standardName = $standardName)"

    fun clear() {
        origin.previousMoveMarker = null
        display.previousMoveMarker = null
    }

    fun render() {
        origin.previousMoveMarker = Material.BROWN_CONCRETE
        display.previousMoveMarker = Material.ORANGE_CONCRETE
    }
}

abstract class MoveCandidate(
    val piece: Piece, val target: Square, val floor: Material,
    val pass: Collection<Pos>, val help: Collection<Piece> = emptyList(),
    val needed: Collection<Pos> = pass,
    val control: Square? = target, val promotion: PieceType? = null,
    val mustCapture: Boolean = false, val display: Square = target
) {

    companion object {
        val RED = Material.RED_CONCRETE
        val GREEN = Material.GREEN_CONCRETE
        val BLUE = Material.BLUE_CONCRETE
    }

    override fun toString() =
        "MoveCandidate(piece = $piece, target = ${target.pos}, pass = [${pass.joinToString()}], help = [${help.joinToString()}], needed = [${needed.joinToString()}], control = ${control?.pos}, promotion = $promotion, mustCapture = $mustCapture, display = ${display.pos})"

    val origin = piece.square

    open fun execute(): MoveData {
        val base = baseName()
        val standardBase = baseStandardName()
        val hasMoved = piece.hasMoved
        val ct = captured?.capture(piece.side)
        piece.move(target)
        promotion?.let { piece.promote(it) }
        game.variant.finishMove(this)
        val hmc =
            if (piece.type == PieceType.PAWN || ct != null) board.resetMovesSinceLastCapture() else board.increaseMovesSinceLastCapture()
        val ch = checkForChecks(piece.side, game)
        return MoveData(piece, origin, target, base + ch, standardBase + ch, ct != null, display) {
            hmc()
            promotion?.let { piece.square.piece?.demote(piece) }
            piece.move(origin)
            ct?.let { captured?.resurrect(it) }
            piece.force(hasMoved)
        }
    }

    open fun baseName() = buildString {
        if (piece.type != PieceType.PAWN) {
            append(piece.type.char.uppercaseChar())
            append(getUniquenessCoordinate(piece, target))
        } else if (control != null)
            append(piece.pos.fileStr)
        if (captured != null)
            append(ConfigManager.getString("Chess.Capture"))
        promotion?.let { append(it.char.uppercaseChar()) }
        append(target.pos)
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
        display.render()
    }

    fun clear() {
        display.moveMarker = null
        display.render()
    }

    val board = origin.board
    val game = origin.game

    val blocks: List<Piece>
        get() = needed.mapNotNull { board[it]?.piece }

    val captured = control?.piece
}

fun getUniquenessCoordinate(piece: Piece, target: Square): String {
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

fun defaultColor(square: Square) =
    if (square.piece == null) MoveCandidate.GREEN else MoveCandidate.RED

inline fun jumps(piece: Piece, dirs: Collection<Pair<Int, Int>>, f: (Square) -> MoveCandidate) =
    dirs.map { piece.pos + it }.filter { it.isValid() }
        .mapNotNull { piece.square.board[it] }.map(f)

inline fun rays(piece: Piece, dirs: Collection<Pair<Int, Int>>, f: (Int, Pair<Int, Int>, Square) -> MoveCandidate) =
    dirs.flatMap { dir ->
        PosSteps(piece.pos + dir, dir).mapIndexedNotNull { index, pos ->
            piece.square.board[pos]?.let { f(index + 1, dir, it) }
        }
    }

fun knightMovement(piece: Piece): List<MoveCandidate> {
    class KnightJump(piece: Piece, target: Square, floor: Material) : MoveCandidate(piece, target, floor, emptyList())

    return jumps(piece, rotationsOf(2, 1)) {
        KnightJump(piece, it, defaultColor(it))
    }
}

fun rookMovement(piece: Piece): List<MoveCandidate> {
    class RookMove(piece: Piece, target: Square, dir: Pair<Int, Int>, amount: Int, floor: Material)
        : MoveCandidate(piece, target, floor, PosSteps(piece.pos + dir, dir, amount - 1))

    return rays(piece, rotationsOf(1, 0)) { index, dir, square ->
        RookMove(piece, square, dir, index, defaultColor(square))
    }
}

fun bishopMovement(piece: Piece): List<MoveCandidate> {
    class BishopMove(piece: Piece, target: Square, dir: Pair<Int, Int>, amount: Int, floor: Material)
        : MoveCandidate(piece, target, floor, PosSteps(piece.pos + dir, dir, amount - 1))

    return rays(piece, rotationsOf(1, 1)) { index, dir, square ->
        BishopMove(piece, square, dir, index, defaultColor(square))
    }
}

fun queenMovement(piece: Piece): List<MoveCandidate> {
    class QueenMove(piece: Piece, target: Square, dir: Pair<Int, Int>, amount: Int, floor: Material)
        : MoveCandidate(piece, target, floor, PosSteps(piece.pos + dir, dir, amount - 1))

    return rays(piece, rotationsOf(1, 0) + rotationsOf(1, 1)) { index, dir, square ->
        QueenMove(piece, square, dir, index, defaultColor(square))
    }
}

fun kingMovement(piece: Piece): List<MoveCandidate> {
    class KingMove(piece: Piece, target: Square, floor: Material) : MoveCandidate(piece, target, floor, emptyList())

    class Castles(
        piece: Piece, target: Square,
        val rook: Piece, val rookTarget: Square,
        pass: Collection<Pos>, needed: Collection<Pos>, display: Square
    ) : MoveCandidate(
        piece, target,
        BLUE, pass, help = listOf(piece, rook), needed = needed,
        control = null, display = display
    ) {
        override fun execute(): MoveData {
            val base = baseName()
            val standardBase = baseStandardName()
            val rookOrigin = rook.square
            Piece.autoMove(mapOf(piece to target, rook to rookTarget))
            val ch = checkForChecks(piece.side, game)
            val hmc = board.increaseMovesSinceLastCapture()
            return MoveData(piece, origin, target, base + ch, standardBase + ch, false, display) {
                hmc()
                Piece.autoMove(mapOf(piece to origin, rook to rookOrigin))
                piece.force(false)
                rook.force(false)
            }
        }

        override fun baseName() = baseStandardName()

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

fun pawnMovement(piece: Piece): List<MoveCandidate> {

    fun ifProm(promotion: PieceType?, floor: Material) =
        if (promotion == null) floor else MoveCandidate.BLUE

    fun handlePromotion(pos: Pos, f: (PieceType?) -> Unit) =
        if (pos.rank in listOf(0, 7)) piece.square.game.variant.promotions.forEach(f) else f(null)

    class PawnPush(piece: Piece, target: Square, pass: Pos?, promotion: PieceType?)
        : MoveCandidate(
            piece, target,
            ifProm(promotion, GREEN), listOfNotNull(pass),
            control = null, promotion = promotion
        )

    class PawnCapture(piece: Piece, target: Square, promotion: PieceType?) :
        MoveCandidate(
            piece, target,
            ifProm(promotion, RED), emptyList(),
            promotion = promotion, mustCapture = true
        )

    class EnPassantCapture(piece: Piece, target: Square, control: Square) :
        MoveCandidate(piece, target, RED, emptyList(), control = control, mustCapture = true) {
        override fun execute(): MoveData {
            val base = baseName()
            val standardBase = baseStandardName()
            val hasMoved = piece.hasMoved
            val ct = captured?.capture(piece.side)
            piece.move(target)
            val ch = checkForChecks(piece.side, game)
            val hmc = board.resetMovesSinceLastCapture()
            return MoveData(
                piece, origin, target, "$base$ch e.p.", standardBase + ch,
                true, display
            ) {
                hmc()
                piece.move(origin)
                ct?.let { captured?.resurrect(it) }
                piece.force(hasMoved)
            }
        }
    }

    val ret = mutableListOf<MoveCandidate>()
    piece.square.board[piece.pos.plusR(piece.side.direction)]?.let { t ->
        handlePromotion(t.pos) {
            ret += PawnPush(piece, t, null, it)
        }
    }
    if (!piece.hasMoved)
        piece.square.board[piece.pos.plusR(2 * piece.side.direction)]?.let { t ->
            handlePromotion(t.pos) {
                ret += PawnPush(piece, t, piece.pos.plusR(piece.side.direction), it)
            }
        }
    for (s in listOf(-1, 1)) {
        piece.square.board[piece.pos + Pair(s, piece.side.direction)]?.let { t ->
            handlePromotion(t.pos) {
                ret += PawnCapture(piece, t, it)
            }
        }
        val p = piece.square.board[piece.pos.plusF(s)]?.piece
        val lm = piece.square.board.lastMove
        if (p?.type == PieceType.PAWN && lm?.piece == p && abs(lm.origin.pos.rank - lm.target.pos.rank) == 2) {
            piece.square.board[piece.pos + Pair(s, piece.side.direction)]?.let {
                ret += EnPassantCapture(piece, it, p.square)
            }
        }
    }
    return ret
}
