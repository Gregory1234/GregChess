package gregc.gregchess.fabric.chess.player

import gregc.gregchess.chess.ChessGame
import gregc.gregchess.chess.Color
import gregc.gregchess.chess.player.ChessPlayer
import kotlinx.serialization.Contextual
import kotlinx.serialization.Serializable
import net.minecraft.server.MinecraftServer
import net.minecraft.server.network.ServerPlayerEntity
import java.util.*

@Serializable
class FabricPlayer(val uuid: @Contextual UUID, private var _name: String) : ChessPlayer {
    override fun initSide(color: Color, game: ChessGame): FabricChessSide = FabricChessSide(this, color, game)

    override val name: String get() = _name

    fun getServerPlayer(server: MinecraftServer?) : ServerPlayerEntity? = server?.playerManager?.getPlayer(uuid)

    override fun equals(other: Any?): Boolean = other is FabricPlayer && other.uuid == uuid
    override fun hashCode(): Int = uuid.hashCode()
    override fun toString(): String = "BukkitPlayer(uuid=$uuid, name=$name)"
}