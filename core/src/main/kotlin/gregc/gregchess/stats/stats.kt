package gregc.gregchess.stats

import gregc.gregchess.*
import gregc.gregchess.event.ChessEvent
import gregc.gregchess.match.*
import gregc.gregchess.registry.*
import gregc.gregchess.results.MatchScore
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.serializer
import kotlin.time.Duration

class AddStatsEvent(val stats: ByColor<PlayerStatsSink>) : ChessEvent

fun ChessMatch.addStats(stats: ByColor<PlayerStatsSink>) {
    when(results?.score) {
        MatchScore.Draw -> stats.forEach { it.add(ChessStat.DRAWS, 1) }
        MatchScore.Victory(Color.WHITE) -> {
            stats.white.add(ChessStat.WINS, 1)
            stats.black.add(ChessStat.LOSSES, 1)
        }
        MatchScore.Victory(Color.BLACK) -> {
            stats.white.add(ChessStat.LOSSES, 1)
            stats.black.add(ChessStat.WINS, 1)
        }
        else -> {}
    }
    stats.white.add(ChessStat.MOVES_PLAYED, board.fullmoveCounter - 1, if (currentColor == Color.BLACK) 1 else 0)
    stats.black.add(ChessStat.MOVES_PLAYED, board.fullmoveCounter - 1)
    stats.forEach { it.add(ChessStat.TIME_PLAYED, duration) }
    callEvent(AddStatsEvent(stats))
    stats.forEach { it.commit() }
}

interface PlayerStatsSink {
    fun <T : Any> add(stat: ChessStat<T>, vararg values: T)
    fun commit()
}

interface PlayerStatsView {
    operator fun <T : Any> get(stat: ChessStat<T>) : T
    val stored: Set<ChessStat<*>>
}

class CombinedPlayerStatsView(private val partialStats: Collection<PlayerStatsView>) : PlayerStatsView {
    override fun <T : Any> get(stat: ChessStat<T>): T = stat.aggregate(partialStats.map { it[stat] })
    override val stored: Set<ChessStat<*>> get() = buildSet {
        for (s in partialStats)
            addAll(s.stored)
    }
}

object VoidPlayerStatsSink : PlayerStatsSink {
    override fun <T : Any> add(stat: ChessStat<T>, vararg values: T) {}
    override fun commit() {}
}

@Serializable(with = ChessStat.Serializer::class)
class ChessStat<T : Any>(val serializer: KSerializer<T>, @JvmField val aggregate: (Collection<T>) -> T) : NameRegistered {
    @PublishedApi
    internal object Serializer : NameRegisteredSerializer<ChessStat<*>>("ChessStat", CoreRegistry.STAT)

    override val key get() = CoreRegistry.STAT[this]

    override fun toString(): String = CoreRegistry.STAT.simpleElementToString(this)

    @RegisterAll(ChessStat::class)
    companion object {

        internal val AUTO_REGISTER = AutoRegisterType(ChessStat::class) { m, n, _ -> CoreRegistry.STAT[m, n] = this }

        fun intStat() = ChessStat(Int.serializer(), Collection<Int>::sum)
        fun durationStat() = ChessStat(Duration.serializer()) { it.reduceOrNull(Duration::plus) ?: Duration.ZERO }

        @JvmField
        val WINS = intStat()
        @JvmField
        val LOSSES = intStat()
        @JvmField
        val DRAWS = intStat()
        @JvmField
        val MOVES_PLAYED = intStat()
        @JvmField
        val TIME_PLAYED = durationStat()

        fun registerCore(module: ChessModule) = AutoRegister(module, listOf(AUTO_REGISTER)).registerAll<ChessStat<*>>()
    }
}