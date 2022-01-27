package gregc.gregchess.fabric.chess.player

import gregc.gregchess.chess.*
import gregc.gregchess.chess.move.PromotionTrait
import gregc.gregchess.chess.piece.BoardPiece
import gregc.gregchess.chess.player.ChessSide
import gregc.gregchess.fabric.chess.*
import gregc.gregchess.fabric.chess.component.renderer
import gregc.gregchess.fabric.chess.component.server
import kotlinx.coroutines.launch
import net.minecraft.server.MinecraftServer
import net.minecraft.server.network.ServerPlayerEntity
import net.minecraft.text.TranslatableText
import kotlin.coroutines.suspendCoroutine

class PiecePlayerActionEvent(val piece: BoardPiece, val type: Type) : ChessEvent {
    enum class Type {
        PICK_UP, PLACE_DOWN
    }
}

class FabricChessSide(player: FabricPlayer, color: Color, game: ChessGame) : ChessSide<FabricPlayer>(player, color, game) {

    var held: BoardPiece? = null
        private set(v) {
            val oldHeld = field
            field = v
            oldHeld?.let {
                it.checkExists(game.board)
                game.callEvent(PiecePlayerActionEvent(it, PiecePlayerActionEvent.Type.PLACE_DOWN))
            }
            v?.let {
                it.checkExists(game.board)
                game.callEvent(PiecePlayerActionEvent(it, PiecePlayerActionEvent.Type.PICK_UP))
            }
        }

    override fun start() {
        if (hasTurn || player != opponent.player)
            player.getServerPlayer(game.server)?.sendMessage(TranslatableText("chess.gregchess.you_are_playing_as.${color.name.lowercase()}"),false)
    }

    override fun startTurn() {
        val inCheck = game.variant.isInCheck(game, color)
        if (inCheck) {
            player.getServerPlayer(game.server)?.sendMessage(TranslatableText("chess.gregchess.in_check"),false)
        }
    }

    fun pickUp(pos: Pos) {
        if (!game.running) return
        val piece = game.board[pos] ?: return
        if (piece.color != color) return
        println(piece.getLegalMoves(game.board).groupBy { m -> game.variant.getLegality(m, game) })
        held = piece
    }

    fun makeMove(pos: Pos, floor: ChessboardFloorBlockEntity, server: MinecraftServer?): Boolean {
        if (!game.running) return false
        val piece = held ?: return false
        if (!piece.piece.block.canActuallyPlaceAt(floor.world, floor.pos.up())) return false
        val moves = piece.getLegalMoves(game.board)
        if (pos != piece.pos && pos !in moves.map { it.display }) return false
        held = null
        if (pos == piece.pos) return true
        val chosenMoves = moves.filter { it.display == pos }
        val move = chosenMoves.first()
        val availablePromotions = move.getTrait<PromotionTrait>()?.promotions?.filter { floor.chessControllerBlock?.hasPiece(it) == true }.orEmpty()
        move.getTrait<PromotionTrait>()?.apply {
            if (availablePromotions.isEmpty()) return false
        }
        game.coroutineScope.launch {
            move.getTrait<PromotionTrait>()?.apply {
                promotion = suspendCoroutine { player.getServerPlayer(server)?.openHandledScreen(PromotionMenuFactory(promotions, availablePromotions, floor.world!!, floor.pos, it)) } ?: availablePromotions.first()
            }
            game.renderer.preferBlock(floor)
            game.finishMove(move)
        }
        return true
    }
}

inline fun ByColor<ChessSide<*>>.forEachReal(block: (FabricPlayer) -> Unit) {
    toList().filterIsInstance<FabricChessSide>().map { it.player }.distinct().forEach(block)
}

inline fun ByColor<ChessSide<*>>.forEachRealFabric(server: MinecraftServer?, block: (ServerPlayerEntity) -> Unit) = forEachReal {
    it.getServerPlayer(server)?.let(block)
}

inline fun ByColor<ChessSide<*>>.forEachUnique(block: (FabricChessSide) -> Unit) {
    val players = toList().filterIsInstance<FabricChessSide>()
    if (players.size == 2 && players.all { it.player == players[0].player })
        players.filter { it.hasTurn }.forEach(block)
    else
        players.forEach(block)
}

inline fun ByColor<ChessSide<*>>.forEachUniqueFabric(server: MinecraftServer?, block: (ServerPlayerEntity, Color) -> Unit) = forEachUnique {
    it.player.getServerPlayer(server)?.let { player ->
        block(player, it.color)
    }
}