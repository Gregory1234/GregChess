package gregc.gregchess.chess.component

import gregc.gregchess.*
import gregc.gregchess.chess.*
import org.bukkit.*
import java.lang.IllegalStateException
import java.util.*
import kotlin.math.floor

class Renderer(private val game: ChessGame, private val settings: Settings): Component {

    private companion object {
        val defaultSpawnLocation = Loc(4,101,4)

        val defData = PlayerData(allowFlight = true, isFlying = true)

        val spectatorData = defData.copy(gameMode = GameMode.SPECTATOR)

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

    data class Settings(val tileSize: Int, val arenaWorld: String? = null, val offset: Loc = Loc(0,0,0)) {
        fun getComponent(game: ChessGame) = Renderer(game, this)

        internal val highHalfTile
            get() = floor(tileSize.toDouble()/2).toInt()
        internal val lowHalfTile
            get() = floor((tileSize.toDouble()-1)/2).toInt()
    }

    private val world = game.arena.world

    val spawnLocation
        get() = defaultSpawnLocation + settings.offset

    private val data = mutableMapOf<UUID, PlayerData>()

    fun getPos(loc: Loc) =
        Pos(((settings.tileSize+1) * 8 - 1 - loc.x + settings.offset.x).floorDiv(settings.tileSize), (loc.z - settings.offset.z - 8).floorDiv(settings.tileSize))

    fun getPieceLoc(pos: Pos) =
        Loc((settings.tileSize+1) * 8 - 1 - settings.highHalfTile - pos.file * settings.tileSize, 102, pos.rank * settings.tileSize + 8 + settings.lowHalfTile) + settings.offset

    fun getCapturedLoc(pos: Pair<Int, Int>, by: Side): Loc {
        return when (by) {
            Side.WHITE -> Loc((settings.tileSize+1) * 8 - 1 - 2 * pos.first, 101, 8 - 3 - 2 * pos.second)
            Side.BLACK -> Loc(8 + 2 * pos.first, 101, 8 * (settings.tileSize+1) + 2 + 2 * pos.second)
        } + settings.offset
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
        game.players.forEachReal { it.join() }
    }

    fun renderBoardBase() {
        fill(FillVolume(world, Material.DARK_OAK_PLANKS, Loc(0,100,0) + settings.offset, Loc(8 * (settings.tileSize+2)-1,100,8 * (settings.tileSize+2)-1) + settings.offset))
        fill(FillVolume(world, Material.DARK_OAK_PLANKS, Loc(8 - 1,101,8 - 1) + settings.offset, Loc(8 * (settings.tileSize+1),101,8 * (settings.tileSize+1)) + settings.offset))
    }

    fun removeBoard() {
        fill(FillVolume(world, Material.AIR, Loc(0,100,0) + settings.offset, Loc(8 * (settings.tileSize+2)-1,105,8 * (settings.tileSize+2)-1) + settings.offset))
    }

    private fun HumanPlayer.join(d: PlayerData = defData){
        if (bukkit.uniqueId in data)
            throw IllegalStateException("player already teleported")
        data[bukkit.uniqueId] = bukkit.playerData
        reset(d)
    }

    private fun HumanPlayer.leave(){
        if (bukkit.uniqueId !in data)
            throw IllegalStateException("player data not found")
        bukkit.playerData = data[bukkit.uniqueId]!!
        data.remove(bukkit.uniqueId)
    }

    private fun HumanPlayer.reset(d: PlayerData = defData) {
        bukkit.playerData = d
        bukkit.teleport(spawnLocation.toLocation(this@Renderer.world))
        game[this]?.held?.let { bukkit.inventory.setItem(0, it.item )}
    }

    @GameEvent(GameBaseEvent.PANIC)
    fun evacuate() {
        game.players.forEachReal { it.leave() }
    }

    @GameEvent(GameBaseEvent.SPECTATOR_JOIN, mod = TimeModifier.EARLY)
    fun spectatorJoin(p: HumanPlayer) {
        p.join(spectatorData)
    }

    @GameEvent(GameBaseEvent.SPECTATOR_LEAVE, mod = TimeModifier.LATE)
    fun spectatorLeave(p: HumanPlayer) {
        p.leave()
    }
    @GameEvent(GameBaseEvent.REMOVE_PLAYER)
    fun removePlayer(p: HumanPlayer) {
        p.leave()
    }

    fun resetPlayer(p: HumanPlayer, d: PlayerData = defData) = p.reset(d)
}