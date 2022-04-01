package gregc.gregchess.bukkit.player

import gregc.gregchess.RegisterAll
import gregc.gregchess.bukkit.UUIDAsStringSerializer
import gregc.gregchess.player.ChessPlayerType
import gregc.gregchess.player.enginePlayerType
import org.bukkit.Bukkit

@RegisterAll(ChessPlayerType::class)
object BukkitPlayerType {
    @JvmField
    val BUKKIT = ChessPlayerType(UUIDAsStringSerializer, { Bukkit.getOfflinePlayer(it).name ?: "-" }, ::BukkitChessSide)
    @JvmField
    val STOCKFISH = enginePlayerType(Stockfish.serializer())
}