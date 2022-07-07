package gregc.gregchess.bukkit.match

import gregc.gregchess.*
import gregc.gregchess.bukkit.BukkitRegistering
import gregc.gregchess.bukkit.GregChessPlugin
import gregc.gregchess.bukkit.move.localMoveFormatter
import gregc.gregchess.bukkit.player.*
import gregc.gregchess.bukkit.properties.AddPropertiesEvent
import gregc.gregchess.bukkit.properties.PropertyType
import gregc.gregchess.bukkit.results.quick
import gregc.gregchess.bukkit.stats.BukkitPlayerStats
import gregc.gregchess.match.*
import gregc.gregchess.move.Move
import gregc.gregchess.results.MatchResults
import gregc.gregchess.stats.VoidPlayerStatsSink
import gregc.gregchess.stats.addStats
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import org.bukkit.entity.Player
import org.bukkit.scheduler.BukkitRunnable
import kotlin.time.Duration.Companion.seconds

enum class PlayerDirection {
    JOIN, LEAVE
}

class PlayerEvent(val player: Player, val dir: PlayerDirection) : ChessEvent

@Serializable
class MatchController(val presetName: String) : Component {

    companion object : BukkitRegistering {
        @JvmField
        @Register
        val PRESET = PropertyType()
    }

    override val type get() = BukkitComponentType.MATCH_CONTROLLER

    @Transient
    private lateinit var match: ChessMatch

    override fun init(match: ChessMatch) {
        this.match = match
    }

    internal var quick: ByColor<Boolean> = byColor(false)
    @Transient
    private var lastPrintedMove: Move? = null

    private fun onStart() {
        ChessMatchManager += match
        match.sides.forEachReal {
            match.callEvent(PlayerEvent(it, PlayerDirection.JOIN))
        }
    }

    private fun onRunning() {
        object : BukkitRunnable() {
            override fun run() {
                if (match.running)
                    match.update()
                else
                    cancel()
            }
        }.runTaskTimer(GregChessPlugin.plugin, 0, 2)
    }

    private fun onStop() {
        val results = match.results!!
        with(match.board) {
            val normalMoves = moveHistory.filter { !it.isPhantomMove }
            if (lastPrintedMove != normalMoves.lastOrNull()) {
                val wLast: Move?
                val bLast: Move?
                if (normalMoves.lastOrNull()?.main?.color == Color.WHITE) {
                    wLast = normalMoves.lastOrNull()
                    bLast = null
                } else {
                    wLast = if (normalMoves.size <= 1) null else normalMoves[normalMoves.size - 2]
                    bLast = normalMoves.lastOrNull()
                }
                match.sides.forEachReal { p ->
                    p.sendLastMoves(match.board.fullmoveCounter + 1, wLast, bLast, match.variant.localMoveFormatter)
                }
            }
        }
        val pgn = PGN.generate(match)
        match.sides.forEachUniqueBukkit { player, color ->
            match.coroutineScope.launch {
                player.showMatchResults(color, results)
                if (!results.endReason.quick)
                    delay((if (quick[color]) 0 else 3).seconds)
                match.callEvent(PlayerEvent(player, PlayerDirection.LEAVE))
                player.sendPGN(pgn)
                player.currentChessMatch = null
            }
        }
        if (match.sides.white.player != match.sides.black.player) {
            match.addStats(byColor {
                val player = match.sides[it]
                if (player is BukkitChessSide)
                    BukkitPlayerStats.of(player.uuid)[it, presetName]
                else
                    VoidPlayerStatsSink
            })
        }
        if (!results.endReason.quick)
            match.coroutineScope.launch {
                delay((if (quick.white && quick.black) 0 else 3).seconds)
                ChessMatchManager -= match
            }
        else
            ChessMatchManager -= match
    }

    private fun onPanic() {
        val results = match.results!!
        val pgn = PGN.generate(match)
        match.sides.forEachUniqueBukkit { player, color ->
            player.showMatchResults(color, results)
            player.sendPGN(pgn)
        }
        ChessMatchManager -= match
    }

    @ChessEventHandler
    fun handleEvents(e: ChessBaseEvent) = when (e) {
        ChessBaseEvent.START -> onStart()
        ChessBaseEvent.RUNNING -> onRunning()
        ChessBaseEvent.STOP -> onStop()
        ChessBaseEvent.PANIC -> onPanic()
        ChessBaseEvent.SYNC -> if (match.state == ChessMatch.State.RUNNING) {
            onStart()
            onRunning()
        } else Unit
        ChessBaseEvent.UPDATE -> Unit
        ChessBaseEvent.CLEAR -> Unit
    }

    @ChessEventHandler
    fun handleTurn(e: TurnEvent) {
        if (e == TurnEvent.END) {
            if (match.board.currentTurn == Color.BLACK) {
                with(match.board) {
                    val normalMoves = moveHistory.filter { !it.isPhantomMove }
                    val wLast = if (normalMoves.size <= 1) null else normalMoves[normalMoves.size - 2]
                    val bLast = normalMoves.last()
                    match.sides.forEachReal { p ->
                        p.sendLastMoves(match.board.fullmoveCounter, wLast, bLast, match.variant.localMoveFormatter)
                    }
                    lastPrintedMove = normalMoves.last()
                }
            }
            (match.currentSide as? BukkitChessSide)?.let(GregChessPlugin::clearRequests)
        }
    }

    @ChessEventHandler
    fun addProperties(e: AddPropertiesEvent) {
        e.match(PRESET) { presetName }
    }

    @ChessEventHandler
    fun playerEvent(e: PlayerEvent) = when(e.dir) {
        PlayerDirection.JOIN -> e.player.currentChessSide!!.sendStartMessage()
        PlayerDirection.LEAVE -> {}
    }
}

val ChessMatch.matchController get() = require(BukkitComponentType.MATCH_CONTROLLER)

fun ChessMatch.stop(results: MatchResults, quick: ByColor<Boolean>) {
    matchController.quick = if ((quick.white || quick.black) && sides.white.player == sides.black.player) byColor(true) else quick
    stop(results)
}

fun ChessMatch.quickStop(results: MatchResults) = stop(results, byColor(true))