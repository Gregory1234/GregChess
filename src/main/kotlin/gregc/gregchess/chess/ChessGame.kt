package gregc.gregchess.chess

import gregc.gregchess.*
import gregc.gregchess.chess.component.*
import org.bukkit.Bukkit
import org.bukkit.entity.Player
import org.bukkit.event.Event
import org.bukkit.event.HandlerList
import java.time.LocalDateTime
import java.util.*
import kotlin.reflect.KClass
import kotlin.reflect.safeCast

class ChessGame(val settings: GameSettings) {
    val uniqueId: UUID = UUID.randomUUID()

    override fun toString() = "ChessGame(uniqueId = $uniqueId)"

    val variant = settings.variant

    val board: Chessboard = settings.board.getComponent(this)

    val clock: ChessClock? = settings.clock?.getComponent(this)

    val renderer: Renderer = settings.renderer.getComponent(this)

    val scoreboard = settings.scoreboard.getComponent(this)

    private val components = listOfNotNull(board, clock, renderer, scoreboard).toMutableList()

    fun registerComponent(c: Component) {
        components.add(components.size - 2, c)
    }

    fun <T : Component> getComponent(cl: KClass<T>): T? =
        components.mapNotNull { cl.safeCast(it) }.firstOrNull()

    private var state: GameState = GameState.Initial

    private fun wrongState(cls: Class<*>): Nothing {
        val e = WrongStateException(state, cls)
        stop(EndReason.Error(e))
        throw e
    }

    private inline fun <reified T> require(fail: () -> Nothing = {wrongState(T::class.java)}): T = (state as? T) ?: fail()

    private inline fun requireInitial(fail: () -> Nothing = {wrongState(GameState.Initial::class.java)}) = require<GameState.Initial>(fail)
    private inline fun requireReady(fail: () -> Nothing = {wrongState(GameState.Ready::class.java)}) = require<GameState.Ready>(fail)
    private inline fun requireStarting(fail: () -> Nothing = {wrongState(GameState.Starting::class.java)}) = require<GameState.Starting>(fail)
    private inline fun requireRunning(fail: () -> Nothing = {wrongState(GameState.Running::class.java)}) = require<GameState.Running>(fail)
    private inline fun requireStopping(fail: () -> Nothing = {wrongState(GameState.Stopping::class.java)}) = require<GameState.Stopping>(fail)

    var currentTurn: ChessSide
        get() = require<GameState.WithCurrentPlayer>().currentTurn
        set(v) {
            requireRunning().currentTurn = v
        }

    val currentPlayer: ChessPlayer
        get() = require<GameState.WithCurrentPlayer>().currentPlayer

    inner class AddPlayersScope(val game: ChessGame) {
        private val players = MutableBySides<ChessPlayer?>(null, null)

        fun addPlayer(p: ChessPlayer) {
            players[p.side] = p
        }

        fun human(p: Player, side: ChessSide, silent: Boolean) {
            addPlayer(BukkitChessPlayer(p, side, silent, game))
        }

        fun engine(name: String, side: ChessSide) {
            addPlayer(EnginePlayer(ChessEngine(name), side, game))
        }

        fun build(): BySides<ChessPlayer> = players.map {
            it ?: throw IllegalStateException("player has not been initialized")
        }

    }

