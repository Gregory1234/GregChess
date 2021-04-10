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

class ChessGame(val arena: ChessArena, val settings: Settings) {

    val uniqueId: UUID = UUID.randomUUID()

    override fun toString() = "ChessGame(arena = $arena, uniqueId = $uniqueId)"

    val board: Chessboard = settings.board.getComponent(this)

    val clock: ChessClock? = settings.clock?.getComponent(this)

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
        get() = players.mapNotNull { (it as? ChessPlayer.Human)?.player }.distinct()

    var currentTurn = ChessSide.WHITE

    val world
        get() = arena.world

    val scoreboard = ScoreboardManager(this)

    class AddPlayersScope(private val game: ChessGame){

        fun human(p: Player, side: ChessSide, silent: Boolean) {
            game.players += ChessPlayer.Human(p, side, silent, game)
        }

        fun engine(name: String, side: ChessSide) {
            game.players += ChessPlayer.Engine(ChessEngine(name), side, game)
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
        board.endTurn()
        clock?.endTurn()
        Bukkit.getPluginManager().callEvent(TurnEndEvent(this, this[currentTurn]))
        currentTurn++
        startTurn()
    }

    fun previousTurn() {
        board.previousTurn()
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
            scoreboard += object :
                GameProperty(ConfigManager.getString("Component.Scoreboard.Preset")) {
                override fun invoke() = settings.name
            }
            scoreboard += object :
                PlayerProperty(ConfigManager.getString("Component.Scoreboard.Player")) {
                override fun invoke(s: ChessSide) =
                    ConfigManager.getString("Component.Scoreboard.PlayerPrefix") + this@ChessGame[s].name
            }
            forEachPlayer(arena::teleport)
            black?.sendTitle("", ConfigManager.getString("Title.YouArePlayingAs.Black"))
            white?.sendTitle(
                ConfigManager.getString("Title.YourTurn"),
                ConfigManager.getString("Title.YouArePlayingAs.White")
            )
            white?.sendMessage(ConfigManager.getString("Message.YouArePlayingAs.White"))
            black?.sendMessage(ConfigManager.getString("Message.YouArePlayingAs.Black"))
            board.start()
            clock?.start()
            scoreboard.start()
            started = true
            glog.mid("Started game", uniqueId)
            Bukkit.getPluginManager().callEvent(StartEvent(this))
            startTurn()
            return this
        } catch (e: Exception) {
            arena.world.players.forEach { if (it in realPlayers) arena.safeExit(it) }
            forEachPlayer { it.sendMessage(ConfigManager.getError("TeleportFailed")) }
            stopping = true
            clock?.stop()
            scoreboard.stop()
            board.clear()
            glog.mid("Failed to start game", uniqueId)
            throw e
        }
    }

    fun spectate(p: Player) {
        spectatorUUID += p.uniqueId
        arena.teleportSpectator(p)
    }

    fun spectatorLeave(p: Player) {
        spectatorUUID -= p.uniqueId
        arena.exit(p)
    }

    private fun startTurn() {
        board.startTurn()
        if (!stopping)
            this[currentTurn].startTurn()
        glog.low("Started turn", uniqueId, currentTurn)
    }

    private fun startPreviousTurn() {
        if (!stopping)
            this[currentTurn].startTurn()
        glog.low("Started previous turn", uniqueId, currentTurn)
    }

    sealed class EndReason(val namePath: String, val reasonPGN: String, val winner: ChessSide?) {

        override fun toString() =
            "EndReason.${javaClass.name.split(".", "$").last()}(winner = $winner)"

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
            clock?.stop()
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
                    arena.exit(it)
                } else {
                    anyLong = true
                    TimeManager.runTaskLater(3.seconds) {
                        arena.exit(it)
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
                    arena.exit(it)
                } else {
                    TimeManager.runTaskLater(3.seconds) {
                        arena.exit(it)
                    }
                }
            }
            if (reason is EndReason.PluginRestart) {
                glog.low("Stopped game", uniqueId, reason)
                return
            }
            TimeManager.runTaskLater((if (anyLong) 3 else 0).seconds + 1.ticks) {
                scoreboard.stop()
                board.clear()
                TimeManager.runTaskLater(1.ticks) {
                    players.forEach(ChessPlayer::stop)
                    arena.clear()
                    glog.low("Stopped game", uniqueId, reason)
                    Bukkit.getPluginManager().callEvent(EndEvent(this))
                }
            }
        } catch(e: Exception) {
            arena.world.players.forEach { arena.safeExit(it) }
            Bukkit.getPluginManager().callEvent(EndEvent(this))
            throw e
        }
    }

    operator fun get(player: Player): ChessPlayer.Human? =
        if (players.map { it as? ChessPlayer.Human }.all { it?.player == player })
            this[currentTurn] as? ChessPlayer.Human
        else players.mapNotNull { it as? ChessPlayer.Human }.firstOrNull { it.player == player }

    operator fun get(side: ChessSide): ChessPlayer = tryOrStopNull(
        when (side) {
            ChessSide.WHITE -> white
            ChessSide.BLACK -> black
        }
    )

    fun update() {
        clock?.update()
    }

    fun <E> tryOrStopNull(expr: E?): E = try {
        expr!!
    } catch (e: NullPointerException) {
        stop(EndReason.Error(e))
        throw e
    }

    class SettingsScreen(
        private val arena: ChessArena,
        private inline val startGame: (ChessArena, Settings) -> Unit
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
            startGame(arena, v)
        }

        override fun onCancel() {
            arena.clear()
        }
    }

    data class Settings(
        val name: String,
        val relaxedInsufficientMaterial: Boolean,
        val simpleCastling: Boolean,
        val board: Chessboard.Settings,
        val clock: ChessClock.Settings?
    )


}