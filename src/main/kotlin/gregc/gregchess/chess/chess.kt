package gregc.gregchess.chess

import gregc.gregchess.*
import org.bukkit.*
import org.bukkit.generator.ChunkGenerator
import java.lang.IllegalArgumentException
import java.util.*


class ChessArena(name: String) : Arena(name) {
    class WorldGen : ChunkGenerator() {
        override fun generateChunkData(world: World, random: Random, chunkX: Int, chunkZ: Int, biome: BiomeGrid): ChunkData {
            return createChunkData(world)
        }

        override fun shouldGenerateCaves() = false
        override fun shouldGenerateMobs() = false
        override fun shouldGenerateDecorations() = false
        override fun shouldGenerateStructures() = false
    }

    override val defaultData = PlayerData(allowFlight = true, isFlying = true)
    override val spectatorData = PlayerData(allowFlight = true, isFlying = true, gameMode = GameMode.SPECTATOR)
    override val worldGen: ChunkGenerator = WorldGen()
    override val setSettings: World.() -> Unit = {
        setSpawnLocation(4, 101, 4)
        pvp = false
        setGameRule(GameRule.DO_DAYLIGHT_CYCLE, false)
        difficulty = Difficulty.PEACEFUL
    }
}

enum class ChessSide(val prettyName: String, val character: Char, val direction: Int) {
    WHITE("White", 'w', 1), BLACK("Black", 'b', -1);

    operator fun not(): ChessSide = if (this == WHITE) BLACK else WHITE
    operator fun inc(): ChessSide = not()

    companion object {
        fun parseFromChar(c: Char) = when (c.toLowerCase()) {
            'w' -> WHITE
            'b' -> BLACK
            else -> throw IllegalArgumentException(c.toString())
        }
    }

}

data class ChessPosition(val file: Int, val rank: Int) {
    override fun toString() = "$fileStr$rankStr"
    operator fun plus(diff: Pair<Int, Int>) = plus(diff.first, diff.second)
    fun plus(df: Int, dr: Int) = ChessPosition(file + df, rank + dr)
    fun plusF(df: Int) = plus(df, 0)
    fun plusR(dr: Int) = plus(0, dr)
    private val fileStr = "${'a'+file}"
    private val rankStr = (rank + 1).toString()

    fun neighbours(): List<ChessPosition> =
            (-1..1).star(-1..1) { a, b -> this.plus(a, b) }.filter { it.isValid() } - this

    fun isValid() = file in (0..7) && rank in (0..7)

    fun getBlock(world: World) = world.getBlockAt(toLoc())
    fun fillFloor(world: World, type: Material) {
        (-1..1).star(-1..1) { i, j ->
            val (x, y, z) = toLoc()
            world.getBlockAt(x + i, y - 1, z + j).type = type
        }
    }


    fun toLoc() = Loc(4 * 8 - 2 - file * 3, 102, rank * 3 + 8 + 1)
    fun clear(world: World) {
        fillFloor(world, if ((file + rank) % 2 == 0) Material.SPRUCE_PLANKS else Material.BIRCH_PLANKS)
    }

    companion object {
        fun fromLoc(l: Loc) = ChessPosition((4 * 8 - 1 - l.x).div(3), (l.z - 8).div(3))
        fun parseFromString(s: String) = ChessPosition(s[0].toLowerCase() - 'a', s[1] - '1')
    }
}

