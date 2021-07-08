package gregc.gregchess.chess.component

import gregc.gregchess.*
import gregc.gregchess.chess.*
import org.bukkit.*
import java.util.*

class BukkitRenderer(game: ChessGame, settings: Settings) : MinecraftRenderer(game, settings) {
    private companion object {
        val defData = PlayerData(allowFlight = true, isFlying = true)

        val spectatorData = defData.copy(gameMode = GameMode.SPECTATOR)
        val adminData = defData.copy(gameMode = GameMode.CREATIVE)

        data class FillVolume(val world: World, val mat: Material, val start: Loc, val stop: Loc) {
            constructor(world: World, mat: Material, loc: Loc) : this(world, mat, loc, loc)
        }

        fun fill(vol: FillVolume) {
            for (i in vol.start.x..vol.stop.x)
                for (j in vol.start.y..vol.stop.y)
                    for (k in vol.start.z..vol.stop.z)
                        vol.world.getBlockAt(i, j, k).type = vol.mat
        }
    }

    class Settings(tileSize: Int, offset: Loc = Loc(0, 0, 0)) : MinecraftRenderer.Settings(tileSize, offset) {
        override fun getComponent(game: ChessGame) = BukkitRenderer(game, this)
    }

    private val world = game.arena.world

    private val data = mutableMapOf<UUID, PlayerData>()

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

    private fun BukkitPlayer.join(d: PlayerData = defData) {
        if (uniqueId in data)
            throw IllegalStateException("player already teleported")
        data[uniqueId] = player.playerData
        reset(d)
    }

    private fun BukkitPlayer.leave() {
        if (uniqueId !in data)
            throw IllegalStateException("player data not found")
        player.playerData = data[uniqueId]!!
        data.remove(uniqueId)
    }

    private fun BukkitPlayer.reset(d: PlayerData = defData) {
        player.teleport(spawnLocation.toLocation(this@BukkitRenderer.world))
        player.playerData = d
        game[this]?.held?.let { setItem(0, it.piece) }
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