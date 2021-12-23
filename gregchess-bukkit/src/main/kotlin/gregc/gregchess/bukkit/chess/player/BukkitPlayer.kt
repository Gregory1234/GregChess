package gregc.gregchess.bukkit.chess.player

import gregc.gregchess.chess.ChessGame
import gregc.gregchess.chess.Color
import gregc.gregchess.chess.player.ChessPlayer
import kotlinx.serialization.Contextual
import kotlinx.serialization.Serializable
import org.bukkit.Bukkit
import org.bukkit.entity.Player
import java.util.*

@Serializable
class BukkitPlayer(val uuid: @Contextual UUID, private var _name: String) : ChessPlayer {
    override fun initSide(color: Color, game: ChessGame): BukkitChessSide = BukkitChessSide(this, color, game)

    override val name: String get() = _name

    val bukkit: Player? get() = Bukkit.getPlayer(uuid)?.also { _name = it.name }

    override fun equals(other: Any?): Boolean = other is BukkitPlayer && other.uuid == uuid
    override fun hashCode(): Int = uuid.hashCode()
    override fun toString(): String = "BukkitPlayer(uuid=$uuid, name=$name)"
}