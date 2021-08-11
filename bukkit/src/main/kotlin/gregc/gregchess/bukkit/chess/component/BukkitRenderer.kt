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
            Side.WHITE -> Loc(8 + data.boardSize - 1 - 2 * pos.first, 101, 8 - 3 - 2 * pos.second)
            Side.BLACK -> Loc(8 + 2 * pos.first, 101, 8 + data.boardSize + 2 + 2 * pos.second)
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

    private fun clearPiece(loc: Loc) {
        for (i in 0..10) {
            fill(FillVolume(world, Material.AIR, loc.copy(y = loc.y + i)))
        }
    }

    private val Pos.location get() = loc.toLocation(world)

    private fun playPieceSound(pos: Pos, sound: String, type: PieceType) =
        world.playSound(pos.location, type.getSound(sound), 3.0f, 1.0f)

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

    @ChessEventHandler
    fun handleEvents(e: GameBaseEvent) {
        if (e == GameBaseEvent.PRE_INIT)
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
        val pos = e.piece.pos
        val loc = pos.loc
        val piece = e.piece.piece
        when (e) {
            is PieceEvent.Created -> piece.render(loc)
            is PieceEvent.Cleared -> clearPiece(loc)
            is PieceEvent.Action -> when (e.type) {
                PieceEvent.ActionType.PICK_UP -> {
                    clearPiece(loc)
                    playPieceSound(pos, "PickUp", piece.type)
                }
                PieceEvent.ActionType.PLACE_DOWN -> {
                    piece.render(loc)
                    playPieceSound(pos, "Move", piece.type)
                }
            }
            is PieceEvent.Moved -> {
                clearPiece(e.from.loc)
                piece.render(loc)
                playPieceSound(pos, "Move", piece.type)
            }
            is PieceEvent.Captured -> {
                clearPiece(loc)
                piece.render(e.captured.pos.loc)
                playPieceSound(pos, "Capture", piece.type)
            }
            is PieceEvent.Promoted -> {
                clearPiece(loc)
                e.promotion.piece.render(e.promotion.pos.loc)
            }
            is PieceEvent.Resurrected -> {
                piece.render(loc)
                clearPiece(e.captured.pos.loc)
                playPieceSound(pos, "Move", piece.type)
            }
            is PieceEvent.MultiMoved -> {
                for ((_, f) in e.moves) {
                    clearPiece(f.loc)
                }
                for ((p, _) in e.moves) {
                    p.piece.render(p.pos.loc)
                    playPieceSound(p.pos, "Move", p.type)
                }
            }
        }
    }
}