    fun addPlayers(init: AddPlayersScope.() -> Unit): ChessGame {
        requireInitial()
        state = GameState.Ready(AddPlayersScope(this).run {
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
    val spectators: List<Player>
        get() = (state as? GameState.WithSpectators)?.spectators.orEmpty()

    operator fun contains(p: Player): Boolean = require<GameState.WithPlayers>().contains(p)

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
            if (renderer.checkForFreeArenas()) {
                components.allInit()
                requireStarting().apply {
                    black.sendTitle("", ConfigManager.getString("Title.YouArePlayingAs.Black"))
                    white.sendTitle(
                        ConfigManager.getString("Title.YourTurn"),
                        ConfigManager.getString("Title.YouArePlayingAs.White")
                    )
                    white.sendMessage(ConfigManager.getString("Message.YouArePlayingAs.White"))
                    black.sendMessage(ConfigManager.getString("Message.YouArePlayingAs.Black"))
                }
                variant.start(this)
                components.allStart()
                state = GameState.Running(requireStarting())
                glog.mid("Started game", uniqueId)
                TimeManager.runTaskTimer(0.seconds, 0.1.seconds) {
                    if (!running)
                        cancel()
                    else
                        components.allUpdate()
                }
            } else {
                players.forEachReal { it.sendMessage(ConfigManager.getError("NoArenas")) }
                panic(CommandException("NoArenas"))
                glog.mid("No free arenas", uniqueId)
            }
        } catch (e: Exception) {
            players.forEachReal { it.sendMessage(ConfigManager.getError("TeleportFailed")) }
            panic(e)
            glog.mid("Failed to start game", uniqueId)
            throw e
        }
        Bukkit.getPluginManager().callEvent(StartEvent(this))
        startTurn()
        return this
    }

    fun spectate(p: Player) {
        val st = requireRunning()
        components.allSpectatorJoin(p)
        st.spectatorUUIDs += p.uniqueId
    }

    fun spectatorLeave(p: Player) {
        val st = requireRunning()
        components.allSpectatorLeave(p)
        st.spectatorUUIDs -= p.uniqueId
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

    open class EndReason(val namePath: String, val reasonPGN: String, val winner: ChessSide?) {

        override fun toString() =
            "EndReason.${javaClass.name.split(".", "$").last()}(winner = $winner)"

        // @formatter:off
        class Checkmate(winner: ChessSide) : EndReason("Chess.EndReason.Checkmate", "normal", winner)
        class Resignation(winner: ChessSide) : EndReason("Chess.EndReason.Resignation", "abandoned", winner)
        class Walkover(winner: ChessSide) : EndReason("Chess.EndReason.Walkover", "abandoned", winner)
        class PluginRestart : EndReason("Chess.EndReason.PluginRestart", "emergency", null)
        class Stalemate : EndReason("Chess.EndReason.Stalemate", "normal", null)
        class InsufficientMaterial : EndReason("Chess.EndReason.InsufficientMaterial", "normal", null)
        class FiftyMoves : EndReason("Chess.EndReason.FiftyMoves", "normal", null)
        class Repetition : EndReason("Chess.EndReason.Repetition", "normal", null)
        class DrawAgreement : EndReason("Chess.EndReason.DrawAgreement", "normal", null)
        class Timeout(winner: ChessSide) : ChessGame.EndReason("Chess.EndReason.Timeout", "time forfeit", winner)
        class DrawTimeout : ChessGame.EndReason("Chess.EndReason.DrawTimeout", "time forfeit", null)
        class Error(val e: Exception) : ChessGame.EndReason("Chess.EndReason.Error", "emergency", null) {
            override fun toString() = "EndReason.Error(winner = $winner, e = $e)"
        }

        class AllPiecesLost(winner: ChessSide) : ChessGame.EndReason("Chess.EndReason.PiecesLost", "normal", winner)
        // @formatter:on

        fun getMessage() = ConfigManager.getFormatString(
            when (winner) {
                ChessSide.WHITE -> "Message.GameFinished.WhiteWon"
                ChessSide.BLACK -> "Message.GameFinished.BlackWon"
                null -> "Message.GameFinished.ItWasADraw"
            }, ConfigManager.getString(namePath)
        )
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
        state = GameState.Stopping(requireRunning { requireStopping(); return }, reason)
        try {
            components.allStop()
            var anyLong = false
            players.forEachUnique(currentTurn) {
                val wld = when(reason.winner) {
                    null -> "Drew"
                    it.side -> "Won"
                    else -> "Lost"
                }
                it.sendTitle(
                    ConfigManager.getString("Title.Player.You$wld"),
                    ConfigManager.getString(reason.namePath)
                )
                it.sendMessage(reason.getMessage())
                if (quick[it.side]) {
                    components.allRemovePlayer(it.player)
                } else {
                    anyLong = true
                    TimeManager.runTaskLater(3.seconds) {
                        components.allRemovePlayer(it.player)
                    }
                }
            }
            spectators.forEach {
                if (reason.winner != null) {
                    it.sendDefTitle(
                        ConfigManager.getString("Title.Spectator.${reason.winner.standardName}Won"),
                        ConfigManager.getString(reason.namePath)
                    )
                } else {
                    it.sendDefTitle(
                        ConfigManager.getString("Title.Spectator.ItWasADraw"),
                        ConfigManager.getString(reason.namePath)
                    )
                }
                it.sendMessage(reason.getMessage())
                if (anyLong) {
                    components.allSpectatorLeave(it)
                } else {
                    TimeManager.runTaskLater(3.seconds) {
                        components.allSpectatorLeave(it)
                    }
                }
            }
            if (reason is EndReason.PluginRestart) {
                components.allClear()
                require<GameState.WithPlayers>().forEachPlayer(ChessPlayer::stop)
                glog.low("Stopped game", uniqueId, reason)
                components.allVeryEnd()
                Bukkit.getPluginManager().callEvent(EndEvent(this))
                return
            }
            TimeManager.runTaskLater((if (anyLong) 3 else 0).seconds + 1.ticks) {
                components.allClear()
                TimeManager.runTaskLater(1.ticks) {
                    require<GameState.WithPlayers>().forEachPlayer(ChessPlayer::stop)
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

    operator fun get(player: Player): BukkitChessPlayer? = (state as? GameState.WithCurrentPlayer)?.get(player)

    operator fun get(side: ChessSide): ChessPlayer = require<GameState.WithPlayers>()[side]

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
        append("Arena: ${renderer.arenaName}\n")
        append("Preset: ${settings.name}\n")
        append("Variant: ${variant.name}\n")
        append("Components: ${components.joinToString { it.javaClass.simpleName }}")
    }

}