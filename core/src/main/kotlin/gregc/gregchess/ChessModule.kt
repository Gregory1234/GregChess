package gregc.gregchess

import gregc.gregchess.chess.*
import gregc.gregchess.chess.component.*
import gregc.gregchess.chess.variant.*
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
    private val registries = mutableMapOf<RegistryType<*, *, *>, Registry<*, *, *>>()
    var logger: GregLogger = SystemGregLogger()
    var locked: Boolean = false
        private set

    @Suppress("UNCHECKED_CAST")
    operator fun <K, T, R : Registry<K, T, R>> get(t: RegistryType<K, T, R>): R =
        registries.getOrPut(t) { t.createRegistry(this) } as R

    fun <K, T, V : T, R : Registry<K, T, R>> register(t: RegistryType<K, T, R>, key: K, v: V): V {
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
        registries.values.forEach { it.validate() }
        logger.info("Validated chess module $this")
        extensions.forEach {
            it.validate()
            logger.info("Validated chess extension $it")
        }
        modules += this
    }

    final override fun toString() = "$namespace@${hashCode().toString(16)}"
}

fun ChessModule.register(id: String, pieceType: PieceType) = register(RegistryType.PIECE_TYPE, id, pieceType)

fun <T : GameScore> ChessModule.register(id: String, endReason: EndReason<T>) =
    register(RegistryType.END_REASON, id, endReason)

fun ChessModule.register(id: String, variant: ChessVariant) = register(RegistryType.VARIANT, id, variant)

fun ChessModule.register(id: String, flagType: ChessFlagType) = register(RegistryType.FLAG_TYPE, id, flagType)

fun <T : Any> ChessModule.register(id: String, moveNameTokenType: MoveNameTokenType<T>) =
    register(RegistryType.MOVE_NAME_TOKEN_TYPE, id, moveNameTokenType)

@OptIn(InternalSerializationApi::class)
fun <T : Component, D : ComponentData<T>> ChessModule.registerComponent(
    id: String, tcl: KClass<T>, dcl: KClass<D>
) {
    tcl.companionObjectInstance
    dcl.companionObjectInstance
    register(RegistryType.COMPONENT_CLASS, id, tcl)
    register(RegistryType.COMPONENT_DATA_CLASS, tcl, dcl)
    register(RegistryType.COMPONENT_SERIALIZER, tcl, dcl.serializer())
}

inline fun <reified T : Component, reified D : ComponentData<T>> ChessModule.registerComponent(id: String) =
    registerComponent(id, T::class, D::class)

@OptIn(InternalSerializationApi::class)
fun <T : SimpleComponent> ChessModule.registerSimpleComponent(id: String, tcl: KClass<T>) {
    tcl.companionObjectInstance
    register(RegistryType.COMPONENT_CLASS, id, tcl)
    register(RegistryType.COMPONENT_DATA_CLASS, tcl, SimpleComponentData::class)
    register(RegistryType.COMPONENT_SERIALIZER, tcl, SimpleComponentDataSerializer(tcl))
}

@OptIn(InternalSerializationApi::class)
inline fun <reified T : SimpleComponent> ChessModule.registerSimpleComponent(id: String) =
    registerSimpleComponent(id, T::class)

inline fun <reified T : MoveTrait> ChessModule.registerMoveTrait(id: String) =
    register(RegistryType.MOVE_TRAIT_CLASS, id, T::class)

inline fun <reified T : ChessPlayerInfo> ChessModule.registerPlayerType(id: String) =
    register(RegistryType.PLAYER_TYPE, id, T::class)

object GregChessModule : ChessModule("gregchess") {

    private fun registerVariants() {
        register("normal", ChessVariant.Normal)
        register("antichess", Antichess)
        register("atomic", AtomicChess)
        register("capture_all", CaptureAll)
        register("horde", HordeChess)
        register("king_of_the_hill", KingOfTheHill)
        register("three_checks", ThreeChecks)
    }

    private fun registerComponents() {
        registerComponent<Chessboard, ChessboardState>("chessboard")
        registerComponent<ChessClock, ChessClockData>("clock")
        registerComponent<ThreeChecks.CheckCounter, ThreeChecks.CheckCounterData>("check_counter")
    }

    private fun registerMoveTraits() {
        registerMoveTrait<DefaultHalfmoveClockTrait>("halfmove_clock")
        registerMoveTrait<CastlesTrait>("castles")
        registerMoveTrait<PromotionTrait>("promotion")
        registerMoveTrait<NameTrait>("name")
        registerMoveTrait<CheckTrait>("check")
        registerMoveTrait<CaptureTrait>("capture")
        registerMoveTrait<PawnOriginTrait>("pawn_move")
        registerMoveTrait<PieceOriginTrait>("piece_move")
        registerMoveTrait<TargetTrait>("target")
        registerMoveTrait<AtomicChess.ExplosionTrait>("explosion")
        registerMoveTrait<ThreeChecks.CheckCounterTrait>("check_counter")
    }

    override fun load() {
        PieceType
        EndReason
        MoveNameTokenType
        PawnMovement
        registerComponents()
        registerVariants()
        registerMoveTraits()
    }
}