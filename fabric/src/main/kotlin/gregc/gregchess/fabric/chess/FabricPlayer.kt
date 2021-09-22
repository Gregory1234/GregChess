package gregc.gregchess.fabric.chess

import gregc.gregchess.chess.*
import gregc.gregchess.interact
import gregc.gregchess.name
import kotlinx.serialization.Contextual
import kotlinx.serialization.Serializable
import net.minecraft.server.network.ServerPlayerEntity
import net.minecraft.text.LiteralText
import java.util.*

fun ServerPlayerEntity.showGameResults(color: Color, results: GameResults) {
    sendMessage(LiteralText(results.endReason.name), false)
}

@Serializable
data class FabricPlayerInfo(val uuid: @Contextual UUID) : ChessPlayerInfo {

    override val name: String get() = "fabric player"
    override fun getPlayer(color: Color, game: ChessGame) = FabricPlayer(this, color, game)
}

class FabricPlayer(info: FabricPlayerInfo, color: Color, game: ChessGame) :
    ChessPlayer(info, color, game) {
    val uuid = info.uuid

    override fun init() {
        //player.sendMessage(LiteralText(color.name),false)
    }

    fun pickUp(pos: Pos) {
        if (!game.running) return
        val piece = game.board[pos]?.piece ?: return
        if (piece.color != color) return
        held = piece
    }

    fun makeMove(pos: Pos) {
        if (!game.running) return
        val piece = held ?: return
        val moves = piece.getLegalMoves(game.board)
        if (pos != piece.pos && pos !in moves.map { it.display }) return
        held = null
        if (pos == piece.pos) return
        val chosenMoves = moves.filter { it.display == pos }
        val move = chosenMoves.first()
        interact {
            move.getTrait<PromotionTrait>()?.apply {
                TODO("add a promotion menu")
            }
            game.finishMove(move)
        }
    }
}

val ServerPlayerEntity.cpi get() = FabricPlayerInfo(uuid)

inline fun ByColor<ChessPlayer>.forEachReal(block: (UUID) -> Unit) {
    toList().filterIsInstance<FabricPlayer>().map { it.uuid }.distinct().forEach(block)
}

inline fun ByColor<ChessPlayer>.forEachUnique(block: (FabricPlayer) -> Unit) {
    val players = toList().filterIsInstance<FabricPlayer>()
    if (players.size == 2 && players.all { it.info == players[0].info })
        players.filter { it.hasTurn }.forEach(block)
    else
        players.forEach(block)
}