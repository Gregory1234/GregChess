package gregc.gregchess.bukkit.chess

import gregc.gregchess.asDurationOrNull
import gregc.gregchess.bukkit.*
import gregc.gregchess.chess.ChessEngine
import gregc.gregchess.chess.FEN
import gregc.gregchess.seconds
import java.util.concurrent.*

class Stockfish(override val name: String = Config.engineName) : ChessEngine {

    object Config {
        val hasStockfish get() = config.getBoolean("Chess.HasStockfish", false)
        val stockfishCommand get() = config.getString("Chess.Stockfish.Path")!!
        val engineName get() = config.getString("Chess.Stockfish.Name")!!
    }

    private val process: Process = ProcessBuilder(Config.stockfishCommand).start()

    private val reader = process.inputStream.bufferedReader()

    private val executor = Executors.newCachedThreadPool()

    private fun <T> runTimeout(block: Callable<T>) = executor.submit(block)[moveTime.seconds / 2 + 3, TimeUnit.SECONDS]

    private var moveTime = 0.2.seconds

    override fun stop() = process.destroy()

    override fun setOption(name: String, value: String) {
        when (name) {
            "time" -> {
                moveTime = value.asDurationOrNull().cNotNull(WRONG_DURATION_FORMAT)
            }
            else -> {
                process.outputStream.write(("setoption name $name value $value\n").toByteArray())
                process.outputStream.flush()
            }
        }
    }

    override fun sendCommand(command: String) {
        process.outputStream.write("isready\n".toByteArray())
        process.outputStream.flush()
        runTimeout {
            reader.readLine()
        }
        process.outputStream.write(("$command\n").toByteArray())
        process.outputStream.flush()
    }

    private fun readLine() = runTimeout {
        val a = reader.readLine()
        a
    }

    init {
        readLine()
    }

    override fun getMove(fen: FEN, onSuccess: (String) -> Unit, onException: (Exception) -> Unit) {
        var move = ""
        var exc: Exception? = null
        BukkitTimeManager.runTaskAsynchronously {
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
        BukkitTimeManager.runTaskTimer(moveTime + 1.ticks, 1.ticks) {
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