package gregc.gregchess.fabric.player

import gregc.gregchess.*
import gregc.gregchess.event.*
import gregc.gregchess.fabric.block.ChessboardFloorBlockEntity
import gregc.gregchess.fabric.client.PromotionMenuFactory
import gregc.gregchess.fabric.component.FabricComponentType
import gregc.gregchess.fabric.piece.block
import gregc.gregchess.fabric.renderer.renderer
import gregc.gregchess.fabric.renderer.server
import gregc.gregchess.match.ChessMatch
import gregc.gregchess.move.connector.checkExists
import gregc.gregchess.move.trait.promotionTrait
import gregc.gregchess.piece.BoardPiece
import gregc.gregchess.player.*
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import net.minecraft.text.Text

class PiecePlayerActionEvent(val piece: BoardPiece, val action: Type) : ChessEvent {
    enum class Type {
        PICK_UP, PLACE_DOWN
    }
}

@Serializable
class FabricChessSide(val player: FabricPlayer, override val color: Color) : ChessSide {

    val gameProfile get() = player.gameProfile
    val uuid get() = player.uuid
    override val name: String get() = player.name
    val entity get() = player.entity

    override val type get() = FabricChessSideType.FABRIC

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
        if (match.sides[color].hasTurn || !match.sides.isSamePlayer())
            player.sendMessage(Text.translatable("chess.gregchess.you_are_playing_as.${color.name.lowercase()}"))
    }

    override fun createFacade(match: ChessMatch) = FabricChessSideFacade(match, this)

    private inline fun oncePerPlayer(match: ChessMatch, callback: () -> Unit) {
        if (!match.sides.isSamePlayer() || color == match.currentColor) {
            callback()
        }
    }

    private fun onStart(match: ChessMatch) = oncePerPlayer(match) {
        sendStartMessage(match)
    }

    private fun onStop(match: ChessMatch) = oncePerPlayer(match) {
        player.showMatchResults(color, match.results!!)
    }

    override fun init(match: ChessMatch, events: EventListenerRegistry) {
        require(match.server == player.server)
        events.registerE(TurnEvent.START) {
            startTurn(match)
        }
        events.register<ChessBaseEvent>(OrderConstraint(
            runBeforeAll = true,
            runAfter = setOf(FabricComponentType.MATCH_CONTROLLER)
        )) {
            when(it) {
                ChessBaseEvent.START -> onStart(match)
                else -> {}
            }
        }
        events.register<ChessBaseEvent>(OrderConstraint(
            runAfterAll = true,
            runBefore = setOf(FabricComponentType.MATCH_CONTROLLER)
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
            player.sendMessage(Text.translatable("chess.gregchess.in_check"))
        }
    }

    fun pickUp(match: ChessMatch, pos: Pos) {
        if (!match.running) return
        val piece = match.board[pos] ?: return
        if (piece.color != color) return
        setHeld(match, piece)
    }

    fun makeMove(match: ChessMatch, pos: Pos, floor: ChessboardFloorBlockEntity): Boolean {
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
                promotion = player.openHandledScreen { PromotionMenuFactory(promotions, availablePromotions, floor.world!!, floor.pos, it) } ?: availablePromotions.first()
            }
            match.renderer.preferBlock(floor)
            match.finishMove(move)
        }
        return true
    }
}

class FabricChessSideFacade(match: ChessMatch, side: FabricChessSide) : ChessSideFacade<FabricChessSide>(match, side) {
    val player get() = side.player
    val gameProfile get() = side.gameProfile
    val uuid get() = side.uuid

    val entity get() = side.player

    val held get() = side.held

    fun pickUp(pos: Pos) = side.pickUp(match, pos)

    fun makeMove(pos: Pos, floor: ChessboardFloorBlockEntity) = side.makeMove(match, pos, floor)
}

inline fun ChessSideManagerFacade.forEachReal(block: (FabricPlayer) -> Unit) {
    toList().filterIsInstance<FabricChessSide>().map { it.player }.distinct().forEach(block)
}

inline fun ChessSideManagerFacade.forEachUnique(block: (FabricChessSideFacade) -> Unit) {
    val players = toList().filterIsInstance<FabricChessSideFacade>()
    if (players.size == 2 && isSamePlayer())
        players.filter { it.hasTurn }.forEach(block)
    else
        players.forEach(block)
}

fun ChessSideManagerFacade.isSamePlayer(): Boolean {
    val w = white
    val b = black
    return w is FabricChessSideFacade && b is FabricChessSideFacade && w.uuid == b.uuid
}