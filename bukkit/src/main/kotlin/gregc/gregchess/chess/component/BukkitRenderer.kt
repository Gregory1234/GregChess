package gregc.gregchess.chess.component

import gregc.gregchess.*
import gregc.gregchess.chess.*
import gregc.gregchess.chess.variant.AtomicChess
import org.bukkit.*
import kotlin.math.floor

class BukkitRenderer(private val game: ChessGame, private val settings: Settings) : Renderer {
    companion object {

        private data class FillVolume(val world: World, val mat: Material, val start: Loc, val stop: Loc) {
            constructor(world: World, mat: Material, loc: Loc) : this(world, mat, loc, loc)
        }

        private fun fill(vol: FillVolume) {
            for (i in vol.start.x..vol.stop.x)
                for (j in (vol.start.y..vol.stop.y).reversed())
                    for (k in vol.start.z..vol.stop.z)
                        vol.world.getBlockAt(i, j, k).type = vol.mat
        }

        private val defaultSpawnLocation = Loc(4, 101, 4)
    }
    class Settings(val tileSize: Int, val offset: Loc = Loc(0, 0, 0)) : Renderer.Settings {
        val highHalfTile get() = floor(tileSize.toDouble() / 2).toInt()
        val lowHalfTile get() = floor((tileSize.toDouble() - 1) / 2).toInt()
        val boardSize get() = 8 * tileSize
        override fun getComponent(game: ChessGame) = BukkitRenderer(game, this)
    }

    val spawnLocation
        get() = defaultSpawnLocation + settings.offset

    fun getPos(loc: Loc) =
        Pos(
            file = (8 + settings.boardSize - 1 - loc.x + settings.offset.x).floorDiv(settings.tileSize),
            rank = (loc.z - settings.offset.z - 8).floorDiv(settings.tileSize)
        )

    private val Pos.loc
        get() = Loc(
            x = 8 + settings.boardSize - 1 - settings.highHalfTile - file * settings.tileSize,
            y = 102,
            z = rank * settings.tileSize + 8 + settings.lowHalfTile
        ) + settings.offset

    private val CapturedPos.loc
        get() = when (by) {
            Side.WHITE -> Loc(8 + settings.boardSize - 1 - 2 * pos.first, 101, 8 - 3 - 2 * pos.second)
            Side.BLACK -> Loc(8 + 2 * pos.first, 101, 8 + settings.boardSize + 2 + 2 * pos.second)
        } + settings.offset

    private val world get() = game.arena.world

    private fun fill(from: Loc, to: Loc, mat: Material) =
        fill(FillVolume(world, mat, from + settings.offset, to + settings.offset))

    private fun Piece.render(loc: Loc) {
        type.structure[side].forEachIndexed { i, m ->
            fill(FillVolume(world, m, loc.copy(y = loc.y + i)))
        }
    }

    private fun clearPiece(loc: Loc) {
        (0..10).forEach { i ->
            fill(FillVolume(world, Material.AIR, loc.copy(y = loc.y + i)))
        }
    }

    private fun <R> doAt(pos: Pos, f: (World, Location) -> R) = pos.loc.doIn(world, f)

    private fun playPieceSound(pos: Pos, sound: String, type: PieceType) =
        doAt(pos) { world, l -> world.playSound(l, type.getSound(sound)) }

    @ChessEventHandler
    fun handleExplosion(e: AtomicChess.ExplosionEvent) {
        doAt(e.pos) { world, l -> world.createExplosion(l, 4.0f, false, false) }
    }

    override fun fillFloor(pos: Pos, floor: Floor) {
        val (x, y, z) = pos.loc
        val mi = -settings.lowHalfTile
        val ma = settings.highHalfTile
        fill(FillVolume(world, floor.material, Loc(x + mi, y - 1, z + mi), Loc(x + ma, y - 1, z + ma)))
    }

    override fun renderBoardBase() {
        fill(
            Loc(0, 100, 0),
            Loc(8 + settings.boardSize + 8 - 1, 100, 8 + settings.boardSize + 8 - 1),
            Material.DARK_OAK_PLANKS
        )
        fill(
            Loc(8 - 1, 101, 8 - 1),
            Loc(8 + settings.boardSize, 101, 8 + settings.boardSize),
            Material.DARK_OAK_PLANKS
        )
    }

    override fun removeBoard() {
        fill(
            Loc(0, 100, 0),
            Loc(8 + settings.boardSize + 8 - 1, 105, 8 + settings.boardSize + 8 - 1),
            Material.AIR
        )
    }

    @ChessEventHandler
    fun validate(e: GameBaseEvent) {
        if (e == GameBaseEvent.PRE_INIT)
            game.requireComponent<Arena.Usage>()
    }

    @ChessEventHandler
    fun handlePieceEvents(e: PieceEvent) {
        val pos = e.piece.pos
        val loc = pos.loc
        val piece = e.piece.piece
        when(e) {
            is PieceEvent.Created -> piece.render(loc)
            is PieceEvent.Cleared -> clearPiece(loc)
            is PieceEvent.Action -> when(e.type) {
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
                e.moves.forEach { (_, f) ->
                    clearPiece(f.loc)
                }
                e.moves.forEach { (p, _) ->
                    p.piece.render(p.pos.loc)
                    playPieceSound(p.pos, "Move", p.type)
                }
            }
        }
    }
}