package gregc.gregchess

import gregc.gregchess.chess.*
import gregc.gregchess.chess.component.Component
import gregc.gregchess.chess.component.ComponentType
import gregc.gregchess.chess.move.*
import gregc.gregchess.chess.piece.PieceType
import gregc.gregchess.chess.piece.PlacedPiece
import gregc.gregchess.chess.player.ChessPlayer
import gregc.gregchess.chess.player.ChessPlayerType
import gregc.gregchess.chess.variant.ChessVariant
import gregc.gregchess.registry.Registry
import gregc.gregchess.registry.RegistryBlock
import org.slf4j.Logger
import org.slf4j.LoggerFactory

fun interface ChessExtension {
    fun load()
}

abstract class ChessModule(val name: String, val namespace: String) {
    companion object {
        val modules = mutableSetOf<ChessModule>()
        operator fun get(namespace: String) = modules.first { it.namespace == namespace }
    }

    val logger: Logger = LoggerFactory.getLogger(name)
    var locked: Boolean = false
        private set

    operator fun <K, T, B : RegistryBlock<K, T>> get(t: Registry<K, T, B>): B = t[this]

    fun <K, T, V : T, B : RegistryBlock<K, T>> register(t: Registry<K, T, B>, key: K, v: V): V {
        t[this, key] = v
        return v
    }

    protected abstract fun load()
    fun fullLoad(extensions: Collection<ChessExtension> = emptyList()) {
        require(!locked) { "Module $this was already loaded!" }
        load()
        logger.info("Loaded chess module $this")
        extensions.forEach { it.load() }
        logger.info("Loaded ${extensions.size} extensions to $this")
        locked = true
        Registry.REGISTRIES.forEach { it[this].validate() }
        logger.info("Validated chess module $this")
        modules += this
    }

    final override fun toString() = "$namespace@${hashCode().toString(16)}"
}

fun PieceType.register(module: ChessModule, id: String) = module.register(Registry.PIECE_TYPE, id, this)

fun <T : GameScore> EndReason<T>.register(module: ChessModule, id: String) =
    module.register(Registry.END_REASON, id, this)

fun ChessVariant.register(module: ChessModule, id: String) = module.register(Registry.VARIANT, id, this)

fun ChessFlag.register(module: ChessModule, id: String) = module.register(Registry.FLAG, id, this)

fun <T : Any> MoveNameTokenType<T>.register(module: ChessModule, id: String) =
    module.register(Registry.MOVE_NAME_TOKEN_TYPE, id, this)

fun <T : MoveTrait> MoveTraitType<T>.register(module: ChessModule, id: String) =
    module.register(Registry.MOVE_TRAIT_TYPE, id, this)

fun <T : Component> ComponentType<T>.register(module: ChessModule, id: String) =
    module.register(Registry.COMPONENT_TYPE, id, this)

fun <T : ChessPlayer> ChessPlayerType<T>.register(module: ChessModule, id: String) =
    module.register(Registry.PLAYER_TYPE, id, this)

inline fun <reified T : PlacedPiece> ChessModule.registerPlacedPieceClass(id: String) =
    register(Registry.PLACED_PIECE_CLASS, id, T::class)