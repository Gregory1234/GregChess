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
        val adminData = defData.copy(gameMode = GameMode.CREATIVE)

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

    private fun getPieceLoc(pos: Pos) =
        Loc((settings.tileSize+1) * 8 - 1 - settings.highHalfTile - pos.file * settings.tileSize, 102, pos.rank * settings.tileSize + 8 + settings.lowHalfTile) + settings.offset

    private fun getCapturedLoc(pos: Pair<Int, Int>, by: Side): Loc {
        return when (by) {
            Side.WHITE -> Loc((settings.tileSize+1) * 8 - 1 - 2 * pos.first, 101, 8 - 3 - 2 * pos.second)
            Side.BLACK -> Loc(8 + 2 * pos.first, 101, 8 * (settings.tileSize+1) + 2 + 2 * pos.second)
        } + settings.offset
    }

    private fun renderPiece(loc: Loc, structure: List<Material>) {
        structure.forEachIndexed { i, m ->
            fill(FillVolume(world, m, loc.copy(y = loc.y + i)))
        }
    }

    fun renderPiece(pos: Pos, structure: List<Material>) = renderPiece(getPieceLoc(pos), structure)

    fun renderCapturedPiece(pos: Pair<Int, Int>, by: Side, structure: List<Material>) = renderPiece(getCapturedLoc(pos, by), structure)

    private fun <R> doAt(pos: Pos, f: (World, Location) -> R) = getPieceLoc(pos).doIn(world, f)

    fun playPieceSound(pos: Pos, sound: Sound) = doAt(pos) { world, l -> world.playSound(l, sound) }

    fun explosionAt(pos: Pos) = doAt(pos) { world, l ->
        world.createExplosion(l, 4.0f, false, false)
    }

    fun fillFloor(pos: Pos, floor: Material) {
        val (x, y, z) = getPieceLoc(pos)
        val mi = -settings.lowHalfTile
        val ma = settings.highHalfTile
        fill(FillVolume(world, floor, Loc(x+mi, y - 1, z+mi), Loc(x+ma, y - 1, z+ma)))
    }

    fun renderBoardBase() {
        fill(FillVolume(world, Material.DARK_OAK_PLANKS, Loc(0,100,0) + settings.offset, Loc(8 * (settings.tileSize+2)-1,100,8 * (settings.tileSize+2)-1) + settings.offset))
        fill(FillVolume(world, Material.DARK_OAK_PLANKS, Loc(8 - 1,101,8 - 1) + settings.offset, Loc(8 * (settings.tileSize+1),101,8 * (settings.tileSize+1)) + settings.offset))
    }

    fun removeBoard() {
        fill(FillVolume(world, Material.AIR, Loc(0,100,0) + settings.offset, Loc(8 * (settings.tileSize+2)-1,105,8 * (settings.tileSize+2)-1) + settings.offset))
    }

    private fun BukkitPlayer.join(d: PlayerData = defData){
        if (uniqueId in data)
            throw IllegalStateException("player already teleported")
        data[uniqueId] = player.playerData
        reset(d)
    }

    private fun BukkitPlayer.leave(){
        if (uniqueId !in data)
            throw IllegalStateException("player data not found")
        player.playerData = data[uniqueId]!!
        data.remove(uniqueId)
    }

    private fun BukkitPlayer.reset(d: PlayerData = defData) {
        player.playerData = d
        player.teleport(spawnLocation.toLocation(this@Renderer.world))
        game[this]?.held?.let { setItem(0, it.item )}
    }

    @GameEvent(GameBaseEvent.PANIC)
    fun evacuate() {
        game.players.forEachReal { (it as? BukkitPlayer)?.leave() }
    }

    @GameEvent(GameBaseEvent.SPECTATOR_JOIN, mod = TimeModifier.EARLY, relaxed = true)
    fun spectatorJoin(p: BukkitPlayer) {
        p.join(spectatorData)
    }

    @GameEvent(GameBaseEvent.SPECTATOR_LEAVE, mod = TimeModifier.LATE, relaxed = true)
    fun spectatorLeave(p: BukkitPlayer) {
        p.leave()
    }
    @GameEvent(GameBaseEvent.REMOVE_PLAYER, relaxed = true)
    fun removePlayer(p: BukkitPlayer) {
        p.leave()
    }
    @GameEvent(GameBaseEvent.ADD_PLAYER, GameBaseEvent.RESET_PLAYER, relaxed = true)
    fun resetPlayer(p: BukkitPlayer) {
        p.reset(if (p.isAdmin) adminData else defData)
    }
}