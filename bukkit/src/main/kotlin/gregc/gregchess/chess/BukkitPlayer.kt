package gregc.gregchess.chess

import gregc.gregchess.*
import gregc.gregchess.chess.component.spectators
import net.md_5.bungee.api.chat.ClickEvent
import net.md_5.bungee.api.chat.TextComponent
import org.bukkit.entity.Player


private class BukkitGamePlayerStatus(
    @JvmField val title: LocalizedString,
    @JvmField val msg: LocalizedString? = null,
    @JvmField val minor: Boolean = false
)

class BukkitPlayer private constructor(val player: Player) : MinecraftPlayer(player.uniqueId, player.name) {

    companion object {
        private val bukkitPlayers = mutableMapOf<Player, BukkitPlayer>()
        fun toHuman(p: Player) = bukkitPlayers.getOrPut(p) { BukkitPlayer(p) }
        private val COPY_FEN = message("CopyFEN")
        private val COPY_PGN = message("CopyPGN")
        private val IN_CHECK_MSG = message("InCheck")
        private val YOU_ARE_PLAYING_AS_MSG = BySides { message("YouArePlayingAs.${it.standardName}") }

        private val IN_CHECK_TITLE = title("InCheck")
        private val YOU_ARE_PLAYING_AS_TITLE get() = BySides { title("YouArePlayingAs.${it.standardName}") }
        private val YOUR_TURN = title("YourTurn")

        private val YOU_WON = title("Player.YouWon")
        private val YOU_LOST = title("Player.YouLost")
        private val YOU_DREW = title("Player.YouDrew")

        private val SPECTATOR_WINNER = BySides { title("Spectator.${it.standardName}Won") }
        private val SPECTATOR_DRAW = title("Spectator.ItWasADraw")
    }

    var lang: String = DEFAULT_LANG

    var isAdmin = false
        set(value) {
            field = value
            val loc = player.location
            currentGame?.arenaUsage?.resetPlayer(this)
            player.teleport(loc)
        }

    var spectatedGame: ChessGame? = null
        set(v) {
            field?.let { it.spectators -= this }
            field = v
            field?.let { it.spectators += this }
        }

    val isSpectating get() = spectatedGame != null

    fun sendMessage(msg: String) = player.sendMessage(msg.chatColor())
    fun sendMessage(msg: LocalizedString) = sendMessage(msg.get(lang))

    private fun sendTitle(title: String, subtitle: String) = player.sendDefTitle(title.chatColor(), subtitle.chatColor())

    override fun sendPGN(pgn: PGN) {
        val message = TextComponent(COPY_PGN.get(lang).chatColor())
        message.clickEvent = ClickEvent(ClickEvent.Action.COPY_TO_CLIPBOARD, pgn.toString())
        player.spigot().sendMessage(message)
    }

    override fun sendFEN(fen: FEN) {
        val message = TextComponent(COPY_FEN.get(lang).chatColor())
        message.clickEvent = ClickEvent(ClickEvent.Action.COPY_TO_CLIPBOARD, fen.toString())
        player.spigot().sendMessage(message)
    }

    override fun setItem(i: Int, piece: Piece?) {
        player.inventory.setItem(i, piece?.getItem(lang))
    }

    override fun openPawnPromotionMenu(moves: List<MoveCandidate>) = interact {
        val move = player.openPawnPromotionMenu(moves)
        move.game.finishMove(move)
    }

    override fun showGameResults(side: Side, results: GameResults<*>) {
        val wld = when (results.score) {
            GameScore.Victory(side) -> YOU_WON
            GameScore.Draw -> YOU_DREW
            else -> YOU_LOST
        }
        sendTitle(wld.get(lang), results.name.get(lang))
        sendMessage(results.message)
    }

    override fun showGameResults(results: GameResults<*>) {
        sendTitle(results.score.let { if (it is GameScore.Victory) SPECTATOR_WINNER[it.winner] else SPECTATOR_DRAW }.get(lang), results.name.get(lang))
        sendMessage(results.message)
    }

    override fun toString() = "BukkitPlayer(name=$name, uuid=$uuid)"

    override fun sendGameUpdate(side: Side, status: List<GamePlayerStatus>) {
        if (status.isEmpty())
            return
        if (status.size > 2)
            throw IllegalArgumentException(status.toString())
        val values = status.map {
            when(it) {
                GamePlayerStatus.START ->
                    BukkitGamePlayerStatus(YOU_ARE_PLAYING_AS_TITLE[side], YOU_ARE_PLAYING_AS_MSG[side], true)
                GamePlayerStatus.IN_CHECK -> BukkitGamePlayerStatus(IN_CHECK_TITLE, IN_CHECK_MSG)
                GamePlayerStatus.TURN -> BukkitGamePlayerStatus(YOUR_TURN)
            }
        }
        if (values.size == 1) {
            if (values[0].minor)
                sendTitle("", values[0].title.get(lang))
            else
                sendTitle(values[0].title.get(lang), "")
        } else {
            if (values[0].minor && !values[1].minor)
                sendTitle(values[1].title.get(lang), values[0].title.get(lang))
            else
                sendTitle(values[0].title.get(lang), values[1].title.get(lang))
        }
        values.forEach {
            it.msg?.let(::sendMessage)
        }
    }

    override fun sendLastMoves(num: UInt, wLast: MoveData?, bLast: MoveData?) {
        sendMessage(buildString {
            append(num)
            append(". ")
            wLast?.let { append(it.standardName) }
            append("  | ")
            bLast?.let { append(it.standardName) }
        })
    }
}

private val PAWN_PROMOTION = message("PawnPromotion")

suspend fun Player.openPawnPromotionMenu(moves: List<MoveCandidate>) =
    openMenu(PAWN_PROMOTION, moves.mapIndexed { i, m ->
        ScreenOption((m.promotion ?: m.piece.piece).getItem(human.lang), m, InventoryPosition.fromIndex(i))
    }) ?: moves[0]

val HumanPlayer.chess get() = this.currentGame?.get(this)

val Player.human get() = BukkitPlayer.toHuman(this)

val HumanPlayer.bukkit get() = (this as BukkitPlayer).player