package gregc.gregchess.fabric.chess.player

import gregc.gregchess.chess.*
import gregc.gregchess.chess.move.PromotionTrait
import gregc.gregchess.chess.piece.BoardPiece
import gregc.gregchess.chess.player.ChessSide
import gregc.gregchess.fabric.chess.ChessboardFloorBlockEntity
import gregc.gregchess.fabric.chess.component.renderer
import gregc.gregchess.fabric.chess.component.server
import kotlinx.coroutines.launch
import net.minecraft.block.BlockState
import net.minecraft.server.MinecraftServer
import net.minecraft.server.network.ServerPlayerEntity
import net.minecraft.text.TranslatableText
import kotlin.coroutines.suspendCoroutine

class FabricChessSide(player: FabricPlayer, color: Color, game: ChessGame) : ChessSide<FabricPlayer>(player, color, game) {

    var held: BoardPiece? = null
        private set(v) {
            field?.checkExists(game.board)
            v?.checkExists(game.board)
            field = v
            game.renderer.redrawFloor(game)
        }

    override fun init() {
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

    fun makeMove(pos: Pos, floor: ChessboardFloorBlockEntity, realPlayer: ServerPlayerEntity, state: BlockState) {
        if (!game.running) return
        val piece = held ?: return
        val moves = piece.getLegalMoves(game.board)
        if (pos != piece.pos && pos !in moves.map { it.display }) return
        held = null
        if (pos == piece.pos) return
        val chosenMoves = moves.filter { it.display == pos }
        val move = chosenMoves.first()
        game.coroutineScope.launch {
            move.getTrait<PromotionTrait>()?.apply {
                floor.chessControllerBlock?.promotions = promotions
                realPlayer.openHandledScreen(state.createScreenHandlerFactory(floor.world, floor.pos))
                promotion = suspendCoroutine { floor.chessControllerBlock?.promotionContinuation = it }
            }
        }.invokeOnCompletion {
            if (it != null)
                throw it
            game.finishMove(move)
        }
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