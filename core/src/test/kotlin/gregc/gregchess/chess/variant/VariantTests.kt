package gregc.gregchess.chess.variant

import assertk.Assert
import assertk.all
import assertk.assertions.*
import gregc.gregchess.chess.*
import gregc.gregchess.chess.component.Chessboard
import gregc.gregchess.chess.component.Component
import gregc.gregchess.chess.move.*
import gregc.gregchess.chess.piece.*

open class VariantTests(val variant: ChessVariant, val extraComponents: Collection<Component> = emptyList()) {
    private val playerA = TestPlayer("A")
    private val playerB = TestPlayer("B")

    protected fun mkGame(fen: FEN) =
        ChessGame(TestChessEnvironment, variant, listOf(Chessboard(variant, fen)) + extraComponents, byColor(playerA, playerB)).start()

    protected fun Chessboard.getMove(from: Pos, to: Pos) = get(from)?.getLegalMoves(this)?.singleOrNull { it.display == to }

    protected val Move.name get() = variant.pgnMoveFormatter.format(this)

    protected fun Assert<ChessGame>.pieceAt(pos: Pos) = prop("board[$pos]") { it.board[pos] }.isNotNull()

    protected fun Assert<BoardPiece>.legalMoves(game: ChessGame) = prop("legalMoves") { it.getLegalMoves(game.board) }

    protected fun Assert<List<Move>>.getTo(display: Pos) = prop(display.toString()) { it.singleOrNull { m -> m.display == display } }

    protected fun Assert<List<Move>>.onlyGetTo(vararg displays: Pos) = transform { it.map { m -> m.display } }.containsExactlyInAnyOrder(*displays)

    protected fun Chessboard.setNotMoved(pos: Pos) {
        get(pos)?.copy(hasMoved = false)?.let(::plusAssign)
        updateMoves()
    }

    protected fun Chessboard.addEnPassantFlag(pos: Pos) {
        addFlag(pos, ChessFlag.EN_PASSANT, 1u)
        updateMoves()
    }

    protected fun Assert<Move>.hasTraitsExactly(vararg types: MoveTraitType<*>) =
        prop("traits") { it.traits.map { t -> t.type } }.containsExactlyInAnyOrder(*types)

    protected fun Assert<Move>.hasExtraTraitsExactly(vararg types: MoveTraitType<*>) =
        hasTraitsExactly(MoveTraitType.CHECK, MoveTraitType.HALFMOVE_CLOCK, *types)

    protected fun Assert<Move>.isNormalMove() = prop(Move::isPhantomMove).isFalse()

    protected fun Assert<Move>.needsEmptyExactly(vararg neededEmpty: Pos) = prop(Move::neededEmpty).containsExactlyInAnyOrder(*neededEmpty)

    protected fun Assert<Move>.passesThroughExactly(vararg passedThrough: Pos) = prop(Move::passedThrough).containsExactlyInAnyOrder(*passedThrough)

    protected fun Assert<Move>.stopsBlockingExactly(vararg stopBlocking: Pos) = prop(Move::stopBlocking).containsExactlyInAnyOrder(*stopBlocking)

    protected fun Assert<Move>.startsBlockingExactly(vararg startBlocking: Pos) = prop(Move::startBlocking).containsExactlyInAnyOrder(*startBlocking)

    protected fun Assert<Move>.targets(target: Pos) = trait(MoveTraitType.TARGET).prop(TargetTrait::target).isEqualTo(target)

    protected fun Assert<Move>.requiresFlagsExactly(vararg flags: Pair<Pos, ChessFlag>) =
        trait(MoveTraitType.REQUIRE_FLAG).prop(RequireFlagTrait::flags).transform { it.toList().flatMap { (k,v) -> v.map { f -> k to f } } }.containsExactlyInAnyOrder(*flags)

    protected fun Assert<Move>.createsFlagsExactly(vararg flags: Pair<Pos, Pair<ChessFlag, UInt>>) =
        trait(MoveTraitType.FLAG).prop(FlagTrait::flags).transform { it.toList().flatMap { (k,v) -> v.map { (f, a) -> k to (f to a) } } }.containsExactlyInAnyOrder(*flags)

    protected fun Assert<Move>.promotesTo(vararg promotions: Piece) =
        trait(MoveTraitType.PROMOTION).prop(PromotionTrait::promotions).containsExactly(*promotions)

    protected fun Assert<Move>.captures(capture: Pos, required: Boolean = false, by: Color? = null) = trait(MoveTraitType.CAPTURE).all {
        prop(CaptureTrait::capture).isEqualTo(capture)
        prop(CaptureTrait::by).isEqualTo(by)
        prop(CaptureTrait::hasToCapture).isEqualTo(required)
    }

    protected fun Assert<Move>.castles(side: BoardSide, target: Pos, rookOrigin: Pos, rookTarget: Pos) = all {
        piece("rook").isNotNull().boardPiece().prop(BoardPiece::pos).isEqualTo(rookOrigin)
        trait(MoveTraitType.CASTLES).all {
            prop(CastlesTrait::side).isEqualTo(side)
            prop(CastlesTrait::target).isEqualTo(target)
            prop(CastlesTrait::rookTarget).isEqualTo(rookTarget)
        }
    }

    @Suppress("UNCHECKED_CAST")
    protected fun <T : MoveTrait> Assert<Move>.trait(type: MoveTraitType<T>) =
        prop("traits[$type]") { it.traits.singleOrNull { t -> t.type == type } }.isNotNull() as Assert<T>

    protected fun Assert<Move>.afterExecution(game: ChessGame, promotion: Piece? = null) = transform {
        it.getTrait<PromotionTrait>()?.promotion = promotion
        game.finishMove(it)
        it
    }

    protected fun Assert<Move>.isNamed(name: String) = prop("name") { it.name }.isEqualTo(name)

    protected fun Assert<Move>.piece(name: String) = prop(Move::pieceTracker).prop(name) { it.getOrNull(name) }

    protected fun Assert<PlacedPiece>.boardPiece() = isInstanceOf(BoardPiece::class)

}