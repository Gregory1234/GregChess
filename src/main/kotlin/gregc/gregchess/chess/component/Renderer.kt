package gregc.gregchess.chess.component

import gregc.gregchess.*
import gregc.gregchess.chess.*
import org.bukkit.*
import org.bukkit.entity.Player
import kotlin.math.floor

class Renderer(private val game: ChessGame, private val settings: Settings): Component {

    private companion object {
        data class FillVolume(val world: World, val mat: Material, val start: Loc, val stop: Loc) {
            constructor(world: World, mat: Material, loc: Loc): this(world, mat, loc, loc)
        }
        fun fill(vol: FillVolume) {
            for (i in vol.start.x..vol.stop.x)
                for (j in vol.start.y..vol.stop.y)
                    for (k in vol.start.z..vol.stop.z)
                        vol.world.getBlockAt(i,j,k).type = vol.mat
        }
    }

    data class Settings(val tileSize: Int, val gameModeInfo: GameModeInfo, val arenaWorld: String? = null, val offset: Loc? = null) {
        fun getComponent(game: ChessGame) = Renderer(game, this)

        internal val highHalfTile
            get() = floor(tileSize.toDouble()/2).toInt()
        internal val lowHalfTile
            get() = floor((tileSize.toDouble()-1)/2).toInt()
    }

    private lateinit var arena: Arena

    private val world
        get() = arena.world

    val arenaName
        get() = arena.name

    val spawnLocation
        get() = arena.defData.location ?: world.spawnLocation

    fun getPos(loc: Loc) =
        Pos(((settings.tileSize+1) * 8 - 1 - loc.x + arena.offset.x).floorDiv(settings.tileSize), (loc.z - arena.offset.z - 8).floorDiv(settings.tileSize))

    fun getPieceLoc(pos: Pos) =
        Loc((settings.tileSize+1) * 8 - 1 - settings.highHalfTile - pos.file * settings.tileSize, 102, pos.rank * settings.tileSize + 8 + settings.lowHalfTile) + arena.offset

    fun getCapturedLoc(pos: Pair<Int, Int>, by: Side): Loc {
        return when (by) {
            Side.WHITE -> Loc((settings.tileSize+1) * 8 - 1 - 2 * pos.first, 101, 8 - 3 - 2 * pos.second)
            Side.BLACK -> Loc(8 + 2 * pos.first, 101, 8 * (settings.tileSize+1) + 2 + 2 * pos.second)
        } + arena.offset
    }

    fun renderPiece(loc: Loc, structure: List<Material>) {
        structure.forEachIndexed { i, m ->
            fill(FillVolume(world, m, loc.copy(y = loc.y + i)))
        }
    }

    fun <R> doAt(pos: Pos, f: (World, Location) -> R) = getPieceLoc(pos).doIn(world, f)

    fun playPieceSound(pos: Pos, sound: Sound) = doAt(pos) { world, l -> world.playSound(l, sound) }

    fun fillFloor(pos: Pos, floor: Material) {
        val (x, y, z) = getPieceLoc(pos)
        val mi = -settings.lowHalfTile
        val ma = settings.highHalfTile
        fill(FillVolume(world, floor, Loc(x+mi, y - 1, z+mi), Loc(x+ma, y - 1, z+ma)))
    }

    @GameEvent(GameBaseEvent.INIT, mod = TimeModifier.EARLY)
    fun init() {
        game.players.forEachReal(arena::teleportPlayer)
    }

    fun checkForFreeArenas(): Boolean {
        if (settings.arenaWorld != null){
            arena = Arena(settings.arenaWorld, settings.gameModeInfo, game.scoreboard.scoreboard, settings.offset ?: Loc(0,0,0))
            return true
        }

        val a = ChessManager.nextArena()

        if (a != null) {
            arena = Arena(a, settings.gameModeInfo, game.scoreboard.scoreboard, settings.offset ?: Loc(0,0,0))
            return true
        }
        return false
    }

    fun renderBoardBase() {
        fill(FillVolume(world, Material.DARK_OAK_PLANKS, Loc(0,100,0) + arena.offset, Loc(8 * (settings.tileSize+2)-1,100,8 * (settings.tileSize+2)-1) + arena.offset))
        fill(FillVolume(world, Material.DARK_OAK_PLANKS, Loc(8 - 1,101,8 - 1) + arena.offset, Loc(8 * (settings.tileSize+1),101,8 * (settings.tileSize+1)) + arena.offset))
    }

    fun removeBoard() {
        fill(FillVolume(world, Material.AIR, Loc(0,100,0) + arena.offset, Loc(8 * (settings.tileSize+2)-1,105,8 * (settings.tileSize+2)-1) + arena.offset))
    }

    @GameEvent(GameBaseEvent.PANIC)
    fun evacuate() {
        game.players.forEachReal(arena::leavePlayer)
    }

    @GameEvent(GameBaseEvent.SPECTATOR_JOIN, mod = TimeModifier.EARLY)
    fun spectatorJoin(p: Player) {
        arena.teleportSpectator(p)
    }

    @GameEvent(GameBaseEvent.SPECTATOR_LEAVE, mod = TimeModifier.LATE)
    fun spectatorLeave(p: Player) {
        arena.leavePlayer(p)
    }
    @GameEvent(GameBaseEvent.REMOVE_PLAYER)
    fun removePlayer(p: Player) {
        arena.leavePlayer(p)
    }

    fun resetPlayer(p: Player) {
        p.playerData = arena.defData
        game[p]?.held?.let { p.inventory.setItem(0, it.item )}
    }
}