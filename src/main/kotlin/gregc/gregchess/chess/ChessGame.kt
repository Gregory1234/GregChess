package gregc.gregchess.chess

import gregc.gregchess.*
import gregc.gregchess.chess.component.Chessboard
import org.bukkit.Bukkit
import org.bukkit.Material
import org.bukkit.entity.Player
import org.bukkit.event.Event
import org.bukkit.event.HandlerList
import org.bukkit.inventory.InventoryHolder
import org.bukkit.inventory.ItemStack
import java.util.*
import kotlin.reflect.KClass
import kotlin.reflect.safeCast

class ChessGame(val arena: ChessArena, val settings: Settings, setup: GameSetup.() -> Unit) {
    class GameSetup internal constructor(val uniqueId: UUID) {
        lateinit var white: ChessPlayer
        lateinit var black: ChessPlayer
    }

    val uniqueId: UUID = UUID.randomUUID()
    private val white: ChessPlayer
    private val black: ChessPlayer

    init {
        ChessManager.registerGame(this)
        val s = GameSetup(uniqueId).apply(setup)
        white = s.white
        black = s.black
    }

    override fun toString() = "ChessGame(arena = $arena, uniqueId = $uniqueId)"
    private val components = settings.components.map { it.getComponent(this) }

    val board: Chessboard = getComponent(Chessboard::class)!!

    val players: List<ChessPlayer> = listOf(white, black)

    private val spectatorUUID = mutableListOf<UUID>()

    private val spectators: List<Player>
        get() = spectatorUUID.mapNotNull { Bukkit.getPlayer(it) }

    private val playerUUID: List<UUID> =
        players.mapNotNull { (it as? ChessPlayer.Human)?.player }.map { it.uniqueId }.distinct()

    private val realPlayers: List<Player>
        get() = playerUUID.mapNotNull { Bukkit.getPlayer(it) }

    var currentTurn = ChessSide.WHITE

    val world
        get() = arena.world

    val scoreboard = ScoreboardManager(this)

    init {
        ChessManager.registerGamePlayers(this)
        glog.low("Game created", uniqueId, white, black, arena, settings)
    }

    fun <T : Component> getComponent(cl: KClass<T>): T? =
        components.mapNotNull { cl.safeCast(it) }.firstOrNull()

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
        Bukkit.getPluginManager().callEvent(TurnEndEvent(this, this[currentTurn]))
        currentTurn++
        startTurn()
    }

    fun previousTurn() {
        components.forEach { it.previousTurn() }
        currentTurn++
        startPreviousTurn()
    }

    fun start() {
        try {
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
            black.sendTitle("", ConfigManager.getString("Title.YouArePlayingAs.Black"))
            white.sendTitle(
                ConfigManager.getString("Title.YourTurn"),
                ConfigManager.getString("Title.YouArePlayingAs.White")
            )
            white.sendMessage(ConfigManager.getString("Message.YouArePlayingAs.White"))
            black.sendMessage(ConfigManager.getString("Message.YouArePlayingAs.Black"))
            components.forEach { it.start() }
            scoreboard.start()
            glog.mid("Started game", uniqueId)
            startTurn()
        } catch (e: Exception) {
            arena.world.players.forEach { if (it in realPlayers) arena.safeExit(it) }
            forEachPlayer { it.sendMessage(ConfigManager.getError("TeleportFailed")) }
            stopping = true
            components.forEach { it.stop() }
            scoreboard.stop()
            components.forEach { it.clear() }
            glog.mid("Failed to start game", uniqueId)
            throw e
        }
    }

    fun spectate(p: Player) {
        spectatorUUID += p.uniqueId
        arena.teleportSpectator(p)
        components.forEach { it.spectatorJoin(p) }
    }

    fun spectatorLeave(p: Player) {
        components.forEach { it.spectatorLeave(p) }
        spectatorUUID -= p.uniqueId
        arena.exit(p)
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

    sealed class EndReason(val namePath: String, val winner: ChessSide?) {
        override fun toString() =
            "EndReason.${javaClass.name.split(".", "$").last()}(winner = $winner)"

        class Checkmate(winner: ChessSide) : EndReason("Chess.EndReason.Checkmate", winner)
        class Resignation(winner: ChessSide) : EndReason("Chess.EndReason.Resignation", winner)
        class Walkover(winner: ChessSide) : EndReason("Chess.EndReason.Walkover", winner)
        class PluginRestart : EndReason("Chess.EndReason.PluginRestart", null)
        class ArenaRemoved : EndReason("Chess.EndReason.ArenaRemoved", null)
        class Stalemate : EndReason("Chess.EndReason.Stalemate", null)
        class InsufficientMaterial : EndReason("Chess.EndReason.InsufficientMaterial", null)
        class FiftyMoves : EndReason("Chess.EndReason.FiftyMoves", null)
        class Repetition : EndReason("Chess.EndReason.Repetition", null)
        class DrawAgreement : EndReason("Chess.EndReason.DrawAgreement", null)
        class Timeout(winner: ChessSide) : ChessGame.EndReason("Chess.EndReason.Timeout", winner)
        class DrawTimeout : ChessGame.EndReason("Chess.EndReason.DrawTimeout", null)
        class Error(val e: Exception) : ChessGame.EndReason("Chess.EndReason.Error", null) {
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

    class EndEvent(val game: ChessGame, val gameEndReason: EndReason) : Event() {
        override fun getHandlers() = handlerList

        companion object {
            @Suppress("unused")
            @JvmStatic
            fun getHandlerList(): HandlerList = handlerList
            private val handlerList = HandlerList()
        }
    }

    private var stopping = false

    fun quickStop(reason: EndReason) = stop(reason, realPlayers)

    fun stop(reason: EndReason, quick: List<Player> = emptyList()) {
        if (stopping) return
        stopping = true
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
            components.forEach { it.clear() }
            TimeManager.runTaskLater(1.ticks) {
                players.forEach(ChessPlayer::stop)
                arena.clear()
                glog.low("Stopped game", uniqueId, reason)
                Bukkit.getPluginManager().callEvent(EndEvent(this, reason))
            }
        }
    }

    operator fun get(player: Player): ChessPlayer.Human? =
        if (white is ChessPlayer.Human && black is ChessPlayer.Human && white.player == black.player && white.player == player)
            this[currentTurn] as? ChessPlayer.Human
        else if (white is ChessPlayer.Human && white.player == player)
            white
        else if (black is ChessPlayer.Human && black.player == player)
            black
        else
            null

    operator fun get(side: ChessSide): ChessPlayer = when (side) {
        ChessSide.WHITE -> white
        ChessSide.BLACK -> black
    }

    fun update() {
        components.forEach { it.update() }
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

    interface Component {
        val game: ChessGame
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

    interface ComponentSettings {
        fun getComponent(game: ChessGame): Component
    }

    data class Settings(
        val name: String,
        val relaxedInsufficientMaterial: Boolean,
        val simpleCastling: Boolean,
        val components: Collection<ComponentSettings>
    )


}