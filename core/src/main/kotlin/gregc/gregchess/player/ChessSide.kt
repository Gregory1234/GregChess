package gregc.gregchess.player

import gregc.gregchess.*
import gregc.gregchess.event.ChessEvent
import gregc.gregchess.event.ChessEventRegistry
import gregc.gregchess.match.AnyFacade
import gregc.gregchess.match.ChessMatch
import gregc.gregchess.registry.*
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.modules.SerializersModule

fun interface ChessPlayer<out S : ChessSide> {
    fun createChessSide(color: Color): S
}

@Serializable(with = ChessSideType.Serializer::class)
class ChessSideType<T: ChessSide>(val serializer: KSerializer<T>) : NameRegistered {
    object Serializer : NameRegisteredSerializer<ChessSideType<*>>("ChessSideType", Registry.SIDE_TYPE)

    override val key get() = Registry.SIDE_TYPE[this]

    override fun toString(): String = Registry.SIDE_TYPE.simpleElementToString(this)

    companion object {
        internal val AUTO_REGISTER = AutoRegisterType(ChessSideType::class) { m, n, _ -> Registry.SIDE_TYPE[m, n] = this }
    }
}

// TODO: add an interface for human players
@Serializable(with = ChessSideSerializer::class)
interface ChessSide {
    val name: String
    val color: Color

    val type: ChessSideType<out @SelfType ChessSide>

    fun init(match: ChessMatch, events: ChessEventRegistry) {}

    fun createFacade(match: ChessMatch): ChessSideFacade<*>
}

object ChessSideSerializer : KeyRegisteredSerializer<ChessSideType<*>, ChessSide>("ChessSide", ChessSideType.Serializer) {

    @Suppress("UNCHECKED_CAST")
    override fun ChessSideType<*>.valueSerializer(module: SerializersModule): KSerializer<ChessSide> = serializer as KSerializer<ChessSide>

    override val ChessSide.key: ChessSideType<*> get() = type

}

abstract class ChessSideFacade<T : ChessSide>(final override val match: ChessMatch, val side: T) : AnyFacade {
    @Suppress("UNCHECKED_CAST")
    val type: ChessSideType<T> get() = side.type as ChessSideType<T>

    val name get() = side.name
    val color get() = side.color

    val hasTurn get() = match.board.currentTurn == color
    val opponent get() = match.sideFacades[!color]

    final override fun callEvent(event: ChessEvent) = super.callEvent(event)
}