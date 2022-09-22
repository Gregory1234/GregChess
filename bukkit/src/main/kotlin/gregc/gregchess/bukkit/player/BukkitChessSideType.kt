package gregc.gregchess.bukkit.player

import gregc.gregchess.player.ChessSideType
import gregc.gregchess.player.EngineChessSide
import gregc.gregchess.registry.RegisterAll

@RegisterAll(ChessSideType::class)
object BukkitChessSideType {
    @JvmField
    val BUKKIT = ChessSideType<BukkitChessSide>()
    @JvmField
    val STOCKFISH = ChessSideType<EngineChessSide<Stockfish>>()
}