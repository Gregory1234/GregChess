@file:UseSerializers(DurationSerializer::class)

package gregc.gregchess.bukkit.chess.player

import gregc.gregchess.DurationSerializer
import gregc.gregchess.bukkit.*
import gregc.gregchess.bukkit.coroutines.BukkitContext
import gregc.gregchess.bukkit.coroutines.BukkitDispatcher
import gregc.gregchess.chess.FEN
import gregc.gregchess.chess.player.ChessEngine
import gregc.gregchess.chess.player.NoEngineMoveException
import gregc.gregchess.passExceptions
import kotlinx.coroutines.*
import kotlinx.serialization.*
import kotlin.time.Duration.Companion.seconds
import kotlin.time.ExperimentalTime

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
    private var moveTime = 0.2.seconds

    override fun stop() = process.destroy()

    @Suppress("BlockingMethodInNonBlockingContext")
    private suspend fun readLineRaw(): String = withContext(Dispatchers.IO) { reader.readLine() }

    @Suppress("BlockingMethodInNonBlockingContext")
    private suspend fun writeRaw(string: String) = withContext(Dispatchers.IO) {
        process.outputStream.write(string.toByteArray())
        process.outputStream.flush()
    }

    override suspend fun setOption(name: String, value: String) {
        when (name) {
            "time" -> moveTime = value.toDuration()
            else -> writeRaw("setoption name $name value $value\n")
        }
    }

    @OptIn(ExperimentalTime::class)
    override suspend fun sendCommand(command: String) = withContext(Dispatchers.IO) {
        writeRaw("isready\n")
        withTimeout(moveTime / 2 + 3.seconds) { readLineRaw() }
        writeRaw("$command\n")
    }

    @OptIn(ExperimentalTime::class)
    private suspend fun readLine() = withContext(Dispatchers.IO) {
        withTimeout(moveTime / 2 + 3.seconds) { readLineRaw() }
    }

    init {
        GregChess.coroutineScope.launch {
            readLine()
        }.passExceptions()
    }

    override suspend fun getMove(fen: FEN): String =
        withContext(BukkitDispatcher(GregChess.plugin, BukkitContext.ASYNC)) {
            sendCommand("position fen $fen")
            sendCommand("go movetime " + moveTime.inWholeMilliseconds)
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