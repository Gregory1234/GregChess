package gregc.gregchess.chess

import gregc.gregchess.GregChessInfo
import gregc.gregchess.chatColor
import org.bukkit.Bukkit
import org.bukkit.Material
import org.bukkit.configuration.ConfigurationSection
import org.bukkit.entity.Player
import org.bukkit.event.Event
import org.bukkit.event.HandlerList
import org.bukkit.inventory.InventoryHolder
import org.bukkit.inventory.ItemStack
import org.bukkit.scoreboard.DisplaySlot
import java.lang.module.Configuration
import java.util.concurrent.TimeUnit
import kotlin.reflect.KClass
import kotlin.reflect.safeCast

class ChessGame(
    private val white: ChessPlayer,
    private val black: ChessPlayer,
    val arena: ChessArena,
    val settings: Settings
) {

    override fun toString() = "ChessGame(arena = $arena)"
    private val components = settings.components.map { it.getComponent(this) }

    val board: Chessboard = getComponent(Chessboard::class)!!

    constructor(
        whitePlayer: Player,
        blackPlayer: Player,
        arena: ChessArena,
        settings: Settings
    ) : this(
        ChessPlayer.Human(whitePlayer, ChessSide.WHITE, whitePlayer == blackPlayer),
        ChessPlayer.Human(blackPlayer, ChessSide.BLACK, whitePlayer == blackPlayer),
        arena,
        settings
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

    val scoreboard = ScoreboardManager(this)

    fun <T : Component> getComponent(cl: KClass<T>): T? = components.mapNotNull { cl.safeCast(it) }.firstOrNull()

    fun nextTurn() {
        components.forEach { it.endTurn() }
        currentTurn++
        startTurn()
    }

    fun start() {
        scoreboard.start()
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
                Bukkit.getScheduler().runTaskLater(GregChessInfo.plugin, Runnable {
                    arena.exit(it)
                }, 3 * 20L)
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
                Bukkit.getScheduler().runTaskLater(GregChessInfo.plugin, Runnable {
                    arena.exit(it)
                }, 3 * 20L)
            }
        }
        if (reason is EndReason.PluginRestart)
            return
        Bukkit.getScheduler().runTaskLater(GregChessInfo.plugin, Runnable {
            scoreboard.stop()
            arena.clearScoreboard()
            components.forEach { it.clear() }
            Bukkit.getScheduler().runTaskLater(GregChessInfo.plugin, Runnable {
                players.forEach(ChessPlayer::stop)
                Bukkit.getPluginManager().callEvent(EndEvent(this))
            }, 1L)
        }, (if (anyLong) (3 * 20L) else 0) + 1)
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

    class SettingsMenu(private inline val callback: (Settings) -> Unit) : InventoryHolder {
        var finished: Boolean = false
        private val inv = Bukkit.createInventory(this, 9, "Choose settings")

        init {
            for ((p, _) in Settings.settingsChoice) {
                val item = ItemStack(Material.IRON_BLOCK)
                val meta = item.itemMeta
                meta?.setDisplayName(p)
                item.itemMeta = meta
                inv.addItem(item)
            }
        }

        override fun getInventory() = inv

        fun applyEvent(choice: String) {
            Settings.settingsChoice[choice]?.let(callback)
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
    }

    interface ComponentSettings {
        fun getComponent(game: ChessGame): Component
    }

    data class Settings(
        val name: String,
        val relaxedInsufficientMaterial: Boolean,
        val components: Collection<ComponentSettings>
    ) {

        constructor(name: String, relaxedInsufficientMaterial: Boolean, vararg components: ComponentSettings) :
                this(name, relaxedInsufficientMaterial, components.toList())

        companion object {
            private val componentChoice: MutableMap<String, Map<String, ComponentSettings>> = mutableMapOf()

            fun <T : ComponentSettings> registerComponent(name: String, presets: Map<String, T>) {
                componentChoice[name] = presets
            }

            inline fun <T : ComponentSettings> registerComponent(
                name: String,
                path: String,
                parser: (ConfigurationSection) -> T
            ) {
                val section = GregChessInfo.plugin.config.getConfigurationSection(path) ?: return
                registerComponent(name, section.getValues(false).mapNotNull { (key, value) ->
                    if (value !is ConfigurationSection) return@mapNotNull null
                    Pair(key, parser(value))
                }.toMap())
            }

            val settingsChoice: Map<String, Settings>
                get() {
                    val presets =
                        GregChessInfo.plugin.config.getConfigurationSection("Settings.Presets") ?: return emptyMap()
                    return presets.getValues(false).mapNotNull { (key, value) ->
                        if (value !is ConfigurationSection) return@mapNotNull null
                        val relaxedInsufficientMaterial = value.getBoolean("Relaxed")
                        val components = value.getValues(false)
                            .mapNotNull { (k, v) -> componentChoice[k]?.get(v.toString()) }
                        Pair(key, Settings(key, relaxedInsufficientMaterial, components))
                    }.toMap()
                }
        }
    }


}