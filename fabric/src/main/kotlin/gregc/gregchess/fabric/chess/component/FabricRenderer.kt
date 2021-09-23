package gregc.gregchess.fabric.chess.component

import gregc.gregchess.Loc
import gregc.gregchess.chess.*
import gregc.gregchess.chess.component.Component
import gregc.gregchess.chess.component.ComponentData
import gregc.gregchess.fabric.blockpos
import gregc.gregchess.fabric.chess.*
import gregc.gregchess.fabric.loc
import kotlinx.serialization.Contextual
import kotlinx.serialization.Serializable
import net.minecraft.block.enums.DoubleBlockHalf
import net.minecraft.world.World

@Serializable
data class FabricRendererSettings(
    val controllerLoc: Loc,
    val world: @Contextual World
) : ComponentData<FabricRenderer> {
    constructor(controller: ChessControllerBlockEntity) : this(controller.pos.loc, controller.world!!)

    val controller: ChessControllerBlockEntity
        get() = world.getBlockEntity(controllerLoc.blockpos) as ChessControllerBlockEntity

    val floor: List<ChessboardFloorBlockEntity> get() = controller.floorBlockEntities

    override fun getComponent(game: ChessGame) = FabricRenderer(game, this)
}

class FabricRenderer(game: ChessGame, override val data: FabricRendererSettings) : Component(game) {

    private val tileBlocks: Map<Pos, List<ChessboardFloorBlockEntity>> by lazy { data.floor.groupBy { it.boardPos!! } }

    @ChessEventHandler
    fun onFloorUpdate(e: FloorUpdateEvent) {
        tileBlocks[e.pos]?.forEach {
            it.updateFloor(e.floor)
        }
    }

    @ChessEventHandler
    fun onBaseEvent(e: GameBaseEvent) {
        if (e == GameBaseEvent.STOP || e == GameBaseEvent.PANIC) {
            tileBlocks.forEach { (p,l) ->
                l.forEach {
                    it.updateFloor()
                }
            }
            (data.world.getBlockEntity(data.controllerLoc.blockpos) as? ChessControllerBlockEntity)?.currentGame = null
        }
    }

    @ChessEventHandler
    fun handlePieceEvents(e: PieceEvent) {
        when (e) {
            is PieceEvent.Created -> {}
            is PieceEvent.Cleared -> {}
            is PieceEvent.Action -> {}
            is PieceEvent.Moved -> {
                val pieceBlock = tileBlocks[e.from]?.firstNotNullOfOrNull { it.directPiece }
                pieceBlock?.safeBreak()
                val newBlockPos = tileBlocks[e.piece.pos]?.randomOrNull()?.pos
                if (newBlockPos != null) {
                    when (val b = e.piece.piece.block) {
                        is TallPieceBlock -> data.world.apply {
                            setBlockState(newBlockPos.up(1), b.defaultState.with(TallPieceBlock.HALF, DoubleBlockHalf.LOWER))
                            setBlockState(newBlockPos.up(2), b.defaultState.with(TallPieceBlock.HALF, DoubleBlockHalf.UPPER))
                        }
                        is ShortPieceBlock -> data.world.setBlockState(newBlockPos.up(1), b.defaultState)
                    }

                }
            }
            is PieceEvent.Captured -> {
                val pieceBlock = tileBlocks[e.piece.pos]?.firstNotNullOfOrNull { it.directPiece }
                pieceBlock?.safeBreak(true)
            }
            is PieceEvent.Promoted -> {
                TODO("Promotion")
            }
            is PieceEvent.Resurrected -> {
                TODO("This doesn't really make sense here...")
            }
            is PieceEvent.MultiMoved -> {
                for ((o, _) in e.moves) {
                    val pieceBlock = tileBlocks[o.pos]?.firstNotNullOfOrNull { it.directPiece }
                    pieceBlock?.safeBreak()
                }
                for ((_, t) in e.moves) {
                    val newBlockPos = tileBlocks[t.pos]?.randomOrNull()?.pos
                    if (newBlockPos != null) {
                        when (val b = t.piece.block) {
                            is TallPieceBlock -> data.world.apply {
                                setBlockState(newBlockPos.up(1), b.defaultState.with(TallPieceBlock.HALF, DoubleBlockHalf.LOWER))
                                setBlockState(newBlockPos.up(2), b.defaultState.with(TallPieceBlock.HALF, DoubleBlockHalf.UPPER))
                            }
                            is ShortPieceBlock -> data.world.setBlockState(newBlockPos.up(1), b.defaultState)
                        }

                    }
                }
            }
        }
    }

}

val ChessGame.renderer get() = requireComponent<FabricRenderer>()