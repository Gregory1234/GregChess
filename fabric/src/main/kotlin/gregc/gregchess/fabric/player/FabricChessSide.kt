package gregc.gregchess.fabric.player

import com.mojang.authlib.GameProfile
import gregc.gregchess.*
import gregc.gregchess.event.*
import gregc.gregchess.fabric.GameProfileSerializer
import gregc.gregchess.fabric.block.ChessboardFloorBlockEntity
import gregc.gregchess.fabric.client.PromotionMenuFactory
import gregc.gregchess.fabric.component.FabricComponentType
import gregc.gregchess.fabric.event.FabricChessEventType
import gregc.gregchess.fabric.piece.block
import gregc.gregchess.fabric.renderer.renderer
import gregc.gregchess.fabric.renderer.server
import gregc.gregchess.match.ChessMatch
import gregc.gregchess.move.connector.checkExists
import gregc.gregchess.move.trait.promotionTrait
import gregc.gregchess.piece.BoardPiece
import gregc.gregchess.player.ChessSide
import gregc.gregchess.player.ChessSideFacade
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import net.minecraft.server.MinecraftServer
import net.minecraft.server.network.ServerPlayerEntity
import net.minecraft.text.Text
import kotlin.coroutines.suspendCoroutine

class PiecePlayerActionEvent(val piece: BoardPiece, val action: Type) : ChessEvent {
    enum class Type {
        PICK_UP, PLACE_DOWN
    }
    override val type get() = FabricChessEventType.PIECE_PLAYER_ACTION
}

@Serializable
class FabricChessSide(@Serializable(with = GameProfileSerializer::class) val gameProfile: GameProfile, override val color: Color) : ChessSide {

    val uuid get() = gameProfile.id

    override val name: String get() = gameProfile.name

    override val type get() = FabricPlayerType.FABRIC

    fun getServerPlayer(server: MinecraftServer?) = server?.playerManager?.getPlayer(uuid)

    private var _held: BoardPiece? = null

    val held: BoardPiece? get() = _held

    private fun setHeld(match: ChessMatch, newHeld: BoardPiece?) {
        val oldHeld = _held
        _held = newHeld
        oldHeld?.let {
            match.board.checkExists(it)
            match.callEvent(PiecePlayerActionEvent(it, PiecePlayerActionEvent.Type.PLACE_DOWN))
        }
        newHeld?.let {
            match.board.checkExists(it)
            match.callEvent(PiecePlayerActionEvent(it, PiecePlayerActionEvent.Type.PICK_UP))
        }
    }

    private fun sendStartMessage(match: ChessMatch) {
        if (match[color].hasTurn || !match.sideFacades.isSamePlayer())
            getServerPlayer(match.server)?.sendMessage(Text.translatable("chess.gregchess.you_are_playing_as.${color.name.lowercase()}"),false)
    }

    override fun createFacade(match: ChessMatch) = FabricChessSideFacade(match, this)

    private inline fun oncePerPlayer(match: ChessMatch, callback: () -> Unit) {
        if (!match.sideFacades.isSamePlayer() || color == match.currentColor) {
            callback()
        }
    }

    private fun onStart(match: ChessMatch) = oncePerPlayer(match) {
        sendStartMessage(match)
    }

    private fun onStop(match: ChessMatch) = oncePerPlayer(match) {
        getServerPlayer(match.server)?.showMatchResults(color, match.results!!)
    }

    override fun init(match: ChessMatch, events: ChessEventRegistry) {
        events.registerE(TurnEvent.START) {
            startTurn(match)
        }
        events.register(ChessEventType.BASE, ChessEventOrderConstraint(
            runBeforeAll = true,
            runAfter = setOf(ChessEventComponentOwner(FabricComponentType.MATCH_CONTROLLER))
        )) {
            when(it) {
                ChessBaseEvent.START -> onStart(match)
                else -> {}
            }
        }
        events.register(ChessEventType.BASE, ChessEventOrderConstraint(
            runAfterAll = true,
            runBefore = setOf(ChessEventComponentOwner(FabricComponentType.MATCH_CONTROLLER))
        )) {
            when(it) {
                ChessBaseEvent.STOP -> onStop(match)
                else -> {}
            }
        }
    }

    private fun startTurn(match: ChessMatch) {
        val inCheck = match.variant.isInCheck(match.board, color)
        if (inCheck) {
            getServerPlayer(match.server)?.sendMessage(Text.translatable("chess.gregchess.in_check"),false)
        }
    }

    fun pickUp(match: ChessMatch, pos: Pos) {
        if (!match.running) return
        val piece = match.board[pos] ?: return
        if (piece.color != color) return
        setHeld(match, piece)
    }

    fun makeMove(match: ChessMatch, pos: Pos, floor: ChessboardFloorBlockEntity): Boolean {
        val server = match.server
        if (!match.running) return false
        val piece = held ?: return false
        if (!piece.piece.block.canActuallyPlaceAt(floor.world, floor.pos.up())) return false
        val moves = piece.getLegalMoves(match.board)
        if (pos != piece.pos && pos !in moves.map { it.display }) return false
        setHeld(match, null)
        if (pos == piece.pos) return true
        val chosenMoves = moves.filter { it.display == pos }
        val move = chosenMoves.first()
        val availablePromotions = move.promotionTrait?.promotions?.filter { floor.chessControllerBlock.entity?.hasPiece(it) == true }.orEmpty()
        move.promotionTrait?.apply {
            if (availablePromotions.isEmpty()) return false
        }
        match.coroutineScope.launch {
            move.promotionTrait?.apply {
                promotion = suspendCoroutine { getServerPlayer(server)?.openHandledScreen(PromotionMenuFactory(promotions, availablePromotions, floor.world!!, floor.pos, it)) } ?: availablePromotions.first()
            }
            match.renderer.preferBlock(floor)
            match.finishMove(move)
        }
        return true
    }
}

class FabricChessSideFacade(match: ChessMatch, side: FabricChessSide) : ChessSideFacade<FabricChessSide>(match, side) {
    val gameProfile get() = side.gameProfile
    val uuid get() = side.uuid

    val serverPlayer get() = side.getServerPlayer(match.server)

    val held get() = side.held

    fun pickUp(pos: Pos) = side.pickUp(match, pos)

    fun makeMove(pos: Pos, floor: ChessboardFloorBlockEntity) = side.makeMove(match, pos, floor)
}

inline fun ByColor<ChessSideFacade<*>>.forEachReal(block: (GameProfile) -> Unit) {
    toList().filterIsInstance<FabricChessSide>().map { it.gameProfile }.distinct().forEach(block)
}

inline fun ByColor<ChessSideFacade<*>>.forEachRealEntity(block: (ServerPlayerEntity) -> Unit) = forEachReal {
    white.match.server?.playerManager?.getPlayer(it.id)?.let(block)
}

inline fun ByColor<ChessSideFacade<*>>.forEachUnique(block: (FabricChessSideFacade) -> Unit) {
    val players = toList().filterIsInstance<FabricChessSideFacade>()
    if (players.size == 2 && isSamePlayer())
        players.filter { it.hasTurn }.forEach(block)
    else
        players.forEach(block)
}

inline fun ByColor<ChessSideFacade<*>>.forEachUniqueEntity(block: (ServerPlayerEntity, Color) -> Unit) = forEachUnique {
    it.serverPlayer?.let { player ->
        block(player, it.color)
    }
}

fun ByColor<ChessSideFacade<*>>.isSamePlayer(): Boolean {
    val w = white
    val b = black
    return w is FabricChessSideFacade && b is FabricChessSideFacade && w.uuid == b.uuid
}