package gregc.gregchess

import gregc.gregchess.chess.*
import gregc.gregchess.chess.component.ChessClock
import gregc.gregchess.chess.component.PropertyType
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
fun ChessModule.register(pieceType: PieceType) =
    register(RegistryType.PIECE_TYPE, pieceType.name.lowercase(), pieceType)

val ChessModule.endReasons get() = this[RegistryType.END_REASON]
fun <T: GameScore> ChessModule.register(endReason: EndReason<T>) =
    register(RegistryType.END_REASON, endReason.name.lowercase(), endReason)

val ChessModule.propertyTypes get() = this[RegistryType.PROPERTY_TYPE]
fun <T> ChessModule.register(propertyType: PropertyType<T>) =
    register(RegistryType.PROPERTY_TYPE, propertyType.name.lowercase(), propertyType)

val ChessModule.variants get() = this[RegistryType.VARIANT]
fun ChessModule.register(variant: ChessVariant) =
    register(RegistryType.VARIANT, variant.name.lowercase(), variant)

object GregChessModule : ChessModule("gregchess") {
    private val registries = mutableMapOf<RegistryType<*, *>, Registry<*, *>>()

    @Suppress("UNCHECKED_CAST")
    override fun <K, T> get(t: RegistryType<K, T>): Registry<K, T> =
        registries.getOrPut(t) { Registry(this, t) } as Registry<K, T>


    override fun load() {
        PieceType.Companion
        EndReason.Companion
        ChessClock.Companion
        for (v in listOf(ChessVariant.Normal, Antichess, AtomicChess, CaptureAll, HordeChess, KingOfTheHill, ThreeChecks))
            register(RegistryType.VARIANT, v.name.lowercase(), v)
    }
}