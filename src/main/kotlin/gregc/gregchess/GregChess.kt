package gregc.gregchess

import gregc.gregchess.chess.ChessManager
import org.bukkit.plugin.java.JavaPlugin
import kotlin.contracts.ExperimentalContracts
@Suppress("unused")
class GregChess : JavaPlugin() {

    private val chess = ChessManager(this)

    @ExperimentalContracts
    override fun onEnable() {
        saveDefaultConfig()
        chess.start()
    }

    override fun onDisable() {
        chess.stop()
    }
}
