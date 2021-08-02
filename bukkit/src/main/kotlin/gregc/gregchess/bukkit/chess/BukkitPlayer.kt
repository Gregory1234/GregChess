package gregc.gregchess.bukkit.chess

import gregc.gregchess.bukkit.*
import gregc.gregchess.bukkit.chess.component.spectators
import gregc.gregchess.chess.*
import net.md_5.bungee.api.chat.ClickEvent
import net.md_5.bungee.api.chat.TextComponent
import org.bukkit.entity.Player


private class BukkitGamePlayerStatus(
    @JvmField val title: Message,
    @JvmField val msg: Message? = null,
    @JvmField val minor: Boolean = false
)

class BukkitPlayer private constructor(val player: Player) : MinecraftPlayer(player.uniqueId, player.name) {

    companion object {
        private val bukkitPlayers = mutableMapOf<Player, BukkitPlayer>()
        fun toHuman(p: Player) = bukkitPlayers.getOrPut(p) { BukkitPlayer(p) }
        private val COPY_FEN = message("CopyFEN")
        private val COPY_PGN = message("CopyPGN")
        private val IN_CHECK_MSG = message("InCheck")
        private val YOU_ARE_PLAYING_AS_MSG = BySides { message("YouArePlayingAs.${it.configName}") }

        private val IN_CHECK_TITLE = title("InCheck")
        private val YOU_ARE_PLAYING_AS_TITLE get() = BySides { title("YouArePlayingAs.${it.configName}") }
        private val YOUR_TURN = title("YourTurn")

        private val YOU_WON = title("Player.YouWon")
        private val YOU_LOST = title("Player.YouLost")
        private val YOU_DREW = title("Player.YouDrew")

        private val SPECTATOR_WINNER = BySides { title("Spectator.${it.configName}Won") }
        private val SPECTATOR_DRAW = title("Spectator.ItWasADraw")
    }

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
    fun sendMessage(msg: Message) = sendMessage(msg.get())

    private fun sendTitle(title: String, subtitle: String) =
        player.sendTitle(title.chatColor(), subtitle.chatColor(), 10, 70, 20)

    override fun sendPGN(pgn: PGN) {
        val message = TextComponent(COPY_PGN.get())
        message.clickEvent = ClickEvent(ClickEvent.Action.COPY_TO_CLIPBOARD, pgn.toString())
        player.spigot().sendMessage(message)
    }

    override fun sendFEN(fen: FEN) {
        val message = TextComponent(COPY_FEN.get())
        message.clickEvent = ClickEvent(ClickEvent.Action.COPY_TO_CLIPBOARD, fen.toString())
        player.spigot().sendMessage(message)
    }

    fun setItem(i: Int, piece: Piece?) {
        player.inventory.setItem(i, piece?.item)
    }

    override suspend fun openPawnPromotionMenu(promotions: Collection<Piece>): Piece =
        player.openPawnPromotionMenu(promotions)

    override fun showGameResults(side: Side, results: GameResults<*>) {
        val wld = when (results.score) {
            GameScore.Victory(side) -> YOU_WON
            GameScore.Draw -> YOU_DREW
            else -> YOU_LOST
        }
        sendTitle(wld.get(), results.name)
        sendMessage(results.message)
    }

    override fun showGameResults(results: GameResults<*>) {
        sendTitle(results.score.let { if (it is GameScore.Victory) SPECTATOR_WINNER[it.winner] else SPECTATOR_DRAW }
            .get(), results.name)
        sendMessage(results.message)
    }

    override fun toString() = "BukkitPlayer(name=$name, uuid=$uuid)"

    override fun sendGameUpdate(side: Side, status: List<GamePlayerStatus>) {
        if (status.isEmpty())
            return
        if (status.size > 2)
            throw IllegalArgumentException(status.toString())
        val values = status.map {
            when (it) {
                GamePlayerStatus.START ->
                    BukkitGamePlayerStatus(YOU_ARE_PLAYING_AS_TITLE[side], YOU_ARE_PLAYING_AS_MSG[side], true)
                GamePlayerStatus.IN_CHECK -> BukkitGamePlayerStatus(IN_CHECK_TITLE, IN_CHECK_MSG)
                GamePlayerStatus.TURN -> BukkitGamePlayerStatus(YOUR_TURN)
            }
        }
        if (values.size == 1) {
            if (values[0].minor)
                sendTitle("", values[0].title.get())
            else
                sendTitle(values[0].title.get(), "")
        } else {
            if (values[0].minor && !values[1].minor)
                sendTitle(values[1].title.get(), values[0].title.get())
            else
                sendTitle(values[0].title.get(), values[1].title.get())
        }
        values.forEach {
            it.msg?.let(::sendMessage)
        }
    }

    override fun sendLastMoves(num: UInt, wLast: MoveData?, bLast: MoveData?) {
        sendMessage(buildString {
            append(num)
            append(". ")
            wLast?.let { append(it.name.localName) }
            append("  | ")
            bLast?.let { append(it.name.localName) }
        })
    }
}

private val PAWN_PROMOTION = message("PawnPromotion")

suspend fun Player.openPawnPromotionMenu(promotions: Collection<Piece>) =
    openMenu(PAWN_PROMOTION, promotions.mapIndexed { i, p ->
        ScreenOption(p.item, p, i.toInvPos())
    }) ?: promotions.first()

val HumanPlayer.chess get() = this.currentGame?.get(this)

val Player.human get() = BukkitPlayer.toHuman(this)

val HumanPlayer.bukkit get() = (this as BukkitPlayer).player