package gregc.gregchess

import gregc.gregchess.chess.*
import gregc.gregchess.chess.component.*
import gregc.gregchess.chess.variant.*

interface ChessModuleExtension {
    fun load()
    fun validate(main: ChessModule) {}
}

abstract class ChessModule(val namespace: String) {
    companion object {
        val modules = mutableSetOf<ChessModule>()
        fun getOrNull(namespace: String) = modules.firstOrNull { it.namespace == namespace }
        operator fun get(namespace: String) = modules.first { it.namespace == namespace }
    }
    val extensions = mutableSetOf<ChessModuleExtension>()
    private val registries = mutableMapOf<RegistryType<*, *, *>, Registry<*, *, *>>()

    @Suppress("UNCHECKED_CAST")
    operator fun <K, T, R: Registry<K, T, R>> get(t: RegistryType<K, T, R>): R =
        registries.getOrPut(t) { t.createRegistry(this) } as R
    fun <K, T, V: T, R : Registry<K, T, R>> register(t: RegistryType<K, T, R>, key: K, v: V): V {
        this[t][key] = v
        return v
    }
    protected abstract fun load()
    fun fullLoad() {
        load()
        extensions.forEach { it.load() }
        registries.values.forEach { it.validate() }
        extensions.forEach { it.validate(this) }
        modules += this
    }
}

fun ChessModule.register(id: String, pieceType: PieceType) = register(RegistryType.PIECE_TYPE, id, pieceType)

fun <T: GameScore> ChessModule.register(id: String, endReason: EndReason<T>) =
    register(RegistryType.END_REASON, id, endReason)

fun ChessModule.register(id: String, variant: ChessVariant) = register(RegistryType.VARIANT, id, variant)

fun ChessModule.register(id: String, flagType: ChessFlagType) = register(RegistryType.FLAG_TYPE, id, flagType)

fun <T: Any> ChessModule.register(id: String, moveNameTokenType: MoveNameTokenType<T>) =
    register(RegistryType.MOVE_NAME_TOKEN_TYPE, id, moveNameTokenType)

inline fun <reified T: Component, reified D: ComponentData<T>> ChessModule.registerComponent(id: String) {
    register(RegistryType.COMPONENT_CLASS, id, T::class)
    register(RegistryType.COMPONENT_DATA_CLASS, T::class, D::class)
}

inline fun <reified T: MoveTrait> ChessModule.registerMoveTrait(id: String) =
    register(RegistryType.MOVE_TRAIT_CLASS, id, T::class)

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
        registerComponent<AtomicChess.ExplosionManager, AtomicChess.ExplosionManagerData>("explosion_manager")
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