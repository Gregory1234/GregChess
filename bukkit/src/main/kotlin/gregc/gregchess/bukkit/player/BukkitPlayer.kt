package gregc.gregchess.bukkit.player

import gregc.gregchess.Color
import gregc.gregchess.bukkit.config
import gregc.gregchess.bukkit.match.presetName
import gregc.gregchess.bukkit.message
import gregc.gregchess.bukkitutils.*
import gregc.gregchess.bukkitutils.player.BukkitHuman
import gregc.gregchess.bukkitutils.player.BukkitHumanProvider
import gregc.gregchess.match.ChessMatch
import gregc.gregchess.player.ChessPlayer
import gregc.gregchess.results.EndReason
import gregc.gregchess.results.lostBy
import kotlinx.coroutines.*
import kotlinx.serialization.*
import kotlinx.serialization.descriptors.*
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import org.bukkit.Bukkit
import org.bukkit.OfflinePlayer
import org.bukkit.entity.Player
import java.time.Instant
import java.util.*

@Serializable(BukkitPlayer.Serializer::class)
class BukkitPlayer private constructor(val bukkit: OfflinePlayer) : BukkitHuman, ChessPlayer<BukkitChessSide> {
    companion object {
        private val players = mutableMapOf<UUID, BukkitPlayer>()
        fun get(uuid: UUID) = BukkitPlayerProvider.getPlayer(uuid)
        fun get(player: OfflinePlayer) = players.getOrPut(player.uniqueId) { BukkitPlayer(player) }

        private val allowRejoining get() = config.getBoolean("Rejoin.AllowRejoining")
        private val rejoinDuration get() = config.getString("Rejoin.Duration")?.toDurationOrNull()
        private val REJOIN_REMINDER = message("RejoinReminder")
        private val REMATCH_REMINDER = message("RematchReminder")
    }
    @OptIn(InternalSerializationApi::class, ExperimentalSerializationApi::class)
    @PublishedApi
    internal object Serializer : KSerializer<BukkitPlayer> {
        override val descriptor: SerialDescriptor
            get() = buildSerialDescriptor("BukkitPlayer", SerialKind.CONTEXTUAL)

        override fun serialize(encoder: Encoder, value: BukkitPlayer) {
            encoder.encodeSerializableValue(encoder.serializersModule.getContextual(UUID::class)!!, value.uuid)
        }

        override fun deserialize(decoder: Decoder): BukkitPlayer =
            get(decoder.decodeSerializableValue(decoder.serializersModule.getContextual(UUID::class)!!))
    }
    private class MatchInfo(val color: Color?, var leaveTime: Instant? = null, var resignJob: Job? = null)

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
            it.sides[checkNotNull(activeMatchInfo[it]).color ?: it.currentColor] as BukkitChessSideFacade
        }
    private val activeMatchInfo = mutableMapOf<ChessMatch, MatchInfo>()
    val activeMatches get() = activeMatchInfo.keys

    fun sendRejoinReminder() {
        if (allowRejoining && config.getBoolean("Rejoin.SendReminder") && activeMatches.isNotEmpty()) {
            sendMessage(textComponent(REJOIN_REMINDER.get()) {
                onClickCommand("/chess rejoin")
            })
        }
    }

    internal fun registerMatch(match: ChessMatch) {
        require(match !in activeMatches)
        val side = requireNotNull(match.sides[uuid])
        val opponent = side.opponent as? BukkitChessSideFacade
        activeMatchInfo[match] = MatchInfo(if (opponent?.player == this) null else side.color)
    }

    internal fun unregisterMatch(match: ChessMatch) {
        val info = checkNotNull(activeMatchInfo[match])
        check(currentMatch != match)
        info.resignJob?.cancel()
        activeMatchInfo.remove(match, info)
    }

    internal fun leaveMatchDirect(match: ChessMatch = checkNotNull(currentMatch), canRequestRematch: Boolean = false) {
        require(currentMatch == match)
        if (canRequestRematch) {
            (currentSide!!.opponent as? BukkitChessSideFacade)?.let {
                rematchInfo = RematchInfo(it.player, match.environment.pgnRound, match.presetName, !it.color)
                if (config.getBoolean("Request.Rematch.SendReminder") && !match.sides.isSamePlayer()) {
                    sendMessage(textComponent(REMATCH_REMINDER.get()) {
                        onClickCommand("/chess rematch")
                    })
                }
            }
        }
        currentMatch = null
        match.callEvent(PlayerEvent(this, PlayerDirection.LEAVE))
    }

    fun leaveMatch() {
        val match = checkNotNull(currentMatch)
        if (allowRejoining) {
            val info = checkNotNull(activeMatchInfo[match])
            info.leaveTime = match.environment.clock.instant()
            rejoinDuration?.let { duration ->
                info.resignJob = match.coroutineScope.launch {
                    delay(duration)
                }.also {
                    it.invokeOnCompletion { e ->
                        if (e == null) {
                            match.stop((info.color ?: match.currentColor).lostBy(EndReason.WALKOVER))
                        }
                    }
                }
            }
            leaveMatchDirect()
            sendRejoinReminder()
        } else {
            val color = match.sides[uuid]!!.color
            quickLeave = true
            match.stop(color.lostBy(EndReason.WALKOVER))
            quickLeave = false
        }
    }

    private val matchToRejoin get() = activeMatchInfo.toList().maxByOrNull { it.second.leaveTime ?: Instant.MIN }?.first

    fun joinMatch(match: ChessMatch = checkNotNull(matchToRejoin)) {
        val info = checkNotNull(activeMatchInfo[match])
        check(!isInMatch)
        check(!isSpectatingMatch)
        info.leaveTime = null
        info.resignJob?.cancel()
        info.resignJob = null
        currentMatch = match
        match.callEvent(PlayerEvent(this, PlayerDirection.JOIN))
    }

    var spectatedMatch: ChessMatch? = null
        private set
    val isSpectatingMatch: Boolean get() = spectatedMatch != null

    fun spectateMatch(match: ChessMatch) {
        check(!isInMatch)
        check(!isSpectatingMatch)
        spectatedMatch = match
        match.callEvent(SpectatorEvent(this, PlayerDirection.JOIN))
    }

    fun leaveSpectatedMatch(match: ChessMatch = checkNotNull(spectatedMatch)) {
        require(spectatedMatch == match)
        spectatedMatch = null
        match.callEvent(SpectatorEvent(this, PlayerDirection.LEAVE))
    }

    class RematchInfo(val target: BukkitPlayer, val lastRound: Int, val preset: String, val lastColor: Color)

    var rematchInfo: RematchInfo? = null
        private set
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