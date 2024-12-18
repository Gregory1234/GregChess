package gregc.gregchess.chess.variant

import assertk.Assert
import assertk.all
import assertk.assertions.*
import gregc.gregchess.*
import gregc.gregchess.board.*
import gregc.gregchess.chess.*
import gregc.gregchess.component.Component
import gregc.gregchess.match.ChessMatch
import gregc.gregchess.match.ChessTimeManager
import gregc.gregchess.move.Move
import gregc.gregchess.move.trait.*
import gregc.gregchess.piece.*
import gregc.gregchess.player.ChessSideManager
import gregc.gregchess.variant.ChessVariant

open class VariantTests(val variant: ChessVariant, val variantOptions: Long, val extraComponents: Collection<Component> = emptyList()) {

    protected fun mkMatch(fen: FEN) =
        ChessMatch(TestChessEnvironment, TestMatchInfo(), variant, listOf(ChessSideManager(TestChessPlayer(false), TestChessPlayer(false)), Chessboard(variant, variantOptions, fen), ChessTimeManager()) + extraComponents, 0).start()

    protected fun Chessboard.getMove(from: Pos, to: Pos) = get(from)?.getLegalMoves(this)?.singleOrNull { it.display == to }

    protected val Move.name get() = variant.pgnMoveFormatter.format(this)

    protected fun Assert<ChessMatch>.pieceAt(pos: Pos) = prop("board[$pos]") { it.board[pos] }.isNotNull()

    protected fun Assert<BoardPiece>.legalMoves(match: ChessMatch) = prop("legalMoves") { it.getLegalMoves(match.board) }

    protected fun Assert<List<Move>>.getTo(display: Pos) = prop(display.toString()) { it.singleOrNull { m -> m.display == display } }

    protected fun Assert<List<Move>>.onlyGetTo(vararg displays: Pos) = transform { it.map { m -> m.display } }.containsExactlyInAnyOrder(*displays)

    protected fun ChessboardFacade.setNotMoved(pos: Pos) {
        get(pos)?.copy(hasMoved = false)?.let(component::plusAssign)
        updateMoves()
    }

    protected fun ChessboardFacade.addEnPassantFlag(pos: Pos) {
        set(pos, ChessFlag.EN_PASSANT, 1)
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

    protected fun Assert<Move>.createsFlagsExactly(vararg flags: Pair<Pos, Pair<ChessFlag, Int>>) =
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

    protected fun Assert<Move>.resolveName(match: ChessMatch, promotion: Piece? = null) = transform {
        it.promotionTrait?.promotion = promotion
        match.resolveName(it)
        it
    }

    protected fun Assert<Move>.isNamed(name: String) = prop("name") { it.name }.isEqualTo(name)

    protected fun Assert<Move>.piece(name: String) = prop(Move::pieceTracker).prop(name) { it.getOrNull(name) }

    protected fun Assert<PlacedPiece>.boardPiece() = isInstanceOf(BoardPiece::class)

}