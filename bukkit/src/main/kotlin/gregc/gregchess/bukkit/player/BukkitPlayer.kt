package gregc.gregchess.bukkit.player

import gregc.gregchess.Color
import gregc.gregchess.bukkitutils.getOfflinePlayerByName
import gregc.gregchess.bukkitutils.player.BukkitHuman
import gregc.gregchess.bukkitutils.player.BukkitHumanProvider
import gregc.gregchess.player.ChessPlayer
import kotlinx.serialization.*
import kotlinx.serialization.descriptors.*
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import org.bukkit.Bukkit
import org.bukkit.OfflinePlayer
import org.bukkit.entity.Player
import java.util.*

@Serializable(BukkitPlayer.Serializer::class)
class BukkitPlayer private constructor(val bukkit: OfflinePlayer) : BukkitHuman, ChessPlayer<BukkitChessSide> {
    companion object {
        private val players = mutableMapOf<UUID, BukkitPlayer>()
        fun get(uuid: UUID) = BukkitPlayerProvider.getPlayer(uuid)
        fun get(player: OfflinePlayer) = players.getOrPut(player.uniqueId) { BukkitPlayer(player) }
    }
    @OptIn(InternalSerializationApi::class, ExperimentalSerializationApi::class)
    object Serializer : KSerializer<BukkitPlayer> {
        override val descriptor: SerialDescriptor
            get() = buildSerialDescriptor("BukkitPlayer", SerialKind.CONTEXTUAL)

        override fun serialize(encoder: Encoder, value: BukkitPlayer) {
            encoder.encodeSerializableValue(encoder.serializersModule.getContextual(UUID::class)!!, value.uuid)
        }

        override fun deserialize(decoder: Decoder): BukkitPlayer =
            get(decoder.decodeSerializableValue(decoder.serializersModule.getContextual(UUID::class)!!))
    }

    override fun toString(): String = "BukkitPlayer(bukkit=$bukkit)"

    override val entity: Player? get() = bukkit.player
    override val name: String get() = bukkit.name ?: ""
    override val uuid: UUID get() = bukkit.uniqueId
    override fun createChessSide(color: Color): BukkitChessSide = BukkitChessSide(this, color)

    var quickLeave: Boolean = false
        internal set
}

object BukkitPlayerProvider : BukkitHumanProvider<BukkitPlayer, BukkitPlayer> {
    override fun getOnlinePlayer(uuid: UUID): BukkitPlayer? = Bukkit.getPlayer(uuid)?.let(BukkitPlayer.Companion::get)
    override fun getOnlinePlayer(name: String): BukkitPlayer? = Bukkit.getPlayer(name)?.let(BukkitPlayer.Companion::get)
    override fun getPlayer(uuid: UUID): BukkitPlayer = Bukkit.getOfflinePlayer(uuid).let(BukkitPlayer.Companion::get)
    override fun getPlayer(name: String): BukkitPlayer? = getOfflinePlayerByName(name)?.let(BukkitPlayer.Companion::get)
}

val org.bukkit.event.player.PlayerEvent.gregchessPlayer get() = BukkitPlayerProvider.getOnlinePlayer(player.uniqueId)!!
val org.bukkit.event.block.BlockBreakEvent.gregchessPlayer get() = BukkitPlayerProvider.getOnlinePlayer(player.uniqueId)!!
val org.bukkit.event.entity.EntityEvent.gregchessPlayer get() = (entity as? Player)?.uniqueId?.let(BukkitPlayerProvider::getOnlinePlayer)
val org.bukkit.event.inventory.InventoryInteractEvent.gregchessPlayer get() = (whoClicked as? Player)?.uniqueId?.let(BukkitPlayerProvider::getOnlinePlayer)