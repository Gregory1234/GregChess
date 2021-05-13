package gregc.gregchess.chess

import gregc.gregchess.*
import gregc.gregchess.chess.component.ChessClock
import gregc.gregchess.chess.component.Chessboard
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

    val renderer: Renderer = Renderer(this)

    val scoreboard = ScoreboardManager(this)

    private val components = listOfNotNull(board, clock, renderer, scoreboard).toMutableList()

    fun registerComponent(c: Component) {
        components.add(components.size-2, c)
    }

    fun <T : Component> getComponent(cl: KClass<T>): T? =
        components.mapNotNull { cl.safeCast(it) }.firstOrNull()

    private val players = mutableListOf<ChessPlayer>()

    private val white
        get() = players.firstOrNull { it.side == ChessSide.WHITE }
    private val black
        get() = players.firstOrNull { it.side == ChessSide.BLACK }

    private val spectatorUUID = mutableListOf<UUID>()

    private val spectators: List<Player>
        get() = spectatorUUID.mapNotNull { Bukkit.getPlayer(it) }

    val chessPlayers: List<ChessPlayer>
        get() = players.toList()

    private val realPlayers: List<Player>
        get() = players.mapNotNull { (it as? BukkitChessPlayer)?.player }.distinct()

    var currentTurn = ChessSide.WHITE

    class AddPlayersScope(private val game: ChessGame) {

        fun human(p: Player, side: ChessSide, silent: Boolean) {
            game.players += BukkitChessPlayer(p, side, silent, game)
        }

        fun engine(name: String, side: ChessSide) {
            game.players += EnginePlayer(ChessEngine(name), side, game)
        }

    }

    fun addPlayers(init: AddPlayersScope.() -> Unit): ChessGame {
        if (started)
            throw IllegalArgumentException()
        AddPlayersScope(this).init()
        return this
    }

    private var started = false

    lateinit var startTime: LocalDateTime
        private set

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
        components.forEach { it.endTurn() }
        variant.checkForGameEnd(this)
        Bukkit.getPluginManager().callEvent(TurnEndEvent(this, this[currentTurn]))
        currentTurn++
        startTurn()
    }

    fun previousTurn() {
        components.forEach { it.previousTurn() }
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
            startTime = LocalDateTime.now()
            if (renderer.checkForFreeArenas()) {
                scoreboard += object :
                    GameProperty(ConfigManager.getString("Component.Scoreboard.Preset")) {
                    override fun invoke() = settings.name
                }
                scoreboard += object :
                    PlayerProperty(ConfigManager.getString("Component.Scoreboard.Player")) {
                    override fun invoke(s: ChessSide) =
                        ConfigManager.getString("Component.Scoreboard.PlayerPrefix") + this@ChessGame[s].name
                }
                forEachPlayer(renderer::addPlayer)
                black?.sendTitle("", ConfigManager.getString("Title.YouArePlayingAs.Black"))
                white?.sendTitle(
                    ConfigManager.getString("Title.YourTurn"),
                    ConfigManager.getString("Title.YouArePlayingAs.White")
                )
                white?.sendMessage(ConfigManager.getString("Message.YouArePlayingAs.White"))
                black?.sendMessage(ConfigManager.getString("Message.YouArePlayingAs.Black"))
                variant.start(this)
                components.forEach { it.start() }
                started = true
                glog.mid("Started game", uniqueId)
                TimeManager.runTaskTimer(0.seconds, 0.1.seconds) {
                    if (stopping)
                        cancel()
                    update()
                }
                Bukkit.getPluginManager().callEvent(StartEvent(this))
                startTurn()
            } else {
                forEachPlayer { it.sendMessage(ConfigManager.getError("NoArenas")) }
                stopping = true
                glog.mid("No free arenas", uniqueId)
            }
            return this
        } catch (e: Exception) {
            renderer.evacuate()
            forEachPlayer { it.sendMessage(ConfigManager.getError("TeleportFailed")) }
            stopping = true
            components.forEach { it.stop() }
            components.forEach { it.clear() }
            glog.mid("Failed to start game", uniqueId)
            throw e
        }
    }

    fun spectate(p: Player) {
        components.forEach { it.spectatorJoin(p) }
        spectatorUUID += p.uniqueId
    }

    fun spectatorLeave(p: Player) {
        components.forEach { it.spectatorLeave(p) }
        spectatorUUID -= p.uniqueId
    }

    private fun startTurn() {
        components.forEach { it.startTurn() }
        if (!stopping)
            this[currentTurn].startTurn()
        glog.low("Started turn", uniqueId, currentTurn)
    }

    private fun startPreviousTurn() {
        components.forEach { it.startPreviousTurn() }
        if (!stopping)
            this[currentTurn].startTurn()
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
        class ArenaRemoved : EndReason("Chess.EndReason.ArenaRemoved", "emergency", null)
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

    private var stopping = false
    var endReason: EndReason? = null
        private set

    fun quickStop(reason: EndReason) = stop(reason, realPlayers)

    fun stop(reason: EndReason, quick: List<Player> = emptyList()) {
        if (stopping) return
        stopping = true
        endReason = reason
        try {
            components.forEach { it.stop() }
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
                    renderer.removePlayer(it)
                } else {
                    anyLong = true
                    TimeManager.runTaskLater(3.seconds) {
                        renderer.removePlayer(it)
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
                    renderer.removePlayer(it)
                } else {
                    TimeManager.runTaskLater(3.seconds) {
                        renderer.removePlayer(it)
                    }
                }
            }
            if (reason is EndReason.PluginRestart) {
                glog.low("Stopped game", uniqueId, reason)
                return
            }
            TimeManager.runTaskLater((if (anyLong) 3 else 0).seconds + 1.ticks) {
                components.forEach { it.clear() }
                TimeManager.runTaskLater(1.ticks) {
                    players.forEach(ChessPlayer::stop)
                    renderer.clearArena()
                    glog.low("Stopped game", uniqueId, reason)
                    Bukkit.getPluginManager().callEvent(EndEvent(this))
                }
            }
        } catch (e: Exception) {
            renderer.evacuate()
            Bukkit.getPluginManager().callEvent(EndEvent(this))
            throw e
        }
    }

    operator fun get(player: Player): BukkitChessPlayer? =
        if (players.map { it as? BukkitChessPlayer }.all { it?.player == player })
            this[currentTurn] as? BukkitChessPlayer
        else players.mapNotNull { it as? BukkitChessPlayer }.firstOrNull { it.player == player }

    operator fun get(side: ChessSide): ChessPlayer = tryOrStopNull(
        when (side) {
            ChessSide.WHITE -> white
            ChessSide.BLACK -> black
        }
    )

    fun update() {
        components.forEach { it.update() }
    }

    fun <E> tryOrStopNull(expr: E?): E = try {
        expr!!
    } catch (e: NullPointerException) {
        stop(EndReason.Error(e))
        throw e
    }

    fun finishMove(move: MoveCandidate) {
        val data = move.execute()
        board.lastMove?.clear()
        board.lastMove = data
        board.lastMove?.render()
        glog.low("Finished move", data)
        nextTurn()
    }

    fun getInfo() = buildTextComponent {
        appendCopy("UUID: $uniqueId\n", uniqueId)
        append("Players: ${players.joinToString { "${it.name} as ${it.side.standardName}" }}\n")
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
        val clock: ChessClock.Settings?
    )

    interface Component {
        fun start() {}
        fun update() {}
        fun stop() {}
        fun clear() {}
        fun spectatorJoin(p: Player) {}
        fun spectatorLeave(p: Player) {}
        fun startTurn() {}
        fun endTurn() {}
        fun startPreviousTurn() {}
        fun previousTurn() {}
    }


}