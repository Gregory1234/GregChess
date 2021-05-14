package gregc.gregchess.chess

import gregc.gregchess.*
import org.bukkit.*
import org.bukkit.entity.Player
import org.bukkit.generator.ChunkGenerator
import org.bukkit.scoreboard.Scoreboard
import java.lang.IllegalStateException
import java.util.*

class Arena(val name: String, private val gameModeInfo: GameModeInfo, scoreboard: Scoreboard?, val offset: Loc) {

    private object WorldGen : ChunkGenerator() {
        override fun generateChunkData(world: World, random: Random, chunkX: Int, chunkZ: Int, biome: BiomeGrid) =
            createChunkData(world)

        override fun shouldGenerateCaves() = false
        override fun shouldGenerateMobs() = false
        override fun shouldGenerateDecorations() = false
        override fun shouldGenerateStructures() = false
    }

    val world = run {

        if (GregInfo.server.getWorld(name) != null) {
            glog.low("World already exists", name)
            GregInfo.server.getWorld(name)!!
        }
        else {
            GregInfo.server.createWorld(WorldCreator(name).generator(WorldGen))
            glog.io("Created arena", name)
            GregInfo.server.getWorld(name)!!
        }
    }.apply {
        pvp = gameModeInfo.pvpOn
        setGameRule(GameRule.DO_DAYLIGHT_CYCLE, false)
        difficulty = Difficulty.PEACEFUL
    }

    private val data: MutableMap<UUID, PlayerData> = mutableMapOf()

    val defData: PlayerData =
        gameModeInfo.defData.copy(
            location = (gameModeInfo.spawnLoc + offset).toLocation(world),
            scoreboard = scoreboard ?: gameModeInfo.defData.scoreboard
        )

    fun teleportPlayer(p: Player) {
        if (p.uniqueId in data)
            throw IllegalStateException("player already teleported")
        data[p.uniqueId] = p.playerData
        p.playerData = defData
    }

    fun teleportSpectator(p: Player) {
        if (p.uniqueId in data)
            throw IllegalStateException("player already teleported")
        data[p.uniqueId] = p.playerData
        p.playerData = defData
        p.gameMode = GameMode.SPECTATOR
    }

    fun leavePlayer(p: Player) {
        if (p.uniqueId !in data)
            throw IllegalStateException("player data not found")
        p.playerData = data[p.uniqueId]!!
        data.remove(p.uniqueId)
    }

}

data class GameModeInfo(val spawnLoc: Loc, val defData: PlayerData, val pvpOn: Boolean)
