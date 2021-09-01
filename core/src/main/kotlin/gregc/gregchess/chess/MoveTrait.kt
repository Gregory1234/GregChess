package gregc.gregchess.chess

import gregc.gregchess.ChessModule
import gregc.gregchess.RegistryType
import kotlinx.serialization.*
import kotlinx.serialization.descriptors.*
import kotlinx.serialization.encoding.*

class TraitsCouldNotExecuteException(traits: Collection<MoveTrait>): Exception(traits.toList().toString())

@Serializable(with = MoveTraitSerializer::class)
interface MoveTrait {
    val nameTokens: MoveName
    fun setup(game: ChessGame, move: Move) {}
    fun execute(game: ChessGame, move: Move, pass: UByte, remaining: List<MoveTrait>): Boolean = true
    fun undo(game: ChessGame, move: Move, pass: UByte, remaining: List<MoveTrait>): Boolean = true
}

@OptIn(ExperimentalSerializationApi::class, InternalSerializationApi::class)
@Suppress("UNCHECKED_CAST")
object MoveTraitSerializer: KSerializer<MoveTrait> {
    override val descriptor: SerialDescriptor
        get() = buildClassSerialDescriptor("MoveTrait") {
            element<String>("type")
            element("value", buildSerialDescriptor("MoveTraitValue", SerialKind.CONTEXTUAL))
        }

    override fun serialize(encoder: Encoder, value: MoveTrait) {
        val actualSerializer = value::class.serializer()
        val id = RegistryType.MOVE_TRAIT_CLASS.getModule(value::class).namespace + ":" + RegistryType.MOVE_TRAIT_CLASS[value::class]
        encoder.encodeStructure(descriptor) {
            encodeStringElement(descriptor, 0, id)
            encodeSerializableElement(descriptor, 1, actualSerializer as KSerializer<MoveTrait>, value)
        }
    }

    override fun deserialize(decoder: Decoder): MoveTrait = decoder.decodeStructure(descriptor) {
        var type: String? = null
        var ret: MoveTrait? = null
        if (decodeSequentially()) {
            type = decodeStringElement(descriptor, 0)
            val serializer = ChessModule[type.substringBefore(":")][RegistryType.MOVE_TRAIT_CLASS][type.substringAfter(":")].serializer()
            return decodeSerializableElement(descriptor, 1, serializer)
        }

        mainLoop@ while (true) {
            when (val index = decodeElementIndex(descriptor)) {
                CompositeDecoder.DECODE_DONE -> {
                    break@mainLoop
                }
                0 -> {
                    type = decodeStringElement(descriptor, index)
                }
                1 -> {
                    val serializer = ChessModule[type!!.substringBefore(":")][RegistryType.MOVE_TRAIT_CLASS][type.substringAfter(":")].serializer()
                    ret = decodeSerializableElement(descriptor, index, serializer)
                }
                else -> throw SerializationException("Invalid index")
            }
        }
        ret!!
    }
}

@Serializable
class DefaultHalfmoveClockTrait(var halfmoveClock: UInt? = null): MoveTrait {
    override val nameTokens = MoveName()
    override fun execute(game: ChessGame, move: Move, pass: UByte, remaining: List<MoveTrait>): Boolean {
        if (remaining.any { it is CaptureTrait })
            return false
        halfmoveClock = game.board.halfmoveClock
        if (move.piece.type == PieceType.PAWN || move.getTrait<CaptureTrait>()?.captured != null) {
            game.board.halfmoveClock = 0u
        } else {
            game.board.halfmoveClock++
        }
        return true
    }

    override fun undo(game: ChessGame, move: Move, pass: UByte, remaining: List<MoveTrait>): Boolean {
        game.board.halfmoveClock = halfmoveClock!!
        return true
    }
}

@Serializable
class CastlesTrait(val rook: PieceInfo, val side: BoardSide, val target: Pos, val rookTarget: Pos): MoveTrait {
    override val nameTokens = MoveName(listOf(MoveNameTokenType.CASTLE.of(side)))
    override fun execute(game: ChessGame, move: Move, pass: UByte, remaining: List<MoveTrait>): Boolean {
        val boardPiece = game.board[move.piece.pos]?.piece
        val boardRook = game.board[rook.pos]?.piece
        val targetSquare = game.board[target]
        val rookTargetSquare = game.board[rookTarget]
        if (boardPiece == null || boardRook == null ||
            targetSquare == null || (target != rook.pos && targetSquare.piece != null) ||
            rookTargetSquare == null || (rookTarget != move.piece.pos && rookTargetSquare.piece != null)
        )
            return false
        BoardPiece.autoMove(mapOf(
            boardPiece to targetSquare,
            boardRook to rookTargetSquare
        ))
        return true
    }

