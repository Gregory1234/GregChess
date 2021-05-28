package gregc.gregchess.chess

import gregc.gregchess.*
import net.md_5.bungee.api.chat.ClickEvent
import net.md_5.bungee.api.chat.TextComponent
import org.bukkit.GameMode
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack
import java.util.*

abstract class HumanPlayer(val name: String) {
    abstract var isAdmin: Boolean
    var currentGame: ChessGame? = null
    var spectatedGame: ChessGame? = null
        set(v) {
            field?.spectatorLeave(this)
            field = v
            field?.spectate(this)
        }
    val games = mutableListOf<ChessGame>()

    abstract fun sendMessage(msg: String)
    abstract fun sendTitle(title: String, subtitle: String = "")
    fun isInGame(): Boolean = currentGame != null
    fun isSpectating(): Boolean = spectatedGame != null
    abstract fun sendPGN(pgn: PGN)
    abstract fun sendCommandMessage(msg: String, action: String, command: String)
    abstract fun setItem(i: Int, item: ItemStack?)
    abstract fun pawnPromotionScreen(piece: Piece, moves: List<Pair<PieceType, MoveCandidate>>)
}

abstract class MinecraftPlayer(val uniqueId: UUID, name: String): HumanPlayer(name)

class BukkitPlayer private constructor(val player: Player): MinecraftPlayer(player.uniqueId, player.name) {
    class PawnPromotionScreen(
        private val pawn: Piece,
        private val moves: List<Pair<PieceType, MoveCandidate>>,
        private val player: ChessPlayer?
    ) : Screen<MoveCandidate>("Message.PawnPromotion") {
        override fun getContent() = moves.mapIndexed { i, (t, m) ->
            ScreenOption(t.getItem(pawn.side), m, InventoryPosition.fromIndex(i))
        }

        override fun onClick(v: MoveCandidate) {
            player?.game?.finishMove(v)
        }

        override fun onCancel() {
            player?.game?.finishMove(moves.first().second)
        }

    }

    companion object {
        private val bukkitPlayers = mutableMapOf<Player, BukkitPlayer>()
        fun toHuman(p: Player) = bukkitPlayers.getOrPut(p){ BukkitPlayer(p) }
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
        val message = TextComponent(ConfigManager.getString("Message.CopyPGN"))
        message.clickEvent = ClickEvent(ClickEvent.Action.COPY_TO_CLIPBOARD, pgn.toString())
        player.spigot().sendMessage(message)
    }

    override fun sendCommandMessage(msg: String, action: String, command: String) {
        player.spigot().sendMessage(buildTextComponent {
            append(msg)
            append(action, ClickEvent(ClickEvent.Action.RUN_COMMAND, command))
        })
    }

    override fun setItem(i: Int, item: ItemStack?) {
        player.inventory.setItem(i, item)
    }

    override fun pawnPromotionScreen(piece: Piece, moves: List<Pair<PieceType, MoveCandidate>>) {
        player.openScreen(PawnPromotionScreen(piece, moves, chess))
    }
}

val HumanPlayer.chess get() = this.currentGame?.get(this)

val Player.human get() = BukkitPlayer.toHuman(this)