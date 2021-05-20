package gregc.gregchess.chess

import gregc.gregchess.*
import gregc.gregchess.chess.component.*
import gregc.gregchess.chess.variant.ChessVariant
import org.bukkit.Bukkit
import org.bukkit.Material
import org.bukkit.entity.Player
import org.bukkit.event.Event
import org.bukkit.event.HandlerList
import org.bukkit.inventory.ItemStack
import java.time.LocalDateTime
import java.util.*
import kotlin.reflect.KClass
import kotlin.reflect.safeCast

class ChessGame(val settings: Settings) {
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

    private lateinit var players: BySides<ChessPlayer>

    private val white
        get() = players.white
    private val black
        get() = players.black

    private val spectatorUUID = mutableListOf<UUID>()

    private val spectators: List<Player>
        get() = spectatorUUID.mapNotNull { Bukkit.getPlayer(it) }

    val chessPlayers: List<ChessPlayer>
        get() = players.toList()

    private val realPlayers: List<Player>
        get() = chessPlayers.mapNotNull { (it as? BukkitChessPlayer)?.player }.distinct()

    var currentTurn = ChessSide.WHITE

    val currentPlayer
        get() = this[currentTurn]

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

    private fun requireBeforeStart() {
        if (started)
            throw IllegalStateException("already started")
    }

    private fun requireRunning() {
        if (!started)
            throw IllegalStateException("not started yet")
        if (stopping)
            throw IllegalStateException("already stopping")
    }

    private fun requireStarted() {
        if (!started)
            throw IllegalStateException("not started yet")
    }

    fun addPlayers(init: AddPlayersScope.() -> Unit): ChessGame {
        requireBeforeStart()
        players = AddPlayersScope(this).run {
            init()
            build()
        }
        return this
    }

    var started = false
        private set
    lateinit var startTime: LocalDateTime
        private set

    val running
        get() = started && !stopping

    class TurnEndEvent(val game: ChessGame, val player: ChessPlayer) : Event() {
        override fun getHandlers() = handlerList

        companion object {
            @Suppress("unused")
            @JvmStatic
            fun getHandlerList(): HandlerList = handlerList
            private val handlerList = HandlerList()
        }
    }

    fun forEachPlayer(function: (Player) -> Unit) = realPlayers.forEach(function)
    fun forEachSpectator(function: (Player) -> Unit) = spectators.forEach(function)

    operator fun contains(p: Player) = p in realPlayers

    fun nextTurn() {
        requireRunning()
        components.runGameEvent(GameBaseEvent.END_TURN)
        variant.checkForGameEnd(this)
        Bukkit.getPluginManager().callEvent(TurnEndEvent(this, currentPlayer))
        currentTurn++
        startTurn()
    }

