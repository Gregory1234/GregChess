package gregc.gregchess.chess

import gregc.gregchess.*
import net.md_5.bungee.api.chat.ClickEvent
import net.md_5.bungee.api.chat.TextComponent
import org.bukkit.entity.Player

class BukkitPlayer private constructor(val player: Player) : MinecraftPlayer(player.uniqueId, player.name) {

    companion object {
        private val bukkitPlayers = mutableMapOf<Player, BukkitPlayer>()
        fun toHuman(p: Player) = bukkitPlayers.getOrPut(p) { BukkitPlayer(p) }
        private val MessageConfig.copyFEN by MessageConfig
        private val MessageConfig.copyPGN by MessageConfig
    }

    override var isAdmin = false
        set(value) {
            field = value
            val loc = player.location
            currentGame?.resetPlayer(this)
            player.teleport(loc)
        }

    override fun sendMessage(msg: String) = player.sendMessage(msg)

    override fun sendTitle(title: String, subtitle: String) = player.sendDefTitle(title, subtitle)

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

    override fun sendCommandMessage(msg: String, action: String, command: String) {
        player.spigot().sendMessage(buildTextComponent {
            append(msg)
            append(" ")
            append(action, ClickEvent(ClickEvent.Action.RUN_COMMAND, command))
        })
    }

    override fun setItem(i: Int, piece: Piece?) {
        player.inventory.setItem(i, piece?.item?.get(lang))
    }

    override fun openPawnPromotionMenu(moves: List<MoveCandidate>) = interact {
        val move = player.openPawnPromotionMenu(moves)
        move.game.finishMove(move)
    }

    override fun toString() = "BukkitPlayer(name=$name, uniqueId=$uniqueId)"
}

val MessageConfig.pawnPromotion by MessageConfig

suspend fun Player.openPawnPromotionMenu(moves: List<MoveCandidate>) =
    openMenu(Config.message.pawnPromotion, moves.mapIndexed { i, m ->
        ScreenOption((m.promotion?.item ?: m.piece.piece.item).get(human.lang), m, InventoryPosition.fromIndex(i))
    }) ?: moves[0]

val HumanPlayer.chess get() = this.currentGame?.get(this)

val Player.human get() = BukkitPlayer.toHuman(this)