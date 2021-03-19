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

class ChessGame(
    private val white: ChessPlayer,
    private val black: ChessPlayer,
    val arena: ChessArena,
    val settings: Settings,
    private val chessManager: ChessManager
) {
    val uniqueId: UUID = UUID.randomUUID()
    override fun toString() = "ChessGame(arena = $arena, uniqueId = $uniqueId)"
    private val components = settings.components.map { it.getComponent(this) }

    val board: Chessboard = getComponent(Chessboard::class)!!

    constructor(
        whitePlayer: Player,
        blackPlayer: Player,
        arena: ChessArena,
        settings: Settings,
        chessManager: ChessManager
    ) : this(
        ChessPlayer.Human(whitePlayer, ChessSide.WHITE, whitePlayer == blackPlayer),
        ChessPlayer.Human(blackPlayer, ChessSide.BLACK, whitePlayer == blackPlayer),
        arena,
        settings,
        chessManager
    )

    init {
        white.game = this
        black.game = this
        glog.low("Game created", uniqueId, white, black, arena, settings)
    }

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

    val config = chessManager.config

    val scoreboard = ScoreboardManager(this, chessManager.timeManager)

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
            scoreboard += object : GameProperty(config.getString("Component.Scoreboard.Preset")) {
                override fun invoke() = settings.name
            }
            scoreboard += object : PlayerProperty(config.getString("Component.Scoreboard.Player")) {
                override fun invoke(s: ChessSide) =
                    config.getString("Component.Scoreboard.PlayerPrefix") + this@ChessGame[s].name
            }
            forEachPlayer(arena::teleport)
            black.sendTitle("", config.getString("Title.YouArePlayingAs.Black"))
            white.sendTitle(
                config.getString("Title.YourTurn"),
                config.getString("Title.YouArePlayingAs.White")
            )
            white.sendMessage(config.getString("Message.YouArePlayingAs.White"))
            black.sendMessage(config.getString("Message.YouArePlayingAs.Black"))
            components.forEach { it.start() }
            scoreboard.start()
            glog.mid("Started game", uniqueId)
            startTurn()
        } catch (e: Exception) {
            arena.world.players.forEach { if (it in realPlayers) arena.safeExit(it) }
            forEachPlayer { it.sendMessage(config.getError("TeleportFailed")) }
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

        fun getMessage(config: ConfigManager) = config.getFormatString(
            when (winner) {
                ChessSide.WHITE -> "Message.GameFinished.WhiteWon"
                ChessSide.BLACK -> "Message.GameFinished.BlackWon"
                null -> "Message.GameFinished.ItWasADraw"
            }, config.getString(namePath)
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

    fun quickStop(reason: EndReason) = stop(reason, realPlayers)

    fun stop(reason: EndReason, quick: List<Player> = emptyList()) {
        if (stopping) return
        stopping = true
        components.forEach { it.stop() }
        var anyLong = false
        forEachPlayer {
            if (reason.winner != null) {
                this[it]?.sendTitle(
                    config.getString(if (reason.winner == this[it]!!.side) "Title.Player.YouWon" else "Title.Player.YouLost"),
                    config.getString(reason.namePath)
                )
            } else {
                this[it]?.sendTitle(
                    config.getString("Title.Player.YouDrew"),
                    config.getString(reason.namePath)
                )
            }
            it.sendMessage(reason.getMessage(config))
            if (it in quick) {
                arena.exit(it)
            } else {
                anyLong = true
                chessManager.timeManager.runTaskLater(3.seconds) {
                    arena.exit(it)
                }
            }
        }
        forEachSpectator {
            if (reason.winner != null) {
                this[it]?.sendTitle(
                    config.getString(if (reason.winner == ChessSide.WHITE) "Title.Spectator.WhiteWon" else "Title.Spectator.BlackWon"),
                    config.getString(reason.namePath)
                )
            } else {
                this[it]?.sendTitle(
                    config.getString("Title.Spectator.ItWasADraw"),
                    config.getString(reason.namePath)
                )
            }
            it.sendMessage(reason.getMessage(config))
            if (anyLong) {
                arena.exit(it)
            } else {
                chessManager.timeManager.runTaskLater(3.seconds) {
                    arena.exit(it)
                }
            }
        }
        if (reason is EndReason.PluginRestart) {
            glog.low("Stopped game", uniqueId, reason)
            return
        }
        chessManager.timeManager.runTaskLater((if (anyLong) 3 else 0).seconds + 1.ticks) {
            scoreboard.stop()
            components.forEach { it.clear() }
            chessManager.timeManager.runTaskLater(1.ticks) {
                players.forEach(ChessPlayer::stop)
                arena.clear()
                glog.low("Stopped game", uniqueId, reason)
                Bukkit.getPluginManager().callEvent(EndEvent(this))
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
        private val settingsManager: SettingsManager,
        private inline val cancelCallback: () -> Unit,
        private inline val callback: (Settings) -> Unit
    ) :
        InventoryHolder {
        private val inv = Bukkit.createInventory(
            this,
            9,
            settingsManager.config.getString("Message.ChooseSettings")
        )
        var finished: Boolean = false

        init {
            for ((p, _) in settingsManager.settingsChoice) {
                val item = ItemStack(Material.IRON_BLOCK)
                val meta = item.itemMeta
                meta?.setDisplayName(p)
                item.itemMeta = meta
                inv.addItem(item)
            }
        }

        override fun getInventory() = inv

        fun applyEvent(choice: String) {
            finished = true
            settingsManager.settingsChoice[choice]?.let(callback)
        }

        fun cancel() {
            finished = true
            cancelCallback()
        }
    }

    interface Component {
        val game: ChessGame
        fun start()
        fun update()
        fun stop()
        fun clear()
        fun spectatorJoin(p: Player)
        fun spectatorLeave(p: Player)
        fun startTurn()
        fun endTurn()
        fun startPreviousTurn()
        fun previousTurn()
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