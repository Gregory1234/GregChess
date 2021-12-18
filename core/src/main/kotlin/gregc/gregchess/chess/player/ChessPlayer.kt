package gregc.gregchess.chess.player

import gregc.gregchess.chess.ChessGame
import gregc.gregchess.chess.Color
import gregc.gregchess.registry.*
import kotlinx.serialization.*
import kotlinx.serialization.descriptors.*
import kotlinx.serialization.encoding.*
import kotlin.reflect.KClass
import kotlin.reflect.full.superclasses

// TODO: consider adding back a separate player interface
@Serializable(with = ChessPlayerType.Serializer::class)
class ChessPlayerType<T : Any>(
    val playerSerializer: KSerializer<T>,
    val createPlayer: T.(Color, ChessGame) -> ChessPlayer<T>
): NameRegistered {

    object Serializer : NameRegisteredSerializer<ChessPlayerType<*>>("ChessPlayerType", Registry.PLAYER_TYPE)

    override val key: RegistryKey<String> get() = Registry.PLAYER_TYPE[this]
}

fun playerType(p: Any) : ChessPlayerType<*> {
    val visited = mutableListOf<KClass<*>>()
    val q = mutableListOf<KClass<*>>()
    var cl: KClass<*> = p::class
    while (true) {
        val ret = Registry.PLAYER_TYPE_CLASS.getOrNull(cl)
        if (ret != null)
            return ret.key
        else {
            visited += cl
            q.addAll(cl.superclasses)
            q.removeAll(visited)
            cl = q.removeFirst()
        }
    }
}

@OptIn(ExperimentalSerializationApi::class, InternalSerializationApi::class)
@Suppress("UNCHECKED_CAST")
object ChessPlayerDataSerializer : KSerializer<Any> {
    override val descriptor: SerialDescriptor = buildClassSerialDescriptor("ChessPlayerData") {
        element<String>("type")
        element("value", buildSerialDescriptor("ChessPlayerValue", SerialKind.CONTEXTUAL))
    }

    override fun serialize(encoder: Encoder, value: Any) {
        val type = playerType(value)
        val actualSerializer = type.playerSerializer
        val id = type.key.toString()
        encoder.encodeStructure(descriptor) {
            encodeStringElement(descriptor, 0, id)
            encodeSerializableElement(descriptor, 1, actualSerializer as KSerializer<Any>, value)
        }
    }

    override fun deserialize(decoder: Decoder): Any = decoder.decodeStructure(descriptor) {
        var type: ChessPlayerType<*>? = null
        var ret: Any? = null

        if (decodeSequentially()) { // sequential decoding protocol
            type = Registry.PLAYER_TYPE[decodeStringElement(descriptor, 0).toKey()]
            ret = decodeSerializableElement(descriptor, 1, type.playerSerializer)
        } else {
            while (true) {
                when (val index = decodeElementIndex(descriptor)) {
                    0 -> type = Registry.PLAYER_TYPE[decodeStringElement(descriptor, 0).toKey()]
                    1 -> ret = decodeSerializableElement(descriptor, 1, type!!.playerSerializer)
                    CompositeDecoder.DECODE_DONE -> break
                    else -> error("Unexpected index: $index")
                }
            }
        }
        ret!!
    }
}

abstract class ChessPlayer<T : Any>(val player: T, val color: Color, val name: String, val game: ChessGame) {

    @Suppress("UNCHECKED_CAST")
    val type: ChessPlayerType<T> get() = playerType(player) as ChessPlayerType<T>

    val opponent
        get() = game[!color]

    val hasTurn
        get() = game.currentTurn == color

    val pieces
        get() = game.board.piecesOf(color)

    val king
        get() = game.board.kingOf(color)

    open fun init() {}
    open fun stop() {}
    open fun startTurn() {}

}