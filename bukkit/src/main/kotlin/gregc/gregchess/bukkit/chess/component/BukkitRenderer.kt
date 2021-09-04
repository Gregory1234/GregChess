package gregc.gregchess.bukkit.chess.component

import gregc.gregchess.Loc
import gregc.gregchess.bukkit.chess.*
import gregc.gregchess.bukkit.toLocation
import gregc.gregchess.chess.*
import gregc.gregchess.chess.component.Component
import gregc.gregchess.chess.component.ComponentData
import gregc.gregchess.chess.variant.AtomicChess
import kotlinx.serialization.Serializable
import org.bukkit.Material
import org.bukkit.World
import kotlin.math.floor

@Serializable
data class BukkitRendererSettings(
    val tileSize: Int,
    val offset: Loc = Loc(0, 0, 0)
) : ComponentData<BukkitRenderer> {
    val highHalfTile get() = floor(tileSize.toDouble() / 2).toInt()
    val lowHalfTile get() = floor((tileSize.toDouble() - 1) / 2).toInt()
    val boardSize get() = 8 * tileSize
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

        private fun fillBorder(vol: FillVolume) {
            for (i in vol.start.x..vol.stop.x)
                for (j in (vol.start.y..vol.stop.y).reversed())
                    for (k in vol.start.z..vol.stop.z)
                        if (i in listOf(vol.start.x, vol.stop.x) || k in listOf(vol.start.z, vol.stop.z))
                            vol.world.getBlockAt(i, j, k).type = vol.mat
        }

        private val defaultSpawnLocation = Loc(4, 101, 4)
    }

    val spawnLocation
        get() = defaultSpawnLocation + data.offset

    fun getPos(loc: Loc) =
        Pos(
            file = (8 + data.boardSize - 1 - loc.x + data.offset.x).floorDiv(data.tileSize),
            rank = (loc.z - data.offset.z - 8).floorDiv(data.tileSize)
        )

    private val Pos.loc
        get() = Loc(
            x = 8 + data.boardSize - 1 - data.highHalfTile - file * data.tileSize,
            y = 102,
            z = rank * data.tileSize + 8 + data.lowHalfTile
        ) + data.offset

    private val CapturedPos.loc
        get() = when (by) {
            Side.WHITE -> Loc(8 + data.boardSize - 1 - 2 * pos, 101, 8 - 3 - 2 * row)
            Side.BLACK -> Loc(8 + 2 * pos, 101, 8 + data.boardSize + 2 + 2 * row)
        } + data.offset

    private val world get() = game.arena.world

    private fun fill(from: Loc, to: Loc, mat: Material) =
        fill(FillVolume(world, mat, from + data.offset, to + data.offset))

    private fun fillBorder(from: Loc, to: Loc, mat: Material) =
        fillBorder(FillVolume(world, mat, from + data.offset, to + data.offset))

    private fun Piece.render(loc: Loc) {
        for ((i, m) in type.structure[side].withIndex())
            fill(FillVolume(world, m, loc.copy(y = loc.y + i)))
    }

    private fun PieceInfo.render() = piece.render(pos.loc)

    private fun CapturedPiece.render() = piece.render(pos.loc)

    private fun clearPiece(loc: Loc) {
        for (i in 0..10) {
            fill(FillVolume(world, Material.AIR, loc.copy(y = loc.y + i)))
        }
    }

    private fun PieceInfo.clearRender() = clearPiece(pos.loc)

    private fun CapturedPiece.clearRender() = clearPiece(pos.loc)

    private val Pos.location get() = loc.toLocation(world)

    private fun playPieceSound(pos: Pos, sound: String, type: PieceType) =
        world.playSound(pos.location, type.getSound(sound), 3.0f, 1.0f)

    private fun PieceInfo.playSound(sound: String) = playPieceSound(pos, sound, type)

    @ChessEventHandler
    fun handleExplosion(e: AtomicChess.ExplosionEvent) {
        world.createExplosion(e.pos.location, 4.0f, false, false)
    }

    private fun Pos.fillFloor(floor: Floor) {
        val (x, y, z) = loc
        val mi = -data.lowHalfTile
        val ma = data.highHalfTile
        fill(FillVolume(world, floor.material, Loc(x + mi, y - 1, z + mi), Loc(x + ma, y - 1, z + ma)))
    }

    private fun renderBoardBase() {
        fill(
            Loc(0, 100, 0),
            Loc(8 + data.boardSize + 8 - 1, 100, 8 + data.boardSize + 8 - 1),
            Material.DARK_OAK_PLANKS
        )
        fillBorder(
            Loc(8 - 1, 101, 8 - 1),
            Loc(8 + data.boardSize, 101, 8 + data.boardSize),
            Material.DARK_OAK_PLANKS
        )
    }

    private fun removeBoard() {
        fill(
            Loc(0, 100, 0),
            Loc(8 + data.boardSize + 8 - 1, 105, 8 + data.boardSize + 8 - 1),
            Material.AIR
        )
    }

    override fun validate() {
        game.requireComponent<Arena.Usage>()
    }

    @ChessEventHandler
    fun onStart(e: GameStartStageEvent) {
        if (e == GameStartStageEvent.START)
            renderBoardBase()
    }

    @ChessEventHandler
    fun onStop(e: GameStopStageEvent)  {
        if (e == GameStopStageEvent.CLEAR || e == GameStopStageEvent.PANIC)
            removeBoard()
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
                e.piece.piece.clearRender()
                e.piece.captured.render()
                e.piece.piece.playSound("Capture")
            }
            is PieceEvent.Promoted -> {
                e.piece.clearRender()
                e.promotion.render()
            }
            is PieceEvent.Resurrected -> {
                e.piece.piece.render()
                e.piece.captured.clearRender()
                e.piece.piece.playSound("Move")
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