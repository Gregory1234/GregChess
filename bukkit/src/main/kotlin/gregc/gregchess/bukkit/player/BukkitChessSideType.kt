package gregc.gregchess.bukkit.player

import gregc.gregchess.RegisterAll
import gregc.gregchess.player.ChessSideType
import gregc.gregchess.player.EngineChessSide

@RegisterAll(ChessSideType::class)
object BukkitChessSideType {
    @JvmField
    val BUKKIT = ChessSideType(BukkitChessSide.serializer())
    @JvmField
    val STOCKFISH = ChessSideType(EngineChessSide.serializer(Stockfish.serializer()))
}