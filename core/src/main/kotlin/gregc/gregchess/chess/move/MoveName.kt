package gregc.gregchess.chess.move

import gregc.gregchess.*
import gregc.gregchess.chess.*
import gregc.gregchess.chess.piece.PieceType
import gregc.gregchess.registry.*
import kotlinx.serialization.*
import kotlinx.serialization.modules.SerializersModule
import kotlin.reflect.KClass
import kotlin.reflect.full.createType

@Serializable(with = MoveNameTokenType.Serializer::class)
class MoveNameTokenType<T : Any>(val cl: KClass<T>) : NameRegistered {

    object Serializer :
        NameRegisteredSerializer<MoveNameTokenType<*>>("MoveNameTokenType", RegistryType.MOVE_NAME_TOKEN_TYPE)

    override val key get() = RegistryType.MOVE_NAME_TOKEN_TYPE[this]

    override fun toString(): String = "$key@${hashCode().toString(16)}"

    companion object {

        @JvmField
        val PIECE_TYPE = GregChess.register("piece_type", MoveNameTokenType(PieceType::class))
        @JvmField
        val UNIQUENESS_COORDINATE = GregChess.register("uniqueness_coordinate", MoveNameTokenType(UniquenessCoordinate::class))
        @JvmField
        val CAPTURE = GregChess.register("capture", MoveNameTokenType(Unit::class))
        @JvmField
        val TARGET = GregChess.register("target", MoveNameTokenType(Pos::class))
        @JvmField
        val PROMOTION = GregChess.register("promotion", MoveNameTokenType(PieceType::class))
        @JvmField
        val CHECK = GregChess.register("check", MoveNameTokenType(Unit::class))
        @JvmField
        val CHECKMATE = GregChess.register("checkmate", MoveNameTokenType(Unit::class))
        @JvmField
        val CASTLE = GregChess.register("castle", MoveNameTokenType(BoardSide::class))
        @JvmField
        val EN_PASSANT = GregChess.register("en_passant", MoveNameTokenType(Unit::class))
    }
}

@Serializable(with = MoveName.Serializer::class)
data class MoveName(private val tokens: Map<MoveNameTokenType<*>, Any>) {
    constructor(names: Collection<MoveName>) : this(names.flatMap { it.tokens.toList() }.toMap())
    init {
        for ((t,v) in tokens)
            require(t.cl.isInstance(v))
    }

    operator fun <T : Any> get(type: MoveNameTokenType<T>): T = getOrNull(type)!!

    @Suppress("UNCHECKED_CAST")
    fun <T : Any> getOrNull(type: MoveNameTokenType<T>): T? = tokens[type] as T?

    fun format(formatter: MoveNameFormatter): String = formatter.format(this)

    object Serializer : ClassMapSerializer<MoveName, MoveNameTokenType<*>, Any>("MoveName", MoveNameTokenType.Serializer) {
        override fun MoveName.asMap(): Map<MoveNameTokenType<*>, Any> = tokens
        override fun fromMap(m: Map<MoveNameTokenType<*>, Any>): MoveName = MoveName(m)
        @Suppress("UNCHECKED_CAST")
        override fun MoveNameTokenType<*>.valueSerializer(module: SerializersModule): KSerializer<Any> =
            module.serializer(cl.createType()) as KSerializer<Any>
    }
}

fun interface MoveNameFormatter {
    fun format(name: MoveName): String
}