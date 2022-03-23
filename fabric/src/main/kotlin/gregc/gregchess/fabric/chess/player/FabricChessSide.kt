package gregc.gregchess.fabric.chess.player

import com.mojang.authlib.GameProfile
import gregc.gregchess.chess.*
import gregc.gregchess.chess.move.promotionTrait
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

class FabricChessSide(val gameProfile: GameProfile, color: Color, game: ChessGame) : ChessSide<GameProfile>(FabricPlayerType.FABRIC, gameProfile, color, game) {

    val uuid get() = gameProfile.id

    fun getServerPlayer(server: MinecraftServer?) = server?.playerManager?.getPlayer(uuid)

    var held: BoardPiece? = null
        private set(v) {
            val oldHeld = field
            field = v
            oldHeld?.let {
                game.board.checkExists(it)
                game.callEvent(PiecePlayerActionEvent(it, PiecePlayerActionEvent.Type.PLACE_DOWN))
            }
            v?.let {
                game.board.checkExists(it)
                game.callEvent(PiecePlayerActionEvent(it, PiecePlayerActionEvent.Type.PICK_UP))
            }
        }

    override fun start() {
        if (hasTurn || player != opponent.player)
            getServerPlayer(game.server)?.sendMessage(TranslatableText("chess.gregchess.you_are_playing_as.${color.name.lowercase()}"),false)
    }

    override fun startTurn() {
        val inCheck = game.variant.isInCheck(game.board, color)
        if (inCheck) {
            getServerPlayer(game.server)?.sendMessage(TranslatableText("chess.gregchess.in_check"),false)
        }
    }

    fun pickUp(pos: Pos) {
        if (!game.running) return
        val piece = game.board[pos] ?: return
        if (piece.color != color) return
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
        val availablePromotions = move.promotionTrait?.promotions?.filter { floor.chessControllerBlock.entity?.hasPiece(it) == true }.orEmpty()
        move.promotionTrait?.apply {
            if (availablePromotions.isEmpty()) return false
        }
        game.coroutineScope.launch {
            move.promotionTrait?.apply {
                promotion = suspendCoroutine { getServerPlayer(server)?.openHandledScreen(PromotionMenuFactory(promotions, availablePromotions, floor.world!!, floor.pos, it)) } ?: availablePromotions.first()
            }
            game.renderer.preferBlock(floor)
            game.finishMove(move)
        }
        return true
    }
}

inline fun ByColor<ChessSide<*>>.forEachReal(block: (GameProfile) -> Unit) {
    toList().filterIsInstance<FabricChessSide>().map { it.gameProfile }.distinct().forEach(block)
}

inline fun ByColor<ChessSide<*>>.forEachRealEntity(server: MinecraftServer?, block: (ServerPlayerEntity) -> Unit) = forEachReal {
    server?.playerManager?.getPlayer(it.id)?.let(block)
}

inline fun ByColor<ChessSide<*>>.forEachUnique(block: (FabricChessSide) -> Unit) {
    val players = toList().filterIsInstance<FabricChessSide>()
    if (players.size == 2 && players.all { it.player == players[0].player })
        players.filter { it.hasTurn }.forEach(block)
    else
        players.forEach(block)
}

inline fun ByColor<ChessSide<*>>.forEachUniqueEntity(server: MinecraftServer?, block: (ServerPlayerEntity, Color) -> Unit) = forEachUnique {
    it.getServerPlayer(server)?.let { player ->
        block(player, it.color)
    }
}