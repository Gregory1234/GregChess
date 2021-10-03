package gregc.gregchess.bukkit.chess.component

import gregc.gregchess.Loc
import gregc.gregchess.bukkit.chess.*
import gregc.gregchess.bukkit.toLocation
import gregc.gregchess.chess.*
import gregc.gregchess.chess.component.*
import gregc.gregchess.chess.variant.AtomicChess
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import org.bukkit.Material
import org.bukkit.World
import kotlin.math.floor
import kotlin.reflect.KClass

@Serializable
data class BukkitRendererSettings(
    @Transient val arena: Arena = Arena.cNextArena()
) : ComponentData<BukkitRenderer> {
    override val componentClass: KClass<out BukkitRenderer> get() = BukkitRenderer::class

    override fun getComponent(game: ChessGame) = BukkitRenderer(game, this)
}

class BukkitRenderer(game: ChessGame, override val data: BukkitRendererSettings) : Component(game) {
    companion object {

        private class FillVolume(val world: World, val mat: Material, val start: Loc, val stop: Loc) {
            constructor(world: World, mat: Material, loc: Loc) : this(world, mat, loc, loc)
        }

        private fun fill(vol: FillVolume) {
            for (i in vol.start.x..vol.stop.x)
                for (j in (vol.start.y..vol.stop.y).reversed())
                    for (k in vol.start.z..vol.stop.z)
                        vol.world.getBlockAt(i, j, k).type = vol.mat
        }
    }

    val arena: Arena get() = data.arena
    private val tileSize get() = arena.tileSize
    private val highHalfTile get() = floor(tileSize.toDouble() / 2).toInt()
    private val lowHalfTile get() = floor((tileSize.toDouble() - 1) / 2).toInt()
    private val boardSize get() = 8 * tileSize
    private val boardStart get() = arena.boardStart
    private val capturedStart get() = arena.capturedStart

    fun getPos(loc: Loc) =
        Pos(file = 7 - (loc.x - boardStart.x).floorDiv(tileSize), rank = (loc.z - boardStart.z).floorDiv(tileSize))

    private val Pos.loc
        get() = Loc(boardSize - 2 - file * tileSize, 1, 1 + rank * tileSize) + boardStart

    private val CapturedPos.loc
        get() = capturedStart[by] + when (by) {
            Color.WHITE -> Loc(-2 * pos, 0, -2 * row)
            Color.BLACK -> Loc(2 * pos, 0, 2 * row)
        }

    private val world get() = arena.world

    private fun Piece.render(loc: Loc) {
        for ((i, m) in type.structure[color].withIndex())
            fill(FillVolume(world, m, loc.copy(y = loc.y + i)))
    }

    private fun BoardPiece.render() = piece.render(pos.loc)

    private fun CapturedPiece.render() = piece.render(pos.loc)

    private fun clearPiece(loc: Loc) {
        for (i in 0..10) {
            fill(FillVolume(world, Material.AIR, loc.copy(y = loc.y + i)))
        }
    }

    private fun BoardPiece.clearRender() = clearPiece(pos.loc)

    private fun CapturedPiece.clearRender() = clearPiece(pos.loc)

    private val Pos.location get() = loc.toLocation(world)

    private fun playPieceSound(pos: Pos, sound: String, type: PieceType) =
        world.playSound(pos.location, type.getSound(sound), 3.0f, 1.0f)

    private fun BoardPiece.playSound(sound: String) = playPieceSound(pos, sound, type)

    override fun handleEvent(e: ChessEvent) {
        arena.handleEvent(e)
        super.handleEvent(e)
    }

    @ChessEventHandler
    fun handleExplosion(e: AtomicChess.ExplosionEvent) {
        world.createExplosion(e.pos.location, 4.0f, false, false)
    }

    private fun Pos.fillFloor(floor: Floor) {
        val (x, y, z) = loc
        val mi = -lowHalfTile
        val ma = highHalfTile
        fill(FillVolume(world, floor.material, Loc(x + mi, y - 1, z + mi), Loc(x + ma, y - 1, z + ma)))
    }

    override fun validate() {
        arena.game = game
    }

    @ChessEventHandler
    fun onStop(e: GameStopStageEvent) {
        if (e == GameStopStageEvent.CLEAR || e == GameStopStageEvent.PANIC) {
            game.board.pieces.forEach {
                it.clearRender()
            }
            game.board.data.capturedPieces.forEach {
                it.clearRender()
            }
        }
    }

    @ChessEventHandler
    fun onFloorUpdate(e: FloorUpdateEvent) = e.pos.fillFloor(e.floor)

    @ChessEventHandler
    fun handlePieceEvents(e: PieceEvent) {
        when (e) {
            is PieceEvent.Created -> e.piece.render()
            is PieceEvent.Cleared -> e.piece.clearRender()
            is PieceEvent.Action -> when (e.type) {
                PieceEvent.ActionType.PICK_UP -> {
                    e.piece.clearRender()
                    e.piece.playSound("PickUp")
                }
                PieceEvent.ActionType.PLACE_DOWN -> {
                    e.piece.render()
                    e.piece.playSound("Move")
                }
            }
            is PieceEvent.Moved -> {
                clearPiece(e.from.loc)
                e.piece.render()
                e.piece.playSound("Move")
            }
            is PieceEvent.Captured -> {
                e.piece.boardPiece.clearRender()
                e.piece.captured.render()
                e.piece.boardPiece.playSound("Capture")
            }
            is PieceEvent.Promoted -> {
                e.piece.clearRender()
                e.promotion.render()
            }
            is PieceEvent.Resurrected -> {
                e.piece.boardPiece.render()
                e.piece.captured.clearRender()
                e.piece.boardPiece.playSound("Move")
            }
            is PieceEvent.MultiMoved -> {
                for ((o, _) in e.moves) {
                    o.clearRender()
                }
                for ((_, t) in e.moves) {
                    t.render()
                    t.playSound("Move")
                }
            }
        }
    }
}

val ChessGame.arena get() = renderer.arena

val ChessGame.renderer get() = requireComponent<BukkitRenderer>()