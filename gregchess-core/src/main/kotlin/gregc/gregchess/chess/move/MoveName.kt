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
        NameRegisteredSerializer<MoveNameTokenType<*>>("MoveNameTokenType", Registry.MOVE_NAME_TOKEN_TYPE)

    override val key get() = Registry.MOVE_NAME_TOKEN_TYPE[this]

    override fun toString(): String = "$key@${hashCode().toString(16)}"

    companion object {

        private inline fun <reified T : Any> mk(name: String) =
            GregChess.registerMoveNameTokenType(name, MoveNameTokenType(T::class))

        @JvmField
        val PIECE_TYPE = mk<PieceType>("piece_type")
        @JvmField
        val UNIQUENESS_COORDINATE = mk<UniquenessCoordinate>("uniqueness_coordinate")
        @JvmField
        val CAPTURE = mk<Unit>("capture")
        @JvmField
        val TARGET = mk<Pos>("target")
        @JvmField
        val PROMOTION = mk<PieceType>("promotion")
        @JvmField
        val CHECK = mk<Unit>("check")
        @JvmField
        val CHECKMATE = mk<Unit>("checkmate")
        @JvmField
        val CASTLE = mk<BoardSide>("castle")
        @JvmField
        val EN_PASSANT = mk<Unit>("en_passant")
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