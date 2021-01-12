package gregc.gregchess.chess

import gregc.gregchess.GregChessInfo
import gregc.gregchess.chatColor
import org.bukkit.Bukkit
import org.bukkit.Material
import org.bukkit.entity.Player
import org.bukkit.event.Event
import org.bukkit.event.HandlerList
import org.bukkit.inventory.InventoryHolder
import org.bukkit.inventory.ItemStack
import org.bukkit.scoreboard.DisplaySlot
import java.util.concurrent.TimeUnit

class ChessGame(
    private val white: ChessPlayer,
    private val black: ChessPlayer,
    private val arena: ChessArena,
    val settings: Settings
) {
    override fun toString() = "ChessGame(arena = $arena)"

    val board = Chessboard(this)

    val timer = ChessTimer(this, settings.timerSettings)

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

    fun nextTurn() {
        timer.switchPlayer()
        currentTurn++
        startTurn()
    }

    fun start() {
        addScoreboard("calculating", "calculating")
        realPlayers.forEach(arena::teleport)
        board.render()
        black.sendTitle("", chatColor("You are playing with the black pieces"))
        white.sendTitle(chatColor("&eIt is your turn"), chatColor("You are playing with the white pieces"))
        white.sendMessage(chatColor("&eYou are playing with the white pieces"))
        black.sendMessage(chatColor("&eYou are playing with the black pieces"))
        board.setFromFEN("rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1")
        startTurn()
        timer.start()
    }

    fun spectate(p: Player) {
        spectators += p
        arena.teleportSpectator(p)
    }

    fun spectatorLeave(p: Player) {
        spectators -= p
        arena.exit(p)
    }

    private fun startTurn() {
        board.updateMoves()
        board.checkForGameEnd()
        if (!stopping)
            this[currentTurn].startTurn()
    }

    sealed class EndReason(val prettyName: String, val winner: ChessSide?) {
        class Checkmate(winner: ChessSide) : EndReason("checkmate", winner)
        class Resignation(winner: ChessSide) : EndReason("resignation", winner)
        class Walkover(winner: ChessSide) : EndReason("walkover", winner)
        class PluginRestart : EndReason("plugin restarting", null)
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
        timer.stop()
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
            arena.clearScoreboard()
            board.clear()
            Bukkit.getScheduler().runTaskLater(GregChessInfo.plugin, Runnable {
                players.forEach(ChessPlayer::stop)
                Bukkit.getPluginManager().callEvent(EndEvent(this))
            }, 1L)
        }, (if (anyLong) (3 * 20L) else 0) + 1)
    }

    operator fun get(player: Player): ChessPlayer.Human? =
        if (white is ChessPlayer.Human && black is ChessPlayer.Human && white.player == black.player && white.player == player)
            this[currentTurn] as? ChessPlayer.Human
        else if (white is ChessPlayer.Human && white.player == player )
            white
        else if (black is ChessPlayer.Human && black.player == player )
            black
        else
            null

    operator fun get(side: ChessSide): ChessPlayer = when (side) {
        ChessSide.WHITE -> white
        ChessSide.BLACK -> black
    }

    fun displayClock(whiteTime: Long, blackTime: Long) {
        fun format(time: Long) =
            "%02d:%02d".format(TimeUnit.MILLISECONDS.toMinutes(time), TimeUnit.MILLISECONDS.toSeconds(time) % 60)
        addScoreboard(format(whiteTime), format(blackTime))
    }

    private fun addScoreboard(whiteTime: String, blackTine: String) {
        arena.clearScoreboard()
        val objective = arena.scoreboard.registerNewObjective("GregChess", "", "GregChess game")
        objective.displaySlot = DisplaySlot.SIDEBAR
        objective.getScore("White player:").score = 9
        objective.getScore(chatColor("&b${white.name} ")).score = 8
        objective.getScore("White timer:").score = 7
        objective.getScore("$whiteTime ").score = 6
        objective.getScore("").score = 5
        objective.getScore("Black player:").score = 4
        objective.getScore(chatColor("&b${black.name}")).score = 3
        objective.getScore("Black timer:").score = 2
        objective.getScore(blackTine).score = 1
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

    data class Settings(val timerSettings: ChessTimer.Settings, val relaxedInsufficientMaterial: Boolean) {
        companion object {
            private val rapid10 = Settings(ChessTimer.Settings.rapid10, true)
            private val blitz3 = Settings(ChessTimer.Settings.blitz3, true)

            val settingsChoice = mutableMapOf("Rapid 10+10" to rapid10, "Blitz 5+3" to blitz3)
        }
    }


}