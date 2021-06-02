package gregc.gregchess.chess

import gregc.gregchess.*
import gregc.gregchess.chess.component.*
import org.bukkit.Bukkit
import org.bukkit.event.Event
import org.bukkit.event.HandlerList
import java.time.LocalDateTime
import java.util.*
import kotlin.reflect.KClass
import kotlin.reflect.KProperty0
import kotlin.reflect.safeCast

class ChessGame(private val timeManager: TimeManager, val arena: Arena, val settings: GameSettings) {
    val uniqueId: UUID = UUID.randomUUID()

    override fun toString() = "ChessGame(uniqueId = $uniqueId)"

    val variant = settings.variant

    private val components = listOf(arena) + (settings.components + variant.extraComponents).map {it.getComponent(this)}

    init {
        try {
            requireComponent<Chessboard>()
            requireComponent<ScoreboardManager>()
        } catch (e: Exception) {
            panic(e)
            throw e
        }
        arena.game = this
    }

    val board: Chessboard = requireComponent()

    val clock: ChessClock? = getComponent()

    val renderers: List<Renderer<*>> = components.mapNotNull { Renderer::class.safeCast(it) }

    val scoreboard: ScoreboardManager = requireComponent()

    @Suppress("unchecked_cast")
    fun <T, R> withRenderer(block: (Renderer<T>) -> R): R? =
        renderers.firstNotNullOfOrNull { try {block(it as Renderer<T>)} catch (e: Exception) { null } }

    fun <T : Component> getComponent(cl: KClass<T>): T? =
        components.mapNotNull { cl.safeCast(it) }.firstOrNull()

    fun <T : Component> requireComponent(cl: KClass<T>): T = getComponent(cl) ?: throw ComponentNotFoundException(cl)

    inline fun <reified T : Component> getComponent(): T? = getComponent(T::class)

    inline fun <reified T : Component> requireComponent(): T = requireComponent(T::class)

    private var state: GameState = GameState.Initial

    private inline fun <reified T> wrongState(): Nothing {
        val e = WrongStateException(state, T::class.java)
        stop(EndReason.Error(e))
        throw e
    }

    private inline fun <reified T> require(fail: () -> Nothing = {wrongState<T>()}): T = (state as? T) ?: fail()

    private inline fun requireInitial(fail: () -> Nothing = {wrongState<GameState.Initial>()}) = require<GameState.Initial>(fail)
    private inline fun requireReady(fail: () -> Nothing = {wrongState<GameState.Ready>()}) = require<GameState.Ready>(fail)
    private inline fun requireStarting(fail: () -> Nothing = {wrongState<GameState.Starting>()}) = require<GameState.Starting>(fail)
    private inline fun requireRunning(fail: () -> Nothing = {wrongState<GameState.Running>()}) = require<GameState.Running>(fail)
    private inline fun requireStopping(fail: () -> Nothing = {wrongState<GameState.Stopping>()}) = require<GameState.Stopping>(fail)

    var currentTurn: Side
        get() = require<GameState.WithCurrentPlayer>().currentTurn
        set(v) {
            requireRunning().currentTurn = v
        }

    val currentPlayer: ChessPlayer
        get() = require<GameState.WithCurrentPlayer>().currentPlayer

    inner class AddPlayersScope() {
        private val players = MutableBySides<ChessPlayer?>(null, null)

        fun addPlayer(p: ChessPlayer) {
            players[p.side] = p
        }

        fun human(p: HumanPlayer, side: Side, silent: Boolean) =
            addPlayer(HumanChessPlayer(p, side, silent, this@ChessGame))

        fun engine(name: String, side: Side) =
            addPlayer(EnginePlayer(ChessEngine(timeManager, name), side, this@ChessGame))

        fun build(): BySides<ChessPlayer> = players.map {
            it ?: throw IllegalStateException("player has not been initialized")
        }

    }

    fun addPlayers(init: AddPlayersScope.() -> Unit): ChessGame {
        requireInitial()
        state = GameState.Ready(AddPlayersScope().run {
            init()
            build()
        })
        return this
    }

    val startTime: LocalDateTime
        get() = require<GameState.WithStartTime>().startTime

    val running: Boolean
        get() = state.running

    class TurnEndEvent(val game: ChessGame, val player: ChessPlayer) : Event() {
        override fun getHandlers() = handlerList

