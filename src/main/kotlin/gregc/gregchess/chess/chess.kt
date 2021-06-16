package gregc.gregchess.chess

import gregc.gregchess.*
import java.util.concurrent.*

enum class Side(val standardChar: Char, val direction: Int) {
    WHITE('w', 1),
    BLACK('b', -1);

    val standardName = name.snakeToPascal()

    operator fun not(): Side = if (this == WHITE) BLACK else WHITE
    operator fun inc(): Side = not()

    fun getPieceName(name: String) = Config.side.getSidePieceName(this, name)

    companion object {
        fun parseFromStandardChar(c: Char) =
            values().firstOrNull { it.standardChar == c }
                ?: throw IllegalArgumentException(c.toString())
    }

}

data class MutableBySides<T>(var white: T, var black: T) {
    constructor(block: (Side) -> T) : this(block(Side.WHITE), block(Side.BLACK))
    constructor(v: T) : this(v, v)

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

    inline fun <R> map(crossinline f: (T) -> R) = BySides { f(this[it]) }
    inline fun <R> mapIndexed(crossinline f: (Side, T) -> R) = BySides { f(it, this[it]) }
    fun toBySides() = BySides(white, black)
}

data class BySides<T>(val white: T, val black: T) {
    constructor(block: (Side) -> T) : this(block(Side.WHITE), block(Side.BLACK))
    constructor(v: T) : this(v, v)

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

    inline fun <R> map(crossinline f: (T) -> R) = BySides { f(this[it]) }
    inline fun <R> mapIndexed(crossinline f: (Side, T) -> R) = BySides { f(it, this[it]) }
    fun toMutableBySides() = MutableBySides(white, black)
}

interface ChessEngine {
    val name: String
    fun stop()
    fun setOption(name: String, value: String)
    fun sendCommand(command: String)
    fun getMove(fen: FEN, onSuccess: (String) -> Unit, onException: (Exception) -> Unit)
}

class Stockfish(val timeManager: TimeManager, override val name: String = Config.stockfish.engineName): ChessEngine {
    private val process: Process = ProcessBuilder(Config.stockfish.stockfishCommand).start()

    private val reader = process.inputStream.bufferedReader()

    private val executor = Executors.newCachedThreadPool()

    private var moveTime = 0.2.seconds

    override fun stop() = process.destroy()

    override fun setOption(name: String, value: String) {
        when (name) {
            "time" -> {
                moveTime = cNotNull(parseDuration(value), Config.error.wrongDurationFormat)
            }
            else -> {
                glog.io("setoption name $name value $value")
                process.outputStream.write(("setoption name $name value $value\n").toByteArray())
                process.outputStream.flush()
            }
        }
    }

    override fun sendCommand(command: String) {
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

    override fun getMove(fen: FEN, onSuccess: (String) -> Unit, onException: (Exception) -> Unit) {
        var move = ""
        var exc: Exception? = null
        timeManager.runTaskAsynchronously {
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
        }
        //TODO: this is potentially dangerous!
        timeManager.runTaskTimer(moveTime + 1.ticks, 1.ticks) {
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