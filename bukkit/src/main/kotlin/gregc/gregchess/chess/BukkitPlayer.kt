package gregc.gregchess.chess

import gregc.gregchess.*
import net.md_5.bungee.api.chat.ClickEvent
import net.md_5.bungee.api.chat.TextComponent
import org.bukkit.entity.Player

val MessageConfig.inCheck by MessageConfig
val MessageConfig.youArePlayingAs get() = BySides { getMessage("YouArePlayingAs.${it.standardName}") }

val TitleConfig.inCheck by TitleConfig
val TitleConfig.youArePlayingAs get() = BySides { getTitle("YouArePlayingAs.${it.standardName}") }
val TitleConfig.yourTurn by TitleConfig

data class BukkitGamePlayerStatus(
    val original: GamePlayerStatus,
    val title: LocalizedString,
    val msg: LocalizedString? = null,
    val minor: Boolean = false
)

class BukkitPlayer private constructor(val player: Player) : MinecraftPlayer(player.uniqueId, player.name) {

    companion object {
        private val bukkitPlayers = mutableMapOf<Player, BukkitPlayer>()
        fun toHuman(p: Player) = bukkitPlayers.getOrPut(p) { BukkitPlayer(p) }
        private val MessageConfig.copyFEN by MessageConfig
        private val MessageConfig.copyPGN by MessageConfig
    }

    var lang: String = DEFAULT_LANG

    var isAdmin = false
        set(value) {
            field = value
            val loc = player.location
            currentGame?.resetPlayer(this)
            player.teleport(loc)
        }

    fun sendMessage(msg: String) = player.sendMessage(msg)
    fun sendMessage(msg: LocalizedString) = sendMessage(msg.get(lang))

    private fun sendTitle(title: String, subtitle: String) = player.sendDefTitle(title, subtitle)

    override fun sendPGN(pgn: PGN) {
        val message = TextComponent(Config.message.copyPGN.get(lang))
        message.clickEvent = ClickEvent(ClickEvent.Action.COPY_TO_CLIPBOARD, pgn.toString())
        player.spigot().sendMessage(message)
    }

    override fun sendFEN(fen: FEN) {
        val message = TextComponent(Config.message.copyFEN.get(lang))
        message.clickEvent = ClickEvent(ClickEvent.Action.COPY_TO_CLIPBOARD, fen.toString())
        player.spigot().sendMessage(message)
    }

    override fun setItem(i: Int, piece: Piece?) {
        player.inventory.setItem(i, piece?.item?.get(lang))
    }

    override fun openPawnPromotionMenu(moves: List<MoveCandidate>) = interact {
        val move = player.openPawnPromotionMenu(moves)
        move.game.finishMove(move)
    }

    override fun showEndReason(side: Side, reason: EndReason) {
        val wld = when (reason.winner) {
            side -> "Won"
            null -> "Drew"
            else -> "Lost"
        }
        sendTitle(config.getLocalizedString("Title.Player.You$wld").get(lang), reason.name.get(lang))
        sendMessage(reason.message)
    }

    override fun showEndReason(reason: EndReason) {
        val winner = reason.winner?.standardName?.plus("Won") ?: "ItWasADraw"
        sendTitle(config.getLocalizedString("Title.Spectator.$winner").get(lang), reason.name.get(lang))
        sendMessage(reason.message)
    }

    override fun toString() = "BukkitPlayer(name=$name, uniqueId=$uniqueId)"

    override fun sendGameUpdate(side: Side, status: List<GamePlayerStatus>) {
        if (status.isEmpty())
            return
        if (status.size > 2)
            throw IllegalArgumentException(status.toString())
        val values = status.map {
            when(it) {
                GamePlayerStatus.START ->
                    BukkitGamePlayerStatus(it,
                        Config.title.youArePlayingAs[side],
                        Config.message.youArePlayingAs[side], true)
                GamePlayerStatus.IN_CHECK -> BukkitGamePlayerStatus(it, Config.title.inCheck, Config.message.inCheck)
                GamePlayerStatus.TURN -> BukkitGamePlayerStatus(it, Config.title.yourTurn)
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

val MessageConfig.pawnPromotion by MessageConfig

suspend fun Player.openPawnPromotionMenu(moves: List<MoveCandidate>) =
    openMenu(Config.message.pawnPromotion, moves.mapIndexed { i, m ->
        ScreenOption((m.promotion?.item ?: m.piece.piece.item).get(human.lang), m, InventoryPosition.fromIndex(i))
    }) ?: moves[0]

val HumanPlayer.chess get() = this.currentGame?.get(this)

val Player.human get() = BukkitPlayer.toHuman(this)

val HumanPlayer.bukkit get() = (this as BukkitPlayer).player