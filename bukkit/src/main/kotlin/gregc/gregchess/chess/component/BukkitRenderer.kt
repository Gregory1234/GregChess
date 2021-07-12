package gregc.gregchess.chess.component

import gregc.gregchess.*
import gregc.gregchess.chess.*
import org.bukkit.*
import java.util.*

class BukkitRenderer(game: ChessGame, settings: Settings) : MinecraftRenderer(game, settings) {
    companion object {

        private val extraFunctionProviders: List<(ExtraRendererFunction<*>) -> Optional<Any?>> = emptyList()

        private data class FillVolume(val world: World, val mat: Material, val start: Loc, val stop: Loc) {
            constructor(world: World, mat: Material, loc: Loc) : this(world, mat, loc, loc)
        }

        private fun fill(vol: FillVolume) {
            for (i in vol.start.x..vol.stop.x)
                for (j in (vol.start.y..vol.stop.y).reversed())
                    for (k in vol.start.z..vol.stop.z)
                        vol.world.getBlockAt(i, j, k).type = vol.mat
        }
    }

    class Settings(tileSize: Int, offset: Loc = Loc(0, 0, 0)) : MinecraftRenderer.Settings(tileSize, offset) {
        override fun getComponent(game: ChessGame) = BukkitRenderer(game, this)
    }

    private val world get() = game.arena.world

    private fun fill(from: Loc, to: Loc, mat: Material) =
        fill(FillVolume(world, mat, from + settings.offset, to + settings.offset))

    override fun renderPiece(loc: Loc, piece: Piece) {
        piece.type.structure[piece.side].forEachIndexed { i, m ->
            fill(FillVolume(world, m, loc.copy(y = loc.y + i)))
        }
    }

    override fun clearPiece(loc: Loc) {
        (0..10).forEach { i ->
            fill(FillVolume(world, Material.AIR, loc.copy(y = loc.y + i)))
        }
    }

    private fun <R> doAt(pos: Pos, f: (World, Location) -> R) = getPieceLoc(pos).doIn(world, f)

    override fun playPieceSound(pos: Pos, sound: PieceSound, type: PieceType) =
        doAt(pos) { world, l -> world.playSound(l, type.getSound(sound)) }

    override fun explosionAt(pos: Pos) {
        doAt(pos) { world, l -> world.createExplosion(l, 4.0f, false, false) }
    }

    override fun fillFloor(pos: Pos, floor: Floor) {
        val (x, y, z) = getPieceLoc(pos)
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

    override fun <R> executeAny(f: ExtraRendererFunction<R>): Any? {
        extraFunctionProviders.forEach {
            val v = it(f)
            if (v.isPresent)
                return v.orElseThrow()
        }
        return super.executeAny(f)
    }

    @GameEvent(GameBaseEvent.PRE_INIT)
    fun validate() {
        game.requireComponent<Arena.Usage>()
    }
}