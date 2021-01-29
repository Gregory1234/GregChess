package gregc.gregchess.chess

import gregc.gregchess.Arena
import gregc.gregchess.PlayerData
import gregc.gregchess.info
import gregc.gregchess.star
import org.bukkit.Difficulty
import org.bukkit.GameMode
import org.bukkit.GameRule
import org.bukkit.World
import org.bukkit.generator.ChunkGenerator
import java.util.*
import java.util.concurrent.Callable
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit


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
    val fileStr = "${'a'+file}"
    val rankStr = (rank + 1).toString()

    fun neighbours(): List<ChessPosition> =
            (-1..1).star(-1..1) { a, b -> this.plus(a, b) }.filter { it.isValid() } - this

    fun isValid() = file in (0..7) && rank in (0..7)

    companion object {
        fun parseFromString(s: String) = ChessPosition(s[0].toLowerCase() - 'a', s[1] - '1')
    }
}

class ChessEngine(val name: String) {
    private val process: Process = ProcessBuilder("stockfish").start()

    private val reader = process.inputStream.bufferedReader()

    private val executor = Executors.newCachedThreadPool()

    fun stop() = process.destroy()

    fun setOption(name: String, value: String) {
        process.outputStream.write(("setoption name $name value $value\n").toByteArray())
        process.outputStream.flush()
        info(name, value)
    }

    fun sendCommand(command: String) {
        process.outputStream.write("isready\n".toByteArray())
        process.outputStream.flush()
        executor.submit(Callable {
            reader.readLine()
        })[5, TimeUnit.SECONDS]
        process.outputStream.write(("$command\n").toByteArray())
        process.outputStream.flush()
        info(command)
    }

    private fun readLine() = executor.submit(Callable {
        reader.readLine()
    })[5, TimeUnit.SECONDS]

    init {
        readLine()
    }

    fun getMove(fen: String): String {

        sendCommand("position fen $fen")
        sendCommand("go movetime 200")

        while (true) {
            val line = readLine().split(" ")
            info(line)
            if (line[0] == "bestmove") {
                if (line[1] == "(none)")
                    return ""
                return line[1]
            }
        }
    }
}