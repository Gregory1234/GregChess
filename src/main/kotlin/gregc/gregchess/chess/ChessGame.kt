package gregc.gregchess.chess

import gregc.gregchess.GregChessInfo
import gregc.gregchess.chatColor
import org.bukkit.Bukkit
import org.bukkit.entity.Player
import org.bukkit.event.Event
import org.bukkit.event.HandlerList

class ChessGame(whitePlayer: Player, blackPlayer: Player, private val arena: ChessArena) {

    override fun toString() = "ChessGame(arena = $arena)"

    val board = Chessboard(this)

    private val white = ChessPlayer(whitePlayer, ChessSide.WHITE, this, whitePlayer == blackPlayer)
    private val black = ChessPlayer(blackPlayer, ChessSide.BLACK, this, whitePlayer == blackPlayer)
    val realPlayers = listOf(whitePlayer, blackPlayer).distinctBy { it.uniqueId }
    var currentTurn = ChessSide.WHITE

    val world
        get() = arena.world

    fun nextTurn() {
        currentTurn++
        startTurn()
    }

    fun start() {
        realPlayers.forEach(arena::teleport)
        board.render()
        black.player.sendTitle("", chatColor("You are playing with the black pieces"), 10, 70, 20)
        white.player.sendTitle(chatColor("&eIt is your turn"), chatColor("You are playing with the white pieces"), 10, 70, 20)
        white.sendMessage(chatColor("&eYou are playing with the white pieces"))
        black.sendMessage(chatColor("&eYou are playing with the black pieces"))
        board.setFromFEN("rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1")
        startTurn()
    }

    private fun startTurn() {
        board.updateMoves()
        board.checkForGameEnd()
        if (!stopping)
            this[currentTurn].startTurn()
    }

    sealed class EndReason(val prettyName: String, val winner: ChessSide?) {
        class Checkmate(winner: ChessSide) : EndReason("checkmate", winner)
        class Resignation(winner: ChessSide) : EndReason("walkover", winner)
        class PluginRestart : EndReason("plugin restarting", null)
        class Stalemate : EndReason("stalemate", null)
        class InsufficientMaterial : EndReason("insufficient material", null)
        class FiftyMoves : EndReason("50-move rule", null)
        class Repetition : EndReason("repetition", null)
        class DrawAgreement : EndReason("agreement", null)


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
        board.clear()
        var anyLong = false
        realPlayers.forEach {
            if (reason.winner != null) {
                it.sendTitle(chatColor(if (reason.winner == this[it]!!.side) "&aYou won" else "&cYou lost"), chatColor(reason.prettyName), 10, 70, 20)
                //it.spigot().sendMessage(ChatMessageType.ACTION_BAR, *TextComponent.fromLegacyText(chatColor("${if (reason.winner == this[it]!!.side) "&aYou won" else "&cYou lost"} by ${reason.prettyName}!")))
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
        if (reason is EndReason.PluginRestart)
            return
        if (anyLong) {
            Bukkit.getScheduler().runTaskLater(GregChessInfo.plugin, Runnable {
                board.clear()
            }, 3 * 20L + 1)
            Bukkit.getScheduler().runTaskLater(GregChessInfo.plugin, Runnable {
                Bukkit.getPluginManager().callEvent(EndEvent(this))
            }, 3 * 20L + 2)
        } else {
            Bukkit.getScheduler().runTaskLater(GregChessInfo.plugin, Runnable {
                board.clear()
            }, 1)
            Bukkit.getScheduler().runTaskLater(GregChessInfo.plugin, Runnable {
                Bukkit.getPluginManager().callEvent(EndEvent(this))
            }, 2)
        }
    }

    operator fun get(player: Player): ChessPlayer? =
            if (white.player == black.player && white.player == player)
                this[currentTurn]
            else if (white.player == player)
                white
            else if (black.player == player)
                black
            else
                null

    operator fun get(side: ChessSide): ChessPlayer = when (side) {
        ChessSide.WHITE -> white
        ChessSide.BLACK -> black
    }


}