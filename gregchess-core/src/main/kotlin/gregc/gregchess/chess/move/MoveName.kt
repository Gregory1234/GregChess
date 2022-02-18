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

    override fun toString(): String = Registry.MOVE_NAME_TOKEN_TYPE.simpleElementToString(this)

    @RegisterAll(MoveNameTokenType::class)
    companion object {

        internal val AUTO_REGISTER = AutoRegisterType(MoveNameTokenType::class) { m, n, _ -> register(m, n) }

        @JvmField
        val PIECE_TYPE = MoveNameTokenType(PieceType::class)
        @JvmField
        val UNIQUENESS_COORDINATE = MoveNameTokenType(UniquenessCoordinate::class)
        @JvmField
        val CAPTURE = MoveNameTokenType(Unit::class)
        @JvmField
        val TARGET = MoveNameTokenType(Pos::class)
        @JvmField
        val PROMOTION = MoveNameTokenType(PieceType::class)
        @JvmField
        val CHECK = MoveNameTokenType(Unit::class)
        @JvmField
        val CHECKMATE = MoveNameTokenType(Unit::class)
        @JvmField
        val CASTLE = MoveNameTokenType(BoardSide::class)
        @JvmField
        val EN_PASSANT = MoveNameTokenType(Unit::class)

        fun registerCore(module: ChessModule) = AutoRegister(module, listOf(AUTO_REGISTER)).registerAll<MoveNameTokenType<*>>()
    }
}

// TODO: consider removing MoveName and MoveNameTokenType
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