        companion object {
            @Suppress("unused")
            @JvmStatic
            fun getHandlerList(): HandlerList = handlerList
            private val handlerList = HandlerList()
        }
    }

    val players: BySides<ChessPlayer>
        get() = require<GameState.WithPlayers>().players
    val spectators: List<HumanPlayer>
        get() = (state as? GameState.WithSpectators)?.spectators.orEmpty()

    operator fun contains(p: HumanPlayer): Boolean = require<GameState.WithPlayers>().contains(p)

    fun nextTurn() {
        requireRunning()
        components.allEndTurn()
        variant.checkForGameEnd(this)
        if (running) {
            Bukkit.getPluginManager().callEvent(TurnEndEvent(this, currentPlayer))
            currentTurn++
            startTurn()
        }
    }

    fun previousTurn() {
        requireRunning()
        components.allPrePreviousTurn()
        currentTurn++
        startPreviousTurn()
    }

    class StartEvent(val game: ChessGame) : Event() {

        override fun getHandlers() = handlerList

        companion object {
            @Suppress("unused")
            @JvmStatic
            fun getHandlerList(): HandlerList = handlerList
            private val handlerList = HandlerList()
        }
    }

    fun start(): ChessGame {
        try {
            state = GameState.Starting(requireReady())
            players.forEachReal {
                components.allAddPlayer(it)
            }
            components.allInit()
            requireStarting().apply {
                black.sendTitle("", Config.title.youArePlayingAs.black)
                white.sendTitle(Config.title.yourTurn, Config.title.youArePlayingAs.white)
                players.forEach {
                    it.sendMessage(Config.message.youArePlayingAs[it.side])
                }
            }
            variant.start(this)
            components.allStart()
            state = GameState.Running(requireStarting())
            glog.mid("Started game", uniqueId)
            timeManager.runTaskTimer(0.seconds, 0.1.seconds) {
                if (!running)
                    cancel()
                else
                    components.allUpdate()
            }
        } catch (e: Exception) {
            players.forEachReal { it.sendMessage(Config.message.error.teleportFailed) }
            panic(e)
            glog.mid("Failed to start game", uniqueId)
            throw e
        }
        Bukkit.getPluginManager().callEvent(StartEvent(this))
        startTurn()
        return this
    }

    fun spectate(p: HumanPlayer) {
        val st = requireRunning()
        components.allSpectatorJoin(p)
        st.spectators += p
    }

    fun spectatorLeave(p: HumanPlayer) {
        val st = requireRunning()
        components.allSpectatorLeave(p)
        st.spectators -= p
    }

    fun resetPlayer(p: BukkitPlayer) {
        components.allResetPlayer(p)
    }

    private fun startTurn() {
        requireRunning()
        components.allStartTurn()
        currentPlayer.startTurn()
        glog.low("Started turn", uniqueId, currentTurn)
    }

    private fun startPreviousTurn() {
        requireRunning()
        components.allStartPreviousTurn()
        currentPlayer.startTurn()
        glog.low("Started previous turn", uniqueId, currentTurn)
    }

    open class EndReason(val namePath: KProperty0<String>, val reasonPGN: String, val winner: Side?) {

        override fun toString() =
            "EndReason.${javaClass.name.split(".", "$").last()}(winner = $winner)"

        // @formatter:off
        class Checkmate(winner: Side) : EndReason(Config.chess.endReason::checkmate, "normal", winner)
        class Resignation(winner: Side) : EndReason(Config.chess.endReason::resignation, "abandoned", winner)
        class Walkover(winner: Side) : EndReason(Config.chess.endReason::walkover, "abandoned", winner)
        class PluginRestart : EndReason(Config.chess.endReason::pluginRestart, "emergency", null)
        class ArenaRemoved : EndReason(Config.chess.endReason::arenaRemoved, "emergency", null)
        class Stalemate : EndReason(Config.chess.endReason::stalemate, "normal", null)
        class InsufficientMaterial : EndReason(Config.chess.endReason::insufficientMaterial, "normal", null)
        class FiftyMoves : EndReason(Config.chess.endReason::fiftyMoves, "normal", null)
        class Repetition : EndReason(Config.chess.endReason::repetition, "normal", null)
        class DrawAgreement : EndReason(Config.chess.endReason::drawAgreement, "normal", null)
        class Timeout(winner: Side) : ChessGame.EndReason(Config.chess.endReason::timeout, "time forfeit", winner)
        class DrawTimeout : ChessGame.EndReason(Config.chess.endReason::drawTimeout, "time forfeit", null)
        class Error(val e: Exception) : ChessGame.EndReason(Config.chess.endReason::error, "emergency", null) {
            override fun toString() = "EndReason.Error(winner = $winner, e = $e)"
        }

