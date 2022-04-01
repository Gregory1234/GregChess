package gregc.gregchess.bukkit.player

import gregc.gregchess.board.FEN
import gregc.gregchess.bukkit.GregChessPlugin
import gregc.gregchess.bukkit.config
import gregc.gregchess.bukkitutils.coroutines.BukkitContext
import gregc.gregchess.bukkitutils.coroutines.BukkitDispatcher
import gregc.gregchess.bukkitutils.getPathString
import gregc.gregchess.bukkitutils.toDuration
import gregc.gregchess.player.ChessEngine
import gregc.gregchess.player.NoEngineMoveException
import kotlinx.coroutines.*
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import kotlin.time.Duration.Companion.seconds
import kotlin.time.ExperimentalTime

@Serializable
class Stockfish(override val name: String = Config.engineName) : ChessEngine {

    object Config {
        val hasStockfish get() = config.getBoolean("Chess.HasStockfish", false)
        val stockfishCommand get() = config.getPathString("Chess.Stockfish.Path")
        val engineName get() = config.getPathString("Chess.Stockfish.Name")
    }

    override val type get() = BukkitPlayerType.STOCKFISH

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
        GregChessPlugin.coroutineScope.launch {
            readLine()
        }
    }

    override suspend fun getMove(fen: FEN): String =
        withContext(BukkitDispatcher(GregChessPlugin.plugin, BukkitContext.ASYNC)) {
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