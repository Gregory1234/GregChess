package gregc.gregchess.fabric.chess.component

import gregc.gregchess.chess.*
import gregc.gregchess.chess.component.Component
import gregc.gregchess.chess.piece.BoardPiece
import gregc.gregchess.chess.piece.PieceMoveEvent
import gregc.gregchess.fabric.chess.*
import gregc.gregchess.fabric.chess.player.PiecePlayerActionEvent
import kotlinx.serialization.*
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

    @Transient
    private lateinit var game: ChessGame

    override fun init(game: ChessGame) {
        this.game = game
    }

    private val controller: ChessControllerBlockEntity
        get() = world.getBlockEntity(controllerPos) as ChessControllerBlockEntity

    private val floor: List<ChessboardFloorBlockEntity> get() = controller.floorBlockEntities

    private val tileBlocks: Map<Pos, List<ChessboardFloorBlockEntity>> by lazy { floor.groupBy { it.boardPos!! } }

    private val preferredBlocks: MutableMap<Pos, ChessboardFloorBlockEntity> by lazy {
        tileBlocks.mapValues { (_, fs) -> fs.firstOrNull { it.directPiece.entity != null } ?: fs[4] }.toMutableMap()
    }

    internal fun preferBlock(floor: ChessboardFloorBlockEntity) {
        preferredBlocks[floor.boardPos!!] = floor
    }

    private fun redrawFloor() {
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
            (world.getBlockEntity(controllerPos) as? ChessControllerBlockEntity)?.currentGame = null
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
            is TallPieceBlock -> world.apply {
                setBlockState(loc.up(1), b.defaultState.with(TallPieceBlock.HALF, DoubleBlockHalf.LOWER))
                setBlockState(loc.up(2), b.defaultState.with(TallPieceBlock.HALF, DoubleBlockHalf.UPPER))
            }
            is ShortPieceBlock -> world.setBlockState(loc.up(1), b.defaultState)
        }
    }

    @ChessEventHandler
    fun onPiecePlayerAction(e: PiecePlayerActionEvent) = redrawFloor()

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
    fun handlePieceEvents(e: PieceMoveEvent) {
        val broken = mutableListOf<BoardPiece>()
        val placed = mutableListOf<BoardPiece>()
        try {
            for ((o, t) in e.moves)
                when (o) {
                    is BoardPiece -> {
                        if (t == null) continue
                        val pieceBlock = tileBlocks[o.pos]?.firstNotNullOfOrNull { it.directPiece.entity }
                        if (pieceBlock != null) {
                            pieceBlock.safeBreak(!controller.addPiece(o.piece))
                            broken += o
                        }
                    }
                }
            for ((o, t) in e.moves)
                when (t) {
                    is BoardPiece -> {
                        if (o == null) continue
                        val pieceBlock = tileBlocks[t.pos]?.firstNotNullOfOrNull { it.directPiece.entity }
                        if (pieceBlock?.piece != t.piece) {
                            check(pieceBlock == null) { "There is a piece block on ${t.pos} already" }
                            check(controller.removePiece(t.piece)) { "Not enough pieces in the controller" }
                            t.place(choosePlacePos(t.pos, t.piece.block))
                            placed += t
                        }
                    }
                }
        } catch (e: Throwable) {
            for (t in placed.asReversed()) {
                val pieceBlock = tileBlocks[t.pos]?.firstNotNullOfOrNull { it.directPiece.entity }
                pieceBlock?.safeBreak(!controller.addPiece(t.piece))
            }
            for (o in broken.asReversed()) {
                check(controller.removePiece(o.piece)) { "Not enough pieces in the controller" }
                o.place(choosePlacePos(o.pos, o.piece.block))
            }
            throw e
        }
    }
}

val ComponentHolder.server get() = renderer?.world?.server

fun interface ChessFloorRenderer {
    fun ChessGame.getFloorMaterial(p: Pos): Floor
}

val ChessGame.renderer get() = require(FabricComponentType.RENDERER)
val ComponentHolder.renderer get() = get(FabricComponentType.RENDERER)