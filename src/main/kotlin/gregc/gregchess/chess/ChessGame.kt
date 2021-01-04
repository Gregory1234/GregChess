package gregc.gregchess.chess

import gregc.gregchess.GregChessInfo
import gregc.gregchess.chatColor
import org.bukkit.Bukkit
import org.bukkit.entity.Player
import org.bukkit.event.Event
import org.bukkit.event.HandlerList

class ChessGame(whitePlayer: Player, blackPlayer: Player, val arena: ChessArena) {

    override fun toString() = "ChessGame(arena = $arena)"

    val board = Chessboard(this)

    private val white = ChessPlayer(whitePlayer, ChessSide.WHITE, this, whitePlayer == blackPlayer)
    private val black = ChessPlayer(blackPlayer, ChessSide.BLACK, this, whitePlayer == blackPlayer)
    private var movesSinceLastCapture = 0
    private val boardHashes = mutableMapOf<Long, Int>()
    val realPlayers = listOf(whitePlayer, blackPlayer).distinctBy { it.uniqueId }
    var currentTurn = ChessSide.WHITE
    val currentPlayer
        get() = this[currentTurn]

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
        Bukkit.getScheduler().runTaskLater(GregChessInfo.plugin, Runnable { startTurn() }, 70)

    }

    private fun startTurn() {
        if (addBoardHash() == 3)
            stop(EndReason.Repetition())
        if (++movesSinceLastCapture > 50)
            stop(EndReason.FiftyMoves())
        val whitePieces = white.pieces
        val blackPieces = black.pieces
        if (whitePieces.size == 1 && blackPieces.size == 1)
            stop(EndReason.InsufficientMaterial())
        if (whitePieces.size == 2 && whitePieces.any { it.type.minor } && blackPieces.size == 1)
            stop(EndReason.InsufficientMaterial())
        if (blackPieces.size == 2 && blackPieces.any { it.type.minor } && whitePieces.size == 1)
            stop(EndReason.InsufficientMaterial())
        if (whitePieces.size == 2 && whitePieces.any { it.type.minor } && blackPieces.size == 2 && blackPieces.any { it.type.minor })
            stop(EndReason.InsufficientMaterial())
        if (whitePieces.size == 3 && whitePieces.count { it.type == ChessPiece.Type.KNIGHT } == 2 && blackPieces.size == 1)
            stop(EndReason.InsufficientMaterial())
        if (blackPieces.size == 3 && blackPieces.count { it.type == ChessPiece.Type.KNIGHT } == 2 && whitePieces.size == 1)
            stop(EndReason.InsufficientMaterial())
        if (!stopping)
            currentPlayer.startTurn()
    }

    private fun addBoardHash(): Int {
        val hash = board.getPositionHash()
        boardHashes[hash] = (boardHashes[hash] ?: 0).plus(1)
        return boardHashes[hash]!!
    }

    fun resetMovesSinceLastCapture() {
        movesSinceLastCapture = 0
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
        realPlayers.forEach {
            if (reason.winner != null) {
                it.sendTitle(chatColor(if (reason.winner == this[it]!!.side) "&aYou won" else "&cYou lost"), chatColor(reason.prettyName), 10, 70, 20)
                //it.spigot().sendMessage(ChatMessageType.ACTION_BAR, *TextComponent.fromLegacyText(chatColor("${if (reason.winner == this[it]!!.side) "&aYou won" else "&cYou lost"} by ${reason.prettyName}!")))
            }
            it.sendMessage(reason.message)
            if (it in quick) {
                arena.exit(it)
                Bukkit.getPluginManager().callEvent(EndEvent(this))
            } else {
                Bukkit.getScheduler().runTaskLater(GregChessInfo.plugin, Runnable {
                    arena.exit(it)
                    Bukkit.getPluginManager().callEvent(EndEvent(this))
                }, 3 * 20L)
            }
        }
    }

    operator fun get(player: Player): ChessPlayer? =
            if (white.player == black.player && white.player == player)
                currentPlayer
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