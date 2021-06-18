package gregc.gregchess.chess

import gregc.gregchess.*
import gregc.gregchess.chess.component.*
import java.time.LocalDateTime
import java.util.*
import kotlin.reflect.KClass
import kotlin.reflect.safeCast

class ChessGame(private val timeManager: TimeManager, val arena: Arena, val settings: GameSettings) {
    val uniqueId: UUID = UUID.randomUUID()

    override fun toString() = "ChessGame(uniqueId=$uniqueId)"

    val variant = settings.variant

    private val components =
        listOf(arena) + (settings.components + variant.extraComponents).map { it.getComponent(this) }

    fun getComponents() = components.toList()

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
        renderers.firstNotNullOfOrNull {
            try {
                block(it as Renderer<T>)
            } catch (e: Exception) {
                null
            }
        }

    fun <T, R> cRequireRenderer(block: (Renderer<T>) -> R): R = cNotNull(withRenderer(block), Config.error.rendererNotFound)

    fun <T : Component> getComponent(cl: KClass<T>): T? =
        components.mapNotNull { cl.safeCast(it) }.firstOrNull()

    fun <T : Component> requireComponent(cl: KClass<T>): T = getComponent(cl) ?: throw ComponentNotFoundException(cl)

    inline fun <reified T : Component> getComponent(): T? = getComponent(T::class)

    inline fun <reified T : Component> requireComponent(): T = requireComponent(T::class)

    private var state: GameState = GameState.Initial

    private inline fun <reified T> require(): T = (state as? T) ?: run {
        val e = WrongStateException(state, T::class.java)
        stop(EndReason.Error(e))
        throw e
    }

    private fun requireInitial() = require<GameState.Initial>()

    private fun requireReady() = require<GameState.Ready>()

    private fun requireStarting() = require<GameState.Starting>()

    private fun requireRunning() = require<GameState.Running>()

    private fun requireStopping() = require<GameState.Stopping>()

    var currentTurn: Side
        get() = require<GameState.WithCurrentPlayer>().currentTurn
        set(v) {
            requireRunning().currentTurn = v
        }

    val currentPlayer: ChessPlayer
        get() = require<GameState.WithCurrentPlayer>().currentPlayer

    val currentOpponent: ChessPlayer
        get() = require<GameState.WithCurrentPlayer>().currentOpponent

    inner class AddPlayersScope {
        private val players = MutableBySides<ChessPlayer?>(null)

        fun addPlayer(p: ChessPlayer) {
            players[p.side] = p
        }

        fun human(p: HumanPlayer, side: Side, silent: Boolean) =
            addPlayer(HumanChessPlayer(p, side, silent, this@ChessGame))

        fun engine(engine: ChessEngine, side: Side) =
            addPlayer(EnginePlayer(engine, side, this@ChessGame))

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

    fun start(): ChessGame {
        try {
            state = GameState.Starting(requireReady())
            players.forEachReal {
                components.allAddPlayer(it)
            }
            components.allInit()
            requireStarting().apply {
                (black as? HumanChessPlayer)?.sendTitle("", Config.title.youArePlayingAs.black)
                (white as? HumanChessPlayer)?.sendTitle(Config.title.yourTurn, Config.title.youArePlayingAs.white)
                forEachUnique {
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
            players.forEachReal { it.sendMessage(Config.error.teleportFailed) }
            panic(e)
            glog.mid("Failed to start game", uniqueId)
            throw e
        }
        components.allBegin()
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

    fun resetPlayer(p: HumanPlayer) {
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

    val endReason: EndReason?
        get() = (state as? GameState.WithEndReason)?.endReason

    fun quickStop(reason: EndReason) = stop(reason, BySides(true))

    fun stop(reason: EndReason, quick: BySides<Boolean> = BySides(false)) {
        val stopping = GameState.Stopping(state as? GameState.Running ?: run { requireStopping(); return }, reason)
        state = stopping
        try {
            components.allStop()
            players.forEachUnique(currentTurn) {
                interact {
                    it.sendTitle(Config.title.winner(it.side, reason.winner), reason.namePath.get())
                    it.sendMessage(reason.message)
                    timeManager.wait((if (quick[it.side]) 0 else 3).seconds)
                    components.allRemovePlayer(it.player)
                }
            }
            spectators.forEach {
                interact {
                    it.sendTitle(Config.title.spectator(reason.winner), reason.namePath.get())
                    it.sendMessage(reason.message)
                    timeManager.wait((if (quick.white && quick.black) 0 else 3).seconds)
                    components.allSpectatorLeave(it)
                }
            }
            if (reason is EndReason.PluginRestart) {
                components.allClear()
                players.forEach(ChessPlayer::stop)
                state = GameState.Stopped(stopping)
                glog.low("Stopped game", uniqueId, reason)
                components.allVeryEnd()
                return
            }
            interact {
                timeManager.wait((if (quick.white && quick.black) 0 else 3).seconds + 1.ticks)
                components.allClear()
                timeManager.wait(1.ticks)
                players.forEach(ChessPlayer::stop)
                state = GameState.Stopped(stopping)
                glog.low("Stopped game", uniqueId, reason)
                components.allVeryEnd()
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

}