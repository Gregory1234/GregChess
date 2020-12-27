package gregc.gregchess

import gregc.gregchess.chess.ChessManager
import org.bukkit.event.Listener
import org.bukkit.plugin.java.JavaPlugin
import kotlin.contracts.ExperimentalContracts
@Suppress("unused")
class GregChess : JavaPlugin(), Listener {

    private val chess = ChessManager(this)

    @ExperimentalContracts
    override fun onEnable() {
        chess.start()
    }

    override fun onDisable() {
        chess.stop()
    }
}
