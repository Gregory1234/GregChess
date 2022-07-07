package gregc.gregchess.fabric.renderer

import gregc.gregchess.Pos
import gregc.gregchess.fabric.block.ChessControllerBlockEntity
import gregc.gregchess.fabric.block.ChessboardFloorBlockEntity
import gregc.gregchess.fabric.match.FabricComponentType
import gregc.gregchess.fabric.moveBlock
import gregc.gregchess.fabric.piece.*
import gregc.gregchess.fabric.player.PiecePlayerActionEvent
import gregc.gregchess.match.*
import gregc.gregchess.move.connector.PieceMoveEvent
import gregc.gregchess.piece.BoardPiece
import kotlinx.serialization.Contextual
import kotlinx.serialization.Serializable
import net.minecraft.block.enums.DoubleBlockHalf
import net.minecraft.util.math.BlockPos
import net.minecraft.world.World

@Serializable
data class FabricRenderer(
    val controllerPos: @Contextual BlockPos,
    val world: @Contextual World
) : Component {
    constructor(controller: ChessControllerBlockEntity) : this(controller.pos, controller.world!!)

    override val type get() = FabricComponentType.RENDERER

    private val controller: ChessControllerBlockEntity
        get() = world.getBlockEntity(controllerPos) as ChessControllerBlockEntity

    private val floor: List<ChessboardFloorBlockEntity> get() = controller.floorBlockEntities

    private val tileBlocks: Map<Pos, List<ChessboardFloorBlockEntity>> by lazy { floor.groupBy { it.boardPos!! } }

    private val preferredBlocks: MutableMap<Pos, ChessboardFloorBlockEntity> by lazy {
        tileBlocks.mapValues { (_, fs) -> fs.firstOrNull { it.directPiece.block is PieceBlock } ?: fs[4] }.toMutableMap()
    }

    internal fun preferBlock(floor: ChessboardFloorBlockEntity) {
        preferredBlocks[floor.boardPos!!] = floor
    }

    private fun redrawFloor(match: ChessMatch) {
        for (file in 0..7) {
            for (rank in 0..7) {
                tileBlocks[Pos(file, rank)]?.forEach {
                    with(match.variant.floorRenderer) {
                        it.updateFloor(match.getFloorMaterial(Pos(file, rank)))
                    }
                }
            }
        }
    }

    @ChessEventHandler
    fun onBaseEvent(match: ChessMatch, e: ChessBaseEvent) {
        if (e == ChessBaseEvent.STOP || e == ChessBaseEvent.PANIC) {
            tileBlocks.forEach { (_,l) ->
                l.forEach {
                    it.updateFloor()
                }
            }
            (world.getBlockEntity(controllerPos) as? ChessControllerBlockEntity)?.currentMatchUUID = null
        }
    }

    @ChessEventHandler
    fun onTurnStart(match: ChessMatch, e: TurnEvent) {
        if (e == TurnEvent.START || e == TurnEvent.UNDO) {
            redrawFloor(match)
        }
    }

    private fun BoardPiece.place(loc: BlockPos) {
        when (val b = piece.block) {
            is TallPieceBlock -> world.apply {
                setBlockState(loc.up(1), b.defaultState.with(TallPieceBlock.HALF, DoubleBlockHalf.LOWER))
                setBlockState(loc.up(2), b.defaultState.with(TallPieceBlock.HALF, DoubleBlockHalf.UPPER))
            }
            is ShortPieceBlock -> world.setBlockState(loc.up(1), b.defaultState)
        }
    }

    @ChessEventHandler
    @Suppress("UNUSED_PARAMETER")
    fun onPiecePlayerAction(match: ChessMatch, e: PiecePlayerActionEvent) = redrawFloor(match)

    private fun choosePlacePos(pos: Pos, block: PieceBlock): BlockPos {
        val preferredBlock = preferredBlocks[pos]
        return if (preferredBlock != null && block.canActuallyPlaceAt(preferredBlock.world, preferredBlock.pos.up()))
            preferredBlock.pos
        else
            tileBlocks[pos]!!.filter { block.canActuallyPlaceAt(it.world, it.pos.up()) }.random().also {
                preferredBlocks[pos] = it
            }.pos
    }

    // TODO: add move and capture sounds
    @ChessEventHandler
    fun handlePieceEvents(match: ChessMatch, e: PieceMoveEvent) {
        val broken = mutableListOf<BoardPiece>()
        val placed = mutableListOf<BoardPiece>()
        try {
            for ((o, t) in e.moves)
                when (o) {
                    is BoardPiece -> {
                        if (t == null) continue
                        val pieceBlock = tileBlocks[o.pos]?.map { it.directPiece }?.firstOrNull { it.block is PieceBlock }
                        if (pieceBlock != null) {
                            pieceBlock.pos?.let { world.moveBlock(it, !controller.addPiece(o.piece)) }
                            broken += o
                        }
                    }
                }
            for ((o, t) in e.moves)
                when (t) {
                    is BoardPiece -> {
                        if (o == null) continue
                        val pieceBlock = tileBlocks[t.pos]?.map { it.directPiece }?.firstOrNull { it.block is PieceBlock }
                        if ((pieceBlock?.block as? PieceBlock)?.piece != t.piece) {
                            check(pieceBlock == null) { "There is a piece block on ${t.pos} already" }
                            check(controller.removePiece(t.piece)) { "Not enough pieces in the controller" }
                            t.place(choosePlacePos(t.pos, t.piece.block))
                            placed += t
                        }
                    }
                }
        } catch (e: Throwable) {
            for (t in placed.asReversed()) {
                val pieceBlock = tileBlocks[t.pos]?.map { it.directPiece }?.firstOrNull { it.block is PieceBlock }
                pieceBlock?.pos?.let { world.moveBlock(it, !controller.addPiece(t.piece)) }
            }
            for (o in broken.asReversed()) {
                check(controller.removePiece(o.piece)) { "Not enough pieces in the controller" }
                o.place(choosePlacePos(o.pos, o.piece.block))
            }
            throw e
        }
    }
}

val ChessMatch.server get() = renderer.world.server

val ChessMatch.renderer get() = require(FabricComponentType.RENDERER)