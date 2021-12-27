package gregc.gregchess

import gregc.gregchess.chess.*
import gregc.gregchess.chess.component.*
import gregc.gregchess.chess.move.MoveNameTokenType
import gregc.gregchess.chess.move.MoveTrait
import gregc.gregchess.chess.piece.PieceType
import gregc.gregchess.chess.piece.PlacedPiece
import gregc.gregchess.chess.player.ChessPlayer
import gregc.gregchess.chess.variant.ChessVariant
import gregc.gregchess.registry.Registry
import gregc.gregchess.registry.RegistryBlock
import kotlinx.serialization.InternalSerializationApi
import kotlinx.serialization.serializer
import kotlin.reflect.KClass
import kotlin.reflect.full.companionObjectInstance

class ChessModuleValidationException(val module: ChessModule, val text: String) : IllegalStateException("$module: $text")
class ChessExtensionValidationException(val ext: ChessExtension, val text: String) : IllegalStateException("$ext: $text")

class ExtensionType(val name: String) {
    companion object {
        val extensionTypes = mutableSetOf<ExtensionType>()
    }

    override fun toString() = "$name@${hashCode().toString(16)}"
}

abstract class ChessExtension(val module: ChessModule, val extensionType: ExtensionType) {
    abstract fun load()

    protected inline fun requireValid(condition: Boolean, message: () -> String) {
        if (!condition)
            throw ChessExtensionValidationException(this, message())
    }

    open fun validate() {}
    final override fun toString() = "${module.namespace}:${extensionType.name}@${hashCode().toString(16)}"
}

abstract class ChessModule(val namespace: String) {
    companion object {
        val modules = mutableSetOf<ChessModule>()
        fun getOrNull(namespace: String) = modules.firstOrNull { it.namespace == namespace }
        operator fun get(namespace: String) = modules.first { it.namespace == namespace }
    }

    val extensions = mutableSetOf<ChessExtension>()
    var logger: GregLogger = SystemGregLogger()
    var locked: Boolean = false
        private set

    operator fun <K, T, B : RegistryBlock<K, T>> get(t: Registry<K, T, B>): B = t[this]

    fun <K, T, V : T, B : RegistryBlock<K, T>> register(t: Registry<K, T, B>, key: K, v: V): V {
        t[this, key] = v
        return v
    }

    private inline fun requireValid(condition: Boolean, message: () -> String) {
        if (!condition)
            throw ChessModuleValidationException(this, message())
    }

    protected abstract fun load()
    fun fullLoad() {
        load()
        logger.info("Loaded chess module $this")
        ExtensionType.extensionTypes.forEach { et ->
            val count = extensions.count { e -> e.extensionType == et }
            requireValid(count != 0) { "Extension $et not registered" }
            requireValid(count == 1) { "Extension $et registered multiple times: $count" }
        }
        extensions.forEach {
            if (it.extensionType in ExtensionType.extensionTypes) {
                it.load()
                logger.info("Loaded chess extension $it")
            } else {
                logger.warn("Unknown extension $it")
            }
        }
        locked = true
        Registry.REGISTRIES.forEach { it[this].validate() }
        logger.info("Validated chess module $this")
        extensions.forEach {
            it.validate()
            logger.info("Validated chess extension $it")
        }
        modules += this
    }

    final override fun toString() = "$namespace@${hashCode().toString(16)}"
}

fun ChessModule.register(id: String, pieceType: PieceType) = register(Registry.PIECE_TYPE, id, pieceType)

fun <T : GameScore> ChessModule.register(id: String, endReason: EndReason<T>) =
    register(Registry.END_REASON, id, endReason)

fun ChessModule.register(id: String, variant: ChessVariant) = register(Registry.VARIANT, id, variant)

fun ChessModule.register(id: String, flagType: ChessFlag) = register(Registry.FLAG, id, flagType)

fun <T : Any> ChessModule.register(id: String, moveNameTokenType: MoveNameTokenType<T>) =
    register(Registry.MOVE_NAME_TOKEN_TYPE, id, moveNameTokenType)

@OptIn(InternalSerializationApi::class)
fun <T : Component, D : ComponentData<T>> ChessModule.registerComponent(
    id: String, tcl: KClass<T>, dcl: KClass<D>
) {
    tcl.companionObjectInstance
    dcl.companionObjectInstance
    register(Registry.COMPONENT_CLASS, id, tcl)
    register(Registry.COMPONENT_DATA_CLASS, tcl, dcl)
    register(Registry.COMPONENT_SERIALIZER, tcl, dcl.serializer())
}

inline fun <reified T : Component, reified D : ComponentData<T>> ChessModule.registerComponent(id: String) =
    registerComponent(id, T::class, D::class)

fun <T : SimpleComponent> ChessModule.registerSimpleComponent(id: String, tcl: KClass<T>) {
    tcl.companionObjectInstance
    register(Registry.COMPONENT_CLASS, id, tcl)
    register(Registry.COMPONENT_DATA_CLASS, tcl, SimpleComponentData::class)
    register(Registry.COMPONENT_SERIALIZER, tcl, SimpleComponentDataSerializer(tcl))
}

inline fun <reified T : SimpleComponent> ChessModule.registerSimpleComponent(id: String) =
    registerSimpleComponent(id, T::class)

inline fun <reified T : MoveTrait> ChessModule.registerMoveTrait(id: String) =
    register(Registry.MOVE_TRAIT_CLASS, id, T::class)

inline fun <reified T : ChessPlayer> ChessModule.register(id: String) =
    register(Registry.PLAYER_CLASS, id, T::class)


inline fun <reified T : PlacedPiece> ChessModule.registerPlacedPieceClass(id: String) =
    register(Registry.PLACED_PIECE_CLASS, id, T::class)