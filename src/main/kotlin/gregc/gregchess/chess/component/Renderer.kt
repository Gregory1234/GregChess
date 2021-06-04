package gregc.gregchess.chess.component

import gregc.gregchess.*
import gregc.gregchess.chess.*
import org.bukkit.*
import java.util.*
import kotlin.math.floor

interface Renderer<in T>: Component {
    interface Settings<in T>: Component.Settings<Renderer<T>>
    fun getPos(loc: T): Pos
    fun renderPiece(pos: Pos, piece: Piece)
    fun clearPiece(pos: Pos)
    fun renderCapturedPiece(pos: CapturedPos, piece: Piece)
    fun clearCapturedPiece(pos: CapturedPos)
    fun playPieceSound(pos: Pos, sound: PieceSound, type: PieceType)
    fun explosionAt(pos: Pos)
    fun fillFloor(pos: Pos, floor: Floor)
    fun renderBoardBase()
    fun removeBoard()
}

abstract class MinecraftRenderer(protected val game: ChessGame, protected val settings: Settings): Renderer<Loc> {
    abstract class Settings(val tileSize: Int, val offset: Loc = Loc(0,0,0)): Renderer.Settings<Loc> {
        internal val highHalfTile
            get() = floor(tileSize.toDouble()/2).toInt()
        internal val lowHalfTile
            get() = floor((tileSize.toDouble()-1)/2).toInt()
    }

    protected companion object {
        val defaultSpawnLocation = Loc(4, 101, 4)
    }

    val spawnLocation
        get() = defaultSpawnLocation + settings.offset

    override fun getPos(loc: Loc) =
        Pos(((settings.tileSize+1) * 8 - 1 - loc.x + settings.offset.x).floorDiv(settings.tileSize), (loc.z - settings.offset.z - 8).floorDiv(settings.tileSize))

    protected fun getPieceLoc(pos: Pos) =
        Loc((settings.tileSize+1) * 8 - 1 - settings.highHalfTile - pos.file * settings.tileSize, 102, pos.rank * settings.tileSize + 8 + settings.lowHalfTile) + settings.offset

    protected fun getCapturedLoc(pos: CapturedPos): Loc {
        val p = pos.pos
        return when (pos.by) {
            Side.WHITE -> Loc((settings.tileSize+1) * 8 - 1 - 2 * p.first, 101, 8 - 3 - 2 * p.second)
            Side.BLACK -> Loc(8 + 2 * p.first, 101, 8 * (settings.tileSize+1) + 2 + 2 * p.second)
        } + settings.offset
    }

    protected abstract fun renderPiece(loc: Loc, piece: Piece)

    protected abstract fun clearPiece(loc: Loc)

    override fun renderPiece(pos: Pos, piece: Piece) = renderPiece(getPieceLoc(pos), piece)

    override fun clearPiece(pos: Pos) = clearPiece(getPieceLoc(pos))

    override fun renderCapturedPiece(pos: CapturedPos, piece: Piece) = renderPiece(getCapturedLoc(pos), piece)

    override fun clearCapturedPiece(pos: CapturedPos) = clearPiece(getCapturedLoc(pos))
}

class BukkitRenderer(game: ChessGame, settings: Settings): MinecraftRenderer(game, settings) {
    private companion object {
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

    class Settings(tileSize: Int, offset: Loc = Loc(0,0,0))
        : MinecraftRenderer.Settings(tileSize, offset) {
        override fun getComponent(game: ChessGame) = BukkitRenderer(game, this)
    }

    private val world = game.arena.world

    private val data = mutableMapOf<UUID, PlayerData>()

    override fun renderPiece(loc: Loc, piece: Piece) {
        piece.type.view.structure[piece.side].get(game.config).forEachIndexed { i, m ->
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
        doAt(pos) { world, l -> world.playSound(l, type.view.sound[sound].get(game.config)) }

    override fun explosionAt(pos: Pos) {
        doAt(pos) { world, l ->
            world.createExplosion(l, 4.0f, false, false)
        }
    }

    override fun fillFloor(pos: Pos, floor: Floor) {
        val (x, y, z) = getPieceLoc(pos)
        val mi = -settings.lowHalfTile
        val ma = settings.highHalfTile
        fill(FillVolume(world, floor.material.get(game.config), Loc(x+mi, y - 1, z+mi), Loc(x+ma, y - 1, z+ma)))
    }

    override fun renderBoardBase() {
        fill(FillVolume(world, Material.DARK_OAK_PLANKS, Loc(0,100,0) + settings.offset, Loc(8 * (settings.tileSize+2)-1,100,8 * (settings.tileSize+2)-1) + settings.offset))
        fill(FillVolume(world, Material.DARK_OAK_PLANKS, Loc(8 - 1,101,8 - 1) + settings.offset, Loc(8 * (settings.tileSize+1),101,8 * (settings.tileSize+1)) + settings.offset))
    }

    override fun removeBoard() {
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
        player.teleport(spawnLocation.toLocation(this@BukkitRenderer.world))
        game[this]?.held?.let { setItem(game.config, 0, it.piece )}
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
    @GameEvent(GameBaseEvent.ADD_PLAYER, relaxed = true)
    fun addPlayer(p: BukkitPlayer) {
        p.join(if (p.isAdmin) adminData else defData)
    }
    @GameEvent(GameBaseEvent.RESET_PLAYER, relaxed = true)
    fun resetPlayer(p: BukkitPlayer) {
        p.reset(if (p.isAdmin) adminData else defData)
    }
}