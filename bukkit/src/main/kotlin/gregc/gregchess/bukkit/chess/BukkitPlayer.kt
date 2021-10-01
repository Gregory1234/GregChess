package gregc.gregchess.bukkit.chess

import gregc.gregchess.bukkit.*
import gregc.gregchess.bukkit.chess.component.spectators
import gregc.gregchess.chess.*
import gregc.gregchess.interact
import kotlinx.serialization.*
import kotlinx.serialization.descriptors.SerialKind
import kotlinx.serialization.descriptors.buildSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import net.axay.kspigot.chat.literalText
import org.bukkit.Bukkit
import org.bukkit.entity.Player
import java.util.*

private class ExtraPlayerInfo(val player: Player) {
    companion object {
        private val infos = mutableMapOf<Player, ExtraPlayerInfo>()
        fun of(p: Player) = infos.getOrPut(p) { ExtraPlayerInfo(p) }
    }

    var currentGame: ChessGame? = null
    val games = mutableListOf<ChessGame>()

    var isAdmin = false
        set(value) {
            field = value
            val loc = player.location
            currentGame?.callEvent(ResetPlayerEvent(player))
            player.teleport(loc)
        }

    var spectatedGame: ChessGame? = null
        set(v) {
            field?.let { it.spectators -= player }
            field = v
            field?.let { it.spectators += player }
        }
}

private val Player.extra get() = ExtraPlayerInfo.of(this)
val Player.chess: BukkitPlayer?
    get() {
        val players = currentGame?.players?.toList().orEmpty()
            .filterIsInstance<BukkitPlayer>().filter { it.player == this }
        return if (players.size == 2)
            players.firstOrNull { it.hasTurn }
        else
            players.singleOrNull()
    }
var Player.currentGame
    get() = extra.currentGame
    set(v) {
        extra.currentGame = v
    }
val Player.games get() = extra.games
val Player.isInGame get() = currentGame != null
var Player.isAdmin
    get() = extra.isAdmin
    set(v) {
        extra.isAdmin = v
    }
var Player.spectatedGame
    get() = extra.spectatedGame
    set(v) {
        extra.spectatedGame = v
    }
val Player.isSpectating get() = spectatedGame != null

fun Player.sendMessage(msg: Message) = sendMessage(msg.get())

fun Player.sendTitleFull(title: String?, subtitle: String?) = sendTitle(title, subtitle, 10, 70, 20)

private val SPECTATOR_WINNER = byColor { title("Spectator.${it.configName}Won") }
private val SPECTATOR_DRAW = title("Spectator.ItWasADraw")

fun Player.showGameResults(results: GameResults) {
    sendTitleFull(
        results.score.let { if (it is GameScore.Victory) SPECTATOR_WINNER[it.winner] else SPECTATOR_DRAW }.get(),
        results.name
    )
    sendMessage(results.message)
}

private val YOU_WON = title("Player.YouWon")
private val YOU_LOST = title("Player.YouLost")
private val YOU_DREW = title("Player.YouDrew")

fun Player.showGameResults(color: Color, results: GameResults) {
    val wld = when (results.score) {
        GameScore.Victory(color) -> YOU_WON
        GameScore.Draw -> YOU_DREW
        else -> YOU_LOST
    }
    sendTitleFull(wld.get(), results.name)
    sendMessage(results.message)
}

private val COPY_FEN = message("CopyFEN")

fun Player.sendFEN(fen: FEN) {
    spigot().sendMessage(literalText(COPY_FEN.get()) {
        onClickCopy(fen.toString())
    })
}

private val COPY_PGN = message("CopyPGN")

fun Player.sendPGN(pgn: PGN) {
    spigot().sendMessage(literalText(COPY_PGN.get()) {
        onClickCopy(pgn.toString())
    })
}

fun Player.sendLastMoves(num: UInt, wLast: Move?, bLast: Move?) {
    sendMessage(buildString {
        append(num - 1u)
        append(". ")
        wLast?.let { append(it.name.localName) }
        append("  | ")
        bLast?.let { append(it.name.localName) }
    })
}

private val PAWN_PROMOTION = message("PawnPromotion")

suspend fun Player.openPawnPromotionMenu(promotions: Collection<Piece>) =
    openMenu(PAWN_PROMOTION, promotions.mapIndexed { i, p ->
        ScreenOption(p.item, p, i.toInvPos())
    }) ?: promotions.first()

