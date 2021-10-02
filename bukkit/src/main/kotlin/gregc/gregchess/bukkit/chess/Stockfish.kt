@file:UseSerializers(DurationSerializer::class)

package gregc.gregchess.bukkit.chess

import gregc.gregchess.DurationSerializer
import gregc.gregchess.bukkit.*
import gregc.gregchess.bukkit.coroutines.BukkitContext
import gregc.gregchess.bukkit.coroutines.BukkitDispatcher
import gregc.gregchess.chess.*
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import kotlinx.serialization.*
import java.util.concurrent.*

@Serializable
class Stockfish(override val name: String = Config.engineName) : ChessEngine {

    object Config {
        val hasStockfish get() = config.getBoolean("Chess.HasStockfish", false)
        val stockfishCommand get() = config.getPathString("Chess.Stockfish.Path")
        val engineName get() = config.getPathString("Chess.Stockfish.Name")
    }

    @Transient
    private val process: Process = ProcessBuilder(Config.stockfishCommand).start()

    @Transient
    private val reader = process.inputStream.bufferedReader()

    @Transient
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

    override suspend fun getMove(fen: FEN): String =
        withContext(BukkitDispatcher(GregChess.plugin, BukkitContext.ASYNC)) {
            sendCommand("position fen $fen")
            sendCommand("go movetime " + moveTime.toMillis())
            var ret: String? = null
            while (isActive) {
                val line = readLine().split(" ")
                if (line[0] == "bestmove") {
                    if (line[1] != "(none)") {
                        ret = line[1]
                        break
                    } else {
                        throw NoEngineMoveException(fen)
                    }
                }
            }
            ret ?: throw NoEngineMoveException(fen)
        }
}