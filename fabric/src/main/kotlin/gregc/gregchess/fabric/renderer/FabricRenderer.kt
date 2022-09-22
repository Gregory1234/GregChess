package gregc.gregchess.fabric.renderer

import gregc.gregchess.Pos
import gregc.gregchess.component.Component
import gregc.gregchess.event.*
import gregc.gregchess.fabric.block.ChessControllerBlockEntity
import gregc.gregchess.fabric.block.ChessboardFloorBlockEntity
import gregc.gregchess.fabric.component.FabricComponentType
import gregc.gregchess.fabric.event.FabricChessEventType
import gregc.gregchess.fabric.moveBlock
import gregc.gregchess.fabric.piece.*
import gregc.gregchess.fabric.player.PiecePlayerActionEvent
import gregc.gregchess.match.ChessMatch
import gregc.gregchess.move.connector.PieceMoveEvent
import gregc.gregchess.piece.BoardPiece
import gregc.gregchess.piece.CapturedPiece
import kotlinx.serialization.Contextual
import kotlinx.serialization.Serializable
import net.minecraft.block.enums.DoubleBlockHalf
import net.minecraft.sound.SoundCategory
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
                        it.updateFloor(match.getFloor(Pos(file, rank)))
                    }
                }
            }
        }
    }

    override fun init(match: ChessMatch, events: EventListenerRegistry) {
        events.register(ChessEventType.BASE, ::handleBaseEvent)
        events.register(ChessEventType.TURN) { handleTurnEvent(match, it) }
        events.register(FabricChessEventType.PIECE_PLAYER_ACTION) { handlePiecePlayerActionEvent(match, it) }
        events.register(ChessEventType.PIECE_MOVE, ::handlePieceMoveEvent)
    }

    private fun handleBaseEvent(e: ChessBaseEvent) {
        if (e == ChessBaseEvent.STOP || e == ChessBaseEvent.PANIC) {
            tileBlocks.forEach { (_,l) ->
                l.forEach {
                    it.updateFloor()
                }
            }
            (world.getBlockEntity(controllerPos) as? ChessControllerBlockEntity)?.currentMatchUUID = null
        }
    }

    private fun handleTurnEvent(match: ChessMatch, e: TurnEvent) {
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

    private fun choosePlacePos(pos: Pos, block: PieceBlock): BlockPos {
        val preferredBlock = preferredBlocks[pos]
        return if (preferredBlock != null && block.canActuallyPlaceAt(preferredBlock.world, preferredBlock.pos.up()))
            preferredBlock.pos
        else
            tileBlocks[pos]!!.filter { block.canActuallyPlaceAt(it.world, it.pos.up()) }.random().also {
                preferredBlocks[pos] = it
            }.pos
    }

    private fun handlePiecePlayerActionEvent(match: ChessMatch, e: PiecePlayerActionEvent) {
        redrawFloor(match)
        val pieceBlock = tileBlocks[e.piece.pos]?.map { it.directPiece }?.firstOrNull { it.block is PieceBlock }
        world.playSound(null, pieceBlock?.pos, when(e.action) {
            PiecePlayerActionEvent.Type.PICK_UP -> e.piece.piece.block.pieceSounds.pickUp
            PiecePlayerActionEvent.Type.PLACE_DOWN -> e.piece.piece.block.pieceSounds.move
        }, SoundCategory.BLOCKS, 1f, 1f)
    }

    private fun handlePieceMoveEvent(e: PieceMoveEvent) {
        val broken = mutableListOf<BoardPiece>()
        val placed = mutableListOf<BoardPiece>()
        try {
            for ((o, t) in e.moves)
                when (o) {
                    is BoardPiece -> {
                        if (t == null) continue
                        val pieceBlock = tileBlocks[o.pos]?.map { it.directPiece }?.firstOrNull { it.block is PieceBlock }
                        if (pieceBlock != null) {
                            val pos = pieceBlock.pos
                            if (pos != null) {
                                world.moveBlock(listOfNotNull(pos, if (pieceBlock.block is TallPieceBlock) pos.up() else null), !controller.addPiece(o.piece))
                                if (t is CapturedPiece) {
                                    world.playSound(null, pos, o.piece.block.pieceSounds.capture, SoundCategory.BLOCKS, 1f, 1f)
                                }
                            }
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
                            val newPos = choosePlacePos(t.pos, t.piece.block)
                            t.place(newPos)
                            if (o is BoardPiece) {
                                world.playSound(null, newPos, o.piece.block.pieceSounds.move, SoundCategory.BLOCKS, 1f, 1f)
                            }
                            placed += t
                        }
                    }
                }
        } catch (e: Throwable) {
            for (t in placed.asReversed()) {
                val pieceBlock = tileBlocks[t.pos]?.map { it.directPiece }?.firstOrNull { it.block is PieceBlock }
                pieceBlock?.pos?.let { world.moveBlock(listOfNotNull(it, if (pieceBlock.block is TallPieceBlock) it.up() else null), !controller.addPiece(t.piece)) }
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

val ChessMatch.renderer get() = components.require(FabricComponentType.RENDERER)