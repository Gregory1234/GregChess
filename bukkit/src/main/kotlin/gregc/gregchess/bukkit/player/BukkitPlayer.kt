package gregc.gregchess.bukkit.player

import gregc.gregchess.Color
import gregc.gregchess.bukkit.config
import gregc.gregchess.bukkit.match.PlayerDirection
import gregc.gregchess.bukkit.match.PlayerEvent
import gregc.gregchess.bukkit.message
import gregc.gregchess.bukkitutils.getOfflinePlayerByName
import gregc.gregchess.bukkitutils.player.BukkitHuman
import gregc.gregchess.bukkitutils.player.BukkitHumanProvider
import gregc.gregchess.bukkitutils.textComponent
import gregc.gregchess.match.ChessMatch
import gregc.gregchess.player.ChessPlayer
import gregc.gregchess.results.EndReason
import gregc.gregchess.results.lostBy
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

        private val allowRejoining get() = config.getBoolean("Rejoin.AllowRejoining")
        private val REJOIN_REMINDER = message("RejoinReminder")
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
        private set

    var currentMatch: ChessMatch? = null
        private set
    val isInMatch: Boolean get() = currentMatch != null
    val currentSide: BukkitChessSideFacade?
        get() = currentMatch?.let {
            check(it in activeMatchSides)
            it[activeMatchSides[it] ?: it.board.currentTurn] as BukkitChessSideFacade
        }
    private val activeMatchSides = mutableMapOf<ChessMatch, Color?>()
    val activeMatches get() = activeMatchSides.keys

    fun sendRejoinReminder() {
        if (allowRejoining && config.getBoolean("Rejoin.SendReminder") && activeMatches.isNotEmpty()) {
            sendMessage(textComponent(REJOIN_REMINDER.get()) {
                onClickCommand("/chess rejoin")
            })
        }
    }

    internal fun registerMatch(match: ChessMatch) {
        require(match !in activeMatches)
        val side = requireNotNull(match[uuid])
        val opponent = side.opponent as? BukkitChessSideFacade
        activeMatchSides[match] = if (opponent?.player == this) null else side.color
    }

    internal fun unregisterMatch(match: ChessMatch) {
        require(match in activeMatches)
        check(currentMatch != match)
        activeMatchSides.remove(match)
    }

    internal fun leaveMatchDirect(match: ChessMatch = checkNotNull(currentMatch)) {
        require(currentMatch == match)
        currentMatch = null
        match.callEvent(PlayerEvent(this, PlayerDirection.LEAVE))
    }

    fun leaveMatch() {
        // TODO: add a time limit for rejoining
        val match = checkNotNull(currentMatch)
        if (allowRejoining) {
            leaveMatchDirect()
            sendRejoinReminder()
        } else {
            val color = match[uuid]!!.color
            quickLeave = true
            match.stop(color.lostBy(EndReason.WALKOVER))
            quickLeave = false
        }
    }

    fun joinMatch(match: ChessMatch = checkNotNull(activeMatches.firstOrNull())) {
        require(match in activeMatches)
        check(!isInMatch)
        check(!isSpectatingChessMatch)
        currentMatch = match
        match.callEvent(PlayerEvent(this, PlayerDirection.JOIN))
    }
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