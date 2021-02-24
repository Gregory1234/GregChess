package gregc.gregchess.chess

import gregc.gregchess.chatColor
import gregc.gregchess.chess.component.Chessboard
import gregc.gregchess.seconds
import gregc.gregchess.ticks
import org.bukkit.Bukkit
import org.bukkit.Material
import org.bukkit.entity.Player
import org.bukkit.event.Event
import org.bukkit.event.HandlerList
import org.bukkit.inventory.InventoryHolder
import org.bukkit.inventory.ItemStack
import kotlin.reflect.KClass
import kotlin.reflect.safeCast

class ChessGame(
    private val white: ChessPlayer,
    private val black: ChessPlayer,
    val arena: ChessArena,
    val settings: Settings,
    private val chessManager: ChessManager
) {

    override fun toString() = "ChessGame(arena = $arena)"
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
    }

    val players: List<ChessPlayer> = listOf(white, black)

    val spectators = mutableListOf<Player>()

    val realPlayers: List<Player> =
        players.mapNotNull { (it as? ChessPlayer.Human)?.player }.distinctBy { it.uniqueId }

    var currentTurn = ChessSide.WHITE

    val world
        get() = arena.world

    val scoreboard = ScoreboardManager(this, chessManager.timeManager)

    fun <T : Component> getComponent(cl: KClass<T>): T? = components.mapNotNull { cl.safeCast(it) }.firstOrNull()

    class TurnEndEvent(val game: ChessGame, val player: ChessPlayer) : Event() {
        override fun getHandlers() = handlerList

        companion object {
            @Suppress("unused")
            @JvmStatic
            fun getHandlerList(): HandlerList = handlerList
            private val handlerList = HandlerList()
        }
    }

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
        scoreboard += object : GameProperty("Preset") {
            override fun invoke() = settings.name
        }
        scoreboard += object : PlayerProperty("player") {
            override fun invoke(s: ChessSide) = chatColor("&b${this@ChessGame[s].name}")
        }
        realPlayers.forEach(arena::teleport)
        black.sendTitle("", chatColor("You are playing with the black pieces"))
        white.sendTitle(chatColor("&eIt is your turn"), chatColor("You are playing with the white pieces"))
        white.sendMessage(chatColor("&eYou are playing with the white pieces"))
        black.sendMessage(chatColor("&eYou are playing with the black pieces"))
        components.forEach { it.start() }
        scoreboard.start()
        startTurn()
    }

    fun spectate(p: Player) {
        spectators += p
        arena.teleportSpectator(p)
        components.forEach { it.spectatorJoin(p) }
    }

    fun spectatorLeave(p: Player) {
        components.forEach { it.spectatorLeave(p) }
        spectators -= p
        arena.exit(p)
    }

    private fun startTurn() {
        components.forEach { it.startTurn() }
        if (!stopping)
            this[currentTurn].startTurn()
    }

    private fun startPreviousTurn() {
        components.forEach { it.startPreviousTurn() }
        if (!stopping)
            this[currentTurn].startTurn()
    }

    sealed class EndReason(val prettyName: String, val winner: ChessSide?) {
        class Checkmate(winner: ChessSide) : EndReason("checkmate", winner)
        class Resignation(winner: ChessSide) : EndReason("resignation", winner)
        class Walkover(winner: ChessSide) : EndReason("walkover", winner)
        class PluginRestart : EndReason("plugin restarting", null)
        class ArenaRemoved : EndReason("arena deleted", null)
        class Stalemate : EndReason("stalemate", null)
        class InsufficientMaterial : EndReason("insufficient material", null)
        class FiftyMoves : EndReason("50-move rule", null)
        class Repetition : EndReason("repetition", null)
        class DrawAgreement : EndReason("agreement", null)
        class Timeout(winner: ChessSide) : ChessGame.EndReason("timeout", winner)
        class DrawTimeout : ChessGame.EndReason("timeout vs insufficient material", null)
        class Error(val e: Exception) : ChessGame.EndReason("error", null)

        val message
            get() = "The game has finished. ${winner?.prettyName?.plus(" won") ?: "It was a draw"} by ${prettyName}."
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

    fun stop(reason: EndReason, quick: List<Player> = emptyList()) {
        if (stopping) return
        stopping = true
        components.forEach { it.stop() }
        var anyLong = false
        realPlayers.forEach {
            if (reason.winner != null) {
                this[it]?.sendTitle(
                    chatColor(if (reason.winner == this[it]!!.side) "&aYou won" else "&cYou lost"),
                    chatColor(reason.prettyName)
                )
            } else {
                this[it]?.sendTitle(chatColor("&eYou drew"), chatColor(reason.prettyName))
            }
            it.sendMessage(reason.message)
            if (it in quick) {
                arena.exit(it)
            } else {
                anyLong = true
                chessManager.timeManager.runTaskLater(3.seconds) {
                    arena.exit(it)
                }
            }
        }
        spectators.forEach {
            if (reason.winner != null) {
                this[it]?.sendTitle(
                    chatColor(if (reason.winner == ChessSide.WHITE) "&eWhite won" else "&eBlack lost"),
                    chatColor(reason.prettyName)
                )
            } else {
                this[it]?.sendTitle(chatColor("&eDraw"), chatColor(reason.prettyName))
            }
            it.sendMessage(reason.message)
            if (anyLong) {
                arena.exit(it)
            } else {
                chessManager.timeManager.runTaskLater(3.seconds) {
                    arena.exit(it)
                }
            }
        }
        if (reason is EndReason.PluginRestart)
            return
        chessManager.timeManager.runTaskLater((if (anyLong) 3 else 0).seconds + 1.ticks) {
            scoreboard.stop()
            components.forEach { it.clear() }
            chessManager.timeManager.runTaskLater(1.ticks) {
                players.forEach(ChessPlayer::stop)
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

    class SettingsMenu(private val settingsManager: SettingsManager, private inline val callback: (Settings) -> Unit) : InventoryHolder {
        private val inv = Bukkit.createInventory(this, 9, "Choose settings")

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
            settingsManager.settingsChoice[choice]?.let(callback)
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
        val components: Collection<ComponentSettings>
    )


}