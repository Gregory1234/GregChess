package gregc.gregchess.fabric.chess.component

import gregc.gregchess.chess.*
import gregc.gregchess.chess.component.Component
import gregc.gregchess.chess.component.ComponentData
import gregc.gregchess.chess.piece.BoardPiece
import gregc.gregchess.chess.piece.PieceEvent
import gregc.gregchess.fabric.chess.*
import kotlinx.serialization.Contextual
import kotlinx.serialization.Serializable
import net.minecraft.block.enums.DoubleBlockHalf
import net.minecraft.util.math.BlockPos
import net.minecraft.world.World
import kotlin.reflect.KClass

@Serializable
data class FabricRendererSettings(
    val controllerPos: @Contextual BlockPos,
    val world: @Contextual World
) : ComponentData<FabricRenderer> {
    constructor(controller: ChessControllerBlockEntity) : this(controller.pos, controller.world!!)

    val controller: ChessControllerBlockEntity
        get() = world.getBlockEntity(controllerPos) as ChessControllerBlockEntity

    val floor: List<ChessboardFloorBlockEntity> get() = controller.floorBlockEntities

    override val componentClass: KClass<out FabricRenderer> get() = FabricRenderer::class

    override fun getComponent(game: ChessGame) = FabricRenderer(game, this)
}

val ChessGame.server get() = renderer.data.world.server

fun interface ChessFloorRenderer {
    fun ChessGame.getFloorMaterial(p: Pos): Floor
}

class FabricRenderer(game: ChessGame, override val data: FabricRendererSettings) : Component(game) {

    private val tileBlocks: Map<Pos, List<ChessboardFloorBlockEntity>> by lazy { data.floor.groupBy { it.boardPos!! } }

    // TODO: make this private
    fun redrawFloor() {
        for (file in 0..7) {
            for (rank in 0..7) {
                tileBlocks[Pos(file, rank)]?.forEach {
                    with(game.variant.floorRenderer) {
                        it.updateFloor(game.getFloorMaterial(Pos(file, rank)))
                    }
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
            (data.world.getBlockEntity(data.controllerPos) as? ChessControllerBlockEntity)?.currentGame = null
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
        println(e)
        when (e) {
            is PieceEvent.Created -> {}
            is PieceEvent.Cleared -> {}
            is PieceEvent.Moved -> {
                for ((o, _) in e.moves)
                    when (o) {
                        is BoardPiece -> {
                            val pieceBlock = tileBlocks[o.pos]?.firstNotNullOfOrNull { it.directPiece }
                            pieceBlock?.safeBreak(!data.controller.addPiece(o.piece))
                        }
                    }
                for ((_, t) in e.moves)
                    when (t) {
                        is BoardPiece -> {
                            val pieceBlock = tileBlocks[t.pos]?.firstNotNullOfOrNull { it.directPiece }
                            if (pieceBlock == null) {
                                val newBlockPos = tileBlocks[t.pos]?.randomOrNull()?.pos
                                if (newBlockPos != null) {
                                    check(data.controller.removePiece(t.piece)) { "Not enough pieces in the controller" }
                                    t.place(newBlockPos)
                                }
                            }
                        }
                    }
            }
        }
    }

}

val ChessGame.renderer get() = requireComponent<FabricRenderer>()