        class AllPiecesLost(winner: Side) : ChessGame.EndReason(Config.chess.endReason::piecesLost, "normal", winner)
        // @formatter:on

        fun getMessage() = Config.message.gameFinished[winner](namePath.get())
    }

    class EndEvent(val game: ChessGame) : Event() {

        override fun getHandlers() = handlerList

        companion object {
            @Suppress("unused")
            @JvmStatic
            fun getHandlerList(): HandlerList = handlerList
            private val handlerList = HandlerList()
        }
    }

    val endReason: EndReason?
        get() = (state as? GameState.WithEndReason)?.endReason

    fun quickStop(reason: EndReason) = stop(reason, BySides(white = true, black = true))

    fun stop(reason: EndReason, quick: BySides<Boolean> = BySides(white = false, black = false)) {
        val stopping = GameState.Stopping(requireRunning { requireStopping(); return }, reason)
        state = stopping
        try {
            components.allStop()
            var anyLong = false
            players.forEachUnique(currentTurn) {
                it.sendTitle(
                    Config.title.player.run {
                        when(reason.winner) {it.side -> youWon; null -> youDrew; else -> youLost} },
                    reason.namePath.get()
                )
                it.sendMessage(reason.getMessage())
                if (quick[it.side]) {
                    components.allRemovePlayer(it.player)
                } else {
                    anyLong = true
                    timeManager.runTaskLater(3.seconds) {
                        components.allRemovePlayer(it.player)
                    }
                }
            }
            spectators.forEach {
                it.sendTitle(Config.title.spectator[reason.winner], reason.namePath.get())
                it.sendMessage(reason.getMessage())
                if (anyLong) {
                    components.allSpectatorLeave(it)
                } else {
                    timeManager.runTaskLater(3.seconds) {
                        components.allSpectatorLeave(it)
                    }
                }
            }
            if (reason is EndReason.PluginRestart) {
                components.allClear()
                require<GameState.WithPlayers>().forEachPlayer(ChessPlayer::stop)
                state = GameState.Stopped(stopping)
                glog.low("Stopped game", uniqueId, reason)
                components.allVeryEnd()
                Bukkit.getPluginManager().callEvent(EndEvent(this))
                return
            }
            timeManager.runTaskLater((if (anyLong) 3 else 0).seconds + 1.ticks) {
                components.allClear()
                timeManager.runTaskLater(1.ticks) {
                    require<GameState.WithPlayers>().forEachPlayer(ChessPlayer::stop)
                    state = GameState.Stopped(stopping)
                    components.allVeryEnd()
                    glog.low("Stopped game", uniqueId, reason)
                    Bukkit.getPluginManager().callEvent(EndEvent(this))
                }
            }
        } catch (e: Exception) {
            panic(e)
            throw e
        }
    }

    private fun panic(e: Exception) {
        e.printStackTrace()
        components.allPanic(e)
        state = GameState.Error(state, e)
        Bukkit.getPluginManager().callEvent(EndEvent(this))
    }

    operator fun get(player: HumanPlayer): HumanChessPlayer? = (state as? GameState.WithCurrentPlayer)?.get(player)

    operator fun get(side: Side): ChessPlayer = require<GameState.WithPlayers>()[side]

    fun <E> tryOrStopNull(expr: E?): E = try {
        expr!!
    } catch (e: NullPointerException) {
        stop(EndReason.Error(e))
        throw e
    }

    fun finishMove(move: MoveCandidate) {
        requireRunning()
        val data = move.execute()
        board.lastMove?.clear()
        board.lastMove = data
        board.lastMove?.render()
        glog.low("Finished move", data)
        nextTurn()
    }

    fun getInfo() = buildTextComponent {
        appendCopy("UUID: $uniqueId\n", uniqueId)
        append("Players: ${players.toList().joinToString { "${it.name} as ${it.side.standardName}" }}\n")
        append("Spectators: ${spectators.joinToString { it.name }}\n")
        append("Arena: ${arena.name}\n")
        append("Preset: ${settings.name}\n")
        append("Variant: ${variant.name}\n")
        append("Components: ${components.joinToString { it.javaClass.simpleName }}")
    }

}