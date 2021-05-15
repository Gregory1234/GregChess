package gregc.gregchess.chess

import gregc.gregchess.*
import org.bukkit.Bukkit
import java.util.concurrent.Callable
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

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

data class BySides<T>(var white: T, var black: T) {
    operator fun get(side: ChessSide) = when (side) {
        ChessSide.WHITE -> white
        ChessSide.BLACK -> black
    }

    operator fun set(side: ChessSide, value: T) = when (side) {
        ChessSide.WHITE -> {
            white = value
        }
        ChessSide.BLACK -> {
            black = value
        }
    }

    fun toList(): List<T> = listOf(white, black)
    fun forEach(f: (T) -> Unit) {
        f(white)
        f(black)
    }
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