    override fun undo(game: ChessGame, move: Move, pass: UByte, remaining: List<MoveTrait>): Boolean {
        val boardPiece = game.board[target]?.piece
        val boardRook = game.board[rookTarget]?.piece
        val targetSquare = game.board[move.piece.pos]
        val rookTargetSquare = game.board[rook.pos]
        if (boardPiece == null || boardRook == null ||
            targetSquare == null || (target != rook.pos && targetSquare.piece != null) ||
            rookTargetSquare == null || (rookTarget != move.piece.pos && rookTargetSquare.piece != null)
        )
            return false
        BoardPiece.autoMove(mapOf(
            boardPiece to targetSquare,
            boardRook to rookTargetSquare
        ))
        boardPiece.force(false)
        boardRook.force(false)
        return true
    }
}

@Serializable
class PromotionTrait(val promotions: List<Piece>?, var promotion: Piece? = null): MoveTrait {
    override val nameTokens = MoveName(listOfNotNull(promotion?.type?.let { MoveNameTokenType.PROMOTION.of(it) }))

    override fun execute(game: ChessGame, move: Move, pass: UByte, remaining: List<MoveTrait>): Boolean {
        if ((promotions == null) != (promotion == null))
            return false
        if (promotions?.contains(promotion) == false)
            return false
        promotion?.let {
            game.board[move.piece.pos]?.piece?.promote(it)
        }
        return true
    }

    override fun undo(game: ChessGame, move: Move, pass: UByte, remaining: List<MoveTrait>): Boolean {
        if (game.board[move.piece.pos]?.piece == null)
            return false
        game.board[move.piece.pos]?.piece?.promote(move.piece.piece)
        return true
    }
}

@Serializable
class NameTrait(override val nameTokens: MoveName): MoveTrait

@Serializable
class CheckTrait(override val nameTokens: MoveName = MoveName()): MoveTrait {
    override fun execute(game: ChessGame, move: Move, pass: UByte, remaining: List<MoveTrait>): Boolean {
        if (remaining.all { it is CheckTrait }) {
            if (nameTokens.isEmpty()) {
                nameTokens += checkForChecks(move.piece.side, game)
            }
            return true
        }
        return false
    }
}

@Serializable
class CaptureTrait(val capture: Pos, val hasToCapture: Boolean = false, var captured: PieceInfo? = null, var capturedPiece: CapturedPiece? = null): MoveTrait  {
    override val nameTokens get() = MoveName(listOfNotNull(MoveNameTokenType.CAPTURE.mk.takeIf { captured != null }))

    override fun execute(game: ChessGame, move: Move, pass: UByte, remaining: List<MoveTrait>): Boolean {
        game.board[capture]?.piece?.let {
            captured = it.info
            capturedPiece = it.capture(move.piece.side)
        }
        return true
    }

    override fun undo(game: ChessGame, move: Move, pass: UByte, remaining: List<MoveTrait>): Boolean {
        captured?.let {
            if (game.board[it.pos]?.piece != null)
                return false
            game.board += it
            game.board[it.pos]?.piece?.resurrect(capturedPiece!!)
        }
        return true
    }
}

@Serializable
class PawnOriginTrait(override val nameTokens: MoveName = MoveName()): MoveTrait {
    override fun setup(game: ChessGame, move: Move) {
        move.getTrait<CaptureTrait>()?.let {
            if (game.board[it.capture]?.piece != null && nameTokens.isEmpty()) {
                nameTokens += MoveNameTokenType.UNIQUENESS_COORDINATE.of(UniquenessCoordinate(file = move.piece.pos.file))
            }
        }
    }
}

@Serializable
class PieceOriginTrait(override val nameTokens: MoveName = MoveName()): MoveTrait {
    override fun setup(game: ChessGame, move: Move) {
        if (nameTokens.isEmpty()) {
            nameTokens += MoveNameTokenType.PIECE_TYPE.of(move.piece.type)
            // TODO: clean this up
            nameTokens += MoveNameTokenType.UNIQUENESS_COORDINATE.of(getUniquenessCoordinate(game.board[move.piece.pos]!!.piece!!, game.board[move.getTrait<TargetTrait>()!!.target]!!))
        }
    }
}

@Serializable
class TargetTrait(val target: Pos, var hasMoved: Boolean = true): MoveTrait {
    override val nameTokens = MoveName(listOf(MoveNameTokenType.TARGET.of(target)))

    override fun execute(game: ChessGame, move: Move, pass: UByte, remaining: List<MoveTrait>): Boolean {
        game.board[target].let { t ->
            if (t == null || t.piece != null)
                return false
            game.board[move.piece.pos]?.piece.let { p ->
                if (p == null)
                    return false
                hasMoved = p.hasMoved
                p.move(t)
            }
        }
        return true
    }

    override fun undo(game: ChessGame, move: Move, pass: UByte, remaining: List<MoveTrait>): Boolean {
        game.board[move.piece.pos].let { t ->
            if (t == null || t.piece != null)
                return false
            game.board[target]?.piece.let { p ->
                if (p == null)
                    return false
                p.move(t)
                p.force(hasMoved)
            }
        }
        return true
    }
}