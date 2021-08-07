package gregc.gregchess.chess.component

import gregc.gregchess.chess.ChessGame
import kotlin.reflect.KClass

interface Component {
    interface Settings<out T : Component> {
        fun getComponent(game: ChessGame): T
    }
}

class ComponentNotFoundException(cl: KClass<out Component>) : Exception(cl.toString())
class ComponentSettingsNotFoundException(cl: KClass<out Component.Settings<*>>) : Exception(cl.toString())