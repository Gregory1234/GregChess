package gregc.gregchess.fabric.chess.component

import gregc.gregchess.chess.*
import gregc.gregchess.chess.component.Component
import gregc.gregchess.chess.piece.BoardPiece
import gregc.gregchess.chess.piece.PieceEvent
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
        tileBlocks.mapValues { (_, fs) -> fs.firstOrNull { it.directPiece != null } ?: fs[4] }.toMutableMap()
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

    @ChessEventHandler
    fun handlePieceEvents(e: PieceEvent) = when (e) {
        is PieceEvent.Created -> {}
        is PieceEvent.Cleared -> {}
        is PieceEvent.Moved -> {
            for ((o, _) in e.moves)
                when (o) {
                    is BoardPiece -> {
                        val pieceBlock = tileBlocks[o.pos]?.firstNotNullOfOrNull { it.directPiece }
                        pieceBlock?.safeBreak(!controller.addPiece(o.piece))
                    }
                }
            for ((_, t) in e.moves)
                when (t) {
                    is BoardPiece -> {
                        val pieceBlock = tileBlocks[t.pos]?.firstNotNullOfOrNull { it.directPiece }
                        if (pieceBlock == null) {
                            val preferredBlock = preferredBlocks[t.pos]
                            val newBlockPos =
                                if (preferredBlock != null && t.piece.block.canActuallyPlaceAt(preferredBlock.world, preferredBlock.pos.up()))
                                    preferredBlock.pos
                                else
                                    tileBlocks[t.pos]?.filter { t.piece.block.canActuallyPlaceAt(it.world, it.pos.up()) }?.random()?.pos
                            if (newBlockPos != null) {
                                check(controller.removePiece(t.piece)) { "Not enough pieces in the controller" }
                                t.place(newBlockPos)
                            }
                        }
                    }
                }
        }
    }
}

val ChessGame.server get() = renderer.world.server

fun interface ChessFloorRenderer {
    fun ChessGame.getFloorMaterial(p: Pos): Floor
}

val ChessGame.renderer get() = requireComponent<FabricRenderer>()