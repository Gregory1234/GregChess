package gregc.gregchess.chess

import gregc.gregchess.*
import org.bukkit.*
import org.bukkit.generator.ChunkGenerator
import java.util.*
import java.util.concurrent.Callable
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

enum class Side(private val path: String, val standardName: String, val standardChar: Char, val direction: Int) {
    WHITE("Chess.Side.White", "White", 'w', 1),
    BLACK("Chess.Side.Black", "Black", 'b', -1);

    operator fun not(): Side = if (this == WHITE) BLACK else WHITE
    operator fun inc(): Side = not()

    fun getPieceName(name: String) =
        ConfigManager.getFormatString("$path.Piece", name)

    companion object {
        fun parseFromStandardChar(c: Char) =
            values().firstOrNull { it.standardChar == c }
                ?: throw IllegalArgumentException(c.toString())
    }

}

data class MutableBySides<T>(var white: T, var black: T) {
    operator fun get(side: Side) = when (side) {
        Side.WHITE -> white
        Side.BLACK -> black
    }

    operator fun set(side: Side, value: T) = when (side) {
        Side.WHITE -> {
            white = value
        }
        Side.BLACK -> {
            black = value
        }
    }

    fun toList(): List<T> = listOf(white, black)
    inline fun forEach(f: (T) -> Unit) {
        f(white)
        f(black)
    }
    inline fun <R> forEachIndexed(f: (Side, T) -> R) {
        f(Side.WHITE, white)
        f(Side.BLACK, black)
    }
    inline fun <R> map(f: (T) -> R) = BySides(f(white), f(black))
    inline fun <R> mapIndexed(f: (Side, T) -> R) = BySides(f(Side.WHITE, white), f(Side.BLACK, black))
    fun toBySides() = BySides(white, black)
}

data class BySides<T>(val white: T, val black: T) {
    operator fun get(side: Side) = when (side) {
        Side.WHITE -> white
        Side.BLACK -> black
    }

    fun toList(): List<T> = listOf(white, black)
    inline fun forEach(f: (T) -> Unit) {
        f(white)
        f(black)
    }
    inline fun <R> forEachIndexed(f: (Side, T) -> R) {
        f(Side.WHITE, white)
        f(Side.BLACK, black)
    }
    inline fun <R> map(f: (T) -> R) = BySides(f(white), f(black))
    inline fun <R> mapIndexed(f: (Side, T) -> R) = BySides(f(Side.WHITE, white), f(Side.BLACK, black))
    fun toMutableBySides() = MutableBySides(white, black)
}

class ChessEngine(val name: String) {
    private val process: Process = ProcessBuilder("stockfish").start()

    private val reader = process.inputStream.bufferedReader()

    private val executor = Executors.newCachedThreadPool()

    private var moveTime = 0.2.seconds

    fun stop() = process.destroy()

    fun setOption(name: String, value: String) {
        when (name) {
            "time" -> {
                moveTime = cNotNull(parseDuration(value), "WrongDurationFormat")
            }
            else -> {
                glog.io("setoption name $name value $value")
                process.outputStream.write(("setoption name $name value $value\n").toByteArray())
                process.outputStream.flush()
            }
        }
    }

    fun sendCommand(command: String) {
        glog.io("isready")
        process.outputStream.write("isready\n".toByteArray())
        process.outputStream.flush()
        executor.submit(Callable {
            glog.io(reader.readLine())
        })[moveTime.seconds / 2 + 3, TimeUnit.SECONDS]
        glog.io(command)
        process.outputStream.write(("$command\n").toByteArray())
        process.outputStream.flush()
    }

    private fun readLine() = executor.submit(Callable {
        val a = reader.readLine()
        glog.io(a)
        a
    })[moveTime.seconds / 2 + 3, TimeUnit.SECONDS]

    init {
        readLine()
    }

    fun getMove(fen: FEN, onSuccess: (String) -> Unit, onException: (Exception) -> Unit) {
        var move = ""
        var exc: Exception? = null
        Bukkit.getScheduler().runTaskAsynchronously(GregInfo.plugin, Runnable {
            try {
                sendCommand("position fen $fen")
                sendCommand("go movetime " + moveTime.toMillis())
                while (true) {
                    val line = readLine().split(" ")
                    if (line[0] == "bestmove") {
                        if (line[1] != "(none)")
                            move = line[1]
                        break
                    }
                }
            } catch (e: Exception) {
                exc = e
                throw e
            }
        })
        //TODO: this is potentially dangerous!
        TimeManager.runTaskTimer(moveTime + 1.ticks, 1.ticks) {
            if (move != "") {
                onSuccess(move)
                cancel()
            } else if (exc != null) {
                onException(exc!!)
                cancel()
            }
        }
    }
}

@JvmInline
value class Arena(val name: String){
    object WorldGen : ChunkGenerator() {
        override fun generateChunkData(world: World, random: Random, chunkX: Int, chunkZ: Int, biome: BiomeGrid) =
            createChunkData(world)

        override fun shouldGenerateCaves() = false
        override fun shouldGenerateMobs() = false
        override fun shouldGenerateDecorations() = false
        override fun shouldGenerateStructures() = false
    }
}

val Arena.world: World
    get() {
        val world = GregInfo.server.getWorld(name)

        return (if (world != null) {
            glog.low("World already exists", name)
            world
        } else {
            val ret = GregInfo.server.createWorld(WorldCreator(name).generator(Arena.WorldGen))!!
            glog.io("Created arena", name)
            ret
        }).apply {
            pvp = false
            setGameRule(GameRule.DO_DAYLIGHT_CYCLE, false)
            difficulty = Difficulty.PEACEFUL
        }
    }