val Player.cpi get() = BukkitPlayerInfo(this)

@Serializable(with = BukkitPlayerInfo.Serializer::class)
data class BukkitPlayerInfo(val player: Player) : ChessPlayerInfo {
    @OptIn(ExperimentalSerializationApi::class, InternalSerializationApi::class)
    object Serializer : KSerializer<BukkitPlayerInfo> {
        override val descriptor = buildSerialDescriptor("BukkitPlayerInfo", SerialKind.CONTEXTUAL)

        override fun serialize(encoder: Encoder, value: BukkitPlayerInfo) =
            encoder.encodeSerializableValue(encoder.serializersModule.serializer(), value.uuid)

        override fun deserialize(decoder: Decoder): BukkitPlayerInfo {
            val uuid: UUID = decoder.decodeSerializableValue(decoder.serializersModule.serializer())
            return BukkitPlayerInfo(Bukkit.getPlayer(uuid).cNotNull(PLAYER_NOT_FOUND))
        }
    }

    override val name: String get() = player.name
    val uuid: UUID get() = player.uniqueId
    override fun getPlayer(color: Color, game: ChessGame) = BukkitPlayer(this, color, game)
}

class BukkitPlayer(info: BukkitPlayerInfo, color: Color, game: ChessGame) : ChessPlayer(info, color, game) {

    val player: Player = info.player

    private val silent get() = this.info == opponent.info

    companion object {
        private val IN_CHECK_MSG = message("InCheck")
        private val YOU_ARE_PLAYING_AS_MSG = byColor { message("YouArePlayingAs.${it.configName}") }

        private val IN_CHECK_TITLE = title("InCheck")
        private val YOU_ARE_PLAYING_AS_TITLE = byColor { title("YouArePlayingAs.${it.configName}") }
        private val YOUR_TURN = title("YourTurn")
    }

    override fun toString() = "BukkitPlayer(name=$name)"

    fun pickUp(pos: Pos) {
        if (!game.running) return
        val piece = game.board[pos]?.piece ?: return
        if (piece.color != color) return
        held = piece
        player.inventory.setItem(0, piece.piece.item)
    }

    fun makeMove(pos: Pos) {
        if (!game.running) return
        val piece = held ?: return
        val moves = piece.getLegalMoves(game.board)
        if (pos != piece.pos && pos !in moves.map { it.display }) return
        held = null
        player.inventory.setItem(0, null)
        if (pos == piece.pos) return
        val chosenMoves = moves.filter { it.display == pos }
        val move = chosenMoves.first()
        interact {
            move.getTrait<PromotionTrait>()?.apply {
                promotion = promotions?.let { player.openPawnPromotionMenu(it) }
            }
            game.finishMove(move)
        }
    }

    private var firstTurn = true

    private fun sendTitleList(titles: List<Pair<Message, Boolean>>) {
        val title = titles.firstOrNull { it.second }
        val subtitle = titles.firstOrNull { it != title }
        player.sendTitleFull(title?.first?.get() ?: "", subtitle?.first?.get() ?: "")
    }

    override fun startTurn() {
        if (firstTurn) {
            firstTurn = false
            return
        }
        val inCheck = king?.let { game.variant.isInCheck(it, game.board) } == true
        sendTitleList(buildList {
            if (inCheck)
                this += IN_CHECK_TITLE to true
            if (!silent)
                this += YOUR_TURN to true
        })
        if (inCheck)
            player.sendMessage(IN_CHECK_MSG)
    }

    override fun init() {
        sendTitleList(buildList {
            this += YOU_ARE_PLAYING_AS_TITLE[color] to false
            if (hasTurn)
                this += YOUR_TURN to true
        })
        player.sendMessage(YOU_ARE_PLAYING_AS_MSG[color])
    }
}

inline fun ByColor<ChessPlayer>.forEachReal(block: (Player) -> Unit) {
    toList().filterIsInstance<BukkitPlayer>().map { it.player }.distinct().forEach(block)
}

inline fun ByColor<ChessPlayer>.forEachUnique(block: (BukkitPlayer) -> Unit) {
    val players = toList().filterIsInstance<BukkitPlayer>()
    if (players.size == 2 && players.all { it.player == players[0].player })
        players.filter { it.hasTurn }.forEach(block)
    else
        players.forEach(block)
}
