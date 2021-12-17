package gregc.gregchess.fabric.chess.component

import gregc.gregchess.Loc
import gregc.gregchess.chess.*
import gregc.gregchess.chess.component.Component
import gregc.gregchess.chess.component.ComponentData
import gregc.gregchess.chess.move.*
import gregc.gregchess.chess.piece.BoardPiece
import gregc.gregchess.chess.piece.PieceEvent
import gregc.gregchess.fabric.blockpos
import gregc.gregchess.fabric.chess.*
import gregc.gregchess.fabric.loc
import kotlinx.serialization.Contextual
import kotlinx.serialization.Serializable
import net.minecraft.block.enums.DoubleBlockHalf
import net.minecraft.util.math.BlockPos
import net.minecraft.world.World
import kotlin.reflect.KClass

@Serializable
data class FabricRendererSettings(
    val controllerLoc: Loc,
    val world: @Contextual World
) : ComponentData<FabricRenderer> {
    constructor(controller: ChessControllerBlockEntity) : this(controller.pos.loc, controller.world!!)

    val controller: ChessControllerBlockEntity
        get() = world.getBlockEntity(controllerLoc.blockpos) as ChessControllerBlockEntity

    val floor: List<ChessboardFloorBlockEntity> get() = controller.floorBlockEntities

    override val componentClass: KClass<out FabricRenderer> get() = FabricRenderer::class

    override fun getComponent(game: ChessGame) = FabricRenderer(game, this)
}

class FabricRenderer(game: ChessGame, override val data: FabricRendererSettings) : Component(game) {

    private val tileBlocks: Map<Pos, List<ChessboardFloorBlockEntity>> by lazy { data.floor.groupBy { it.boardPos!! } }

    // TODO: make this private
    var heldPiece: BoardPiece? = null

    private val Move.floorMaterial: Floor
        get() {
            if (getTrait<CastlesTrait>() != null || getTrait<PromotionTrait>() != null)
                return Floor.SPECIAL
            getTrait<TargetTrait>()?.let {
                if (game.board[it.target]?.piece != null)
                    return Floor.CAPTURE
            }
            return Floor.MOVE
        }

    private val Pos.floorMaterial: Floor
        get() {
            if (this == heldPiece?.pos)
                return Floor.NOTHING
            val moves = heldPiece?.getLegalMoves(game.board).orEmpty()
            if (this in moves.map { it.display })
                return moves.first { it.display == this }.floorMaterial
            if (this == game.board.lastMove?.piece?.pos)
                return Floor.LAST_START
            if (this == game.board.lastMove?.display)
                return Floor.LAST_END
            if (this in game.variant.specialSquares)
                return Floor.OTHER
            return if ((file + rank) % 2 == 0) Floor.DARK else Floor.LIGHT
        }

    // TODO: make this private
    fun redrawFloor() {
        for (file in 0..7) {
            for (rank in 0..7) {
                tileBlocks[Pos(file, rank)]?.forEach {
                    it.updateFloor(Pos(file, rank).floorMaterial)
                }
            }
        }
    }

    @ChessEventHandler
    fun onBaseEvent(e: GameBaseEvent) {
        if (e == GameBaseEvent.STOP || e == GameBaseEvent.PANIC) {
            tileBlocks.forEach { (_,l) ->
                l.forEach {
                    it.updateFloor()
                }
            }
            (data.world.getBlockEntity(data.controllerLoc.blockpos) as? ChessControllerBlockEntity)?.currentGame = null
        }
    }

    @ChessEventHandler
    fun onTurnStart(e: TurnEvent) {
        if (e == TurnEvent.START || e == TurnEvent.UNDO) {
            redrawFloor()
        }
    }

    private fun BoardPiece.place(loc: BlockPos) {
        when (val b = piece.block) {
            is TallPieceBlock -> data.world.apply {
                setBlockState(loc.up(1), b.defaultState.with(TallPieceBlock.HALF, DoubleBlockHalf.LOWER))
                setBlockState(loc.up(2), b.defaultState.with(TallPieceBlock.HALF, DoubleBlockHalf.UPPER))
            }
            is ShortPieceBlock -> data.world.setBlockState(loc.up(1), b.defaultState)
        }
    }

    @ChessEventHandler
    fun handlePieceEvents(e: PieceEvent) {
        when (e) {
            is PieceEvent.Created -> {}
            is PieceEvent.Cleared -> {}
            is PieceEvent.Moved -> {
                val pieceBlock = tileBlocks[e.from]?.firstNotNullOfOrNull { it.directPiece }
                pieceBlock?.safeBreak()
                val newBlockPos = tileBlocks[e.piece.pos]?.randomOrNull()?.pos
                if (newBlockPos != null)
                    e.piece.place(newBlockPos)
            }
            is PieceEvent.Captured -> {
                val pieceBlock = tileBlocks[e.piece.pos]?.firstNotNullOfOrNull { it.directPiece }
                pieceBlock?.safeBreak(!data.controller.addPiece(e.piece.piece))
            }
            is PieceEvent.Promoted -> {
                val pieceBlock = tileBlocks[e.piece.pos]?.firstNotNullOfOrNull { it.directPiece }
                val blockPos = pieceBlock?.floorBlock?.pos
                pieceBlock?.safeBreak(!data.controller.addPiece(e.piece.piece))
                check(data.controller.removePiece(e.promotion.piece)) { "Not enough pieces in the controller" }
                if (blockPos != null)
                    e.promotion.place(blockPos)
            }
            is PieceEvent.Resurrected -> {
                // TODO: handle this better
                check(data.controller.removePiece(e.piece.piece)) { "Not enough pieces in the controller" }
                val newBlockPos = tileBlocks[e.piece.pos]?.randomOrNull()?.pos
                if (newBlockPos != null)
                    e.piece.boardPiece.place(newBlockPos)
            }
            is PieceEvent.MultiMoved -> {
                for ((o, _) in e.moves) {
                    val pieceBlock = tileBlocks[o.pos]?.firstNotNullOfOrNull { it.directPiece }
                    pieceBlock?.safeBreak()
                }
                for ((_, t) in e.moves) {
                    val newBlockPos = tileBlocks[t.pos]?.randomOrNull()?.pos
                    if (newBlockPos != null)
                        t.place(newBlockPos)
                }
            }
        }
    }

}

val ChessGame.renderer get() = requireComponent<FabricRenderer>()