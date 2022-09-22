package gregc.gregchess.piece

import gregc.gregchess.Color
import gregc.gregchess.CoreRegistry
import gregc.gregchess.registry.*
import gregc.gregchess.registry.view.FiniteBiRegistryView
import kotlinx.serialization.Serializable

object PieceRegistryView : FiniteBiRegistryView<String, Piece> {

    override fun getOrNull(value: Piece): RegistryKey<String>? =
        CoreRegistry.PIECE_TYPE.getOrNull(value.type)?.let { (module, name) ->
            RegistryKey(module, "${value.color.toString().lowercase()}_$name")
        }

    override val values: Set<Piece>
        get() = CoreRegistry.PIECE_TYPE.values.flatMap { listOf(white(it), black(it)) }.toSet()

    override fun valuesOf(module: ChessModule): Set<Piece> =
        CoreRegistry.PIECE_TYPE.valuesOf(module).flatMap { listOf(white(it), black(it)) }.toSet()

    override val keys: Set<RegistryKey<String>>
        get() = values.map { get(it) }.toSet()

    override fun getOrNull(module: ChessModule, key: String): Piece? = when (key.take(6)) {
        "white_" -> CoreRegistry.PIECE_TYPE.getOrNull(module, key.drop(6))?.of(Color.WHITE)
        "black_" -> CoreRegistry.PIECE_TYPE.getOrNull(module, key.drop(6))?.of(Color.BLACK)
        else -> null
    }

    override fun keysOf(module: ChessModule): Set<String> =
        CoreRegistry.PIECE_TYPE.keysOf(module).flatMap { listOf("white_$it", "black_$it") }.toSet()
}

@Serializable(with = Piece.Serializer::class)
data class Piece(val type: PieceType, val color: Color) : NameRegistered {
    override val key: RegistryKey<String> get() = PieceRegistryView[this]

    object Serializer : NameRegisteredSerializer<Piece>("Piece", PieceRegistryView)

    val char : Char
        get() = when (color) {
            Color.WHITE -> type.char.uppercaseChar()
            Color.BLACK -> type.char
        }


}

fun PieceType.of(color: Color) = Piece(this, color)

fun white(type: PieceType) = type.of(Color.WHITE)
fun black(type: PieceType) = type.of(Color.BLACK)
