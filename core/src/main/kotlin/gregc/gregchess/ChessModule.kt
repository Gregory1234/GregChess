package gregc.gregchess

import gregc.gregchess.chess.*
import gregc.gregchess.chess.variant.*

interface ChessModuleExtension {
    fun load()
}

abstract class ChessModule(val namespace: String) {
    companion object {
        val modules = mutableSetOf<ChessModule>()
        fun getOrNull(namespace: String) = modules.firstOrNull { it.namespace == namespace }
        operator fun get(namespace: String) = modules.first { it.namespace == namespace }
    }
    val extensions = mutableSetOf<ChessModuleExtension>()

    abstract operator fun <K, T> get(t: RegistryType<K, T>): Registry<K, T>
    fun <K, T, R: T> register(t: RegistryType<K, T>, key: K, v: R): R {
        this[t][key] = v
        return v
    }
    protected abstract fun load()
    fun fullLoad() {
        load()
        extensions.forEach(ChessModuleExtension::load)
        modules += this
    }
}

val ChessModule.pieceTypes get() = this[RegistryType.PIECE_TYPE]
fun ChessModule.register(id: String, pieceType: PieceType) = register(RegistryType.PIECE_TYPE, id, pieceType)

val ChessModule.endReasons get() = this[RegistryType.END_REASON]
fun <T: GameScore> ChessModule.register(id: String, endReason: EndReason<T>) =
    register(RegistryType.END_REASON, id, endReason)

val ChessModule.variants get() = this[RegistryType.VARIANT]
fun ChessModule.register(id: String, variant: ChessVariant) = register(RegistryType.VARIANT, id, variant)

object GregChessModule : ChessModule("gregchess") {
    private val registries = mutableMapOf<RegistryType<*, *>, Registry<*, *>>()

    @Suppress("UNCHECKED_CAST")
    override fun <K, T> get(t: RegistryType<K, T>): Registry<K, T> =
        registries.getOrPut(t) { Registry(this, t) } as Registry<K, T>

    private fun registerVariants() {
        register("normal", ChessVariant.Normal)
        register("antichess", Antichess)
        register("atomic", AtomicChess)
        register("capture_all", CaptureAll)
        register("horde", HordeChess)
        register("king_of_the_hill", KingOfTheHill)
        register("three_checks", ThreeChecks)
    }

    override fun load() {
        PieceType.Companion
        EndReason.Companion
        registerVariants()
    }
}