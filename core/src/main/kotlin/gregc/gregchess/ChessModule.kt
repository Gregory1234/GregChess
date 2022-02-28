package gregc.gregchess

import gregc.gregchess.chess.*
import gregc.gregchess.chess.component.Component
import gregc.gregchess.chess.component.ComponentType
import gregc.gregchess.chess.move.*
import gregc.gregchess.chess.piece.*
import gregc.gregchess.chess.player.ChessPlayer
import gregc.gregchess.chess.player.ChessPlayerType
import gregc.gregchess.chess.variant.ChessVariant
import gregc.gregchess.registry.Registry
import gregc.gregchess.registry.RegistryBlock
import org.slf4j.Logger
import org.slf4j.LoggerFactory

abstract class ChessModule(val name: String, val namespace: String) {
    val logger: Logger = LoggerFactory.getLogger(name)
    private var lockedBefore = true
    private var lockedAfter = false
    val locked: Boolean get() = lockedBefore || lockedAfter

    operator fun <K, T, B : RegistryBlock<K, T>> get(t: Registry<K, T, B>): B = t[this]

    fun <K, T, V : T, B : RegistryBlock<K, T>> register(t: Registry<K, T, B>, key: K, v: V): V {
        t[this, key] = v
        return v
    }

    protected abstract fun load()
    protected abstract fun postLoad()
    protected abstract fun validate()
    protected abstract fun finish()
    fun fullLoad() {
        require(lockedBefore && !lockedAfter) { "Module $this was already loaded!" }
        lockedBefore = false
        load()
        postLoad()
        logger.info("Loaded chess module $this")
        lockedAfter = true
        validate()
        logger.info("Validated chess module $this")
        finish()
    }

    final override fun toString() = "$namespace@${hashCode().toString(16)}"
}

fun PieceType.register(module: ChessModule, id: String) = module.register(Registry.PIECE_TYPE, id, this)

fun <T : GameScore> EndReason<T>.register(module: ChessModule, id: String) =
    module.register(Registry.END_REASON, id, this)

fun ChessVariant.register(module: ChessModule, id: String) = module.register(Registry.VARIANT, id, this)

fun ChessFlag.register(module: ChessModule, id: String) = module.register(Registry.FLAG, id, this)

fun <T : MoveTrait> MoveTraitType<T>.register(module: ChessModule, id: String) =
    module.register(Registry.MOVE_TRAIT_TYPE, id, this)

fun <T : Component> ComponentType<T>.register(module: ChessModule, id: String) =
    module.register(Registry.COMPONENT_TYPE, id, this)

fun <T : ChessPlayer> ChessPlayerType<T>.register(module: ChessModule, id: String) =
    module.register(Registry.PLAYER_TYPE, id, this)

fun <T : PlacedPiece> PlacedPieceType<T>.register(module: ChessModule, id: String) =
    module.register(Registry.PLACED_PIECE_TYPE, id, this)

fun <T> ChessVariantOption<T>.register(module: ChessModule, id: String) =
    module.register(Registry.VARIANT_OPTION, id, this)

fun <T : Any> ChessStat<T>.register(module: ChessModule, id: String) =
    module.register(Registry.STAT, id, this)