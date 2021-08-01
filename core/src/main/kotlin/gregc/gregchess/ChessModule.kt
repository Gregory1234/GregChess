package gregc.gregchess

import gregc.gregchess.chess.*
import gregc.gregchess.chess.component.ChessClock
import gregc.gregchess.chess.component.PropertyType
import gregc.gregchess.chess.variant.*

interface ChessModule {
    companion object {
        val modules = mutableSetOf<MainChessModule>()
        operator fun get(pieceType: PieceType) = modules.first {pieceType in it.pieceTypes}
        operator fun get(endReason: EndReason<*>) = modules.first {endReason in it.endReasons}
        operator fun get(propertyType: PropertyType<*>) = modules.first {propertyType in it.propertyTypes}
        operator fun get(variant: ChessVariant) = modules.first {variant in it.variants}
        fun getOrNull(namespace: String) = modules.firstOrNull { it.namespace == namespace }
        operator fun get(namespace: String) = modules.first { it.namespace == namespace }
    }

    val pieceTypes: Collection<PieceType> get() = emptyList()
    val variants: Collection<ChessVariant> get() = emptyList()
    val endReasons: Collection<EndReason<*>> get() = emptyList()
    val propertyTypes: Collection<PropertyType<*>> get() = emptyList()
    fun load()
}

interface MainChessModule: ChessModule {
    val extensions: MutableCollection<ChessModuleExtension>
    val namespace: String
}

interface ChessModuleExtension: ChessModule {
    val base: MainChessModule
}

object GregChessModule: MainChessModule {
    private val pieceTypes_ = mutableListOf<PieceType>()
    private val endReasons_ = mutableListOf<EndReason<*>>()
    private val propertyTypes_ = mutableListOf<PropertyType<*>>()
    internal fun register(pieceType: PieceType): PieceType { pieceTypes_ += pieceType; return pieceType }
    internal fun <T: GameScore> register(endReason: EndReason<T>): EndReason<T> { endReasons_ += endReason; return endReason }
    internal fun <T> register(propertyType: PropertyType<T>): PropertyType<T> { propertyTypes_ += propertyType; return propertyType }
    private val variants_ = listOf(ChessVariant.Normal, Antichess, AtomicChess, CaptureAll, HordeChess, KingOfTheHill, ThreeChecks)
    override val pieceTypes get() = pieceTypes_.toList() + extensions.flatMap { it.pieceTypes }
    override val variants get() = variants_ + extensions.flatMap { it.variants }
    override val endReasons get() = endReasons_.toList() + extensions.flatMap { it.endReasons }
    override val propertyTypes get() = propertyTypes_.toList() + extensions.flatMap { it.propertyTypes }

    override val extensions = mutableSetOf<ChessModuleExtension>()
    override val namespace = "gregchess"
    override fun load() {
        PieceType.Companion
        EndReason.Companion
        ChessClock.Companion
        variants_
        ChessModule.modules += this
        extensions.forEach(ChessModuleExtension::load)
    }
}