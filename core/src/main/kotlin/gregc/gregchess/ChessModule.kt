package gregc.gregchess

import gregc.gregchess.chess.*
import gregc.gregchess.chess.component.ChessClock
import gregc.gregchess.chess.component.PropertyType
import gregc.gregchess.chess.variant.*

interface ChessModule {
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
    val modules = mutableListOf<MainChessModule>(this)
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
    override val extensions = mutableListOf<ChessModuleExtension>()
    override val namespace = "gregchess"
    override fun load() {
        PieceType.Companion
        EndReason.Companion
        ChessClock.Companion
        variants_
        extensions.forEach(ChessModuleExtension::load)
    }
    fun pieceTypeModule(pieceType: PieceType) = modules.first {pieceType in it.pieceTypes}
    fun endReasonModule(endReason: EndReason<*>) = modules.first {endReason in it.endReasons}
    fun propertyTypeModule(propertyType: PropertyType<*>) = modules.first {propertyType in it.propertyTypes}
    fun variantModule(variant: ChessVariant) = modules.first {variant in it.variants}
    fun getModuleOrNull(namespace: String) = modules.firstOrNull { it.namespace == namespace }
    fun getModule(namespace: String) = modules.first { it.namespace == namespace }
}