    fun previousTurn() {
        requireRunning()
        components.runGameEvent(GameBaseEvent.PRE_PREVIOUS_TURN)
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
            requireBeforeStart()
            startTime = LocalDateTime.now()
            if (renderer.checkForFreeArenas()) {
                components.runGameEvent(GameBaseEvent.INIT)
                black.sendTitle("", ConfigManager.getString("Title.YouArePlayingAs.Black"))
                white.sendTitle(
                    ConfigManager.getString("Title.YourTurn"),
                    ConfigManager.getString("Title.YouArePlayingAs.White")
                )
                white.sendMessage(ConfigManager.getString("Message.YouArePlayingAs.White"))
                black.sendMessage(ConfigManager.getString("Message.YouArePlayingAs.Black"))
                variant.start(this)
                components.runGameEvent(GameBaseEvent.START)
                started = true
                glog.mid("Started game", uniqueId)
                TimeManager.runTaskTimer(0.seconds, 0.1.seconds) {
                    if (stopping)
                        cancel()
                    else
                        components.runGameEvent(GameBaseEvent.UPDATE)
                }
            } else {
                forEachPlayer { it.sendMessage(ConfigManager.getError("NoArenas")) }
                panic(CommandException("NoArenas"))
                glog.mid("No free arenas", uniqueId)
            }
        } catch (e: Exception) {
            forEachPlayer { it.sendMessage(ConfigManager.getError("TeleportFailed")) }
            panic(e)
            glog.mid("Failed to start game", uniqueId)
            throw e
        }
        Bukkit.getPluginManager().callEvent(StartEvent(this))
        startTurn()
        return this
    }

    fun spectate(p: Player) {
        requireRunning()
        components.runGameEvent(GameBaseEvent.SPECTATOR_JOIN, p)
        spectatorUUID += p.uniqueId
    }

    fun spectatorLeave(p: Player) {
        requireRunning()
        components.runGameEvent(GameBaseEvent.SPECTATOR_LEAVE, p)
        spectatorUUID -= p.uniqueId
    }

    private fun startTurn() {
        components.runGameEvent(GameBaseEvent.START_TURN)
        if (!stopping)
            currentPlayer.startTurn()
        glog.low("Started turn", uniqueId, currentTurn)
    }

    private fun startPreviousTurn() {
        components.runGameEvent(GameBaseEvent.START_PREVIOUS_TURN)
        if (!stopping)
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

    var stopping = false
        private set
    var endReason: EndReason? = null
        private set

    fun quickStop(reason: EndReason) = stop(reason, realPlayers)

    fun stop(reason: EndReason, quick: List<Player> = emptyList()) {
        requireStarted()
        if (stopping) return
        stopping = true
        endReason = reason
        try {
            components.runGameEvent(GameBaseEvent.STOP)
            var anyLong = false
            forEachPlayer {
                if (reason.winner != null) {
                    this[it]?.sendTitle(
                        ConfigManager.getString(if (reason.winner == this[it]!!.side) "Title.Player.YouWon" else "Title.Player.YouLost"),
                        ConfigManager.getString(reason.namePath)
                    )
                } else {
                    this[it]?.sendTitle(
                        ConfigManager.getString("Title.Player.YouDrew"),
                        ConfigManager.getString(reason.namePath)
                    )
                }
                it.sendMessage(reason.getMessage())
                if (it in quick) {
                    components.runGameEvent(GameBaseEvent.REMOVE_PLAYER, it)
                } else {
                    anyLong = true
                    TimeManager.runTaskLater(3.seconds) {
                        components.runGameEvent(GameBaseEvent.REMOVE_PLAYER, it)
                    }
                }
            }
            forEachSpectator {
                if (reason.winner != null) {
                    this[it]?.sendTitle(
                        ConfigManager.getString(if (reason.winner == ChessSide.WHITE) "Title.Spectator.WhiteWon" else "Title.Spectator.BlackWon"),
                        ConfigManager.getString(reason.namePath)
                    )
                } else {
                    this[it]?.sendTitle(
                        ConfigManager.getString("Title.Spectator.ItWasADraw"),
                        ConfigManager.getString(reason.namePath)
                    )
                }
                it.sendMessage(reason.getMessage())
                if (anyLong) {
                    components.runGameEvent(GameBaseEvent.SPECTATOR_LEAVE, it)
                } else {
                    TimeManager.runTaskLater(3.seconds) {
                        components.runGameEvent(GameBaseEvent.SPECTATOR_LEAVE, it)
                    }
                }
            }
            if (reason is EndReason.PluginRestart) {
                components.runGameEvent(GameBaseEvent.CLEAR)
                players.forEach(ChessPlayer::stop)
                glog.low("Stopped game", uniqueId, reason)
                components.runGameEvent(GameBaseEvent.VERY_END)
                Bukkit.getPluginManager().callEvent(EndEvent(this))
                return
            }
            TimeManager.runTaskLater((if (anyLong) 3 else 0).seconds + 1.ticks) {
                components.runGameEvent(GameBaseEvent.CLEAR)
                TimeManager.runTaskLater(1.ticks) {
                    players.forEach(ChessPlayer::stop)
                    components.runGameEvent(GameBaseEvent.VERY_END)
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
        stopping = true
        components.runGameEvent(GameBaseEvent.PANIC, e)
        endReason = EndReason.Error(e)
        Bukkit.getPluginManager().callEvent(EndEvent(this))
    }

    operator fun get(player: Player): BukkitChessPlayer? =
        if (chessPlayers.map { it as? BukkitChessPlayer }.all { it?.player == player })
            currentPlayer as? BukkitChessPlayer
        else chessPlayers.mapNotNull { it as? BukkitChessPlayer }.firstOrNull { it.player == player }

    operator fun get(side: ChessSide): ChessPlayer = tryOrStopNull(players[side])

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
        append("Players: ${chessPlayers.joinToString { "${it.name} as ${it.side.standardName}" }}\n")
        append("Spectators: ${spectators.joinToString { it.name }}\n")
        append("Arena: ${renderer.arenaName}\n")
        append("Preset: ${settings.name}\n")
        append("Variant: ${variant.name}\n")
        append("Components: ${components.joinToString { it.javaClass.simpleName }}")
    }

    class SettingsScreen(
        private inline val startGame: (Settings) -> Unit
    ) : Screen<Settings>("Message.ChooseSettings") {
        override fun getContent() =
            SettingsManager.settingsChoice.toList().mapIndexed { index, (name, s) ->
                val item = ItemStack(Material.IRON_BLOCK)
                val meta = item.itemMeta
                meta?.setDisplayName(name)
                item.itemMeta = meta
                ScreenOption(item, s, InventoryPosition.fromIndex(index))
            }

        override fun onClick(v: Settings) {
            startGame(v)
        }

        override fun onCancel() {
        }
    }

    data class Settings(
        val name: String,
        val simpleCastling: Boolean,
        val variant: ChessVariant,
        val board: Chessboard.Settings,
        val clock: ChessClock.Settings?,
        val renderer: Renderer.Settings,
        val scoreboard: ScoreboardManager.Settings
    )

}