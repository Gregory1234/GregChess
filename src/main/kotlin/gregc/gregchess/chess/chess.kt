package gregc.gregchess.chess

import gregc.gregchess.*
import org.bukkit.*
import org.bukkit.generator.ChunkGenerator
import java.util.*
import java.util.concurrent.Callable
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit


class ChessArena(name: String) : Arena(name, "Chess.ResourcePack") {
    class WorldGen : ChunkGenerator() {
        override fun generateChunkData(
            world: World,
            random: Random,
            chunkX: Int,
            chunkZ: Int,
            biome: BiomeGrid
        ): ChunkData {
            return createChunkData(world)
        }

        override fun shouldGenerateCaves() = false
        override fun shouldGenerateMobs() = false
        override fun shouldGenerateDecorations() = false
        override fun shouldGenerateStructures() = false
    }

    override val defaultData = PlayerData(allowFlight = true, isFlying = true)
    override val spectatorData =
        PlayerData(allowFlight = true, isFlying = true, gameMode = GameMode.SPECTATOR)
    override val worldGen: ChunkGenerator = WorldGen()
    override val setSettings: World.() -> Unit = {
        setSpawnLocation(4, 101, 4)
        pvp = false
        setGameRule(GameRule.DO_DAYLIGHT_CYCLE, false)
        difficulty = Difficulty.PEACEFUL
    }
}

enum class ChessSide(
    private val path: String,
    val standardName: String,
    val standardChar: Char,
    val direction: Int
) {
    WHITE("Chess.Side.White", "White", 'w', 1),
    BLACK("Chess.Side.Black", "Black", 'b', -1);

    operator fun not(): ChessSide = if (this == WHITE) BLACK else WHITE
    operator fun inc(): ChessSide = not()

    fun getPieceName(name: String) =
        ConfigManager.getFormatString("$path.Piece", name)

    companion object {
        fun parseFromStandardChar(c: Char) =
            values().firstOrNull { it.standardChar == c }
                ?: throw IllegalArgumentException(c.toString())
    }

}

data class ChessPosition(val file: Int, val rank: Int) {
    override fun toString() = "$fileStr$rankStr"
    operator fun plus(diff: Pair<Int, Int>) = plus(diff.first, diff.second)
    fun plus(df: Int, dr: Int) = ChessPosition(file + df, rank + dr)
    fun plusF(df: Int) = plus(df, 0)
    fun plusR(dr: Int) = plus(0, dr)
    val fileStr = "${'a' + file}"
    val rankStr = (rank + 1).toString()

    fun neighbours(): List<ChessPosition> =
        (Pair(-1, -1)..Pair(1, 1)).map(::plus).filter { it.isValid() } - this

    fun isValid() = file in (0..7) && rank in (0..7)

    companion object {
        fun parseFromString(s: String): ChessPosition {
            if (s.length != 2 || s[0] !in 'a'..'h' || s[1] !in '1'..'8')
                throw IllegalArgumentException(s)
            return ChessPosition(s[0].toLowerCase() - 'a', s[1] - '1')
        }
    }
}

class ChessPositionSteps(
    val start: ChessPosition,
    private val jump: Pair<Int, Int>,
    override val size: Int
) :
    Collection<ChessPosition> {
    class ChessIterator(
        val start: ChessPosition,
        private val jump: Pair<Int, Int>,
        private var remaining: Int
    ) : Iterator<ChessPosition> {
        private var value = start

        override fun hasNext() = remaining > 0

        override fun next(): ChessPosition {
            val ret = value
            value += jump
            remaining--
            return ret
        }
    }

    companion object {
        private fun calcSize(start: ChessPosition, jump: Pair<Int, Int>): Int {
            val ret = mutableListOf<Int>()

            if (jump.first > 0) {
                ret += Math.floorDiv(8 - start.file, jump.first)
            } else if (jump.first < 0) {
                ret += Math.floorDiv(start.file + 1, -jump.first)
            }

            if (jump.second > 0) {
                ret += Math.floorDiv(8 - start.rank, jump.second)
            } else if (jump.second < 0) {
                ret += Math.floorDiv(start.rank + 1, -jump.second)
            }

            return ret.minOrNull() ?: 1
        }
    }

    constructor(start: ChessPosition, jump: Pair<Int, Int>) :
            this(start, jump, calcSize(start, jump))

    override fun contains(element: ChessPosition): Boolean {
        if (jump.first == 0) {
            if (element.file != start.file)
                return false
            if (jump.second == 0)
                return element == start
            val m = Math.floorMod(element.rank - start.rank, jump.second)
            if (m != 0)
                return false
            val d = Math.floorDiv(element.rank - start.rank, jump.second)
            return d in (0 until size)
        } else {
            val m = Math.floorMod(element.file - start.file, jump.first)
            if (m != 0)
                return false
            val d = Math.floorDiv(element.file - start.file, jump.first)
            if (d !in (0 until size))
                return false
            return (element.rank - start.rank) == d * jump.second
        }
    }

    override fun containsAll(elements: Collection<ChessPosition>) = elements.all { it in this }

    override fun isEmpty() = size <= 0

    override fun iterator() = ChessIterator(start, jump, size)
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

data class ChessSquare(val pos: ChessPosition, val game: ChessGame) {
    var piece: ChessPiece? = null
    var bakedMoves: List<MoveCandidate>? = null

    private val baseFloor =
        if ((pos.file + pos.rank) % 2 == 0) Material.SPRUCE_PLANKS else Material.BIRCH_PLANKS
    var variantMarker: Material? = null
    var previousMoveMarker: Material? = null
    var moveMarker: Material? = null
    private val floor
        get() = moveMarker ?: previousMoveMarker ?: variantMarker ?: baseFloor

    val board
        get() = game.board

    override fun toString() =
        "ChessSquare(game.uniqueId = ${game.uniqueId}, pos = $pos, piece = $piece, floor = $floor)"

    fun render() {
        board.renderer.fillFloor(pos, floor)
    }

    fun clear() {
        piece?.clear()
        variantMarker = null
        bakedMoves = null
        previousMoveMarker = null
        moveMarker = null
        board.renderer.fillFloor(pos, floor)
    }

    fun neighbours() = pos.neighbours().mapNotNull { this.board[it] }
}
